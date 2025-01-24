/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.types

import org.jetbrains.kotlin.types.model.KotlinTypeMarker
import org.jetbrains.kotlin.types.model.SimpleTypeMarker
import org.jetbrains.kotlin.types.model.TypeArgumentMarker
import org.jetbrains.kotlin.types.model.TypeConstructorMarker
import org.jetbrains.kotlin.types.model.TypeParameterMarker
import org.jetbrains.kotlin.types.model.TypeVariance

fun TypeSystemCommonBackendContext.computeExpandedTypeForInlineClass(inlineClassType: KotlinTypeMarker): KotlinTypeMarker? =
    computeExpandedTypeInner(inlineClassType, hashSetOf())

private fun TypeSystemCommonBackendContext.computeExpandedTypeInner(
    kotlinType: KotlinTypeMarker, visitedClassifiers: HashSet<TypeConstructorMarker>
): KotlinTypeMarker? {
    val classifier = kotlinType.typeConstructor()
    if (!visitedClassifiers.add(classifier)) return null

    val typeParameter = classifier.getTypeParameterClassifier()

    return when {
        typeParameter != null -> {
            val upperBound = typeParameter.getRepresentativeUpperBound()
            computeExpandedTypeInner(upperBound, visitedClassifiers)
                ?.let { expandedUpperBound ->
                    val upperBoundIsPrimitiveOrInlineClass =
                        upperBound.typeConstructor().isInlineClass() || upperBound is SimpleTypeMarker && upperBound.isPrimitiveType()
                    when {
                        expandedUpperBound is SimpleTypeMarker && expandedUpperBound.isPrimitiveType() &&
                                kotlinType.isNullableType() && upperBoundIsPrimitiveOrInlineClass -> upperBound.makeNullable()
                        expandedUpperBound.isNullableType() || !kotlinType.isMarkedNullable() -> expandedUpperBound
                        else -> expandedUpperBound.makeNullable()
                    }
                }
        }

        classifier.isInlineClass() -> {
            // kotlinType is the boxed inline class type
            val unsubstitutedUnderlyingType = kotlinType.getUnsubstitutedUnderlyingType()
            val unsubstitutedTypeParameter = getTypeParameter(unsubstitutedUnderlyingType)
            val unsubstitutedArrayElement = unsubstitutedUnderlyingType
                ?.let { getArrayElementProjection(it) }
                ?.let { getTypeParameter(it.getType()) to it.getVariance() }
            val underlyingType = when {
                // case <A> (val value: A)
                unsubstitutedTypeParameter != null -> {
                    val typeParameters = classifier.getParameters()
                    val typeArguments = kotlinType.getArguments().mapIndexed { index, typeArgument ->
                        typeArgument.getType() ?: typeParameters[index].getRepresentativeUpperBound()
                    }
                    val mapping = typeParameters.map { it.getTypeConstructor() }.zip(typeArguments).toMap()
                    val substitutedType = typeSubstitutorByTypeConstructor(mapping).safeSubstitute(unsubstitutedTypeParameter.getRepresentativeUpperBound())
                    if (unsubstitutedUnderlyingType?.isNullableType() == true) substitutedType.makeNullable() else substitutedType
                }
                // case <A> (val value: Array<A>)
                // this logic coincides with [org.jetbrains.kotlin.types.AbstractTypeMapper.mapArrayType]
                unsubstitutedArrayElement?.first != null -> {
                    val elementTypeBounded = when (unsubstitutedArrayElement.second) {
                        TypeVariance.IN -> nullableAnyType()
                        TypeVariance.INV, TypeVariance.OUT -> unsubstitutedArrayElement.first!!.getRepresentativeUpperBound()
                    }
                    val arrayType = arrayType(elementTypeBounded)
                    if (unsubstitutedUnderlyingType.isNullableType()) arrayType.makeNullable() else arrayType
                }
                // otherwise
                else -> kotlinType.getSubstitutedUnderlyingType() ?: unsubstitutedUnderlyingType
            } ?: return null
            val expandedUnderlyingType = computeExpandedTypeInner(underlyingType, visitedClassifiers) ?: return null
            when {
                !kotlinType.isNullableType() -> expandedUnderlyingType

                // Here inline class type is nullable. Apply nullability to the expandedUnderlyingType.

                // Nullable types become inline class boxes
                expandedUnderlyingType.isNullableType() -> kotlinType

                // Primitives become inline class boxes
                expandedUnderlyingType is SimpleTypeMarker && expandedUnderlyingType.isPrimitiveType() -> kotlinType

                // Non-null reference types become nullable reference types
                else -> expandedUnderlyingType.makeNullable()
            }
        }

        else -> kotlinType
    }
}

fun TypeSystemCommonBackendContext.getTypeParameter(type: KotlinTypeMarker?): TypeParameterMarker? =
    type?.typeConstructor()?.getTypeParameterClassifier()

fun TypeSystemCommonBackendContext.getArrayElementProjection(type: KotlinTypeMarker): TypeArgumentMarker? {
    if (!type.isArrayOrNullableArray()) return null
    return type.getArgument(0)
}

