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

package androidx.compose.compiler.plugins.kotlin

import androidx.compose.compiler.plugins.kotlin.analysis.StabilityInferencer
import androidx.compose.compiler.plugins.kotlin.facade.SourceFile
import androidx.compose.compiler.plugins.kotlin.lower.ComposableLambdaAnnotator
import androidx.compose.compiler.plugins.kotlin.lower.hiddenfromobjc.AddHiddenFromObjCLowering
import androidx.compose.compiler.plugins.kotlin.lower.hiddenfromobjc.hiddenFromCClassId
import androidx.compose.compiler.plugins.kotlin.lower.hiddenfromobjc.hiddenFromObjCClassId
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.compiler.plugin.registerExtensionsForTest
import org.jetbrains.kotlin.config.AnalysisFlags
import org.jetbrains.kotlin.config.LanguageVersionSettingsImpl
import org.jetbrains.kotlin.config.languageVersionSettings
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.name
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.isAnnotation
import org.jetbrains.kotlin.ir.visitors.acceptVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCompilerApi::class, UnsafeDuringIrConstructionAPI::class)
class HiddenFromObjCAndCLoweringTests : AbstractIrTransformTest() {

    @Test
    fun testComposableDeclarationsGetHiddenFromObjCAndHiddenFromC() {
        val annotationsSource = SourceFile(
            "NativeAnnotations.kt",
            """
            package kotlin.native

            @Target(AnnotationTarget.PROPERTY, AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
            @Retention(AnnotationRetention.BINARY)
            annotation class HiddenFromObjC

            @Target(AnnotationTarget.PROPERTY, AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
            @Retention(AnnotationRetention.BINARY)
            annotation class HiddenFromC
            """.trimIndent()
        )

        val testSource = SourceFile(
            "Test.kt",
            """
            import androidx.compose.runtime.Composable

            @Composable
            fun ComposableFun() {}

            fun NonComposableFun() {}

            val composableGetterProp: Int
                @Composable get() = 42

            var composableVarProp: @Composable () -> Unit = {}

            data class ComposableDataClass(val content: @Composable () -> Unit)
            """.trimIndent()
        )

        var capturedContext: IrPluginContext? = null

        val irModule = compileToIr(
            listOf(annotationsSource, testSource),
            updateConfiguration = { configuration ->
                val current = configuration.languageVersionSettings
                configuration.languageVersionSettings = LanguageVersionSettingsImpl(
                    current.languageVersion,
                    current.apiVersion,
                    mapOf(
                        AnalysisFlags.allowUnstableDependencies to true,
                        AnalysisFlags.skipPrereleaseCheck to true,
                        AnalysisFlags.allowKotlinPackage to true,
                    )
                )
            },
            registerExtensions = { configuration ->
                registerExtensionsForTest(this, configuration) {
                    with(ComposePluginRegistrar.Companion) {
                        registerCommonExtensions()
                    }
                    IrGenerationExtension.registerExtension(object : IrGenerationExtension {
                        override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
                            capturedContext = pluginContext
                            moduleFragment.acceptVoid(ComposableLambdaAnnotator(pluginContext))
                            val stabilityInferencer = StabilityInferencer(true, emptySet())
                            val lowering = AddHiddenFromObjCLowering(
                                pluginContext,
                                EmptyModuleMetrics,
                                stabilityInferencer,
                                FeatureFlags()
                            )
                            moduleFragment.transformChildrenVoid(lowering)
                        }
                    })
                    IrGenerationExtension.registerExtension(ComposePluginRegistrar.createComposeIrExtension(configuration))
                }
            }
        )

        val pluginContext = capturedContext!!
        val testFile = irModule.files.single { it.name == "Test.kt" }

        fun IrDeclaration.metadataAnnotationCount(classId: org.jetbrains.kotlin.name.ClassId): Int =
            pluginContext.metadataDeclarationRegistrar
                .getMetadataVisibleAnnotationsForElement(this)
                .count { it.isAnnotation(classId) }

        val composableFun = testFile.declarations.filterIsInstance<IrSimpleFunction>().single { it.name.asString() == "ComposableFun" }
        assertTrue(composableFun.hasAnnotation(hiddenFromObjCClassId))
        assertTrue(composableFun.hasAnnotation(hiddenFromCClassId))
        assertEquals(1, composableFun.metadataAnnotationCount(hiddenFromObjCClassId))
        assertEquals(1, composableFun.metadataAnnotationCount(hiddenFromCClassId))

        val nonComposableFun = testFile.declarations.filterIsInstance<IrSimpleFunction>().single { it.name.asString() == "NonComposableFun" }
        assertFalse(nonComposableFun.hasAnnotation(hiddenFromObjCClassId))
        assertFalse(nonComposableFun.hasAnnotation(hiddenFromCClassId))

        val composableGetterProp = testFile.declarations.filterIsInstance<IrProperty>().single { it.name.asString() == "composableGetterProp" }
        assertTrue(composableGetterProp.hasAnnotation(hiddenFromObjCClassId))
        assertTrue(composableGetterProp.hasAnnotation(hiddenFromCClassId))
        assertTrue(composableGetterProp.getter!!.hasAnnotation(hiddenFromObjCClassId))
        assertTrue(composableGetterProp.getter!!.hasAnnotation(hiddenFromCClassId))
        assertEquals(1, composableGetterProp.getter!!.metadataAnnotationCount(hiddenFromCClassId))

        val composableVarProp = testFile.declarations.filterIsInstance<IrProperty>().single { it.name.asString() == "composableVarProp" }
        assertTrue(composableVarProp.hasAnnotation(hiddenFromObjCClassId))
        assertTrue(composableVarProp.hasAnnotation(hiddenFromCClassId))
        assertTrue(composableVarProp.getter!!.hasAnnotation(hiddenFromCClassId))
        assertTrue(composableVarProp.setter!!.hasAnnotation(hiddenFromCClassId))
        assertEquals(1, composableVarProp.getter!!.metadataAnnotationCount(hiddenFromCClassId))
        assertEquals(1, composableVarProp.setter!!.metadataAnnotationCount(hiddenFromCClassId))

        val composableDataClass = testFile.declarations.filterIsInstance<IrClass>().single { it.name.asString() == "ComposableDataClass" }
        assertTrue(composableDataClass.hasAnnotation(hiddenFromObjCClassId))
        assertTrue(composableDataClass.hasAnnotation(hiddenFromCClassId))
        assertEquals(1, composableDataClass.metadataAnnotationCount(hiddenFromCClassId))
    }
}
