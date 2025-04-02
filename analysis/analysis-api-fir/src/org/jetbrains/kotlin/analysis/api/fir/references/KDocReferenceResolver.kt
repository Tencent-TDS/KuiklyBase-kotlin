/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.api.fir.references

import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.fir.references.KDocReferenceResolver.canBeReferencedAsExtensionOn
import org.jetbrains.kotlin.analysis.api.fir.references.KDocReferenceResolver.getTypeQualifiedExtensions
import org.jetbrains.kotlin.analysis.api.scopes.KaScope
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaDeclarationContainerSymbol
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.KaTypeParameterType
import org.jetbrains.kotlin.analysis.utils.printer.parentOfType
import org.jetbrains.kotlin.load.java.possibleGetMethodNames
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.isOneSegmentFQN
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.utils.yieldIfNotNull

internal object KDocReferenceResolver {
    /**
     * [symbol] is the symbol referenced by this resolve result.
     *
     * [receiverClassReference] is an optional receiver type in
     * the case of extension function references (see [getTypeQualifiedExtensions]).
     */
    private data class ResolveResult(val symbol: KaSymbol, val receiverClassReference: KaClassLikeSymbol?)

    private fun KaSymbol.toResolveResult(receiverClassReference: KaClassLikeSymbol? = null): ResolveResult =
        ResolveResult(symbol = this, receiverClassReference)

    private fun Sequence<KaSymbol>.toResolveResults(): Sequence<ResolveResult> = this.map { it.toResolveResult() }
    private fun Iterable<KaSymbol>.toResolveResults(): Iterable<ResolveResult> = this.map { it.toResolveResult() }

    /**
     * Resolves the [selectedFqName] of KDoc
     *
     * To properly resolve qualifier parts in the middle,
     * we need to resolve the whole qualifier to understand which parts of the qualifier are package or class qualifiers.
     * And then we will be able to resolve the qualifier selected by the user to the proper class, package or callable.
     *
     * It's possible that the whole qualifier is invalid, in this case we still want to resolve our [selectedFqName].
     * To do this, we are trying to resolve the whole qualifier until we succeed.
     *
     * @param selectedFqName the selected fully qualified name of the KDoc
     * @param fullFqName the whole fully qualified name of the KDoc
     * @param contextElement the context element in which the KDoc is defined
     *
     * @return the sequence of [KaSymbol](s) resolved from the fully qualified name
     *         based on the selected FqName and context element
     */
    internal fun resolveKdocFqName(
        analysisSession: KaSession,
        selectedFqName: FqName,
        fullFqName: FqName,
        contextElement: KtElement,
    ): Sequence<KaSymbol> {
        with(analysisSession) {
            //ensure file context is provided for "non-physical" code as well
            val contextDeclarationOrSelf = PsiTreeUtil.getContextOfType(contextElement, KtDeclaration::class.java, false)
                ?: contextElement
            val fullSymbolsResolved =
                resolveKdocFqName(fullFqName, contextDeclarationOrSelf)
            if (selectedFqName == fullFqName) return fullSymbolsResolved.map { it.symbol }
            if (fullSymbolsResolved.none()) {
                val parent = fullFqName.parent()
                return resolveKdocFqName(analysisSession, selectedFqName, parent, contextDeclarationOrSelf)
            }
            val goBackSteps = fullFqName.pathSegments().size - selectedFqName.pathSegments().size
            check(goBackSteps > 0) {
                "Selected FqName ($selectedFqName) should be smaller than the whole FqName ($fullFqName)"
            }
            return fullSymbolsResolved.mapNotNull { findParentSymbol(it, goBackSteps, selectedFqName) }
        }
    }

    /**
     * Finds the parent symbol of the given [ResolveResult] by traversing back up the symbol hierarchy a [goBackSteps] steps,
     * or until the containing class or object symbol is found.
     *
     * Knows about the [ResolveResult.receiverClassReference] field and uses it in case it's not empty.
     */
    private fun KaSession.findParentSymbol(resolveResult: ResolveResult, goBackSteps: Int, selectedFqName: FqName): KaSymbol? {
        return if (resolveResult.receiverClassReference != null) {
            findParentSymbol(resolveResult.receiverClassReference, goBackSteps - 1, selectedFqName)
        } else {
            findParentSymbol(resolveResult.symbol, goBackSteps, selectedFqName)
        }
    }

    /**
     * Finds the parent symbol of the given [KaSymbol] by traversing back up the symbol hierarchy a certain number of steps,
     * or until the containing class or object symbol is found.
     *
     * @param symbol The [KaSymbol] whose parent symbol needs to be found.
     * @param goBackSteps The number of steps to go back up the symbol hierarchy.
     * @param selectedFqName The fully qualified name of the selected package.
     * @return The [goBackSteps]-th parent [KaSymbol]
     */
    private fun KaSession.findParentSymbol(symbol: KaSymbol, goBackSteps: Int, selectedFqName: FqName): KaSymbol? {
        if (symbol !is KaDeclarationSymbol && symbol !is KaPackageSymbol) return null

        if (symbol is KaDeclarationSymbol) {
            goToNthParent(symbol, goBackSteps)?.let { return it }
        }

        return findPackage(selectedFqName)
    }

    /**
     * N.B. Works only for [KaClassSymbol] parents chain.
     */
    private fun KaSession.goToNthParent(symbol: KaDeclarationSymbol, steps: Int): KaDeclarationSymbol? {
        var currentSymbol = symbol

        repeat(steps) {
            currentSymbol = currentSymbol.containingDeclaration as? KaClassSymbol ?: return null
        }

        return currentSymbol
    }

    private fun KaSession.resolveKdocFqName(
        fqName: FqName,
        contextElement: KtElement
    ): Sequence<ResolveResult> {
        val containingFile = contextElement.containingKtFile
        val scopeContext = containingFile.scopeContext(contextElement)
        val scopeContextScopes = scopeContext.scopes.map { it.scope }
        val shortName = fqName.shortName()

        val allScopesContainingName = sequence<KaScope> {
            yieldAll(scopeContextScopes.map { getSymbolsFromMemberScope(fqName, it) })
            yieldAll(getPotentialPackageScopes(fqName))
        }

        val allMatchingSymbols = sequence {
            yieldAll(getExtensionReceiverSymbolByThisQualifier(fqName, contextElement).toResolveResults())
            if (fqName.isOneSegmentFQN()) yieldAll(getSymbolsFromDeclaration(shortName, contextElement).toResolveResults())
            yieldAll(allScopesContainingName.flatMap { scope -> scope.classifiers(shortName) }.toResolveResults())

            yieldIfNotNull(findPackage(fqName)?.toResolveResult())

            yieldAll(getTypeQualifiedExtensions(fqName, scopeContextScopes))

            val callables = allScopesContainingName.map { scope -> scope.callables(shortName) }
            yieldAll(callables.flatMap { callableSymbols -> callableSymbols.filterIsInstance<KaFunctionSymbol>() }.toResolveResults())
            yieldAll(getSymbolsFromSyntheticProperty(fqName, allScopesContainingName).toResolveResults())
            yieldAll(callables.flatMap { scope -> scope.filterIsInstance<KaVariableSymbol>() }.toResolveResults())
            yieldAll(AdditionalKDocResolutionProvider.resolveKdocFqName(useSiteSession, fqName, contextElement).toResolveResults())
        }.distinct()

        return allMatchingSymbols
    }

    private fun KaSession.getPotentialPackageScopes(fqName: FqName): Sequence<KaScope> =
        sequence {
            val fqNameSegments = fqName.pathSegments()
            for (numberOfSegments in fqNameSegments.size - 1 downTo 1) {
                val packageName = FqName.fromSegments(fqNameSegments.take(numberOfSegments).map { it.toString() })
                val declarationNameToFind = FqName.fromSegments(fqNameSegments.drop(numberOfSegments).map { it.toString() })
                yieldIfNotNull(
                    findPackage(packageName)?.packageScope?.let { packageScope ->
                        getSymbolsFromMemberScope(declarationNameToFind, packageScope)
                    }
                )
            }
        }


    private fun KaSession.getSymbolsFromSyntheticProperty(fqName: FqName, scopes: Sequence<KaScope>): Sequence<KaSymbol> {
        val getterNames = possibleGetMethodNames(fqName.shortNameOrSpecial())
        return scopes.map { scope -> scope.callables { it in getterNames } }.flatMap { callables ->
            callables.filter { symbol ->
                val symbolLocation = symbol.location
                val symbolOrigin = symbol.origin
                symbolLocation == KaSymbolLocation.CLASS && (symbolOrigin == KaSymbolOrigin.JAVA_LIBRARY || symbolOrigin == KaSymbolOrigin.JAVA_SOURCE)
            }
        }
    }

    private fun KaSession.getExtensionReceiverSymbolByThisQualifier(
        fqName: FqName,
        contextElement: KtElement,
    ): Collection<KaSymbol> {
        val owner = contextElement.parentOfType<KtDeclaration>(withSelf = true) ?: return emptyList()
        if (fqName.pathSegments().singleOrNull()?.asString() == "this") {
            if (owner is KtCallableDeclaration && owner.receiverTypeReference != null) {
                val symbol = owner.symbol as? KaCallableSymbol ?: return emptyList()
                return listOfNotNull(symbol.receiverParameter)
            }
        }
        return emptyList()
    }

    private fun KaSession.getSymbolsFromDeclaration(name: Name, owner: KtElement): List<KaSymbol> = buildList {
        if (owner is KtNamedDeclaration) {
            if (owner.nameAsName == name) {
                add(owner.symbol)
            }
        }
        if (owner is KtTypeParameterListOwner) {
            for (typeParameter in owner.typeParameters) {
                if (typeParameter.nameAsName == name) {
                    add(typeParameter.symbol)
                }
            }
        }
        if (owner is KtCallableDeclaration) {
            for (typeParameter in owner.valueParameters) {
                if (typeParameter.nameAsName == name) {
                    add(typeParameter.symbol)
                }
            }
        }

        if (owner is KtClassOrObject) {
            owner.primaryConstructor?.let { addAll(getSymbolsFromDeclaration(name, it)) }
        }
    }


    private fun KaSession.getCompositeCombinedMemberAndCompanionObjectScope(symbol: KaDeclarationContainerSymbol): KaScope =
        listOfNotNull(
            symbol.combinedMemberScope,
            getCompanionObjectMemberScope(symbol),
        ).asCompositeScope()

    private fun KaSession.getCompanionObjectMemberScope(symbol: KaDeclarationContainerSymbol): KaScope? {
        val namedClassSymbol = symbol as? KaNamedClassSymbol ?: return null
        val companionSymbol = namedClassSymbol.companionObject ?: return null
        return companionSymbol.memberScope
    }

    private fun KaSession.getSymbolsFromMemberScope(fqName: FqName, scope: KaScope): KaScope {
        val finalScope = fqName.pathSegments()
            .dropLast(1)
            .fold(scope) { currentScope, fqNamePart ->
                currentScope
                    .classifiers(fqNamePart)
                    .filterIsInstance<KaDeclarationContainerSymbol>()
                    .map { getCompositeCombinedMemberAndCompanionObjectScope(it) }
                    .toList()
                    .asCompositeScope()
            }

        return finalScope
    }

    /**
     * Tries to resolve [fqName] into available extension callables (functions or properties)
     * prefixed with a suitable extension receiver type (like in `Foo.bar`, or `foo.Foo.bar`).
     *
     * Relies on the fact that in such references only the last qualifier refers to the
     * actual extension callable, and the part before that refers to the receiver type (either fully
     * or partially qualified).
     *
     * For example, `foo.Foo.bar` may only refer to the extension callable `bar` with
     * a `foo.Foo` receiver type, and this function will only look for such combinations.
     *
     * N.B. This function only searches for extension callables qualified by receiver types!
     * It does not try to resolve fully qualified or member functions, because they are dealt
     * with by the other parts of [KDocReferenceResolver].
     */
    private fun KaSession.getTypeQualifiedExtensions(fqName: FqName, scopes: Collection<KaScope>): Sequence<ResolveResult> {
        if (fqName.isRoot) return emptySequence()
        val extensionName = fqName.shortName()

        val receiverTypeName = fqName.parent()
        if (receiverTypeName.isRoot) return emptySequence()

        val scopesContainingPossibleReceivers = sequence<KaScope> {
            yieldAll(scopes.map { getSymbolsFromMemberScope(receiverTypeName, it) })
            yieldAll(getPotentialPackageScopes(receiverTypeName))
        }

        val possibleReceivers =
            scopesContainingPossibleReceivers.flatMap { it.classifiers(receiverTypeName.shortName()) }.filterIsInstance<KaClassLikeSymbol>()
        val possibleExtensions = scopes.asSequence().flatMap { it.callables(extensionName) }.filter { it.isExtension }

        if (possibleExtensions.none() || possibleReceivers.none()) return emptySequence()

        return possibleReceivers.flatMap { receiverClassSymbol ->
            val receiverType = buildClassType(receiverClassSymbol)
            possibleExtensions.filter { canBeReferencedAsExtensionOn(it, receiverType) }
                .map { it.toResolveResult(receiverClassReference = receiverClassSymbol) }
        }
    }

    /**
     * Returns true if we consider that [this] extension function prefixed with [actualReceiverType] in
     * a KDoc reference should be considered as legal and resolved, and false otherwise.
     *
     * This is **not** an actual type check, it is just an opinionated approximation.
     * The main guideline was K1 KDoc resolve.
     *
     * This check might change in the future, as the Dokka team advances with KDoc rules.
     */
    private fun KaSession.canBeReferencedAsExtensionOn(symbol: KaCallableSymbol, actualReceiverType: KaType): Boolean {
        val extensionReceiverType = symbol.receiverParameter?.returnType ?: return false
        return isPossiblySuperTypeOf(extensionReceiverType, actualReceiverType)
    }

    /**
     * Same constraints as in [canBeReferencedAsExtensionOn].
     *
     * For a similar function in the `intellij` repository, see `isPossiblySubTypeOf`.
     */
    private fun KaSession.isPossiblySuperTypeOf(type: KaType, actualReceiverType: KaType): Boolean {
        // Type parameters cannot act as receiver types in KDoc
        if (actualReceiverType is KaTypeParameterType) return false

        if (type is KaTypeParameterType) {
            return type.symbol.upperBounds.all { isPossiblySuperTypeOf(it, actualReceiverType) }
        }

        val receiverExpanded = actualReceiverType.expandedSymbol
        val expectedExpanded = type.expandedSymbol

        // if the underlying classes are equal, we consider the check successful
        // despite the possibility of different type bounds
        if (
            receiverExpanded != null &&
            receiverExpanded == expectedExpanded
        ) {
            return true
        }

        return actualReceiverType.isSubtypeOf(type)
    }
}