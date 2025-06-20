/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.konan.target

import org.jetbrains.kotlin.konan.file.File

internal object Android {
    const val API = "21"
    private val architectureMap = mapOf(
            KonanTarget.ANDROID_X86 to "x86",
            KonanTarget.ANDROID_X64 to "x86_64",
            KonanTarget.ANDROID_ARM32 to "arm",
            KonanTarget.ANDROID_ARM64 to "arm64"
    )

    fun architectureDirForTarget(target: KonanTarget) =
            "android-${API}/arch-${architectureMap.getValue(target)}"
}

sealed class ClangArgs(
        private val configurables: Configurables,
        private val forJni: Boolean
) {

    private val absoluteTargetToolchain = configurables.absoluteTargetToolchain
    private val absoluteTargetSysRoot = configurables.absoluteTargetSysRoot
    private val absoluteLlvmHome = configurables.absoluteLlvmHome(configurables.target)
    private val target = configurables.target
    private val targetCPU = configurables.targetCpu
    private val targetTriple = configurables.targetTriple

    // TODO: Should be dropped in favor of real MSVC target.
    private val argsForWindowsJni = forJni && target == KonanTarget.MINGW_X64

    private val clangArgsSpecificForKonanSources : List<String>
        get() {
            val konanOptions = listOfNotNull(
                    target.architecture.name.takeIf { target != KonanTarget.WATCHOS_ARM64 },
                    "ARM32".takeIf { target == KonanTarget.WATCHOS_ARM64 },
                    target.family.name.takeIf { target.family != Family.MINGW },
                    "WINDOWS".takeIf { target.family == Family.MINGW },
                    "MACOSX".takeIf { target.family == Family.OSX },

                    "NO_64BIT_ATOMIC".takeUnless { target.supports64BitAtomics() },
                    "NO_UNALIGNED_ACCESS".takeUnless { target.supportsUnalignedAccess() },
                    "FORBID_BUILTIN_MUL_OVERFLOW".takeUnless { target.supports64BitMulOverflow() },

                    "OBJC_INTEROP".takeIf { target.supportsObjcInterop() },
                    "HAS_FOUNDATION_FRAMEWORK".takeIf { target.hasFoundationFramework() },
                    "HAS_UIKIT_FRAMEWORK".takeIf { target.hasUIKitFramework() },
                    "REPORT_BACKTRACE_TO_IOS_CRASH_LOG".takeIf { target.supportsIosCrashLog() },
                    "SUPPORTS_GRAND_CENTRAL_DISPATCH".takeIf { target.supportsGrandCentralDispatch },
                    "SUPPORTS_SIGNPOSTS".takeIf { target.supportsSignposts },
            ).map { "KONAN_$it=1" }
            val otherOptions = listOfNotNull(
                    "USE_ELF_SYMBOLS=1".takeIf { target.binaryFormat() == BinaryFormat.ELF },
                    "ELFSIZE=${target.pointerBits()}".takeIf { target.binaryFormat() == BinaryFormat.ELF },
                    "MACHSIZE=${target.pointerBits()}".takeIf { target.binaryFormat() == BinaryFormat.MACH_O },
                    "__ANDROID__".takeIf { target.family == Family.ANDROID },
                    "USE_PE_COFF_SYMBOLS=1".takeIf { target.binaryFormat() == BinaryFormat.PE_COFF },
                    "UNICODE".takeIf { target.family == Family.MINGW },
                    "USE_WINAPI_UNWIND=1".takeIf { target.supportsWinAPIUnwind() },
                    "USE_GCC_UNWIND=1".takeIf { target.supportsGccUnwind() },
                    // Clang 11 does not support this attribute. We don't need to handle it properly,
                    // so just undefine it.
                    "NS_FORMAT_ARGUMENT(A)=".takeIf { target.family.isAppleFamily },
            )
            return (konanOptions + otherOptions).map { "-D$it" }
        }

    private val binDir = "$absoluteTargetToolchain/bin"
    // TODO: Use buildList
    private val commonClangArgs: List<String> = mutableListOf<List<String>>().apply {
        // Currently, MinGW toolchain contains old LLVM 8, and -fuse-ld=lld picks linker from there.
        // And, unfortunately, `-fuse-ld=<absolute path>` doesn't work correctly for MSVC toolchain.
        // That's why we just don't add $absoluteTargetToolchain/bin to binary search path in case of JNI compilation.
        // TODO: Can be removed after MinGW sysroot update.
        if (!argsForWindowsJni) {
            add(listOf("-B$binDir"))
        } else {
            require(configurables is MingwConfigurables)
            add(configurables.msvc.compilerFlags())
            add(configurables.windowsKit.compilerFlags())
            // Do not depend on link.exe from Visual Studio.
            add(listOf("-fuse-ld=lld"))
        }
        add(listOf("-fno-stack-protector"))
        if (configurables is GccConfigurables) {
            add(listOf("--gcc-toolchain=${configurables.absoluteGccToolchain}"))
        }
        val targetString: String = when {
            argsForWindowsJni -> "x86_64-pc-windows-msvc"
            configurables is AppleConfigurables -> {
                targetTriple.copy(
                        os = "${targetTriple.os}${configurables.osVersionMin}"
                ).toString()
            }
            else -> configurables.targetTriple.toString()
        }
        add(listOf("-target", targetString))
        val hasCustomSysroot = configurables is ZephyrConfigurables
                || configurables is WasmConfigurables
                || configurables is AndroidConfigurables
                || argsForWindowsJni
        if (!hasCustomSysroot) {
            when (configurables) {
                // isysroot and sysroot on darwin are _almost_ synonyms.
                // The first one parses SDKSettings.json while second one is not.
                is AppleConfigurables -> add(listOf("-isysroot", absoluteTargetSysRoot))
                else -> add(listOf("--sysroot=$absoluteTargetSysRoot"))
            }
        }
        // PIC is not required on Windows (and Clang will fail with `error: unsupported option '-fPIC'`)
        if (configurables !is MingwConfigurables) {
            // `-fPIC` allows us to avoid some problems when producing dynamic library.
            // See KT-43502.
            add(listOf("-fPIC"))
        }
        // See https://github.com/apple/llvm-project/commit/dfa99c49306262eac46becbf1f7f5ba33ecc2fe3
        // TL;DR: This commit adds an OS-agnostic __ENVIRONMENT_OS_VERSION_MIN_REQUIRED__ macro and now
        // some of the Xcode headers use it instead of platform-specific ones. Workaround this problem
        // by manually setting this macro.
        // YouTrack ticket: KT-59167
        val environmentOsVersionMinRequired = when (target.family) {
            Family.OSX -> "__ENVIRONMENT_MAC_OS_X_VERSION_MIN_REQUIRED__"
            Family.IOS -> "__ENVIRONMENT_IPHONE_OS_VERSION_MIN_REQUIRED__"
            Family.TVOS -> "__ENVIRONMENT_TV_OS_VERSION_MIN_REQUIRED__"
            Family.WATCHOS -> "__ENVIRONMENT_WATCH_OS_VERSION_MIN_REQUIRED__"
            else -> null
        }
        if (environmentOsVersionMinRequired != null) {
            add(listOf("-D__ENVIRONMENT_OS_VERSION_MIN_REQUIRED__=$environmentOsVersionMinRequired"))
        }

        // Workaround to make Xcode 15.3 SDK headers work with older Clang from K/N. This should be removed after LLVM update: KT-49279
        // We don't handle simulator targets here, because they use different machinery for macro-expansion (see DYNAMIC_TARGETS_ENABLED)
        // YouTrack ticket:  KT-65542
        val targetConditionals = when (target) {
            KonanTarget.MACOS_ARM64, KonanTarget.MACOS_X64 -> hashMapOf(
                "TARGET_OS_OSX" to "1",
            )
            KonanTarget.IOS_ARM64 -> hashMapOf(
                "TARGET_OS_EMBEDDED" to "1",
                "TARGET_OS_IPHONE" to "1",
                "TARGET_OS_IOS" to "1",
            )
            KonanTarget.TVOS_ARM64 -> hashMapOf(
                "TARGET_OS_EMBEDDED" to "1",
                "TARGET_OS_IPHONE" to "1",
                "TARGET_OS_TV" to "1",
            )
            KonanTarget.WATCHOS_ARM64, KonanTarget.WATCHOS_ARM32, KonanTarget.WATCHOS_DEVICE_ARM64 -> hashMapOf(
                "TARGET_OS_EMBEDDED" to "1",
                "TARGET_OS_IPHONE" to "1",
                "TARGET_OS_WATCH" to "1",
            )
            else -> null
        }
        if (targetConditionals != null) {
            add(targetConditionals.map { "-D${it.key}=${it.value}" })
        }

        if (target.family.isAppleFamily && target.architecture == Architecture.X64) {
            add(listOf("-D_Float16=short"))
        }
    }.flatten()

    private val specificClangArgs: List<String> = when (target) {
        KonanTarget.LINUX_ARM32_HFP -> listOf(
                "-mfpu=vfp", "-mfloat-abi=hard"
        )

       KonanTarget.WATCHOS_ARM32 -> listOf(
                // Force generation of ARM instruction set instead of Thumb-2.
                // It allows LLVM ARM backend to encode bigger offsets in BL instruction,
                // thus allowing to generate a slightly bigger binaries.
                // See KT-37368.
                "-marm"
        )

        KonanTarget.ANDROID_ARM32, KonanTarget.ANDROID_ARM64,
        KonanTarget.ANDROID_X86, KonanTarget.ANDROID_X64 -> {
            val clangTarget = targetTriple.withoutVendor()
            val architectureDir = Android.architectureDirForTarget(target)
            val toolchainSysroot = "$absoluteTargetToolchain/sysroot"
            listOf(
                    "-D__ANDROID_API__=${Android.API}",
                    "--sysroot=$absoluteTargetSysRoot/$architectureDir",
                    "-I$toolchainSysroot/usr/include/c++/v1",
                    "-I$toolchainSysroot/usr/include",
                    "-I$toolchainSysroot/usr/include/$clangTarget"
            )
        }

        KonanTarget.OHOS_ARM64 -> listOf(
            "-mcpu=$targetCPU",
            "-I$absoluteTargetSysRoot/usr/include/$targetTriple",
            "-I$absoluteLlvmHome/lib/clang/12.0.1/include"
        )

        else -> emptyList()
    }

    val clangPaths = listOf("$absoluteLlvmHome/bin", binDir)

    /**
     * Clang args for Objectice-C and plain C compilation.
     */
    val clangArgs: Array<String> = (commonClangArgs + specificClangArgs).toTypedArray()

    /**
     * Clang args for C++ compilation.
     */
    val clangXXArgs: Array<String> = clangArgs + when (configurables) {
        is AppleConfigurables -> arrayOf(
                "-stdlib=libc++",
                // KT-57848
                "-Dat_quick_exit=atexit", "-Dquick_exit=exit",
        )
        else -> emptyArray()
    }

    val clangArgsForKonanSources =
            clangXXArgs + clangArgsSpecificForKonanSources

    private val libclangSpecificArgs =
            // libclang works not exactly the same way as the clang binary and
            // (in particular) uses different default header search path.
            // See e.g. http://lists.llvm.org/pipermail/cfe-dev/2013-November/033680.html
            // We workaround the problem with -isystem flag below.
            // TODO: Revise after update to LLVM 10.
            listOf("-isystem", "$absoluteLlvmHome/lib/clang/${configurables.llvmVersion}/include")

    /**
     * libclang args for plain C and Objective-C.
     *
     * Note that it's different from [clangArgs].
     */
    val libclangArgs: List<String> =
            libclangSpecificArgs + clangArgs

    /**
     * libclang args for C++.
     *
     * Note that it's different from [clangXXArgs].
     */
    val libclangXXArgs: List<String> =
            libclangSpecificArgs + clangXXArgs

    private val targetClangCmd
            = listOf("${absoluteLlvmHome}/bin/clang") + clangArgs

    private val targetClangXXCmd
            = listOf("${absoluteLlvmHome}/bin/clang++") + clangXXArgs

    private val targetArCmd
            = listOf("${absoluteLlvmHome}/bin/llvm-ar")


    fun clangC(vararg userArgs: String) = targetClangCmd + userArgs.asList()

    fun clangCXX(vararg userArgs: String) = targetClangXXCmd + userArgs.asList()

    fun llvmAr(vararg userArgs: String) = targetArCmd + userArgs.asList()

    /**
     * Should be used when compiling library for JNI.
     * For example, it is used for Kotlin/Native's Clang and LLVM libraries.
     */
    class Jni(configurables: Configurables) : ClangArgs(configurables, forJni = true) {
        private val jdkDir by lazy {
            val home = File.javaHome.absoluteFile
            if (home.child("include").exists)
                home.absolutePath
            else
                home.parentFile.absolutePath
        }

        val hostCompilerArgsForJni: Array<String> by lazy {
            listOf("", HostManager.jniHostPlatformIncludeDir)
                    .map { "-I$jdkDir/include/$it" }
                    .toTypedArray()
        }
    }

    /**
     * Used for compiling native code that meant to be run on end-user's hardware.
     * E.g., Kotlin/Native runtime and interop stubs.
     */
    class Native(configurables: Configurables) : ClangArgs(configurables, forJni = false)
}
