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

import org.jetbrains.kotlin.konan.TempFiles
import org.jetbrains.kotlin.konan.properties.Properties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class OhosLinkerTest {

    private fun createOhosLinker(tempDir: Path): OhosLinker {
        val sysrootDir = tempDir.resolve("sysroot").toFile()
        File(sysrootDir, "usr/include/c++/v1").mkdirs()

        val hostName = HostManager.host.name
        val properties = Properties().apply {
            setProperty("dependenciesUrl", "https://download.jetbrains.com/kotlin/native")
            setProperty("targetSysRoot.ohos_arm64", sysrootDir.absolutePath)
            setProperty("llvmHome.$hostName", tempDir.resolve("llvm").toString())
            setProperty("libffiDir.$hostName", "libffi-mock")
            setProperty("linker.$hostName-ohos_arm64", "/usr/bin/ld.lld")
            setProperty("dynamicLinker.ohos_arm64", "/lib/ld-musl-aarch64.so.1")
            setProperty("abiSpecificLibraries.ohos_arm64", "usr/lib/aarch64-linux-ohos")
            setProperty("crtFilesLocation.ohos_arm64", "usr/lib/aarch64-linux-ohos")
            setProperty("linkerNoDebugFlags.ohos_arm64", "-S")
            setProperty("linkerDynamicFlags.ohos_arm64", "-shared")
            setProperty("linkerOptimizationFlags.ohos_arm64", "--gc-sections")
            setProperty("linkerKonanFlags.ohos_arm64", "-lc")
        }
        val configurables = OhosConfigurablesImpl(
            target = KonanTarget.OHOS_ARM64,
            properties = properties,
            dependenciesRoot = tempDir.toString(),
            progressCallback = { _, _, _ -> },
        )
        return OhosLinker(configurables)
    }

    @Test
    fun finalLinkCommandsWritesLibrariesToResponseFile(@TempDir tempDir: Path) {
        val linker = createOhosLinker(tempDir)
        val tempFiles = TempFiles(tempDir.resolve("konan_temp").toString())
        val staticLibs = listOf("/path/to/libfoo.a", "/path/to/libbar.a")
        val dynamicLibs = listOf("/path/to/libbaz.so")

        val commands = with(linker) {
            LinkerArguments(
                tempFiles = tempFiles,
                objectFiles = listOf("/path/to/main.o"),
                executable = "/path/to/libout.so",
                staticLibraries = staticLibs,
                dynamicLibraries = dynamicLibs,
                linkerArgs = listOf("--extra-arg"),
                optimize = false,
                debug = true,
                kind = LinkerOutputKind.DYNAMIC_LIBRARY,
                outputDsymBundle = "",
            ).finalLinkCommands()
        }

        assertEquals(1, commands.size)
        val args = commands.single().args
        assertFalse(args.contains("/path/to/libfoo.a"))
        assertFalse(args.contains("/path/to/libbar.a"))
        assertFalse(args.contains("/path/to/libbaz.so"))
        assertFalse(args.any { it.endsWith("/Scrt1.o") })
        assertFalse(args.contains("-dynamic-linker"))
        assertTrue(args.last().endsWith("/crtn.o"))

        val responseArg = args.single { it.startsWith("@") }
        val responseFile = File(responseArg.removePrefix("@"))
        assertTrue(responseFile.isFile)
        assertEquals(staticLibs + dynamicLibs, responseFile.readLines())
    }

    @Test
    fun finalLinkCommandsOmitsResponseFileWhenLibrariesEmpty(@TempDir tempDir: Path) {
        val linker = createOhosLinker(tempDir)
        val tempFiles = TempFiles(tempDir.resolve("konan_temp").toString())

        val commands = with(linker) {
            LinkerArguments(
                tempFiles = tempFiles,
                objectFiles = listOf("/path/to/main.o"),
                executable = "/path/to/out.kexe",
                staticLibraries = emptyList(),
                dynamicLibraries = emptyList(),
                linkerArgs = emptyList(),
                optimize = false,
                debug = true,
                kind = LinkerOutputKind.EXECUTABLE,
                outputDsymBundle = "",
            ).finalLinkCommands()
        }

        assertEquals(1, commands.size)
        val args = commands.single().args
        assertFalse(args.any { it.startsWith("@") })
        assertTrue(args.any { it.endsWith("/Scrt1.o") })
        assertTrue(args.contains("-dynamic-linker"))
        assertTrue(args.last().endsWith("/crtn.o"))
    }
}
