/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.konan.target

enum class BinaryFormat {
    ELF,
    PE_COFF,
    MACH_O
}

fun KonanTarget.binaryFormat() = when (family) {
    Family.WATCHOS -> BinaryFormat.MACH_O
    Family.IOS -> BinaryFormat.MACH_O
    Family.TVOS -> BinaryFormat.MACH_O
    Family.OSX -> BinaryFormat.MACH_O
    Family.ANDROID -> BinaryFormat.ELF
    Family.LINUX -> BinaryFormat.ELF
    // region @Tencent: Use ELF for OHOS binaries.
    Family.OHOS -> BinaryFormat.ELF
    // endregion
    Family.MINGW -> BinaryFormat.PE_COFF
}

fun KonanTarget.pointerBits() = when (architecture) {
    Architecture.X64 -> 64
    Architecture.X86 -> 32
    Architecture.ARM64 -> if (this == KonanTarget.WATCHOS_ARM64) 32 else 64
    Architecture.ARM32 -> 32
}

fun KonanTarget.supportsLibBacktrace(): Boolean =
        this.family.isAppleFamily ||
                this.family == Family.LINUX ||
                this.family == Family.ANDROID
                // region @Tencent: Allow libbacktrace source info on OHOS.
                || this.family == Family.OHOS
                // endregion

// TODO: Add explicit WATCHOS_DEVICE_ARM64 after compiler update.
fun KonanTarget.supportsCoreSymbolication(): Boolean =
        this in listOf(
                KonanTarget.MACOS_X64, KonanTarget.MACOS_ARM64, KonanTarget.IOS_X64,
                KonanTarget.IOS_SIMULATOR_ARM64, KonanTarget.TVOS_X64, KonanTarget.TVOS_SIMULATOR_ARM64,
                KonanTarget.WATCHOS_X64, KonanTarget.WATCHOS_SIMULATOR_ARM64
        )

// region @Tencent: OHOS uses the GCC unwind personality.
fun KonanTarget.supportsGccUnwind(): Boolean = family == Family.ANDROID || family == Family.LINUX || family == Family.OHOS
// endregion
// MINGW_X64 target does not support GCC unwind, since its sysroot contains libgcc version < 12 having misfeature, see KT-49240
fun KonanTarget.supportsWinAPIUnwind(): Boolean = this is KonanTarget.MINGW_X64
        // region @Tencent: Enable WinAPI unwind for mingw_x86 and mingw_arm64.
        || this is KonanTarget.MINGW_X86 || this is KonanTarget.MINGW_ARM64
        // endregion

fun KonanTarget.supportsObjcInterop(): Boolean = family.isAppleFamily
fun KonanTarget.hasFoundationFramework(): Boolean = family.isAppleFamily
fun KonanTarget.hasUIKitFramework(): Boolean = family == Family.IOS || family == Family.TVOS
fun KonanTarget.supports64BitMulOverflow(): Boolean = when (this) {
    is KonanTarget.LINUX_ARM32_HFP -> false
    is KonanTarget.ANDROID_ARM32 -> false
    is KonanTarget.ANDROID_X86 -> false
    // region @Tencent: i686 clang emits compiler-rt `__mulodi4`; mingw-w64 libgcc does not provide it.
    is KonanTarget.MINGW_X86 -> false
    // endregion
    else -> true
}

// TODO: Add explicit WATCHOS_DEVICE_ARM64 after compiler update.
fun KonanTarget.supportsIosCrashLog(): Boolean = when (this) {
    KonanTarget.IOS_ARM64 -> true
    KonanTarget.WATCHOS_ARM32 -> true
    KonanTarget.WATCHOS_ARM64 -> true
    KonanTarget.TVOS_ARM64 -> true
    else -> false
}

/*
 * While not 100% correct here, using atomic ops on iOS armv7 requires 8 byte alignment,
 * and general ABI requires 4-byte alignment on 64-bit long fields as mentioned in
 * https://developer.apple.com/library/archive/documentation/Xcode/Conceptual/iPhoneOSABIReference/Articles/ARMv6FunctionCallingConventions.html#//apple_ref/doc/uid/TP40009021-SW1
 * See https://github.com/ktorio/ktor/issues/941 for the context.
 */
fun KonanTarget.supports64BitAtomics(): Boolean = when (architecture) {
    Architecture.ARM32 -> false
    Architecture.X86, Architecture.ARM64, Architecture.X64 -> true
} && this != KonanTarget.WATCHOS_ARM64

fun KonanTarget.supportsUnalignedAccess(): Boolean = when (architecture) {
    Architecture.ARM32 -> false
    Architecture.X86, Architecture.ARM64, Architecture.X64 -> true
} && this != KonanTarget.WATCHOS_ARM64

fun KonanTarget.needSmallBinary() = when {
    family == Family.WATCHOS -> true
    family.isAppleFamily -> architecture == Architecture.ARM32
    else -> false
}

fun KonanTarget.supportedSanitizers(): List<SanitizerKind> =
    when(this) {
        is KonanTarget.LINUX_X64 -> listOf(SanitizerKind.ADDRESS, SanitizerKind.THREAD)
        is KonanTarget.MACOS_X64 -> listOf(SanitizerKind.THREAD)
        is KonanTarget.MACOS_ARM64 -> listOf(SanitizerKind.THREAD)
        // TODO: Enable ASAN on macOS. Currently there's an incompatibility between clang frontend version and clang_rt.asan version.
        // TODO: Consider supporting mingw.
        else -> listOf()
    }

val KonanTarget.supportsGrandCentralDispatch
    get() = family.isAppleFamily

val KonanTarget.supportsSignposts
    get() = family.isAppleFamily
