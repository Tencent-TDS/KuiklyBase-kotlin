/*
 * Copyright 2010-2025 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package infra

import org.junit.jupiter.api.DynamicContainer
import org.junit.jupiter.api.DynamicNode
import org.junit.jupiter.api.DynamicTest
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.div
import kotlin.io.path.extension
import kotlin.io.path.readText
import kotlin.io.path.walk
import kotlin.streams.asStream

object TestDataUtils {
    val testDataDir: Path = Paths.get(File(System.getProperty("user.dir") ?: ".").parent, "testData")

    // TODO: for some reason, it's not possible to depend on `:compiler:test-infrastructure-utils` here
    // See org.jetbrains.kotlin.codeMetaInfo.CodeMetaInfoParser
    private val openingDiagnosticRegex = """(<!([^"]*?((".*?")(, ".*?")*?)?[^"]*?)!>)""".toRegex()
    private val closingDiagnosticRegex = """(<!>)""".toRegex()

    private val xmlLikeTagsRegex = """(</?(?:selection|expr|caret)>)""".toRegex()

    private val allMetadataRegex =
        """(${closingDiagnosticRegex.pattern}|${openingDiagnosticRegex.pattern}|${xmlLikeTagsRegex.pattern})""".toRegex()

    fun checkDiagnosticsTestDataFiles(kotlinFileChecker: (String, Path) -> Unit) {
        val diagnosticsDir = testDataDir / "diagnostics"
        diagnosticsDir.walk().forEach { file ->
            if (file.extension == "kt") {
                val source= file.readText()
                // TODO: implement copy tokens without metadata
                val refinedText = source.replace(allMetadataRegex, "")
                if (source != refinedText) {
                    Unit
                }
                kotlinFileChecker(refinedText, file)
            }
        }
    }

    fun createTests(kotlinFileChecker: (String, Path) -> Unit): Iterator<DynamicNode> {
        val diagnosticsDir = testDataDir /// "diagnostics"
        return diagnosticsDir.toFile().getChildrenTests(kotlinFileChecker).iterator()
    }

    private fun File.getChildrenTests(kotlinFileChecker: (String, Path) -> Unit): Sequence<DynamicNode> {
        return walk().maxDepth(1).filter {
            it != this && (it.isDirectory || it.extension == "kt")
        }.map { it.createTest(kotlinFileChecker) }
    }

    private fun File.createTest(kotlinFileChecker: (String, Path) -> Unit): DynamicNode {
        return if (isDirectory) {
            DynamicContainer.dynamicContainer(name, toURI(), getChildrenTests(kotlinFileChecker).asStream())
        } else {
            DynamicTest.dynamicTest(nameWithoutExtension, toURI()) {
                val text = readText()
                val refinedText = text.replace(allMetadataRegex, "")
                if (text != refinedText) {
                    Unit
                }
                kotlinFileChecker(refinedText, toPath())
            }
        }
    }
}

