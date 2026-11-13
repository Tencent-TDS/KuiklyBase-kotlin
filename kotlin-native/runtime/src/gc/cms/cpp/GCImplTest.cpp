/*
 * Copyright 2010-2023 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

#include "GCImpl.hpp"

#include <mutex>
#include <atomic>

#include "gtest/gtest.h"

#include "ExternalRCRef.hpp"
#include "GlobalData.hpp"
#include "TestSupport.hpp"
#include "TracingGCTest.hpp"

using namespace kotlin;

namespace {

class ConcurrentMarkAndSweepTest {
public:
    ~ConcurrentMarkAndSweepTest() {
        mm::GlobalsRegistry::Instance().ClearForTests();
        mm::ExternalRCRefRegistry::instance().clearForTests();
        mm::GlobalData::Instance().gc().ClearForTests();
        mm::GlobalData::Instance().allocator().clearForTests();
    }

    void SetUp() {}
};

} // namespace

TYPED_TEST_P(TracingGCTest, WeakResurrectionAtMarkTermination) {
    std::vector<Mutator> mutators(kDefaultThreadCount);
    std::vector<test_support::RegularWeakReferenceImpl*> weaks(kDefaultThreadCount);
    std::vector<test_support::Object<Payload>*> roots(kDefaultThreadCount);

    // initialize threads
    for (int i = 0; i < kDefaultThreadCount; ++i) {
        mutators[i]
                .Execute([&, i](mm::ThreadData& threadData, Mutator& mutator) {
                    roots[i] = &AllocateObject(threadData);
                    mutator.AddGlobalRoot(roots[i]->header());

                    auto& weakReferee = AllocateObject(threadData);
                    auto& weakRef = [&threadData, &weakReferee]() -> test_support::RegularWeakReferenceImpl& {
                        ObjHolder holder;
                        return test_support::InstallWeakReference(threadData, weakReferee.header(), holder.slot());
                    }();
                    EXPECT_NE(weakRef.get(), nullptr);
                    weaks[i] = &weakRef;
                    mutator.AddGlobalRoot(weakRef.header());
                })
                .wait();
    }

    auto epoch = mm::GlobalData::Instance().gc().Schedule();
    std::atomic gcDone = false;

    // Spin until thread suspension is requested.
    while (!mm::IsThreadSuspensionRequested()) {
    }

    std::vector<std::future<void>> mutatorFutures;
    for (int i = 0; i < kDefaultThreadCount; ++i) {
        mutatorFutures.emplace_back(mutators[i].Execute([&, i](mm::ThreadData& threadData, Mutator& mutator) noexcept {
            while (!gc::mark::test_support::flushActionRequested() && !gcDone.load(std::memory_order_relaxed)) {
                safePoint(threadData);
            }

            threadData.gc().impl().mark_.onSafePoint();

            auto weakReferee = weaks[i]->get();
            (*roots[i])->field2 = weakReferee;
            bool resurrected = weakReferee != nullptr;

            while (!gcDone.load(std::memory_order_relaxed)) {
                safePoint(threadData);
            }

            if (resurrected) {
                EXPECT_NE(weaks[i]->get(), nullptr);
            } else {
                EXPECT_EQ(weaks[i]->get(), nullptr);
            }
        }));
    }

    mm::GlobalData::Instance().gc().WaitFinalizers(epoch);
    gcDone = true;

    for (auto& future : mutatorFutures) {
        future.wait();
    }

    // @Tencent: Removed upstream's manual ExtraObjectData teardown loop here.
    // Upstream blocked `weaks[i]->get()` on `markTerminationMutex_` until `kWeakProcessing`, so `weaks[i]->get()`
    // always returned `nullptr` and the loop body was dead code. With the non-blocking weak read barrier, referents
    // can now actually be resurrected during mark termination, which exposed a double-`Uninstall()` null-pointer crash
    // on `legacy_alloc` (`extraObj.Uninstall()` nulled out the base object before `alloc::destroyExtraObjectData` and
    // `Allocator::clearForTests` called `Uninstall()` again). As in `WeakResurrectionInMark`, `~ConcurrentMarkAndSweepTest`
    // already cleans up resurrected objects and their `ExtraObjectData` via `allocator().clearForTests()`.
}

TYPED_TEST_P(TracingGCTest, ReleaseStableRefDuringRSCollection) {
    std::vector<Mutator> mutators(kDefaultThreadCount);

    std::atomic<size_t> readyThreads = 0;
    std::atomic<bool> gcDone = false;

    std::vector<std::future<void>> mutatorFutures;
    for (int i = 0; i < kDefaultThreadCount; ++i) {
        mutatorFutures.push_back(mutators[i].Execute([&](mm::ThreadData& threadData, Mutator& mutator) {
            auto& obj = AllocateObject(threadData);
            mm::OwningExternalRCRef stableRef(obj.header());

            ++readyThreads;
            while (!mm::IsThreadSuspensionRequested()) {
            }

            mm::safePoint();

            mutator.AddStackRoot(obj.header());
            stableRef.reset();

            while (!gcDone) {
                mm::safePoint();
            }

            EXPECT_THAT(mutator.Alive(), testing::Contains(obj.header()));
        }));
    }

    while (readyThreads < kDefaultThreadCount) {
    }

    mm::GlobalData::Instance().gcScheduler().scheduleAndWaitFinalized();
    gcDone = true;

    for (auto& future : mutatorFutures) {
        future.wait();
    }
}

TYPED_TEST_P(TracingGCTest, TerminateInSTW) {
    // Try to drive the concurrent mark to terminate in STW (after several unsuccessful concurrent attempts).
    // Construct a linked list of arrays and continuously steal from the list just one step ahead of the GC's mark front.

    constexpr int kListLength = 1000;
    constexpr int kArraySize = 200;
    // too many threads over-pace the GC
    constexpr int kMutatorsCount = 2;

    std::atomic gcDone = false;
    std::atomic ready = 0;

    std::vector<Mutator> mutators{kMutatorsCount};
    std::vector<std::future<void>> futures{};
    for (auto& mutator : mutators) {
        futures.emplace_back(mutator.Execute([&ready, &gcDone](mm::ThreadData& threadData, Mutator&mutator) {
            //prepare
            auto& global = mutator.AddGlobalRoot();
            auto& local = mutator.AddStackRoot();

            for (int i = 0; i < kListLength; ++i) {
                auto& next = AllocateArray<kArraySize>(threadData);
                next.elements()[kArraySize - 1] = global->field1.accessor().load();
                global->field1 = next.header();
            }
            local->field1 = global->field1.accessor().load();

            ++ready;

            // wait for GC
            while (!mm::IsThreadSuspensionRequested()) {}
            mm::safePoint(threadData);

            // run
            while (!gcDone.load(std::memory_order_relaxed)) {
                auto* nextPtr = local->field1.accessor().load()->array();
                if (nextPtr == nullptr) break;

                auto& next = test_support::ObjectArray<kArraySize>::FromArrayHeader(nextPtr);
                auto lastAccessor = next.elements()[kArraySize - 1].accessor();
                local->field1 = lastAccessor.load();
                lastAccessor.store(nullptr);

                while (!mm::test_support::safePointsAreActive() && !gcDone.load(std::memory_order_relaxed)) {}
                mm::safePoint(threadData);
            }

            // let the GC catch up if we were too fast
            while (!gcDone) {
                mm::safePoint(threadData);
            }
        }));
    }

    while (ready < kMutatorsCount) {}

    mm::GlobalData::Instance().gcScheduler().scheduleAndWaitFinalized();
    gcDone = true;

    for (auto& future : futures) {
        future.wait();
    }
}

// region @Tencent: Verify weak reference reads do not block when another mutator delays mark termination.
TYPED_TEST_P(TracingGCTest, WeakReadNotBlockedDuringMarkTermination) {
    constexpr int kChainLength = 50000;

    std::vector<Mutator> mutators(2);
    test_support::RegularWeakReferenceImpl* weakRef = nullptr;
    test_support::Object<Payload>* root = nullptr;
    test_support::Object<Payload>* weakObj = nullptr;
    test_support::Object<Payload>* weakChild = nullptr;

    mutators[0]
            .Execute([&](mm::ThreadData& threadData, Mutator& mutator) {
                root = &AllocateObject(threadData);
                mutator.AddGlobalRoot(root->header());

                // Give concurrent mark enough work after STW1 so mutators reliably wake up before tryTerminateMark().
                ObjHeader* head = nullptr;
                for (int i = 0; i < kChainLength; ++i) {
                    auto& node = AllocateObject(threadData);
                    node->field1 = head;
                    head = node.header();
                }
                (*root)->field2 = head;

                weakChild = &AllocateObject(threadData);
                weakObj = &AllocateObject(threadData);
                (*weakObj)->field1 = weakChild->header();

                ObjHolder holder;
                weakRef = &test_support::InstallWeakReference(threadData, weakObj->header(), holder.slot());
                EXPECT_EQ(weakRef->get(), weakObj->header());
                mutator.AddGlobalRoot(weakRef->header());
            })
            .wait();
    mutators[1].Execute([](mm::ThreadData&, Mutator&) {}).wait();

    std::atomic<int> readyMutators = 0;
    std::atomic<bool> flushObservedByBlocker = false;
    std::atomic<bool> caughtFlushInMark = false;
    std::atomic<bool> weakReadCompleted = false;
    std::atomic<bool> gcDone = false;

    auto readerFuture = mutators[0].Execute([&](mm::ThreadData& threadData, Mutator& mutator) noexcept {
        ++readyMutators;
        while (!mm::IsThreadSuspensionRequested()) {
        }
        mm::safePoint(threadData);

        while (!flushObservedByBlocker.load(std::memory_order_acquire) && !gcDone.load(std::memory_order_relaxed)) {
            mm::safePoint(threadData);
        }

        bool resurrected = false;
        if (caughtFlushInMark.load(std::memory_order_relaxed)) {
            // At this point mutators[1] is holding back flushMutatorQueues(), so GC holds markTerminationMutex_.
            // Reading weakRef must not block on markTerminationMutex_ and must resurrect weakObj and weakChild.
            auto* referent = weakRef->get();
            EXPECT_EQ(referent, weakObj->header());
            (*root)->field1 = referent;
            resurrected = referent != nullptr;
        }

        weakReadCompleted.store(true, std::memory_order_release);

        while (!gcDone.load(std::memory_order_relaxed)) {
            mm::safePoint(threadData);
        }

        if (resurrected) {
            EXPECT_EQ(weakRef->get(), weakObj->header());
            EXPECT_THAT(mutator.Alive(), testing::AllOf(testing::Contains(weakObj->header()), testing::Contains(weakChild->header())));
        }
    });

    auto blockerFuture = mutators[1].Execute([&](mm::ThreadData& threadData, Mutator&) noexcept {
        ++readyMutators;
        while (!mm::IsThreadSuspensionRequested()) {
        }
        mm::safePoint(threadData);

        while (!gc::mark::test_support::flushActionRequested() && !gcDone.load(std::memory_order_relaxed)) {
        }

        if (!gcDone.load(std::memory_order_relaxed)) {
            caughtFlushInMark.store(true, std::memory_order_relaxed);
            flushObservedByBlocker.store(true, std::memory_order_release);

            // Stay in kRunnable without hitting a safepoint until mutators[0] finishes reading weakRef.
            while (!weakReadCompleted.load(std::memory_order_acquire) && !gcDone.load(std::memory_order_relaxed)) {
            }
        } else {
            flushObservedByBlocker.store(true, std::memory_order_release);
        }

        while (!gcDone.load(std::memory_order_relaxed)) {
            mm::safePoint(threadData);
        }
    });

    while (readyMutators.load() < 2) {
    }

    mm::GlobalData::Instance().gcScheduler().scheduleAndWaitFinalized();
    gcDone.store(true, std::memory_order_relaxed);

    readerFuture.wait();
    blockerFuture.wait();
}
// endregion

REGISTER_TYPED_TEST_SUITE_WITH_LISTS(
        TracingGCTest, TRACING_GC_TEST_LIST,
        WeakResurrectionAtMarkTermination,
        ReleaseStableRefDuringRSCollection,
        TerminateInSTW,
        // region @Tencent: Register WeakReadNotBlockedDuringMarkTermination test.
        WeakReadNotBlockedDuringMarkTermination
        // endregion
);
INSTANTIATE_TYPED_TEST_SUITE_P(CMS, TracingGCTest, ConcurrentMarkAndSweepTest);
