/*
* Copyright 2024 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
* that can be found in the LICENSE file.
*/

#pragma once
#include <KString.h>

namespace kotlin::mm {

/**
* Dumps memory into the given POSIX file in Kotlin/Native Dump file format, and
* returns success flag. Must be called during STW.
*
* The dump includes raw contents of Kotlin objects in binary form, together
* with corresponding type layouts. The dump can be combined with additional
* metadata emitted by the compiler and converted to the "hprof" format by an
* external tool.
*
*/
bool DumpMemory(int fd) noexcept;
// region Tencent Code
/**
 * 子线程 Dump，适用于不能 fork 的平台如 iOS
 * @param fd 
 * @param asyncCacheDir
 * @return 
 */
bool DumpMemoryAsync(int fd, KRef asyncCacheDir) noexcept;

bool isAsyncDumping() noexcept;
// endregion
} // namespace kotlin::mm
