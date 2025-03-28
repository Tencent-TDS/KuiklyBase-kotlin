/*
 * Copyright 2010-2025 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.analysis.checkers.expression

import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.diagnostics.FirErrors
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.expressions.*
import org.jetbrains.kotlin.fir.references.resolved
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.types.*
import org.jetbrains.kotlin.fir.types.impl.FirImplicitNullableAnyTypeRef


object FirUnboundedTypeParametersUsageChecker : FirBasicExpressionChecker(MppCheckerKind.Common) {
    @OptIn(SymbolInternals::class, UnexpandedTypeCheck::class)
    override fun check(expression: FirStatement, context: CheckerContext, reporter: DiagnosticReporter) {
        val report: (String) -> Unit = {
            reporter.reportOn(
                expression.source,
                FirErrors.INVALID_USAGE_OF_UNBOUNDED_TYPE_PARAMETER,
                "ROMANV; $it;ROMANV",
                context,
            )
        }

        try {

            when (expression) {
                is FirSafeCallExpression -> {
                    expression.receiver.reportIfUnboundedTypeParameter(report) { "SAFE_CALL" }
                }
                is FirEqualityOperatorCall -> {
                    expression.argumentList.arguments.forEach {
                        it.reportIfUnboundedTypeParameter(report) { "EQUALITY" }
                    }
                }
                is FirCheckNotNullCall -> {
                    expression.argument.reportIfUnboundedTypeParameter(report) { "BANG_BANG" }
                }
                is FirPropertyAccessExpression -> {
                    val prop = expression.calleeReference.resolved?.resolvedSymbol?.fir as? FirProperty ?: return
                    if (prop.receiverParameter?.typeRef?.isNullableAny == true) {
                        expression.extensionReceiver?.reportIfUnboundedTypeParameter(report) { "PROP_EXTENSION_RECEIVER" }
                    }
                    if (prop.dispatchReceiverType?.isNullableAny == true) {
                        expression.dispatchReceiver?.reportIfUnboundedTypeParameter(report) { "PROP_DISPATCH_RECEIVER" }
                    }
                }
                is FirFunctionCall -> {
                    val func = expression.calleeReference.resolved?.resolvedSymbol?.fir as? FirFunction ?: return
                    if (func.receiverParameter?.typeRef?.isNullableAny == true) {
                        expression.extensionReceiver?.reportIfUnboundedTypeParameter(report) { "FUNC_EXTENSION_RECEIVER" }
                    }
                    if (func.dispatchReceiverType?.isNullableAny == true) {
                        expression.dispatchReceiver?.reportIfUnboundedTypeParameter(report) { "FUNC_DISPATCH_RECEIVER" }
                    }
                    func.valueParameters.forEachIndexed { index, param ->
                        if (param.returnTypeRef.isNullableAny) {
                            expression.argumentList.arguments.getOrNull(index)?.reportIfUnboundedTypeParameter(report) { "FUNC_PARAM" }
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            report("ERROR: ${e.message}")
        }
    }

    inline fun FirExpression.reportIfUnboundedTypeParameter(report: (String) -> Unit, message: () -> String) {
        if (resolvedType.isUnboundedTypeParameter) {
            report(message())
        }
    }

    val ConeKotlinType?.isUnboundedTypeParameter
        get() = this is ConeTypeParameterType && lookupTag.typeParameterSymbol.resolvedBounds.run {
            isEmpty() || size == 1 && this[0] is FirImplicitNullableAnyTypeRef
        }
}