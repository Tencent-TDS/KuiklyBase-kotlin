/*
 * Copyright 2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

#ifndef CUSTOM_ALLOC_CPP_NEXTFITPAGE_HPP_
#define CUSTOM_ALLOC_CPP_NEXTFITPAGE_HPP_

#include <atomic>
#include <cstdint>
#include <vector>

#include "Constants.hpp"
#include "AnyPage.hpp"
#include "AtomicStack.hpp"
#include "Cell.hpp"
#include "ExtraObjectPage.hpp"
#include "GCStatistics.hpp"
#include "PageSizeInfo.h"

namespace kotlin::alloc {

class alignas(kPageAlignment) NextFitPage : public AnyPage<NextFitPage> {
public:
// region Tencent Code
// @Tencent Tries to optimize memory only in ohos target
#ifdef KONAN_OHOS
    static inline constexpr const size_t SIZE = 128 * KiB;

    static inline constexpr const int MAX_BLOCK_SIZE = 32;
#else
    static inline constexpr const size_t SIZE = 256 * KiB;

    static inline constexpr const int MAX_BLOCK_SIZE = 128;
#endif
// endregion

    static inline constexpr int cellCount() {
        return (SIZE - sizeof(NextFitPage)) / sizeof(Cell);
    }

    static inline constexpr int maxBlockSize() {
        return cellCount() - 2;
    }

    using GCSweepScope = gc::GCHandle::GCSweepScope;

    static GCSweepScope currentGCSweepScope(gc::GCHandle& handle) noexcept { return handle.sweep(); }

    static NextFitPage* Create(uint32_t cellCount) noexcept;

    void Destroy() noexcept;

    // Tries to allocate in current page, returns null if no free block in page is big enough
    uint8_t* TryAllocate(uint32_t blockSize) noexcept;

    bool Sweep(GCSweepScope& sweepHandle, FinalizerQueue& finalizerQueue) noexcept;


    // TODO: Do we need this, or should we implement Dump on top of GetAllocatedBlocks()?
    template <typename F>
    void TraverseAllocatedBlocks(F process) noexcept(noexcept(process(std::declval<uint8_t*>()))) {
        Cell* end = cells_ + cellCount();
        for (Cell* block = cells_ + 1; block != end; block = block->Next()) {
            if (block->isAllocated_) {
                process(block->data_);
            }
        }
    }

    // region Tencent Code
    template <typename F>
    void TraverseAllocatedBlocksForFile(F process) noexcept(noexcept(process(std::declval<uint8_t*>(),std::declval<uintptr_t>()))) {
        Cell* end = cells_ + cellCount();
        size_t cellSize = sizeof(Cell);
        uint32_t offset = cellSize;
        for (Cell* block = cells_ + 1; block != end; block = block->Next()) {
            if (block->isAllocated_) {
                process(block->data_, offset);
            }
            offset += block->size_ * cellSize;
        }
    }

    uintptr_t getOriginAddress(uint32_t offset) const {
        return cellsAddress + offset + offsetof(Cell, data_);
    }
    // endregion

    // Testing method
    bool CheckInvariants() noexcept;

    // Testing method
    std::vector<uint8_t*> GetAllocatedBlocks() noexcept;

    // region Tencent Code
    PageSizeInfo getPageSize();
    // endregion

    private:
    explicit NextFitPage(uint32_t cellCount) noexcept;

    // Looks for a block big enough to hold cellsNeeded. If none big enough is
    // found, update to the largest one.
    void UpdateCurBlock(uint32_t cellsNeeded) noexcept;

    std::size_t GetAllocatedSizeBytes() noexcept;

    Cell* curBlock_;
    // region Tencent Code
    uintptr_t cellsAddress;
    // endregion
    Cell cells_[]; // cells_[0] is reserved for an empty block
};

} // namespace kotlin::alloc

#endif
