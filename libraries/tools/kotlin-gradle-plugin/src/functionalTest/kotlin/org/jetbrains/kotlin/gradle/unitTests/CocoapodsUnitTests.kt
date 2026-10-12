/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

@file:Suppress("FunctionName")

package org.jetbrains.kotlin.gradle.unitTests

import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.plugin.PropertiesProvider.PropertyNames
import org.jetbrains.kotlin.gradle.plugin.cocoapods.KotlinCocoapodsPlugin
import org.jetbrains.kotlin.gradle.plugin.cocoapods.KotlinCocoapodsPlugin.Companion.CFLAGS_PROPERTY
import org.jetbrains.kotlin.gradle.plugin.cocoapods.KotlinCocoapodsPlugin.Companion.FRAMEWORK_PATHS_PROPERTY
import org.jetbrains.kotlin.gradle.plugin.cocoapods.KotlinCocoapodsPlugin.Companion.HEADER_PATHS_PROPERTY
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.TestExecutable
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.FrameworkCopy
import org.jetbrains.kotlin.gradle.targets.native.cocoapods.CocoapodsPluginDiagnostics
import org.jetbrains.kotlin.gradle.targets.native.tasks.PodSetupBuildTask
import org.jetbrains.kotlin.gradle.tasks.DefFileTask
import org.jetbrains.kotlin.gradle.tasks.FatFrameworkTask
import org.jetbrains.kotlin.gradle.util.*
import org.jetbrains.kotlin.gradle.util.assertContainsDiagnostic
import org.jetbrains.kotlin.konan.target.HostManager
import org.junit.jupiter.api.Assumptions
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CocoapodsUnitTests {
    @Test
    fun `migration warning is reported when cocoapods plugin is applied`() {
        buildProjectWithCocoapods {
            assertContainsDiagnostic(CocoapodsPluginDiagnostics.SwiftPMMigrationSuggested)
        }
    }

    @Test
    fun `migration warning is not reported when cocoapods plugin is not applied`() {
        buildProjectWithMPP {
            assertNoDiagnostics(CocoapodsPluginDiagnostics.SwiftPMMigrationSuggested)
        }
    }

    @Test
    fun `migration warning is not reported when suppressed via property`() {
        buildProjectWithMPP {
            propertiesExtension.set(PropertyNames.KOTLIN_NATIVE_COCOAPODS_SWIFTPM_MIGRATION_NOWARN, "true")
            applyCocoapodsPlugin()
            assertNoDiagnostics(CocoapodsPluginDiagnostics.SwiftPMMigrationSuggested)
        }
    }

    @Test
    fun `warning is reported on non-mac machines`() {
        Assumptions.assumeTrue(!HostManager.hostIsMac)

        buildProjectWithCocoapods {
            assertContainsDiagnostic(CocoapodsPluginDiagnostics.UnsupportedOs)
        }
    }

    @Test
    fun `warning is reported when using deprecated properties`() {
        buildProjectWithMPP {
            propertiesExtension.set(CFLAGS_PROPERTY, "cflags value")

            applyCocoapodsPlugin()
            assertContainsDiagnostic(CocoapodsPluginDiagnostics.DeprecatedPropertiesUsed)
        }

        buildProjectWithMPP {
            propertiesExtension.set(HEADER_PATHS_PROPERTY, "header paths value")

            applyCocoapodsPlugin()
            assertContainsDiagnostic(CocoapodsPluginDiagnostics.DeprecatedPropertiesUsed)
        }

        buildProjectWithMPP {
            propertiesExtension.set(FRAMEWORK_PATHS_PROPERTY, "framework paths value")

            applyCocoapodsPlugin()
            assertContainsDiagnostic(CocoapodsPluginDiagnostics.DeprecatedPropertiesUsed)
        }

        buildProjectWithMPP {
            propertiesExtension.set(FRAMEWORK_PATHS_PROPERTY, "framework paths value")
            propertiesExtension.set(HEADER_PATHS_PROPERTY, "header paths value")

            applyCocoapodsPlugin()
            assertContainsDiagnostic(CocoapodsPluginDiagnostics.DeprecatedPropertiesUsed(listOf(FRAMEWORK_PATHS_PROPERTY, HEADER_PATHS_PROPERTY)))
        }
    }

    @Test
    fun `sync framework task dependencies - have aligned inputs and outputs`() {
        val project = buildProjectWithMPP {
            propertiesExtension.set(KotlinCocoapodsPlugin.CONFIGURATION_PROPERTY, "Debug")
            propertiesExtension.set(KotlinCocoapodsPlugin.PLATFORM_PROPERTY, "iphonesimulator")
            propertiesExtension.set(KotlinCocoapodsPlugin.ARCHS_PROPERTY, "arm64 x86_64")

            applyCocoapodsPlugin()
            kotlin {
                iosSimulatorArm64()
                @Suppress("DEPRECATION") // fixme: KT-81704 Cleanup tests after apple x64 family deprecation
                iosX64()

                cocoapods {
                    framework {
                        baseName = "foo"
                    }
                }
            }
        }.evaluate()

        val syncFrameworkTask = assertIsInstance<FrameworkCopy>(project.tasks.getByName("syncFramework"))
        val universalFrameworkTask = assertIsInstance<FatFrameworkTask>(project.tasks.getByName("fatFramework"))

        assertEquals(
            project.layout.buildDirectory.file("cocoapods/fat-frameworks/debug/foo.framework").get().asFile,
            syncFrameworkTask.sourceFramework.get().asFile,
        )
        assertEquals(
            project.layout.buildDirectory.file("cocoapods/fat-frameworks/debug/foo.framework").get().asFile,
            universalFrameworkTask.fatFramework,
        )
        assertEquals(
            listOf(
                project.layout.buildDirectory.file("bin/iosSimulatorArm64/podDebugFramework/foo.framework").get().asFile,
                project.layout.buildDirectory.file("bin/iosX64/podDebugFramework/foo.framework").get().asFile,
            ),
            universalFrameworkTask.frameworks.map { it.file },
        )
    }


    @Test
    fun `KT-67666 regression -- don't crash with eagerly created link tasks`() {
        Assumptions.assumeTrue(HostManager.hostIsMac)

        buildProjectWithCocoapods {
            kotlin {
                targets.withType<KotlinNativeTarget>().all { target ->
                    target.binaries.withType<TestExecutable>().all { it.linkTaskProvider.get() }
                }

                cocoapods {
                    pod("AmazingPod")
                }

                iosArm64()
            }

        }
    }

    // region @Tencent: Test modulemap module name detection and DefFileTask generation.
    @Test
    fun `def file uses resolved module name from modulemap when moduleName is not explicitly set`() {
        val project = buildProjectWithCocoapods {
            kotlin {
                iosArm64()
                cocoapods {
                    pod("MyPod")
                    pod("ExplicitPod") {
                        moduleName = "CustomExplicitModule"
                    }
                }
            }
        }.evaluate()

        val setupTask = assertIsInstance<PodSetupBuildTask>(project.tasks.getByName("podSetupBuildMyPodIos"))
        val tempPodDir = createTempDirectory("pod-modulemap-test").toFile()
        try {
            val modulemapFile = tempPodDir.resolve("Headers/module.modulemap").apply {
                parentFile.mkdirs()
                writeText(
                    """
                    framework module ActualModuleFromMap {
                        umbrella header "ActualModuleFromMap.h"
                        export *
                    }
                    """.trimIndent()
                )
            }
            assertTrue(modulemapFile.exists())

            val inferred = setupTask.inferModuleNameFromInstalledPods(tempPodDir.absolutePath)
            assertEquals("ActualModuleFromMap", inferred)

            val defTask = assertIsInstance<DefFileTask>(project.tasks.getByName("generateDefMyPod"))
            val depNames = defTask.taskDependencies.getDependencies(defTask).map { it.name }
            assertTrue("podBuildMyPodIos" in depNames, "Expected generateDefMyPod to depend on podBuildMyPodIos, got $depNames")

            val pod = defTask.pod.get()
            assertEquals("MyPod", pod.name)
            assertEquals("MyPod", pod.moduleName)
            pod.resolvedModuleName = inferred
            assertEquals("ActualModuleFromMap", pod.effectiveModuleName)

            defTask.generate()
            val defContent = defTask.defFile.get().asFile.readText()
            assertTrue("modules = ActualModuleFromMap" in defContent, "Unexpected def content:\n$defContent")
            assertTrue("linkerOpts = -framework ActualModuleFromMap" in defContent, "Unexpected def content:\n$defContent")

            val explicitDefTask = assertIsInstance<DefFileTask>(project.tasks.getByName("generateDefCustomExplicitModule"))
            val explicitPod = explicitDefTask.pod.get()
            assertEquals("CustomExplicitModule", explicitPod.effectiveModuleName)
            explicitDefTask.generate()
            val explicitDefContent = explicitDefTask.defFile.get().asFile.readText()
            assertTrue("modules = CustomExplicitModule" in explicitDefContent, "Unexpected def content:\n$explicitDefContent")
        } finally {
            tempPodDir.deleteRecursively()
        }
    }
    // endregion
}
