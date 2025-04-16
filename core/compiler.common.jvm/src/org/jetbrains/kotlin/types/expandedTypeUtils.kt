/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.types

import org.jetbrains.kotlin.types.model.KotlinTypeMarker
import org.jetbrains.kotlin.types.model.SimpleTypeMarker
import org.jetbrains.kotlin.types.model.TypeConstructorMarker
import org.jetbrains.kotlin.types.model.TypeParameterMarker

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
            val underlyingType = when (val unsubstitutedUnderlyingType = kotlinType.getUnsubstitutedUnderlyingKind()) {
                is UnderlyingTypeKind.TypeParameter -> {
                    val bound = unsubstitutedUnderlyingType.representativeUpperBound
                    if (unsubstitutedUnderlyingType.type.isNullableType()) bound.makeNullable() else bound
                }
                is UnderlyingTypeKind.ArrayOfTypeParameter -> {
                    val elementTypeBounded = when (unsubstitutedUnderlyingType.variance) {
                        Variance.IN_VARIANCE -> nullableAnyType()
                        else -> unsubstitutedUnderlyingType.representativeElementUpperBound
                    }
                    val arrayType = arrayType(elementTypeBounded)
                    if (unsubstitutedUnderlyingType.type.isNullableType()) arrayType.makeNullable() else arrayType
                }
                // otherwise
                else -> kotlinType.getSubstitutedUnderlyingType() ?: unsubstitutedUnderlyingType?.type
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
