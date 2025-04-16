/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.load.kotlin

import org.jetbrains.kotlin.types.TypeSystemCommonBackendContext
import org.jetbrains.kotlin.types.UnderlyingTypeKind
import org.jetbrains.kotlin.types.model.KotlinTypeMarker
import org.jetbrains.kotlin.types.model.SimpleTypeMarker

internal fun TypeSystemCommonBackendContext.computeUnderlyingType(inlineClassType: KotlinTypeMarker): KotlinTypeMarker? {
    if (!shouldUseUnderlyingType(inlineClassType)) return null

    return when (val underlyingType = inlineClassType.getUnsubstitutedUnderlyingKind()) {
        null -> null
        is UnderlyingTypeKind.TypeParameter -> {
            val type = underlyingType.representativeUpperBound
            if (underlyingType.type.isMarkedNullable()) type.makeNullable() else type
        }
        is UnderlyingTypeKind.ArrayOfTypeParameter -> {
            val arrayType = arrayType(underlyingType.representativeElementUpperBound)
            if (underlyingType.type.isMarkedNullable()) arrayType.makeNullable() else arrayType
        }
        else -> inlineClassType.getSubstitutedUnderlyingType()
    }
}

internal fun TypeSystemCommonBackendContext.shouldUseUnderlyingType(inlineClassType: KotlinTypeMarker): Boolean {
    val underlyingType = inlineClassType.getUnsubstitutedUnderlyingType() ?: return false

    return !inlineClassType.isMarkedNullable() ||
            !underlyingType.isNullableType() && !(underlyingType is SimpleTypeMarker && underlyingType.isPrimitiveType())
}
