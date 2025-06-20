/*
 * Copyright 2010-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin

import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.internal.file.FileOperations
import org.gradle.api.model.ObjectFactory
import org.gradle.process.ExecOperations
import org.gradle.process.ExecResult
import org.gradle.process.ExecSpec
import org.jetbrains.kotlin.konan.target.*
import java.io.File
import javax.inject.Inject

abstract class ExecClang @Inject constructor(
        private val platformManager: PlatformManager,
) {

    @get:Inject
    protected abstract val fileOperations: FileOperations
    @get:Inject
    protected abstract val execOperations: ExecOperations

    private fun clangArgsForCppRuntime(target: KonanTarget): List<String> {
        return platformManager.platform(target).clang.clangArgsForKonanSources.asList()
    }

    fun clangArgsForCppRuntime(targetName: String?): List<String> {
        val target = platformManager.targetManager(targetName).target
        return clangArgsForCppRuntime(target)
    }

    fun resolveExecutable(targetName: String, executableOrNull: String?): String {
        val target = platformManager.targetManager(targetName).target
        return resolveExecutable(target, executableOrNull)
    }

    fun resolveExecutable(target: KonanTarget, executableOrNull: String?): String {
        val executable = executableOrNull ?: "clang"

        val platform = platformManager.platform(target)
        if (listOf("clang", "clang++").contains(executable)) {
            return "${platform.absoluteLlvmHome(target)}/bin/$executable"
        } else {
            throw GradleException("unsupported clang executable: $executable")
        }
    }

    fun resolveToolchainExecutable(target: KonanTarget, executableOrNull: String?): String {
        val executable = executableOrNull ?: "clang"

        if (listOf("clang", "clang++").contains(executable)) {
            // TODO: This is copied from `BitcodeCompiler`. Consider sharing the code instead.
            val platform = platformManager.platform(target)
            return "${platform.absoluteTargetToolchain}/bin/$executable"
        } else {
            throw GradleException("unsupported clang executable: $executable")
        }
    }

    // The bare ones invoke clang with system default sysroot.

    fun execBareClang(action: Action<in ExecSpec>): ExecResult {
        return this.execClang(platformManager.targetByName("host"), emptyList(), action)
    }

    // The konan ones invoke clang with konan provided sysroots.
    // So they require a target or assume it to be the host.
    // The target can be specified as KonanTarget or as a
    // (nullable, which means host) target name.

    // FIXME: See KT-65542 for details
    private fun fixBrokenMacroExpansionInXcode15_3(target: String?) = fixBrokenMacroExpansionInXcode15_3(platformManager.targetManager(target).target)

    private fun fixBrokenMacroExpansionInXcode15_3(target: KonanTarget): List<String> {
        return when (target) {
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
            else -> emptyMap()
        }.map { "-D${it.key}=${it.value}" }
    }

    fun execKonanClang(target: String?, action: Action<in ExecSpec>): ExecResult {
        return this.execClang(platformManager.targetManager(target).target, clangArgsForCppRuntime(target) + fixBrokenMacroExpansionInXcode15_3(target), action)
    }

    fun execKonanClang(target: KonanTarget, action: Action<in ExecSpec>): ExecResult {
        return this.execClang(target, clangArgsForCppRuntime(target) + fixBrokenMacroExpansionInXcode15_3(target), action)
    }

    // The toolchain ones execute clang from the toolchain.

    fun execToolchainClang(target: KonanTarget, action: Action<in ExecSpec>): ExecResult {
        val extendedAction = Action<ExecSpec> {
            action.execute(this)
            executable = resolveToolchainExecutable(target, executable)
            println("${executable} ${args.joinToString(separator = " ")})")
        }
        return execOperations.exec(extendedAction)
    }

    private fun execClang(target: KonanTarget, defaultArgs: List<String>, action: Action<in ExecSpec>): ExecResult {
        val extendedAction = Action<ExecSpec> {
            action.execute(this)
            executable = resolveExecutable(target, executable)

            val hostPlatform = platformManager.hostPlatform
            environment["PATH"] = fileOperations.configurableFiles(hostPlatform.clang.clangPaths).asPath +
                    File.pathSeparator + environment["PATH"]
            args = args + defaultArgs
            if (target == KonanTarget.OHOS_ARM64) {
                args = args.filter { it != "-Werror" }
            }
            println("${executable} ${args.joinToString(separator = " ")})")
        }
        return execOperations.exec(extendedAction)
    }

    companion object {
        @JvmStatic
        fun create(objects: ObjectFactory, platformManager: PlatformManager) =
                objects.newInstance(ExecClang::class.java, platformManager)
    }
}
