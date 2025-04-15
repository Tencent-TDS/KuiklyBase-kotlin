/*
 * Copyright 2010-2025 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.mpp

import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.gradle.plugin.diagnostics.KotlinToolingDiagnostics
import org.jetbrains.kotlin.gradle.testbase.*
import org.jetbrains.kotlin.gradle.uklibs.applyMultiplatform
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.condition.OS
import kotlin.io.path.exists
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@MppGradlePluginTests
class MppCrossCompilationPublicationIT : KGPBaseTest() {

    @DisplayName("Cross compilation enabled, no cinterops, publish library to mavenLocal")
    @GradleTest
    fun testCrossCompilationPublicationWithoutCinterops(
        gradleVersion: GradleVersion,
    ) {
        val multiplatformLibrary = project("multiplatformLibrary", gradleVersion) {
            addKgpToBuildScriptCompilationClasspath()
            buildScriptInjection {
                project.applyMultiplatform {
                    jvm()
                    iosArm64()
                    macosArm64()
                    mingwX64()
                    linuxX64()
                }
            }

            build("publishAllPublicationsToMavenRepository") {
                assertNoDiagnostic(KotlinToolingDiagnostics.CrossCompilationWithCinterops)
            }
        }

        val mavenUrl = multiplatformLibrary.projectPath.resolve("build/repo")
        val libraryRoot = mavenUrl.resolve("com/jetbrains/library")

        // Verify that module directories exist for each target
        val expectedModules = listOf(
            "multiplatformLibrary",
            "multiplatformLibrary-iosarm64",
            "multiplatformLibrary-jvm",
            "multiplatformLibrary-linuxx64",
            "multiplatformLibrary-macosarm64",
            "multiplatformLibrary-mingwx64"
        )

        for (module in expectedModules) {
            val moduleDir = libraryRoot.resolve(module)
            assertTrue(moduleDir.exists(), "Module directory not found: $module")

            // Verify version directory exists
            val versionDir = moduleDir.resolve("1.0")
            assertTrue(versionDir.exists(), "Version directory not found for $module")

            // For native targets, verify KLIB exists
            if (module != "multiplatformLibrary" && module != "multiplatformLibrary-jvm") {
                val klibFile = versionDir.resolve("$module-1.0.klib")
                assertTrue(klibFile.exists(), "KLIB file not found for $module")
            }

            // Check for module metadata
            val metadataFile = versionDir.resolve("$module-1.0.module")
            assertTrue(metadataFile.exists(), "Module metadata not found for $module")

            // Check for POM file
            val pomFile = versionDir.resolve("$module-1.0.pom")
            assertTrue(pomFile.exists(), "POM file not found for $module")
        }
    }

    @DisplayName("Cross compilation disabled, no cinterops, publish library to mavenLocal")
    @GradleTest
    @OsCondition(supportedOn = [OS.LINUX, OS.WINDOWS], enabledOnCI = [OS.LINUX, OS.WINDOWS])
    fun testDisabledCrossCompilationPublicationWithoutCinterops(
        gradleVersion: GradleVersion,
    ) {
        val multiplatformLibrary = project(
            "multiplatformLibrary",
            gradleVersion,
            buildOptions = defaultBuildOptions.copy(
                nativeOptions = defaultBuildOptions.nativeOptions.copy(
                    disableKlibsCrossCompilation = true
                )
            )
        ) {
            addKgpToBuildScriptCompilationClasspath()
            buildScriptInjection {
                project.applyMultiplatform {
                    jvm()
                    iosArm64()
                    macosArm64()
                    mingwX64()
                    linuxX64()
                }
            }

            build("publishAllPublicationsToMavenRepository") {
                assertNoDiagnostic(KotlinToolingDiagnostics.CrossCompilationWithCinterops)
            }
        }

        val mavenUrl = multiplatformLibrary.projectPath.resolve("build/repo")
        val libraryRoot = mavenUrl.resolve("com/jetbrains/library")

        // Verify that only the common and JVM module directories exist
        val expectedModules = listOf(
            "multiplatformLibrary",
            "multiplatformLibrary-jvm",
            "multiplatformLibrary-linuxx64",
            "multiplatformLibrary-mingwx64"
        )

        // Check that expected modules exist
        for (module in expectedModules) {
            val moduleDir = libraryRoot.resolve(module)
            assertTrue(moduleDir.exists(), "Module directory not found: $module")

            // Verify version directory exists
            val versionDir = moduleDir.resolve("1.0")
            assertTrue(versionDir.exists(), "Version directory not found for $module")

            // Check for module metadata
            val metadataFile = versionDir.resolve("$module-1.0.module")
            assertTrue(metadataFile.exists(), "Module metadata not found for $module")

            // Check for POM file
            val pomFile = versionDir.resolve("$module-1.0.pom")
            assertTrue(pomFile.exists(), "POM file not found for $module")
        }

        // Verify that native modules are NOT published
        val unexpectedModules = listOf(
            "multiplatformLibrary-iosarm64",
            "multiplatformLibrary-macosarm64",
        )

        for (module in unexpectedModules) {
            val moduleDir = libraryRoot.resolve(module)
            assertFalse(moduleDir.exists(), "Native module directory should not exist when cross-compilation is disabled: $module")
        }
    }
}