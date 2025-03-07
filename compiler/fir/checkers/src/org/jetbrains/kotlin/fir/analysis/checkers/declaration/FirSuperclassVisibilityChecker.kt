/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.analysis.checkers.declaration

import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.descriptors.EffectiveVisibility
import org.jetbrains.kotlin.fir.types.FirTypeRef
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactory1
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.toClassLikeSymbol
import org.jetbrains.kotlin.fir.analysis.diagnostics.FirErrors
import org.jetbrains.kotlin.fir.declarations.DirectDeclarationsAccess
import org.jetbrains.kotlin.fir.declarations.FirAnonymousObject
import org.jetbrains.kotlin.fir.declarations.FirClass
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.FirTypeAlias
import org.jetbrains.kotlin.fir.declarations.utils.classId
import org.jetbrains.kotlin.fir.declarations.utils.effectiveVisibility
import org.jetbrains.kotlin.fir.declarations.utils.modality
import org.jetbrains.kotlin.fir.declarations.utils.visibility
import org.jetbrains.kotlin.fir.resolve.toClassLikeSymbol
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.types.ConeTypeProjection
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.coneTypeOrNull
import org.jetbrains.kotlin.fir.types.renderForDebugging
import org.jetbrains.kotlin.fir.types.type
import org.jetbrains.kotlin.fir.types.typeContext
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


data class IEData(
    val subclass: String? = null,
    val subclassMod: String? = null,
    val subclassKind: String? = null,
    val subclassVisibility: String? = null,
    val superclass: String? = null,
    val superclassMod: String? = null,
    val superclassKind: String? = null,
    val superclassVisibility: String? = null,
    val superclassHasMethods: Boolean? = null,
    val directInheritance: Boolean = false,
    val inheritancePattern: String? = null,
)


object FirSuperclassVisibilityChecker : FirClassChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirClass) {
        val report = IEReporter(declaration.source, context, reporter, FirErrors.MY_ERROR)
        val selfEffVis = declaration.effectiveVisibility
        declaration.superTypeRefs.forEach {
            checkSupertype(it, selfEffVis) {
                report(
                    it.copy(
                        subclass = declaration.classId.asFqNameString(),
                        subclassMod = declaration.modality.toString(),
                        subclassKind = declaration.classKind.toString(),
                        subclassVisibility = declaration.visibility.toString(),
                    )
                )
            }
        }
    }

    context(context: CheckerContext)
    fun checkTypeParameter(typeProj: ConeTypeProjection, selfEffVis: EffectiveVisibility, report: (IEData) -> Unit) {
        typeProj.type?.typeArguments?.forEach { checkTypeParameter(it, selfEffVis, report) }
        val classLikeSymbol = typeProj.type?.toClassLikeSymbol(context.session) ?: return
        val effVis = classLikeSymbol.effectiveVisibility
        when (effVis.relation(selfEffVis, context.session.typeContext)) {
            EffectiveVisibility.Permissiveness.LESS -> {
                report(dataForSymbol(classLikeSymbol))
            }
            else -> return
        }
    }

    context(context: CheckerContext)
    fun checkSupertype(typeRef: FirTypeRef, selfEffVis: EffectiveVisibility, report: (IEData) -> Unit) {
        typeRef.coneTypeOrNull?.typeArguments?.forEach {
            checkTypeParameter(it, selfEffVis) { d ->
                report(d.copy(inheritancePattern = typeRef.coneType.renderForDebugging(), directInheritance = true))
            }
        }
        val classLikeSymbol = typeRef.toClassLikeSymbol(context.session) ?: return
        val effVis = classLikeSymbol.effectiveVisibility
        when (effVis.relation(selfEffVis, context.session.typeContext)) {
            EffectiveVisibility.Permissiveness.LESS -> {
                report(
                    dataForSymbol(classLikeSymbol).copy(
                        inheritancePattern = typeRef.coneType.renderForDebugging(),
                        directInheritance = true
                    )
                )
            }
            else -> return
        }
    }

    @OptIn(SymbolInternals::class, DirectDeclarationsAccess::class)
    fun dataForSymbol(cls: FirClassLikeSymbol<*>): IEData {
        val fir = cls.fir

        return IEData(
            superclass = fir.classId.asFqNameString(),
            superclassHasMethods = when (fir) {
                is FirAnonymousObject -> null
                is FirRegularClass -> fir.declarations.isNotEmpty()
                is FirTypeAlias -> null
            },
            superclassMod = fir.modality.toString(),
            superclassKind = (fir as? FirClass)?.classKind.toString(),
            superclassVisibility = fir.visibility.toString(),
        )
    }
}
