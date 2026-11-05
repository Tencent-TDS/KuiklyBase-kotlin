/*
 * Tencent is pleased to support the open source community by making Kotlin available.
 * Copyright (C) 2026 THL A29 Limited, a Tencent company. All rights reserved.
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

package org.jetbrains.kotlin.konan.test.klib

import org.jetbrains.kotlin.konan.test.blackbox.*
import org.jetbrains.kotlin.konan.test.blackbox.support.*
import org.jetbrains.kotlin.konan.test.blackbox.support.compilation.TestCompilationArtifact
import org.jetbrains.kotlin.konan.test.blackbox.support.compilation.TestCompilationResult.Companion.assertSuccess
import org.jetbrains.kotlin.konan.test.blackbox.support.runner.TestExecutable
import org.jetbrains.kotlin.konan.test.blackbox.support.runner.TestRunCheck
import org.jetbrains.kotlin.library.components.KlibIrComponentLayout
import org.jetbrains.kotlin.library.encodings.WobblyTF8
import org.jetbrains.kotlin.library.impl.IrArrayReader
import org.jetbrains.kotlin.library.impl.IrArrayWriter
import org.jetbrains.kotlin.library.impl.IrStringWriter
import org.jetbrains.kotlin.library.impl.toArray
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.io.path.readBytes

@Tag("klib")
@EnforcedHostTarget
@EnforcedProperty(ClassLevelProperty.CACHE_MODE, "NO")
class KT90675ValueClassEqualsTest : AbstractNativeSimpleTest() {

    @Test
    fun `value class equals from klib with legacy GENERATED_SINGLE_FIELD_VALUE_CLASS_MEMBER does not box`() {
        val libDir = buildDir.resolve("lib").apply { mkdirs() }
        libDir.resolve("lib.kt").writeText(
            """
            value class LegacyVal(val x: Int)
            """.trimIndent()
        )

        val library = compileLibrary(
            settings = testRunSettings,
            source = libDir,
            packed = false,
        ).assertSuccess().resultingArtifact

        patchKlibOriginToLegacyName(library)

        val fileCheckStage = "CStubs"
        val mainFile = buildDir.resolve("main.kt")
        mainFile.writeText(
            """
            // CHECK-LABEL: define {{i1|zeroext i1}} @"kfun:#compareLegacyVal(LegacyVal;LegacyVal){}kotlin.Boolean"
            // CHECK-NOT: <LegacyVal-box>
            // CHECK-NOT: <LegacyVal-unbox>
            // CHECK-NOT: kfun:kotlin.native.internal#areEqual(kotlin.Any?;kotlin.Any?){}kotlin.Boolean
            // CHECK: icmp eq i32
            // CHECK-LABEL: epilogue:
            fun compareLegacyVal(a: LegacyVal, b: LegacyVal): Boolean = a == b

            fun main() {
                check(compareLegacyVal(LegacyVal(42), LegacyVal(42)))
                check(!compareLegacyVal(LegacyVal(42), LegacyVal(7)))
            }
            """.trimIndent()
        )

        val testCase = generateTestCaseWithSingleFile(
            sourceFile = mainFile,
            freeCompilerArgs = TestCompilerArgs(
                listOf(
                    "-Xsave-llvm-ir-after=$fileCheckStage",
                    "-Xsave-llvm-ir-directory=${buildDir.absolutePath}",
                )
            ),
            testKind = TestKind.STANDALONE_NO_TR,
            extras = TestCase.NoTestRunnerExtras("main"),
        )

        val compilationResult = compileToExecutableInOneStage(
            testCase,
            library.asLibraryDependency(),
        ).assertSuccess()

        val fileCheckDump = buildDir.resolve("out.$fileCheckStage.ll")
        assertTrue(fileCheckDump.exists()) { "Expected LLVM IR dump at ${fileCheckDump.absolutePath}" }

        val fileCheckResult = TestRunCheck.FileCheckMatcher(testRunSettings, mainFile).doFileCheck(fileCheckDump)
        if (!(fileCheckResult.stdout.isEmpty() && fileCheckResult.stderr.isEmpty())) {
            fail<Unit>(
                "FileCheck matching of ${fileCheckDump.absolutePath} failed:\n" +
                        "${fileCheckResult.stdout}\n${fileCheckResult.stderr}"
            )
        }

        runExecutableAndVerify(testCase, TestExecutable.fromCompilationResult(testCase, compilationResult))
    }

    private fun patchKlibOriginToLegacyName(klib: TestCompilationArtifact.KLIB) {
        val stringsFile = KlibIrComponentLayout.createForMainIr(klib.klibFile.toPath()).stringLiteralsFile
        var replaced = false
        val patchedTables = IrArrayReader(stringsFile.readBytes()).toArray().map { tableBytes ->
            val strings = IrArrayReader(tableBytes).toArray().map(WobblyTF8::decode)
            val patchedStrings = strings.map { str ->
                if (str == "GENERATED_INLINE_CLASS_MEMBER") {
                    replaced = true
                    "GENERATED_SINGLE_FIELD_VALUE_CLASS_MEMBER"
                } else {
                    str
                }
            }
            IrStringWriter(patchedStrings, useVarInt = true).writeIntoMemory()
        }
        assertTrue(replaced) { "Expected GENERATED_INLINE_CLASS_MEMBER in ${stringsFile.toAbsolutePath()}" }
        IrArrayWriter(patchedTables, useVarInt = false).writeIntoFile(stringsFile)
    }
}
