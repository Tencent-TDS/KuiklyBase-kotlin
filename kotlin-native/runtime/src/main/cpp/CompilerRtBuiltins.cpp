/*
 * Tencent is pleased to support the open source community by making TDS-KuiklyBase available.
 * Copyright (C) 2025 THL A29 Limited, a Tencent company. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "Common.h"

#include <cstdint>
#include <limits>

#if KONAN_WINDOWS && KONAN_X86

// Clang/LLVM on i686 emits compiler-rt helpers for overflow-checked 64-bit
// arithmetic (std::chrono duration casts, __builtin_mul_overflow, …).
// mingw-w64's libgcc only has the GCC variants (__muldi3, …), not __mulodi4.
// Provide the compiler-rt ABI here so final LLD links succeed.

namespace {

uint64_t mulu64(uint64_t a, uint64_t b) noexcept {
    // 32x32->64 muls only; avoids lowering back to __mulodi4 / __muldi3.
    const uint32_t a0 = static_cast<uint32_t>(a);
    const uint32_t a1 = static_cast<uint32_t>(a >> 32);
    const uint32_t b0 = static_cast<uint32_t>(b);
    const uint32_t b1 = static_cast<uint32_t>(b >> 32);
    const uint64_t p00 = static_cast<uint64_t>(a0) * b0;
    const uint64_t p01 = static_cast<uint64_t>(a0) * b1;
    const uint64_t p10 = static_cast<uint64_t>(a1) * b0;
    return p00 + ((p01 + p10) << 32);
}

} // namespace

extern "C" RUNTIME_EXPORT int64_t __mulodi4(int64_t a, int64_t b, int* overflow) {
    constexpr int64_t kMin = std::numeric_limits<int64_t>::min();
    constexpr int64_t kMax = std::numeric_limits<int64_t>::max();
    *overflow = 0;

    const int64_t result = static_cast<int64_t>(mulu64(static_cast<uint64_t>(a), static_cast<uint64_t>(b)));

    if (a == kMin) {
        if (b != 0 && b != 1) *overflow = 1;
        return result;
    }
    if (b == kMin) {
        if (a != 0 && a != 1) *overflow = 1;
        return result;
    }

    const int64_t absA = a < 0 ? -a : a;
    const int64_t absB = b < 0 ? -b : b;
    if (absA < 2 || absB < 2) return result;

    if ((a < 0) == (b < 0)) {
        if (absA > kMax / absB) *overflow = 1;
    } else {
        if (absA > kMin / -absB) *overflow = 1;
    }
    return result;
}

#endif // KONAN_WINDOWS && KONAN_X86
