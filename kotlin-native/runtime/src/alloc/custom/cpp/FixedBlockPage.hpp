/*
 * Copyright 2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

#ifndef CUSTOM_ALLOC_CPP_FIXEDBLOCKPAGE_HPP_
#define CUSTOM_ALLOC_CPP_FIXEDBLOCKPAGE_HPP_

#include <atomic>
#include <cstdint>
#include <PageSizeInfo.h>
#include <vector>

#include "Constants.hpp"
#include "AtomicStack.hpp"
#include "ExtraObjectPage.hpp"
#include "GCStatistics.hpp"
#include "AnyPage.hpp"

namespace kotlin::alloc {

struct alignas(8) FixedCellRange {
    uint32_t first;
    uint32_t last;
};

struct alignas(8) FixedBlockCell {
    // The FixedBlockCell either contains data or a pointer to the next free cell
    union {
        uint8_t data[];
        FixedCellRange nextFree;
    };
};

class alignas(kPageAlignment) FixedBlockPage : public AnyPage<FixedBlockPage> {
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

    static inline constexpr size_t cellCount() {
        return (SIZE - sizeof(FixedBlockPage)) / sizeof(FixedBlockCell);
    }

    using GCSweepScope = gc::GCHandle::GCSweepScope;

    static GCSweepScope currentGCSweepScope(gc::GCHandle& handle) noexcept { return handle.sweep(); }

    static FixedBlockPage* Create(uint32_t blockSize) noexcept;

    void Destroy() noexcept;

    // Tries to allocate in current page, returns null if no free block in page
    uint8_t* TryAllocate(uint32_t blockSize) noexcept;

    void OnPageOverflow() noexcept;

    bool Sweep(GCSweepScope& sweepHandle, FinalizerQueue& finalizerQueue) noexcept;

    template <typename F>
    void TraverseAllocatedBlocks(F process) noexcept(noexcept(process(std::declval<uint8_t*>()))) {
        FixedCellRange nextFree = nextFree_; // Accessing the previous free list structure.
        for (uint32_t cell = 0 ; cell < end_ ; cell += blockSize_) {
            for (; cell < nextFree.first ; cell += blockSize_) {
                process(cells_[cell].data);
            }
            if (nextFree.last >= end_) {
                break;
            }
            cell = nextFree.last;
            nextFree = cells_[cell].nextFree;
        }
    }

    // region Tencent Code
    template <typename F>
    void TraverseAllocatedBlocksForFile(F process) noexcept(noexcept(process(std::declval<uint8_t*>(),std::declval<uintptr_t>()))) {
        FixedCellRange nextFree = nextFree_; // Accessing the previous free list structure.
        for (uint32_t cell = 0 ; cell < end_ ; cell += blockSize_) {
            for (; cell < nextFree.first ; cell += blockSize_) {
                process(cells_[cell].data, cell);
            }
            if (nextFree.last >= end_) {
                break;
            }
            cell = nextFree.last;
            nextFree = cells_[cell].nextFree;
        }
    }

    // 根据 cells_地址和 next 参数，获取对应 data 的原始地址
    uintptr_t getOriginAddress(uint32_t next) const {
        return cellsAddress + next * sizeof(FixedBlockCell) + offsetof(FixedBlockCell, data);
    }
    // endregion

    // Testing method
    std::vector<uint8_t*> GetAllocatedBlocks() noexcept;

    // region Tencent Code
    PageSizeInfo getPageSize();
    // endregion

private:
    explicit FixedBlockPage(uint32_t blockSize) noexcept;

    FixedCellRange nextFree_;
    uint32_t blockSize_;
    uint32_t end_;
    // region Tencent Code
    uintptr_t cellsAddress;
    // endregion
    FixedBlockCell cells_[];
};

} // namespace kotlin::alloc

#endif
