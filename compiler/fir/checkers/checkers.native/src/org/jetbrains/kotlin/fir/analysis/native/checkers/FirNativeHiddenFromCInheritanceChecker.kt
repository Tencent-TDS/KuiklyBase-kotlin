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

package org.jetbrains.kotlin.fir.analysis.native.checkers

import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirRegularClassChecker
import org.jetbrains.kotlin.fir.analysis.checkers.getContainingClassSymbol
import org.jetbrains.kotlin.fir.analysis.diagnostics.native.FirNativeErrors
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.toAnnotationClassId
import org.jetbrains.kotlin.fir.declarations.utils.superConeTypes
import org.jetbrains.kotlin.fir.declarations.utils.visibility
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.types.isAny
import org.jetbrains.kotlin.fir.types.isNullableAny
import org.jetbrains.kotlin.fir.types.toSymbol
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName

/**
 * Check that the given class does not inherit from class or implements interface that is
 * marked as HiddenFromC.
 */
object FirNativeHiddenFromCInheritanceChecker : FirRegularClassChecker(MppCheckerKind.Common) {
    private val hiddenFromCClassId: ClassId = ClassId.topLevel(FqName("kotlin.native.HiddenFromC"))
    
    override fun check(declaration: FirRegularClass, context: CheckerContext, reporter: DiagnosticReporter) {
        // Enum entries inherit from their enum class.
        if (declaration.classKind == ClassKind.ENUM_ENTRY) {
            return
        }
        // Non-public types do not leak to Objective-C API surface, so it is OK for them
        // to inherit from hidden types.
        if (!declaration.visibility.isPublicAPI) return
        val session = context.session
        // No need to report anything on class that is hidden itself.
        if (checkIsHiddenFromC(declaration.symbol, session)) {
            return
        }

        val superTypes = declaration.superConeTypes
            .filterNot { it.isAny || it.isNullableAny }
            .mapNotNull { it.toSymbol(session) }

        superTypes.firstOrNull { st -> checkIsHiddenFromC(st, session) }?.let {
            reporter.reportOn(declaration.source, FirNativeErrors.SUBTYPE_OF_HIDDEN_FROM_C, context)
        }
    }

    private fun checkContainingClassIsHidden(classSymbol: FirClassLikeSymbol<*>, session: FirSession): Boolean {
        return classSymbol.getContainingClassSymbol(session)?.let {
            if (checkIsHiddenFromC(it, session)) {
                true
            } else {
                checkContainingClassIsHidden(it, session)
            }
        } ?: false
    }

    private fun checkIsHiddenFromC(classSymbol: FirClassLikeSymbol<*>, session: FirSession): Boolean {
        classSymbol.annotations.forEach { annotation ->
            if (annotation.toAnnotationClassId(session) == hiddenFromCClassId) return true
        }
        return checkContainingClassIsHidden(classSymbol, session)
    }
}