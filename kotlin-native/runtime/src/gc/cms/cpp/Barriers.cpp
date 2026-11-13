/*
 * Copyright 2010-2024 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

#include "Barriers.hpp"

#include <algorithm>
#include <atomic>

#include "GCImpl.hpp"
#include "Memory.h"
#include "ThreadData.hpp"
#include "ThreadRegistry.hpp"

using namespace kotlin;

namespace {

enum class BarriersPhase {
    /** Normal execution */
    kDisabled,
    /** During mark closure building */
    kMarkClosure,
    /** After the mark closure is built, but before the mark completed (during weak ref processing) */
    kWeakProcessing
};

const char* toString(BarriersPhase barriersPhase) {
    switch (barriersPhase) {
        case BarriersPhase::kDisabled:
            return "none";
        case BarriersPhase::kMarkClosure:
            return "mark";
        case BarriersPhase::kWeakProcessing:
            return "weak-processing";
    }
}

std::atomic barriersPhase = BarriersPhase::kDisabled;
std::atomic<uint64_t> markingEpoch = 0;

BarriersPhase currentPhase() noexcept {
    return barriersPhase.load(std::memory_order_acquire);
}

BarriersPhase currentPhaseRelaxed() noexcept {
    return barriersPhase.load(std::memory_order_relaxed);
}

ALWAYS_INLINE void assertPhase(BarriersPhase actual, BarriersPhase expected) noexcept {
    RuntimeAssert(actual == expected, "Barriers phase: expected %s but observed %s", toString(expected), toString(actual));
}

ALWAYS_INLINE void assertPhase(BarriersPhase expected) noexcept {
    assertPhase(currentPhaseRelaxed(), expected);
}

ALWAYS_INLINE void assertPhaseNot(BarriersPhase expected) noexcept {
    RuntimeAssert(currentPhaseRelaxed() != expected, "Barriers phase: phase %s not expected", toString(expected));
}

void switchPhase(BarriersPhase from, BarriersPhase to) noexcept {
    auto prev = barriersPhase.exchange(to, std::memory_order_release);
    assertPhase(prev, from);
}

auto& markDispatcher() noexcept {
    return mm::GlobalData::Instance().gc().impl().mark_;
}

inline constexpr auto kTagBarriers = logging::Tag::kBarriers;
#define BarriersLogDebug(phase, format, ...) RuntimeLogDebug({kTagBarriers}, "[%s]" format, toString(phase), ##__VA_ARGS__)

} // namespace

void gc::barriers::BarriersThreadData::onThreadRegistration() noexcept {
    if (currentPhase() != BarriersPhase::kDisabled) {
        startMarkingNewObjects(GCHandle::getByEpoch(markingEpoch.load(std::memory_order_relaxed)));
    }
}

void gc::barriers::BarriersThreadData::startMarkingNewObjects(gc::GCHandle gcHandle) noexcept {
    assertPhaseNot(BarriersPhase::kDisabled);
    markHandle_ = gcHandle.mark();
}

void gc::barriers::BarriersThreadData::stopMarkingNewObjects() noexcept {
    assertPhase(BarriersPhase::kDisabled);
    markHandle_ = std::nullopt;
}

bool gc::barriers::BarriersThreadData::shouldMarkNewObjects() const noexcept {
    return markHandle_.has_value();
}

PERFORMANCE_INLINE void gc::barriers::BarriersThreadData::onAllocation(ObjHeader* allocated) {
    BarriersLogDebug(currentPhaseRelaxed(), "Allocation %p", allocated);
    if (shouldMarkNewObjects()) {
        auto& objectData = alloc::objectDataForObject(allocated);
        objectData.markUncontended();
        markHandle_->addObject();
    }
}

void gc::barriers::enableBarriers(uint64_t epoch) noexcept {
    auto mutators = mm::ThreadRegistry::Instance().LockForIter();
    markingEpoch.store(epoch, std::memory_order_relaxed);
    switchPhase(BarriersPhase::kDisabled, BarriersPhase::kMarkClosure);
    for (auto& mutator : mutators) {
        mutator.gc().impl().barriers_.startMarkingNewObjects(GCHandle::getByEpoch(epoch));
    }
}

void gc::barriers::switchToWeakProcessingBarriers() noexcept {
    switchPhase(BarriersPhase::kMarkClosure, BarriersPhase::kWeakProcessing);
}

void gc::barriers::disableBarriers() noexcept {
    auto mutators = mm::ThreadRegistry::Instance().LockForIter();
    switchPhase(BarriersPhase::kWeakProcessing, BarriersPhase::kDisabled);
    for (auto& mutator : mutators) {
        mutator.gc().impl().barriers_.stopMarkingNewObjects();
    }
}

namespace {

// TODO decide whether it's really beneficial to NO_INLINE the slow path
NO_INLINE void beforeHeapRefUpdateSlowPath(mm::DirectRefAccessor ref, ObjHeader* value, bool loadAtomic) noexcept {
    AssertThreadState(ThreadState::kRunnable);

    ObjHeader* prev;
    if (loadAtomic) {
        prev = ref.loadAtomic(std::memory_order_relaxed);
    } else {
        prev = ref.load();
    }

    if (prev != nullptr && prev->heap()) {
        // TODO Redundant if the destination object is black.
        //      Yet at the moment there is now efficient way to distinguish black and gray objects.

        // TODO perhaps it would be better to pass the thread data from outside
        auto& threadData = *mm::ThreadRegistry::Instance().CurrentThreadData();
        auto& markQueue = *threadData.gc().impl().mark_.markQueue();
        gc::mark::ConcurrentMark::MarkTraits::tryEnqueue(markQueue, prev);
        // No need to add the marked object in statistics here.
        // Objects will be counted on dequeue.
    }
}

} // namespace

PERFORMANCE_INLINE void gc::barriers::beforeHeapRefUpdate(mm::DirectRefAccessor ref, ObjHeader* value, bool loadAtomic) noexcept {
    auto phase = currentPhase();
    BarriersLogDebug(phase, "Write *%p <- %p (%p overwritten)", ref.location(), value, ref.load());
    if (__builtin_expect(phase == BarriersPhase::kMarkClosure, false)) {
        beforeHeapRefUpdateSlowPath(ref, value, loadAtomic);
    }
}

PERFORMANCE_INLINE gc::barriers::ExternalRCRefReleaseGuard::Impl::Impl(mm::DirectRefAccessor ref) noexcept {
    // Can be called with any possible thread state: kotlin, native, unattached thread.
    // This guard synchronizes with the `ConcurrentMark` via the ThreadRegistry lock.
    // It must be done before the barriers phase check.
    if (mm::ThreadRegistry::IsCurrentThreadRegistered()) {
        // If the thread is registered, just ensure, that the root set mutation happens will happen in the runnable state
        stateGuard_ = ThreadStateGuard{ThreadState::kRunnable, true};
        // NOTE that this barrier must be executed before the RC decrement
        beforeHeapRefUpdate(ref, nullptr, false);
    } else {
        // In case of an unregistered thread, just do thing outside the mark phase.
        // NOTE This code can be called from quite an unexpected places (such as TLS destructors).
        // One can't simply register the thread from here.
        markMutex_ = markDispatcher().markMutex();
    }
}

namespace {

/**
 * Before the mark closure is built, every weak read may resurrect a weakly-reachable object.
 * Thus, the referent must be pushed in a mark queue, in case it wold be resurrected behind the mark front.
 */
NO_INLINE void weakRefReadInMarkSlowPath(ObjHeader* weakReferee) noexcept {
    assertPhase(BarriersPhase::kMarkClosure);
    auto& threadData = *mm::ThreadRegistry::Instance().CurrentThreadData();
    auto& markQueue = *threadData.gc().impl().mark_.markQueue();
    gc::mark::ConcurrentMark::MarkTraits::tryEnqueue(markQueue, weakReferee);
}

/** After the mark closure is built, but weak refs are not yet nulled out, every weak read should check if the weak referent is marked. */
NO_INLINE ObjHeader* weakRefReadInWeakSweepSlowPath(ObjHeader* weakReferee) noexcept {
    assertPhase(BarriersPhase::kWeakProcessing);
    if (!gc::isMarked(weakReferee)) {
        return nullptr;
    }
    return weakReferee;
}

} // namespace

PERFORMANCE_INLINE ObjHeader* gc::barriers::weakRefReadBarrier(std_support::atomic_ref<ObjHeader*> weakReferee) noexcept {
    if (__builtin_expect(currentPhase() != BarriersPhase::kDisabled, false)) {
        // Mark dispatcher requires weak reads be protected by the following:
        // region @Tencent: Avoid blocking on markTerminationMutex_ when GC is terminating mark.
        // Running in `kRunnable` guarantees two invariants throughout this barrier:
        // 1. GC cannot enter STW to call `disableBarriers()`, so `currentPhase()` stays in
        //    `kMarkClosure` or `kWeakProcessing` and never transitions to `kDisabled`.
        // 2. GC's `flushMutatorQueues()` sees `suspendedOrNative() == false` for this thread
        //    and will never concurrently touch this thread's local `markQueue()`.
        AssertThreadState(ThreadState::kRunnable);
        {
            // Lock-free fast path: cleared weak references and permanent objects never need
            // mark-queue insertion or liveness checks.
            auto weak = weakReferee.load(std::memory_order_relaxed);
            if (!weak) return nullptr;
            if (weak->permanent()) return weak;
        }

        auto& dispatcher = markDispatcher();
        // Use non-blocking `try_to_lock` on `markTerminationMutex_`. Blocking here (as upstream's
        // `weakReadProtector()` does) forces this thread to spin until every `kRunnable` mutator
        // reaches a safepoint in `flushMutatorQueues()`. On Apple targets, `_tryRetain` enters this
        // barrier while `libobjc` holds a global `SideTable` spinlock; spinning here while another
        // `kRunnable` thread waits for the same `SideTable` lock causes a 3-way deadlock (KT-80769).
        auto weakReadProtector = dispatcher.tryWeakReadProtector();
        if (!weakReadProtector) {
            // `markTerminationMutex_` is currently held by the GC thread in `tryTerminateMark()`.
            // Fall back to `weakRefReadMutex_` (shared lock): GC only acquires its unique lock
            // *after* `flushMutatorQueues()` finishes, for a nanosecond-scale check and phase switch
            // without waiting on any mutator.
            auto weakRefReadFlagProtector = dispatcher.weakRefReadFlagProtector();

            // Reload after acquiring `weakRefReadMutex_` in case GC already switched to
            // `kWeakProcessing` and nulled out `weakReferee` in `processWeaks()`.
            auto weak = weakReferee.load(std::memory_order_relaxed);
            if (!weak) return nullptr;

            auto phase = currentPhase();
            BarriersLogDebug(phase, "Weak read (fallback) %p", weak);

            // Case 1: GC already finished mark termination and entered `kWeakProcessing` before we
            // acquired `weakRefReadMutex_`. The mark closure is finalized; return `weak` iff marked.
            if (__builtin_expect(phase == BarriersPhase::kWeakProcessing, false)) {
                return weakRefReadInWeakSweepSlowPath(weak);
            }

            // Case 2: Still in `kMarkClosure` (GC has not yet acquired `weakRefReadMutex_` to switch
            // phases, or already aborted termination and started a new mark iteration). Holding the
            // shared lock prevents GC from switching to `kWeakProcessing` until we finish below.
            assertPhase(BarriersPhase::kMarkClosure);
            if (gc::isMarked(weak)) {
                // Already marked (in the mark closure or already queued); safe to return directly.
                return weak;
            }

            // `weak` is unmarked and about to be resurrected. Because this thread might have already
            // executed its safepoint flush for the ongoing `flushMutatorQueues()` pass, pushing `weak`
            // into the thread-local `markQueue` alone may not increment `batchesEverShared()` before
            // GC checks it. Setting `weakRefReadFlag_` forces `tryTerminateMark()` to reject termination
            // and run another mark + flush iteration so `weak` and its subgraph are fully traced.
            dispatcher.setWeakRefReadFlag();
            weakRefReadInMarkSlowPath(weak);
            return weak;
        }
        // endregion
        AssertThreadState(ThreadState::kRunnable);

        auto weak = weakReferee.load(std::memory_order_relaxed);
        if (!weak) return nullptr;

        auto phase = currentPhase();
        BarriersLogDebug(phase, "Weak read %p", weak);

        if (__builtin_expect(phase == BarriersPhase::kMarkClosure, false)) {
            weakRefReadInMarkSlowPath(weak);
        } else {
            if (__builtin_expect(phase == BarriersPhase::kWeakProcessing, false)) {
                // TODO reread the referee here under the barrier guard
                //      if `disableBarriers` would be possible outside of STW
                return weakRefReadInWeakSweepSlowPath(weak);
            }
        }
        return weak;
    }

    return weakReferee.load(std::memory_order_relaxed);
}
