/*
 * Copyright 2010-2025 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

@file:Suppress("FunctionName")

package org.jetbrains.kotlin.gradle.unitTests

import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.PropertiesProvider.PropertyNames.KOTLIN_NATIVE_DISABLE_KLIBS_CROSSCOMPILATION
import org.jetbrains.kotlin.gradle.plugin.diagnostics.KotlinToolingDiagnostics
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.util.*
import org.jetbrains.kotlin.konan.target.Family
import org.jetbrains.kotlin.konan.target.HostManager
import org.junit.Assume
import org.junit.Test

class CrossCompilationWithCinteropTests {

    @Test
    fun `cross compilation with macOS cinterop on Windows or Linux host`() {
        Assume.assumeTrue("Run on unsupported hosts (Windows/Linux)", HostManager.hostIsMingw || HostManager.hostIsLinux)

        val project = buildProjectWithMPP {
            kotlin {
                macosX64()
                linuxX64()
                mingwX64()

                addDummyCinterop { it.konanTarget.family.isAppleFamily }
            }
        }.evaluate()

        project.assertContainsDiagnostic(KotlinToolingDiagnostics.CrossCompilationWithCinterops)
    }

    @Test
    fun `cross compilation with macOS cinterop on macOS host`() {
        Assume.assumeTrue("Run on supported macOS host", HostManager.hostIsMac)

        val project = buildProjectWithMPP {
            kotlin {
                macosX64()
                linuxX64()
                mingwX64()

                addDummyCinterop { it.konanTarget.family.isAppleFamily }
            }
        }.evaluate()

        project.assertNoDiagnostics(KotlinToolingDiagnostics.CrossCompilationWithCinterops)
    }

    @Test
    fun `cross compilation disabled with macOS cinterop on Windows or Linux host`() {
        Assume.assumeTrue("Run on unsupported hosts (Windows/Linux)", HostManager.hostIsMingw || HostManager.hostIsLinux)

        val project = buildProject {
            propertiesExtension.set(KOTLIN_NATIVE_DISABLE_KLIBS_CROSSCOMPILATION, "true")
            applyMultiplatformPlugin()
            kotlin {
                macosX64()
                linuxX64()
                mingwX64()

                addDummyCinterop { it.konanTarget.family == Family.LINUX }
            }
        }.evaluate()

        project.assertNoDiagnostics(KotlinToolingDiagnostics.CrossCompilationWithCinterops)
    }
}

private fun KotlinMultiplatformExtension.addDummyCinterop(spec: (KotlinNativeTarget) -> Boolean) {
    targets
        .withType(KotlinNativeTarget::class.java)
        .matching(spec)
        .configureEach { target ->
            target.compilations
                .getByName(KotlinCompilation.MAIN_COMPILATION_NAME)
                .cinterops
                .create("dummy") {
                    it.defFile("dummy.def")
                }
        }
}

