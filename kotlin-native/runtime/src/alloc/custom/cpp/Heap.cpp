/*
 * Copyright 2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

#include "Heap.hpp"

#include <atomic>
#include <cstdint>
#include <cstdlib>
#include <cinttypes>
#include <new>
#include <vector>
#include <unordered_map>

#include "AtomicStack.hpp"
#include "CustomLogging.hpp"
#include "ExtraObjectData.hpp"
#include "ExtraObjectPage.hpp"
#include "GCApi.hpp"
#include "Memory.h"
#include "ThreadRegistry.hpp"

namespace kotlin::alloc {

void Heap::PrepareForGC() noexcept {
    CustomAllocDebug("Heap::PrepareForGC()");
    nextFitPages_.PrepareForGC();
    singleObjectPages_.PrepareForGC();
    for (int blockSize = 0; blockSize <= FixedBlockPage::MAX_BLOCK_SIZE; ++blockSize) {
        fixedBlockPages_[blockSize].PrepareForGC();
    }
    extraObjectPages_.PrepareForGC();
}

FinalizerQueue Heap::Sweep(gc::GCHandle gcHandle) noexcept {
    FinalizerQueue finalizerQueue;
    CustomAllocDebug("Heap: before sweep FinalizerQueue size == %zu", finalizerQueue.size());
    CustomAllocDebug("Heap::Sweep()");
    {
        auto sweepHandle = gcHandle.sweep();
        for (int blockSize = 0; blockSize <= FixedBlockPage::MAX_BLOCK_SIZE; ++blockSize) {
            fixedBlockPages_[blockSize].Sweep(sweepHandle, finalizerQueue);
        }
        nextFitPages_.Sweep(sweepHandle, finalizerQueue);
        singleObjectPages_.SweepAndFree(sweepHandle, finalizerQueue);
    }
    CustomAllocDebug("Heap: before extra sweep FinalizerQueue size == %zu", finalizerQueue.size());
    {
        auto sweepHandle = gcHandle.sweepExtraObjects();
        extraObjectPages_.Sweep(sweepHandle, finalizerQueue);
    }
    // wait for concurrent assistants to finish sweeping the last popped page
    while (concurrentSweepersCount_.load(std::memory_order_acquire) > 0) {
        std::this_thread::yield();
    }
    CustomAllocDebug("Heap::Sweep done");
    return finalizerQueue;
}

NextFitPage* Heap::GetNextFitPage(uint32_t cellCount, FinalizerQueue& finalizerQueue) noexcept {
    CustomAllocDebug("Heap::GetNextFitPage()");
    return nextFitPages_.GetPage(cellCount, finalizerQueue, concurrentSweepersCount_);
}

FixedBlockPage* Heap::GetFixedBlockPage(uint32_t cellCount, FinalizerQueue& finalizerQueue) noexcept {
    CustomAllocDebug("Heap::GetFixedBlockPage()");
    return fixedBlockPages_[cellCount].GetPage(cellCount, finalizerQueue, concurrentSweepersCount_);
}

SingleObjectPage* Heap::GetSingleObjectPage(uint64_t cellCount, FinalizerQueue& finalizerQueue) noexcept {
    CustomAllocInfo("CustomAllocator::AllocateInSingleObjectPage(%" PRIu64 ")", cellCount);
    return singleObjectPages_.NewPage(cellCount);
}

ExtraObjectPage* Heap::GetExtraObjectPage(FinalizerQueue& finalizerQueue) noexcept {
    CustomAllocInfo("CustomAllocator::GetExtraObjectPage()");
    return extraObjectPages_.GetPage(0, finalizerQueue, concurrentSweepersCount_);
}

void Heap::AddToFinalizerQueue(FinalizerQueue queue) noexcept {
    std::unique_lock guard(pendingFinalizerQueueMutex_);
    pendingFinalizerQueue_.mergeFrom(std::move(queue));
}

FinalizerQueue Heap::ExtractFinalizerQueue() noexcept {
    std::unique_lock guard(pendingFinalizerQueueMutex_);
    return std::move(pendingFinalizerQueue_);
}

std::vector<ObjHeader*> Heap::GetAllocatedObjects() noexcept {
    std::vector<ObjHeader*> allocated;
    for (int blockSize = 0; blockSize <= FixedBlockPage::MAX_BLOCK_SIZE; ++blockSize) {
        for (auto* page : fixedBlockPages_[blockSize].GetPages()) {
            for (auto* block : page->GetAllocatedBlocks()) {
                allocated.push_back(reinterpret_cast<CustomHeapObject*>(block)->object());
            }
        }
    }
    for (auto* page : nextFitPages_.GetPages()) {
        for (auto* block : page->GetAllocatedBlocks()) {
            allocated.push_back(reinterpret_cast<CustomHeapObject*>(block)->object());
        }
    }
    for (auto* page : singleObjectPages_.GetPages()) {
        for (auto* block : page->GetAllocatedBlocks()) {
            allocated.push_back(reinterpret_cast<CustomHeapObject*>(block)->object());
        }
    }
    std::vector<ObjHeader*> unfinalized;
    for (auto* block: allocated) {
        if (!block->has_meta_object() || !mm::ExtraObjectData::Get(block)->getFlag(mm::ExtraObjectData::FLAGS_FINALIZED)) {
            unfinalized.push_back(block);
        }
    }
    return unfinalized;
}

void Heap::ClearForTests() noexcept {
    for (int blockSize = 0; blockSize <= FixedBlockPage::MAX_BLOCK_SIZE; ++blockSize) {
        fixedBlockPages_[blockSize].ClearForTests();
    }
    nextFitPages_.ClearForTests();
    singleObjectPages_.ClearForTests();
    extraObjectPages_.ClearForTests();
}

// region Tencent Code
void Heap::onFinishGC() noexcept {
    TraverseAllPage();
}

template <typename PageStoreType, typename PageType>
void Heap::processPages(PageStoreType& pageStore, const char* typeName,
                        const std::function<void(PageType*)>& extraHandler) {
    PageSizeInfo total{0, 0};
    int count = 0;
    std::unordered_map<std::string, int> nameCounter;

    pageStore.TraversePages([&](PageType* page) {
        auto info = page->getPageSize();
        total += info;
        count++;
        // 统计逻辑
        if (extraHandler) extraHandler(page);
    });

    TencentAllocInfo("processPages[%s] Page, count=%d, allocate=%zu, used=%zu, percent=%f",
                     typeName, count, total.allocateSize, total.usedSize, total.getPercent());
}

void Heap::TraverseAllPage() noexcept {
    // FixedBlockPage 特殊处理
    PageSizeInfo fixTotal{0, 0};
    size_t ready = 0, empty = 0, used = 0, unswept = 0;
    for (int bs = 0; bs <= FixedBlockPage::MAX_BLOCK_SIZE; ++bs) {
        auto& store = fixedBlockPages_[bs];
        ready += store.ready_.size();
        empty += store.empty_.size();
        used += store.used_.size();
        unswept += store.unswept_.size();
        processPages<PageStore<FixedBlockPage>, FixedBlockPage>(
                store, "Fix", [&fixTotal](FixedBlockPage* page) { fixTotal += page->getPageSize(); });
    }
    TencentAllocInfo("FixPageStore: ready=%zu empty=%zu used=%zu unswept=%zu", ready, empty, used, unswept);

    // 统一处理其他页面类型
    processPages<PageStore<NextFitPage>, NextFitPage>(nextFitPages_, "NextFit");
    processPages<PageStore<SingleObjectPage>, SingleObjectPage>(singleObjectPages_, "SingleObject");
    processPages<PageStore<ExtraObjectPage>, ExtraObjectPage>(extraObjectPages_, "ExtraObject");
}
// endregion

} // namespace kotlin::alloc
