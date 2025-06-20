/*
 * Copyright 2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

#include "SingleObjectPage.hpp"

#include <atomic>
#include <cstdint>

#include "CustomLogging.hpp"
#include "GCApi.hpp"
#include "NextFitPage.hpp"

namespace kotlin::alloc {

SingleObjectPage* SingleObjectPage::Create(uint64_t cellCount) noexcept {
    CustomAllocInfo("SingleObjectPage::Create(%" PRIu64 ")", cellCount);
    RuntimeAssert(cellCount > NextFitPage::maxBlockSize(), "blockSize too small for SingleObjectPage");
    uint64_t size = sizeof(SingleObjectPage) + cellCount * sizeof(uint64_t);
    return new (SafeAlloc(size)) SingleObjectPage(size);
}

// region Tencent Code
SingleObjectPage::SingleObjectPage(size_t size) noexcept : size_(size),
                                                           dataAddress(reinterpret_cast<uintptr_t>(data_)) {
}
// endregion

void SingleObjectPage::Destroy() noexcept {
    Free(this, size_);
}

uint8_t* SingleObjectPage::Data() noexcept {
    return data_;
}

uint8_t* SingleObjectPage::TryAllocate() noexcept {
    if (isAllocated_) return nullptr;

    allocatedSizeTracker_.onPageOverflow(size_);

    isAllocated_ = true;
    return Data();
}

bool SingleObjectPage::Sweep(GCSweepScope& sweepHandle, FinalizerQueue& finalizerQueue) noexcept {
    CustomAllocDebug("SingleObjectPage@%p::Sweep()", this);
    if (SweepObject(Data(), finalizerQueue, sweepHandle)) {
        return true;
    }

    allocatedSizeTracker_.afterSweep(0);

    isAllocated_ = false;
    return false;
}

std::vector<uint8_t*> SingleObjectPage::GetAllocatedBlocks() noexcept {
    std::vector<uint8_t*> allocated;
    TraverseAllocatedBlocks([&allocated](uint8_t* block) {
        allocated.push_back(block);
    });
    return allocated;
}

// region Tencent Code
PageSizeInfo SingleObjectPage::getPageSize() {
    return PageSizeInfo{size_, allocatedSizeTracker_.getAllocatedSize()};
}

size_t SingleObjectPage::getSize() {
    return size_;
}
// endregion

} // namespace kotlin::alloc
