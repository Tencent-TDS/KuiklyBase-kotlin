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

package org.jetbrains.kotlin.konan.target

import org.jetbrains.kotlin.konan.exec.Command
import org.jetbrains.kotlin.konan.file.isUnixStaticLib
import org.jetbrains.kotlin.konan.properties.KonanPropertiesLoader
import org.jetbrains.kotlin.konan.properties.Properties
import org.jetbrains.kotlin.konan.util.ProgressCallback
import java.io.File

class OhosConfigurablesImpl(
    target: KonanTarget,
    properties: Properties,
    dependenciesRoot: String?,
    progressCallback: ProgressCallback,
) : OhosConfigurables, KonanPropertiesLoader(target, properties, dependenciesRoot, progressCallback = progressCallback) {

    private class SdkLayout(
        val sysroot: String,
        val libcxxInclude: String,
        val libcxxLibraryDirectories: List<String>,
    )

    private val sdkLayout: SdkLayout by lazy { resolveSdkLayout() }

    override val targetSysRoot: String?
        get() = sdkLayout.sysroot

    override val libcxxInclude: String
        get() = sdkLayout.libcxxInclude

    override val libcxxLibraryDirectories: List<String>
        get() = sdkLayout.libcxxLibraryDirectories

    private fun resolveSdkLayout(): SdkLayout {
        val packagedName = super<OhosConfigurables>.targetSysRoot
            ?: error("targetSysRoot.ohos_arm64 is not set")
        // A relative name is the archive extracted beside the LLVM distribution.
        val packagedRoot = if (File(packagedName).isAbsolute) {
            File(packagedName)
        } else {
            val llvm = llvmHome ?: error("llvmHome is not set")
            File(File(absolute(llvm)).parentFile, packagedName)
        }
        val packagedCxx = File(packagedRoot, "usr/include/c++/v1")
        // `.invalid` marks a tree that must not be used. A missing tree means the mirror
        // did not provide the sysroot, so use the DevEco native SDK instead.
        if (packagedRoot.isDirectory && packagedCxx.isDirectory && !File(packagedRoot, ".invalid").exists()) {
            return SdkLayout(
                sysroot = packagedRoot.absolutePath,
                libcxxInclude = packagedCxx.path,
                libcxxLibraryDirectories = emptyList(),
            )
        }

        val sdkRoot = getLocalSdkPath()
        val nativeRoot = File(sdkRoot, "native")
        val devecoSysroot = File(nativeRoot, "sysroot")
        val devecoCxx = File(nativeRoot, "llvm/include/libcxx-ohos/include/c++/v1")
        val devecoLibs = File(nativeRoot, "llvm/lib/$targetTriple")
        if (devecoSysroot.isDirectory && devecoCxx.isDirectory && devecoLibs.isDirectory) {
            checkOhosSdkVersion(sdkRoot)
            return SdkLayout(
                sysroot = devecoSysroot.path,
                libcxxInclude = devecoCxx.path,
                libcxxLibraryDirectories = listOf(devecoLibs.path),
            )
        }

        error(
            "OHOS sysroot is not available.\n" +
                    "Internal builds use $packagedRoot (usr/include/c++/v1 must exist, and .invalid must not).\n" +
                    "Otherwise install DevEco Studio or set OHOS_SDK_HOME to the openharmony SDK " +
                    "(expected native/sysroot, native/llvm/include/libcxx-ohos and native/llvm/lib/$targetTriple). " +
                    "Looked in '$sdkRoot'."
        )
    }

    private fun getLocalSdkPath(): String {
        return getSystemValue("OHOS_SDK_HOME") ?: getBundledSdkFromDevEcoStudio()
    }
    
    private fun getBundledSdkFromDevEcoStudio(): String {
        val devEcoStudioHome = getSystemValue("DEVECO_STUDIO_HOME")
        return when (HostManager.host) {
            KonanTarget.MINGW_X64 -> {
                File(devEcoStudioHome ?: "C:\\Program Files\\Huawei\\DevEco Studio\\", "sdk\\default\\openharmony").path
            }
            KonanTarget.MACOS_X64, KonanTarget.MACOS_ARM64 -> {
                File(devEcoStudioHome ?: "/Applications/DevEco-Studio.app", "Contents/sdk/default/openharmony").path
            }
            else -> {
                // DevEco Studio does not support Linux. The path will be validated outside, don't worry about it. 
                "/usr/local/lib/DevEco-Studio/sdk/default/openharmony"
            }
        }
    }

    private fun checkOhosSdkVersion(sdkRoot: String) {
        if (properties.getProperty("ignoreOhosSdkVersionCheck") != "true") {
            properties.getProperty("minimalOhosSdkVersion")?.toInt()?.let { minimalOhosSdkVersion ->
                val sdkPkg = File(sdkRoot, "native/oh-uni-package.json").readText()
                val apiVersion = Regex(""""apiVersion": "(\d+)"""").find(sdkPkg)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: Int.MAX_VALUE
                if (apiVersion < minimalOhosSdkVersion) {
                    error("Unsupported OHOS SDK version $apiVersion(bundled in $sdkRoot), minimal supported version is $minimalOhosSdkVersion.")
                }
            }
        }
    }

    private fun getSystemValue(key: String): String? {
        return (System.getProperty(key) ?: System.getenv(key))?.takeIf { it.isNotBlank() }
    }
}

interface OhosConfigurables : Configurables, ClangFlags {
    val dynamicLinker get() = targetString("dynamicLinker")!!
    val abiSpecificLibraries get() = targetList("abiSpecificLibraries")
    val crtFilesLocation get() = targetString("crtFilesLocation")!!

    val linker get() = hostTargetString("linker")
    val linkerHostSpecificFlags get() = hostTargetList("linkerHostSpecificFlags")
    val absoluteLinker get() = absolute(linker)

    /**
     * libc++ headers. Upstream clang does not search the sysroot or `libcxx-ohos` for these.
     * The packaged sysroot puts them at `usr/include/c++/v1`. A DevEco native tree keeps them
     * under `llvm/include/libcxx-ohos`.
     */
    val libcxxInclude: String
        get() = "$absoluteTargetSysRoot/usr/include/c++/v1"

    /**
     * Extra `-L` directories for `libc++`, `libc++abi` and `libunwind`.
     * Empty when those libraries already live in [abiSpecificLibraries] under the sysroot.
     */
    val libcxxLibraryDirectories: List<String>
        get() = emptyList()
}

class OhosLinker(targetProperties: OhosConfigurables) : LinkerFlags(targetProperties), OhosConfigurables by targetProperties {

    private val specificLibs =
        abiSpecificLibraries.map { "-L${absoluteTargetSysRoot}/$it" } +
                libcxxLibraryDirectories.map { "-L$it" }
    private val llvmAr = "$absoluteLlvmHome/bin/llvm-ar"

    override val useCompilerDriverAsLinker: Boolean get() = true

    override fun filterStaticLibraries(binaries: List<String>) = binaries.filter { it.isUnixStaticLib }

    override fun LinkerArguments.finalLinkCommands(): List<Command> {
        if (kind == LinkerOutputKind.STATIC_LIBRARY)
            return llvmArStaticLibraryCommands(llvmAr, executable, objectFiles, staticLibraries, tempFiles)

        val dynamic = kind == LinkerOutputKind.DYNAMIC_LIBRARY
        val crtPrefix = "$absoluteTargetSysRoot/$crtFilesLocation"
        val libraries = staticLibraries + dynamicLibraries
        val librariesArgs = if (libraries.isEmpty()) {
            emptyList()
        } else {
            tempFiles.create("libraries").let { librariesListFile ->
                librariesListFile.writeLines(libraries)
                listOf("@${librariesListFile.absolutePath}")
            }
        }
        return listOf(Command(absoluteLinker).apply {
            +"--sysroot=${absoluteTargetSysRoot}"
            +"-export-dynamic"
            +"-z"
            +"relro"
            +"--build-id"
            +"--eh-frame-hdr"
            if (!dynamic) {
                +"-dynamic-linker"
                +dynamicLinker
            }
            linkerHostSpecificFlags.forEach { +it }
            +"-o"
            +executable
            if (!dynamic) +"$crtPrefix/Scrt1.o"
            +"$crtPrefix/crti.o"
            +"--hash-style=gnu"
            +specificLibs
            if (optimize) +linkerOptimizationFlags
            if (!debug) +linkerNoDebugFlags
            if (dynamic) +linkerDynamicFlags
            if (dynamic) +"--soname=${File(executable).name}"
            +objectFiles
            +librariesArgs
            +linkerArgs
            +linkerKonanFlags
            +"$crtPrefix/crtn.o"
        })
    }
}