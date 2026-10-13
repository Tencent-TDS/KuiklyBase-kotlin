/*
 * Copyright 2010-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

#include "std_support/CStdlib.hpp"

// region @Tencent: Use UCRT _aligned_malloc and _aligned_free directly on Windows to avoid Clang mm_malloc.h align==1 mismatch on mingw_arm64.
#ifdef _WIN32
#include <malloc.h>
#else
#include <mm_malloc.h>
#endif
// endregion

#include "Alignment.hpp"
#include "KAssert.h"

using namespace kotlin;

void* std_support::aligned_malloc(std::size_t alignment, std::size_t size) noexcept {
    // Enforcing alignment requirements of std::aligned_alloc.
    RuntimeAssert(IsValidAlignment(alignment), "Invalid alignment %zu", alignment);
    RuntimeAssert(IsAligned(size, alignment), "Size %zu must be aligned to %zu", size, alignment);
// region @Tencent: Use UCRT _aligned_malloc directly on Windows to avoid Clang mm_malloc.h align==1 mismatch on mingw_arm64.
#ifdef _WIN32
    return ::_aligned_malloc(size, alignment);
#else
    return ::_mm_malloc(size, alignment);
#endif
// endregion
}

void std_support::aligned_free(void* ptr) noexcept {
// region @Tencent: Use UCRT _aligned_free directly on Windows to match _aligned_malloc across all mingw architectures.
#ifdef _WIN32
    return ::_aligned_free(ptr);
#else
    return ::_mm_free(ptr);
#endif
// endregion
}
