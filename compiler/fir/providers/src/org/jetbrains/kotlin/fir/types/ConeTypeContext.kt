/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.types

import org.jetbrains.kotlin.builtins.PrimitiveType
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.declarations.utils.expandedConeType
import org.jetbrains.kotlin.fir.declarations.utils.isInner
import org.jetbrains.kotlin.fir.declarations.utils.modality
import org.jetbrains.kotlin.fir.declarations.utils.superConeTypes
import org.jetbrains.kotlin.fir.expressions.*
import org.jetbrains.kotlin.fir.resolve.fullyExpandedType
import org.jetbrains.kotlin.fir.resolve.substitution.ConeSubstitutor
import org.jetbrains.kotlin.fir.resolve.substitution.substitutorByMap
import org.jetbrains.kotlin.fir.resolve.toSymbol
import org.jetbrains.kotlin.fir.symbols.ConeClassLikeLookupTag
import org.jetbrains.kotlin.fir.symbols.ConeClassifierLookupTag
import org.jetbrains.kotlin.fir.symbols.ConeTypeParameterLookupTag
import org.jetbrains.kotlin.fir.symbols.impl.*
import org.jetbrains.kotlin.fir.symbols.lazyResolveToPhase
import org.jetbrains.kotlin.fir.utils.exceptions.withConeTypeEntry
import org.jetbrains.kotlin.fir.utils.exceptions.withFirLookupTagEntry
import org.jetbrains.kotlin.name.*
import org.jetbrains.kotlin.types.TypeCheckerState
import org.jetbrains.kotlin.types.TypeCheckerState.SupertypesPolicy.DoCustomTransform
import org.jetbrains.kotlin.types.TypeCheckerState.SupertypesPolicy.LowerIfFlexible
import org.jetbrains.kotlin.types.TypeSystemCommonBackendContext
import org.jetbrains.kotlin.types.model.*
import org.jetbrains.kotlin.utils.exceptions.errorWithAttachment

class ErrorTypeConstructor(val reason: String) : TypeConstructorMarker {
    override fun toString(): String = reason
}

interface ConeTypeContext : TypeSystemContext, TypeSystemOptimizationContext, TypeCheckerProviderContext, TypeSystemCommonBackendContext {
    val session: FirSession

    override fun TypeConstructorMarker.isIntegerLiteralTypeConstructor(): Boolean {
        return this is ConeIntegerLiteralType
    }

    override fun TypeConstructorMarker.isIntegerLiteralConstantTypeConstructor(): Boolean {
        return this is ConeIntegerLiteralConstantType
    }

    override fun TypeConstructorMarker.isIntegerConstantOperatorTypeConstructor(): Boolean {
        return this is ConeIntegerConstantOperatorType
    }

    override fun TypeConstructorMarker.isLocalType(): Boolean {
        if (this !is ConeClassLikeLookupTag) return false
        return isLocalClass()
    }

    override fun TypeConstructorMarker.isAnonymous(): Boolean {
        if (this !is ConeClassLikeLookupTag) return false
        return isAnonymousClass()
    }

    override val TypeVariableTypeConstructorMarker.typeParameter: TypeParameterMarker?
        get() {
            require(this is ConeTypeVariableTypeConstructor)
            return this.originalTypeParameter
        }

    override fun SimpleTypeMarker.possibleIntegerTypes(): Collection<KotlinTypeMarker> {
        return (this as? ConeIntegerLiteralType)?.possibleTypes ?: emptyList()
    }

    override fun SimpleTypeMarker.fastCorrespondingSupertypes(constructor: TypeConstructorMarker): List<SimpleTypeMarker>? {
        require(this is ConeKotlinType)
        return session.correspondingSupertypesCache.getCorrespondingSupertypes(this, constructor)
    }

    override fun SimpleTypeMarker.isIntegerLiteralType(): Boolean {
        return this is ConeIntegerLiteralType
    }

    override fun KotlinTypeMarker.asSimpleType(): SimpleTypeMarker? {
        assert(this is ConeKotlinType)
        return when (this) {
            is ConeClassLikeType -> fullyExpandedType(session)
            is ConeSimpleKotlinType -> this
            is ConeFlexibleType -> null
            else -> errorWithAttachment("Unknown simpleType: ${this::class}") {
                withConeTypeEntry("type", this@asSimpleType as? ConeKotlinType)
            }
        }
    }

    override fun KotlinTypeMarker.asFlexibleType(): FlexibleTypeMarker? {
        assert(this is ConeKotlinType)
        return this as? ConeFlexibleType
    }

    override fun KotlinTypeMarker.isError(): Boolean {
        assert(this is ConeKotlinType)
        return this is ConeErrorType || this is ConeErrorType || this.typeConstructor().isError() ||
                (this is ConeClassLikeType && this.lookupTag is ConeClassLikeErrorLookupTag)
    }

    override fun KotlinTypeMarker.isUninferredParameter(): Boolean {
        assert(this is ConeKotlinType)
        return this is ConeErrorType && this.isUninferredParameter
    }

    override fun FlexibleTypeMarker.asDynamicType(): ConeDynamicType? {
        assert(this is ConeKotlinType)
        return this as? ConeDynamicType
    }

    override fun KotlinTypeMarker.isRawType(): Boolean {
        require(this is ConeKotlinType)
        return this.isRaw()
    }

    override fun FlexibleTypeMarker.upperBound(): SimpleTypeMarker {
        require(this is ConeFlexibleType)
        return this.upperBound
    }

    override fun FlexibleTypeMarker.lowerBound(): SimpleTypeMarker {
        require(this is ConeFlexibleType)
        return this.lowerBound
    }

    override fun SimpleTypeMarker.asCapturedType(): CapturedTypeMarker? {
        return this as? ConeCapturedType
    }

    override fun SimpleTypeMarker.asDefinitelyNotNullType(): DefinitelyNotNullTypeMarker? {
        require(this is ConeKotlinType)
        return this as? ConeDefinitelyNotNullType
    }

    override fun SimpleTypeMarker.isMarkedNullable(): Boolean {
        require(this is ConeKotlinType)
        return fullyExpandedType(session).nullability.isNullable
    }

    override fun SimpleTypeMarker.withNullability(nullable: Boolean): SimpleTypeMarker {
        require(this is ConeKotlinType)
        return fullyExpandedType(session).withNullability(ConeNullability.create(nullable), session.typeContext) as SimpleTypeMarker
    }

    override fun SimpleTypeMarker.typeConstructor(): TypeConstructorMarker {
        require(this is ConeSimpleKotlinType)
        return this.getConstructor()
    }

    override fun CapturedTypeMarker.typeConstructor(): CapturedTypeConstructorMarker {
        require(this is ConeCapturedType)
        return this.constructor
    }

    override fun CapturedTypeMarker.captureStatus(): CaptureStatus {
        require(this is ConeCapturedType)
        return this.captureStatus
    }

    override fun CapturedTypeMarker.isOldCapturedType(): Boolean = false

    override fun CapturedTypeConstructorMarker.projection(): TypeArgumentMarker {
        require(this is ConeCapturedTypeConstructor)
        return this.projection
    }

    override fun KotlinTypeMarker.argumentsCount(): Int {
        require(this is ConeKotlinType)
        return this.typeArguments.size
    }

    override fun KotlinTypeMarker.getArgument(index: Int): TypeArgumentMarker {
        require(this is ConeKotlinType)
        return this.typeArguments.getOrNull(index) ?: ConeStarProjection
    }

    override fun KotlinTypeMarker.getArguments(): List<TypeArgumentMarker> {
        require(this is ConeKotlinType)
        return this.typeArguments.toList()
    }

    override fun KotlinTypeMarker.asTypeArgument(): TypeArgumentMarker {
        require(this is ConeKotlinType)
        return this
    }

    override fun CapturedTypeMarker.lowerType(): KotlinTypeMarker? {
        require(this is ConeCapturedType)
        if (!this.isMarkedNullable) return this.lowerType
        return this.lowerType?.makeNullable()
    }

    override fun TypeArgumentMarker.isStarProjection(): Boolean {
        require(this is ConeTypeProjection)
        return this is ConeStarProjection
    }

    override fun TypeArgumentMarker.getVariance(): TypeVariance {
        require(this is ConeTypeProjection)

        return when (this.kind) {
            ProjectionKind.STAR -> error("Nekorrektno (c) Stas")
            ProjectionKind.IN -> TypeVariance.IN
            ProjectionKind.OUT -> TypeVariance.OUT
            ProjectionKind.INVARIANT -> TypeVariance.INV
        }
    }

    override fun TypeArgumentMarker.getType(): KotlinTypeMarker {
        require(this is ConeTypeProjection)
        require(this is ConeKotlinTypeProjection) { "No type for StarProjection" }
        return this.type
    }

    override fun TypeArgumentMarker.replaceType(newType: KotlinTypeMarker): TypeArgumentMarker {
        require(this is ConeKotlinTypeProjection)
        require(newType is ConeKotlinType)
        return when (this) {
            is ConeKotlinType -> newType
            is ConeKotlinTypeProjectionOut -> ConeKotlinTypeProjectionOut(newType)
            is ConeKotlinTypeProjectionIn -> ConeKotlinTypeProjectionIn(newType)
            is ConeKotlinTypeConflictingProjection -> ConeKotlinTypeConflictingProjection(newType)
        }
    }

    override fun TypeConstructorMarker.parametersCount(): Int {
        return when (this) {
            is ConeTypeParameterLookupTag,
            is ConeCapturedTypeConstructor,
            is ErrorTypeConstructor,
            is ConeTypeVariableTypeConstructor,
            is ConeIntersectionType -> 0
            is ConeClassLikeLookupTag -> {
                when (val symbol = toSymbol(session)) {
                    is FirAnonymousObjectSymbol -> symbol.fir.typeParameters.size
                    is FirRegularClassSymbol -> symbol.fir.typeParameters.size
                    is FirTypeAliasSymbol -> symbol.fir.typeParameters.size
                    else -> 0
                }
            }
            is ConeIntegerLiteralType -> 0
            is ConeStubTypeConstructor -> 0
            else -> unknownConstructorError()
        }
    }

    override fun TypeConstructorMarker.getParameter(index: Int): TypeParameterMarker {
        return when (val symbol = toClassLikeSymbol()) {
            is FirAnonymousObjectSymbol -> symbol.fir.typeParameters[index].symbol.toLookupTag()
            is FirRegularClassSymbol -> symbol.fir.typeParameters[index].symbol.toLookupTag()
            is FirTypeAliasSymbol -> symbol.fir.typeParameters[index].symbol.toLookupTag()
            else -> errorWithAttachment("Unexpected FirClassLikeSymbol $symbol for ${this::class}") {
                withFirLookupTagEntry("lookupTag", this@getParameter as? ConeClassLikeLookupTag)
            }
        }
    }

    override fun TypeConstructorMarker.getParameters(): List<ConeTypeParameterLookupTag> {
        return when (val symbol = toClassLikeSymbol()) {
            is FirAnonymousObjectSymbol -> symbol.fir.typeParameters.map { it.symbol.toLookupTag() }
            is FirRegularClassSymbol -> symbol.fir.typeParameters.map { it.symbol.toLookupTag() }
            is FirTypeAliasSymbol -> symbol.fir.typeParameters.map { it.symbol.toLookupTag() }
            else -> emptyList()
        }
    }

    fun TypeConstructorMarker.toClassLikeSymbol(): FirClassLikeSymbol<*>? = (this as? ConeClassLikeLookupTag)?.toSymbol(session)

    override fun TypeConstructorMarker.supertypes(): Collection<ConeKotlinType> {
        if (this is ErrorTypeConstructor) return emptyList()
        return when (this) {
            is ConeStubTypeConstructor -> listOf(session.builtinTypes.nullableAnyType.type)
            is ConeTypeVariableTypeConstructor -> emptyList()
            is ConeTypeParameterLookupTag -> bounds().map { it.coneType }
            is ConeClassLikeLookupTag -> {
                when (val symbol = toClassLikeSymbol().also { it?.lazyResolveToPhase(FirResolvePhase.TYPES) }) {
                    is FirClassSymbol<*> -> symbol.fir.superConeTypes
                    is FirTypeAliasSymbol -> listOfNotNull(symbol.fir.expandedConeType)
                    else -> listOf(session.builtinTypes.anyType.type)
                }
            }
            is ConeCapturedTypeConstructor -> supertypes.orEmpty()
            is ConeIntersectionType -> intersectedTypes
            is ConeIntegerLiteralType -> supertypes
            else -> unknownConstructorError()
        }
    }

    override fun TypeConstructorMarker.isIntersection(): Boolean {
        return this is ConeIntersectionType
    }

    override fun TypeConstructorMarker.isClassTypeConstructor(): Boolean {
        // See KT-55383
        return this is ConeClassLikeLookupTag || this is ConeStubTypeConstructor
    }

    override fun TypeConstructorMarker.isInterface(): Boolean {
        return ((this as? ConeClassLikeLookupTag)?.toClassLikeSymbol()?.fir as? FirClass)?.classKind == ClassKind.INTERFACE
    }

    override fun TypeParameterMarker.getVariance(): TypeVariance {
        require(this is ConeTypeParameterLookupTag)
        return this.symbol.fir.variance.convertVariance()
    }

    override fun TypeParameterMarker.upperBoundCount(): Int {
        require(this is ConeTypeParameterLookupTag)
        return this.bounds().size
    }

    override fun TypeParameterMarker.getUpperBound(index: Int): KotlinTypeMarker {
        require(this is ConeTypeParameterLookupTag)
        return this.bounds()[index].coneType
    }

    override fun TypeParameterMarker.getUpperBounds(): List<KotlinTypeMarker> {
        require(this is ConeTypeParameterLookupTag)
        return this.bounds().map { it.coneType }
    }

    override fun TypeParameterMarker.getTypeConstructor(): TypeConstructorMarker {
        require(this is ConeTypeParameterLookupTag)
        return this
    }

    override fun TypeParameterMarker.hasRecursiveBounds(selfConstructor: TypeConstructorMarker?): Boolean {
        require(this is ConeTypeParameterLookupTag)
        this.typeParameterSymbol.lazyResolveToPhase(FirResolvePhase.TYPES)
        return this.bounds().any { typeRef ->
            typeRef.coneType.contains { it.typeConstructor() == this.getTypeConstructor() }
                    && (selfConstructor == null || typeRef.coneType.typeConstructor() == selfConstructor)
        }
    }

    override fun areEqualTypeConstructors(c1: TypeConstructorMarker, c2: TypeConstructorMarker): Boolean {
        if (c1 is ErrorTypeConstructor || c2 is ErrorTypeConstructor) return false
        return c1 == c2
    }

    override fun TypeConstructorMarker.isDenotable(): Boolean {
        return when (this) {
            is ConeClassLikeLookupTag,
            is ConeTypeParameterLookupTag -> true

            is ConeStubTypeConstructor,
            is ConeCapturedTypeConstructor,
            is ErrorTypeConstructor,
            is ConeTypeVariableTypeConstructor,
            is ConeIntegerLiteralType,
            is ConeIntersectionType -> false

            else -> unknownConstructorError()
        }
    }

    override fun TypeConstructorMarker.isCommonFinalClassConstructor(): Boolean {
        val symbol = toClassLikeSymbol() ?: return false
        if (symbol is FirAnonymousObjectSymbol) return true
        val classSymbol = symbol as? FirRegularClassSymbol ?: return false
        val fir = classSymbol.fir
        return fir.modality == Modality.FINAL &&
                fir.classKind != ClassKind.ENUM_ENTRY &&
                fir.classKind != ClassKind.ANNOTATION_CLASS
    }

    override fun captureFromExpression(type: KotlinTypeMarker): ConeKotlinType? {
        require(type is ConeKotlinType)
        return captureFromExpressionInternal(type)
    }

    override fun captureFromArguments(type: SimpleTypeMarker, status: CaptureStatus): SimpleTypeMarker? {
        require(type is ConeKotlinType)
        return captureFromArgumentsInternal(type, status) as SimpleTypeMarker?
    }

    override fun SimpleTypeMarker.asArgumentList(): TypeArgumentListMarker {
        require(this is ConeKotlinType)
        return this
    }

    override fun identicalArguments(a: SimpleTypeMarker, b: SimpleTypeMarker): Boolean {
        require(a is ConeKotlinType)
        require(b is ConeKotlinType)
        return a.typeArguments === b.typeArguments
    }

    override fun TypeConstructorMarker.isAnyConstructor(): Boolean {
        return this is ConeClassLikeLookupTag && classId == StandardClassIds.Any
    }

    override fun TypeConstructorMarker.isNothingConstructor(): Boolean {
        return this is ConeClassLikeLookupTag && classId == StandardClassIds.Nothing
    }

    override fun TypeConstructorMarker.isArrayConstructor(): Boolean {
        return this is ConeClassLikeLookupTag && classId == StandardClassIds.Array
    }

    override fun SimpleTypeMarker.isSingleClassifierType(): Boolean {
        if (isError()) return false
        if (this is ConeCapturedType) return true
        if (this is ConeTypeVariableType) return false
        if (this is ConeIntersectionType) return false
        if (this is ConeIntegerLiteralType) return true
        if (this is ConeStubType) return true
        if (this is ConeDefinitelyNotNullType) return true
        require(this is ConeLookupTagBasedType)
        val typeConstructor = this.typeConstructor()
        return typeConstructor is ConeClassLikeLookupTag ||
                typeConstructor is ConeTypeParameterLookupTag
    }

    override fun SimpleTypeMarker.isPrimitiveType(): Boolean {
        if (this is ConeClassLikeType) {
            return isPrimitive
        }
        return false
    }

    override fun KotlinTypeMarker.getAttributes(): List<AnnotationMarker> {
        require(this is ConeKotlinType)
        return attributes.toList()
    }

    override fun SimpleTypeMarker.isStubType(): Boolean {
        return this is ConeStubType
    }

    override fun SimpleTypeMarker.isStubTypeForVariableInSubtyping(): Boolean {
        return this is ConeStubTypeForTypeVariableInSubtyping
    }

    override fun SimpleTypeMarker.isStubTypeForBuilderInference(): Boolean {
        return false
    }

    override fun TypeConstructorMarker.unwrapStubTypeVariableConstructor(): TypeConstructorMarker {
        if (this !is ConeStubTypeConstructor) return this
        if (this.isTypeVariableInSubtyping) return this
        if (this.isForFixation) return this
        return this.variable.typeConstructor
    }

    override fun intersectTypes(types: Collection<SimpleTypeMarker>): SimpleTypeMarker {
        @Suppress("UNCHECKED_CAST")
        return ConeTypeIntersector.intersectTypes(this as ConeInferenceContext, types as Collection<ConeKotlinType>) as SimpleTypeMarker
    }

    override fun intersectTypes(types: Collection<KotlinTypeMarker>): ConeKotlinType {
        @Suppress("UNCHECKED_CAST")
        return ConeTypeIntersector.intersectTypes(this as ConeInferenceContext, types as Collection<ConeKotlinType>)
    }

    override fun KotlinTypeMarker.isNullableType(): Boolean {
        require(this is ConeKotlinType)
        return canBeNull(session)
    }

    private fun TypeConstructorMarker.toFirRegularClass(): FirRegularClass? {
        return toClassLikeSymbol()?.fir as? FirRegularClass
    }

    override fun nullableAnyType(): SimpleTypeMarker = session.builtinTypes.nullableAnyType.type

    override fun arrayType(componentType: KotlinTypeMarker): SimpleTypeMarker {
        require(componentType is ConeKotlinType)
        return componentType.createArrayType(nullable = false)
    }

    override fun KotlinTypeMarker.isArrayOrNullableArray(): Boolean {
        require(this is ConeKotlinType)
        return this.classId == StandardClassIds.Array
    }

    override fun TypeConstructorMarker.isFinalClassOrEnumEntryOrAnnotationClassConstructor(): Boolean {
        val firRegularClass = toFirRegularClass() ?: return false

        // NB: This API is used to determine if a given type [isMostPreciseCovariantArgument] (at `typeMappingUtil.kt`),
        // affecting the upper bound wildcard when mapping the enclosing type to [PsiType]. See KT-57578 for more details.
        // The counterpart in K1, [ClassicTypeSystemContext], uses [ClassDescriptor.isFinalClass] in `ModalityUtils.kt`,
        // which filters out `enum` class. It seems [ClassDescriptor.isFinalOrEnum] is for truly `final` class.
        // That is, the overall API name---isFinalClassOr...---is misleading.
        val classKind = firRegularClass.classKind
        return classKind.isEnumEntry ||
                classKind.isAnnotationClass ||
                classKind.isObject ||
                classKind.isClass && firRegularClass.symbol.modality == Modality.FINAL
    }

    override fun KotlinTypeMarker.hasAnnotation(fqName: FqName): Boolean {
        require(this is ConeKotlinType)
        val compilerAttribute = CompilerConeAttributes.compilerAttributeKeyByFqName[fqName]
        if (compilerAttribute != null) {
            return compilerAttribute in attributes
        }
        return customAnnotations.any {
            it.resolvedType.fullyExpandedType(session).classId?.asSingleFqName() == fqName
        }
    }

    override fun KotlinTypeMarker.getAnnotationFirstArgumentValue(fqName: FqName): Any? {
        require(this is ConeKotlinType)
        // We don't check for compiler attributes because all of them doesn't have parameters
        val annotationCall = customAnnotations.firstOrNull {
            it.resolvedType.fullyExpandedType(session).classId?.asSingleFqName() == fqName
        } ?: return null

        if (annotationCall is FirAnnotationCall) {
            annotationCall.containingDeclarationSymbol.lazyResolveToPhase(FirResolvePhase.ANNOTATION_ARGUMENTS)
        }

        val argument = when (val argument = annotationCall.argumentMapping.mapping.values.firstOrNull() ?: return null) {
            is FirVarargArgumentsExpression -> argument.arguments.firstOrNull()
            is FirArrayLiteral -> argument.arguments.firstOrNull()
            is FirNamedArgumentExpression -> argument.expression
            else -> argument
        } ?: return null
        return (argument as? FirLiteralExpression)?.value
    }

    override fun TypeConstructorMarker.getTypeParameterClassifier(): TypeParameterMarker? {
        return this as? ConeTypeParameterLookupTag
    }

    override fun TypeConstructorMarker.isInlineClass(): Boolean {
        val fields = getValueClassProperties() ?: return false
        return this@ConeTypeContext.valueClassLoweringKind(fields) == ValueClassKind.Inline
    }

    override fun TypeConstructorMarker.isMultiFieldValueClass(): Boolean {
        val fields = getValueClassProperties() ?: return false
        return this@ConeTypeContext.valueClassLoweringKind(fields) == ValueClassKind.MultiField
    }

    override fun TypeConstructorMarker.getValueClassProperties(): List<Pair<Name, SimpleTypeMarker>>? {
        val firClass = toFirRegularClass() ?: return null
        // NB: [FirRegularClass.valueClassRepresentation] is updated by [FirStatusResolveTransformer].
        return firClass.valueClassRepresentation?.underlyingPropertyNamesToTypes
    }

    override fun TypeConstructorMarker.isInnerClass(): Boolean {
        return toFirRegularClass()?.isInner == true
    }

    override fun TypeParameterMarker.getRepresentativeUpperBound(): KotlinTypeMarker {
        require(this is ConeTypeParameterLookupTag)
        return this.bounds().getOrNull(0)?.coneType
            ?: session.builtinTypes.nullableAnyType.type
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun ConeTypeParameterLookupTag.bounds(): List<FirTypeRef> = symbol.fir.bounds

    override fun KotlinTypeMarker.getUnsubstitutedUnderlyingType(): KotlinTypeMarker? {
        require(this is ConeKotlinType)
        return unsubstitutedUnderlyingTypeForInlineClass(session)
    }

    override fun KotlinTypeMarker.getSubstitutedUnderlyingType(): KotlinTypeMarker? {
        require(this is ConeKotlinType)
        return substitutedUnderlyingTypeForInlineClass(session, this@ConeTypeContext)
    }

    override fun TypeConstructorMarker.getPrimitiveType(): PrimitiveType? =
        getClassFqNameUnsafe()?.let(StandardNames.FqNames.fqNameToPrimitiveType::get)

    override fun TypeConstructorMarker.getPrimitiveArrayType(): PrimitiveType? =
        getClassFqNameUnsafe()?.let(StandardNames.FqNames.arrayClassFqNameToPrimitiveType::get)

    override fun TypeConstructorMarker.isUnderKotlinPackage(): Boolean =
        getClassFqNameUnsafe()?.startsWith(StandardClassIds.BASE_KOTLIN_PACKAGE.shortName()) == true

    override fun TypeConstructorMarker.getClassFqNameUnsafe(): FqNameUnsafe? {
        if (this !is ConeClassLikeLookupTag) return null
        return classId.asSingleFqName().toUnsafe()
    }

    override fun TypeParameterMarker.getName() = (this as ConeTypeParameterLookupTag).name

    override fun TypeParameterMarker.isReified(): Boolean {
        require(this is ConeTypeParameterLookupTag)
        return typeParameterSymbol.fir.isReified
    }

    override fun KotlinTypeMarker.isInterfaceOrAnnotationClass(): Boolean {
        val classKind = typeConstructor().toFirRegularClass()?.classKind ?: return false
        return classKind == ClassKind.ANNOTATION_CLASS || classKind == ClassKind.INTERFACE
    }

    override fun TypeConstructorMarker.isError(): Boolean {
        return this is ErrorTypeConstructor
    }

    private fun TypeConstructorMarker.unknownConstructorError(): Nothing {
        errorWithAttachment("Unknown type constructor: ${this::class.java}") {
            withEntry("constructor", this@unknownConstructorError) { it.toString() }
            withFirLookupTagEntry("constructorAsLookupTag", this@unknownConstructorError as? ConeClassifierLookupTag)
        }
    }

    override fun substitutionSupertypePolicy(type: SimpleTypeMarker): TypeCheckerState.SupertypesPolicy {
        if (type.argumentsCount() == 0) return LowerIfFlexible
        require(type is ConeKotlinType)
        val declaration = when (type) {
            is ConeClassLikeType -> type.lookupTag.toSymbol(session)?.fir
            else -> null
        }

        val substitutor = if (declaration is FirTypeParameterRefsOwner) {
            val substitution =
                declaration.typeParameters.zip(type.typeArguments).associate { (parameter, argument) ->
                    parameter.symbol to ((argument as? ConeKotlinTypeProjection)?.type
                        ?: session.builtinTypes.nullableAnyType.type)//StandardClassIds.Any(session.firSymbolProvider).constructType(emptyArray(), isNullable = true))
                }
            substitutorByMap(substitution, session)
        } else {
            ConeSubstitutor.Empty
        }
        return object : DoCustomTransform() {
            override fun transformType(state: TypeCheckerState, type: KotlinTypeMarker): SimpleTypeMarker {
                val lowerBound = type.lowerBoundIfFlexible()
                require(lowerBound is ConeKotlinType)
                return substitutor.substituteOrSelf(lowerBound) as SimpleTypeMarker
            }

        }
    }

    override fun KotlinTypeMarker.isTypeVariableType(): Boolean {
        return this is ConeTypeVariableType
    }
}
