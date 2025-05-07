/*
 * Copyright 2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

#ifndef CUSTOM_ALLOC_CPP_SINGLEOBJECTPAGE_HPP_
#define CUSTOM_ALLOC_CPP_SINGLEOBJECTPAGE_HPP_

#include <atomic>
#include <cstdint>
#include <PageSizeInfo.h>
#include <vector>

#include "AnyPage.hpp"
#include "AtomicStack.hpp"
#include "ExtraObjectPage.hpp"
#include "GCStatistics.hpp"

namespace kotlin::alloc {

class alignas(kPageAlignment) SingleObjectPage : public AnyPage<SingleObjectPage> {
public:
    using GCSweepScope = gc::GCHandle::GCSweepScope;

    static GCSweepScope currentGCSweepScope(gc::GCHandle& handle) noexcept { return handle.sweep(); }

    static SingleObjectPage* Create(uint64_t cellCount) noexcept;

    void Destroy() noexcept;

    uint8_t* Data() noexcept;

    uint8_t* TryAllocate() noexcept;

    bool Sweep(GCSweepScope& sweepHandle, FinalizerQueue& finalizerQueue) noexcept;

    template <typename F>
    void TraverseAllocatedBlocks(F process) noexcept(noexcept(process(std::declval<uint8_t*>()))) {
        if (isAllocated_) {
            process(data_);
        }
    }

    // region Tencent Code
    template <typename F>
    void TraverseAllocatedBlocksForFile(F process) noexcept(noexcept(process(std::declval<uint8_t*>(),std::declval<uintptr_t>()))) {
        if (isAllocated_) {
            process(data_, dataAddress);
        }
    }

    PageSizeInfo getPageSize();
    size_t getSize();
    // endregion

private:
    friend class Heap;

    explicit SingleObjectPage(size_t size) noexcept;

    // Testing method
    std::vector<uint8_t*> GetAllocatedBlocks() noexcept;

    bool isAllocated_ = false;
    size_t size_;
    // region Tencent Code
    uintptr_t dataAddress;
    // endregion
    struct alignas(8) {
        uint8_t data_[];
    };
};

} // namespace kotlin::alloc

#endif
