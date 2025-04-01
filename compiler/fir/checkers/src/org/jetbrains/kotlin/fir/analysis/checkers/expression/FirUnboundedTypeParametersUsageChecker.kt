/*
 * Copyright 2010-2025 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.analysis.checkers.expression

import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactory1
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
import kotlin.reflect.full.memberProperties

class IEReporter(
    private val source: KtSourceElement?,
    private val context: CheckerContext,
    private val reporter: DiagnosticReporter,
    private val error: KtDiagnosticFactory1<String>,
) {
    operator fun invoke(v: IEData) {
        val dataStr = buildList {
            add(serializeLocation())
            addAll(serializeData(v))
        }.joinToString("; ")
        val str = "$borderTag $dataStr $borderTag"
        reporter.reportOn(source, error, str, context)
    }

    private val borderTag: String = "ROMANV"

    private fun serializeLocation(): String {
        val filename = context.containingFile?.sourceFile?.run { path ?: name } ?: "unknown"
        val mapping = context.containingFile?.sourceFileLinesMapping
        val loc = source?.startOffset?.let { mapping?.getLineAndColumnByOffset(it) }
        return "loc: ${filename}:${loc?.first}:${loc?.second}"
    }

    private fun serializeData(v: IEData): List<String> = buildList {
        v::class.memberProperties.forEach { property ->
            add("${property.name}: ${property.getter.call(v)}")
        }
    }
}


data class IEData(val type: String, val name: String? = null, val error: String? = null)


object FirUnboundedTypeParametersUsageChecker : FirBasicExpressionChecker(MppCheckerKind.Common) {
    @OptIn(SymbolInternals::class, UnexpandedTypeCheck::class)
    override fun check(expression: FirStatement, context: CheckerContext, reporter: DiagnosticReporter) {
        val report = IEReporter(expression.source, context, reporter, FirErrors.INVALID_USAGE_OF_UNBOUNDED_TYPE_PARAMETER)

        try {
            when (expression) {
                is FirSafeCallExpression -> {
                    expression.receiver.reportIfUnboundedTypeParameter(report) { IEData("SAFE_CALL") }
                }
                is FirEqualityOperatorCall -> {
                    expression.argumentList.arguments.forEach {
                        it.reportIfUnboundedTypeParameter(report) { IEData("EQUALITY") }
                    }
                }
                is FirCheckNotNullCall -> {
                    expression.argument.reportIfUnboundedTypeParameter(report) { IEData("BANG_BANG") }
                }
                is FirPropertyAccessExpression -> {
                    val prop = expression.calleeReference.resolved?.resolvedSymbol?.fir as? FirProperty ?: return
                    if (prop.receiverParameter?.typeRef?.isNullableAny == true) {
                        expression.extensionReceiver?.reportIfUnboundedTypeParameter(report) {
                            IEData("PROP_EXTENSION_RECEIVER", name = prop.symbol.name.toString())
                        }
                    }
                    if (prop.dispatchReceiverType?.isNullableAny == true) {
                        expression.dispatchReceiver?.reportIfUnboundedTypeParameter(report) {
                            IEData("PROP_DISPATCH_RECEIVER", name = prop.symbol.name.toString())
                        }
                    }
                }
                is FirFunctionCall -> {
                    val func = expression.calleeReference.resolved?.resolvedSymbol?.fir as? FirFunction ?: return
                    if (func.receiverParameter?.typeRef?.isNullableAny == true) {
                        expression.extensionReceiver?.reportIfUnboundedTypeParameter(report) {
                            IEData("FUNC_EXTENSION_RECEIVER", name = func.symbol.name.toString())
                        }
                    }
                    if (func.dispatchReceiverType?.isNullableAny == true) {
                        expression.dispatchReceiver?.reportIfUnboundedTypeParameter(report) {
                            IEData("FUNC_DISPATCH_RECEIVER", name = func.symbol.name.toString())
                        }
                    }
                    func.valueParameters.forEachIndexed { index, param ->
                        if (param.returnTypeRef.isNullableAny) {
                            expression.argumentList.arguments.getOrNull(index)?.reportIfUnboundedTypeParameter(report) {
                                IEData("FUNC_PARAM", name = func.symbol.name.toString())
                            }
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            report(IEData("ERROR", error = e.message ?: e.javaClass.simpleName))
        }
    }

    inline fun FirExpression.reportIfUnboundedTypeParameter(report: IEReporter, data: () -> IEData) {
        if (resolvedType.isUnboundedTypeParameter) {
            report(data())
        }
    }

    val ConeKotlinType?.isUnboundedTypeParameter
        get() = this is ConeTypeParameterType && lookupTag.typeParameterSymbol.resolvedBounds.run {
            isEmpty() || size == 1 && this[0] is FirImplicitNullableAnyTypeRef
        }
}