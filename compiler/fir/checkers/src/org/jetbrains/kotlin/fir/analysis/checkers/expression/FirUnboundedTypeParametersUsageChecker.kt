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
            val filename = context.containingFile?.sourceFile?.run { path ?: name } ?: "unknown"

            val mapping = context.containingFile?.sourceFileLinesMapping
            val offset = expression.source?.startOffset
            val loc = offset?.let { mapping?.getLineAndColumnByOffset(it) }

            reporter.reportOn(
                expression.source,
                FirErrors.INVALID_USAGE_OF_UNBOUNDED_TYPE_PARAMETER,
                "ROMANV; LOC: ${filename}:${loc?.first}:${loc?.second}; $it ;ROMANV",
                context,
            )
        }

        try {
            when (expression) {
                is FirSafeCallExpression -> {
                    expression.receiver.reportIfUnboundedTypeParameter(report) { "TYPE: SAFE_CALL" }
                }
                is FirEqualityOperatorCall -> {
                    expression.argumentList.arguments.forEach {
                        it.reportIfUnboundedTypeParameter(report) { "TYPE: EQUALITY" }
                    }
                }
                is FirCheckNotNullCall -> {
                    expression.argument.reportIfUnboundedTypeParameter(report) { "TYPE: BANG_BANG" }
                }
                is FirPropertyAccessExpression -> {
                    val prop = expression.calleeReference.resolved?.resolvedSymbol?.fir as? FirProperty ?: return
                    val propName = "; NAME: ${prop.symbol.name.identifier}"
                    if (prop.receiverParameter?.typeRef?.isNullableAny == true) {
                        expression.extensionReceiver?.reportIfUnboundedTypeParameter(report) { "TYPE: PROP_EXTENSION_RECEIVER$propName" }
                    }
                    if (prop.dispatchReceiverType?.isNullableAny == true) {
                        expression.dispatchReceiver?.reportIfUnboundedTypeParameter(report) { "TYPE: PROP_DISPATCH_RECEIVER$propName" }
                    }
                }
                is FirFunctionCall -> {
                    val func = expression.calleeReference.resolved?.resolvedSymbol?.fir as? FirFunction ?: return
                    val funcName = "; NAME: ${func.symbol.name.identifier}"
                    if (func.receiverParameter?.typeRef?.isNullableAny == true) {
                        expression.extensionReceiver?.reportIfUnboundedTypeParameter(report) { "TYPE: FUNC_EXTENSION_RECEIVER$funcName" }
                    }
                    if (func.dispatchReceiverType?.isNullableAny == true) {
                        expression.dispatchReceiver?.reportIfUnboundedTypeParameter(report) { "TYPE: FUNC_DISPATCH_RECEIVER$funcName" }
                    }
                    func.valueParameters.forEachIndexed { index, param ->
                        if (param.returnTypeRef.isNullableAny) {
                            expression.argumentList.arguments.getOrNull(index)
                                ?.reportIfUnboundedTypeParameter(report) { "TYPE: FUNC_PARAM$funcName" }
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