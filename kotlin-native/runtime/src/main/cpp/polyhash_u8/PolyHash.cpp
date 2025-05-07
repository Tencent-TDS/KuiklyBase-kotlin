/*
 * Copyright 2010-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

#include "polyhash_u8/PolyHash.h"
#include "polyhash_u8/naive.h"
#include "polyhash_u8/arm.h"

int polyHash(int length, uint8_t const* str) {
#if defined(__arm__) or defined(__aarch64__)
    return polyHash_arm(length, str);
#else
    return polyHash_naive(length, str);
#endif
}