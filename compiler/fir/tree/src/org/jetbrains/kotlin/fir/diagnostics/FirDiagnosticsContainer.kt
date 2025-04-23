/*
 * Copyright 2010-2025 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.diagnostics

import org.jetbrains.kotlin.diagnostics.rendering.BaseDiagnosticRendererFactory
import org.jetbrains.kotlin.diagnostics.rendering.RootDiagnosticRendererFactory

abstract class FirDiagnosticsContainer {
    /**
     * !!!! Overrides of these val MUST be property without backing field !!!!
     */
    abstract val rendererFactory: BaseDiagnosticRendererFactory

    init {
        RootDiagnosticRendererFactory.registerFactory(rendererFactory)
    }
}
