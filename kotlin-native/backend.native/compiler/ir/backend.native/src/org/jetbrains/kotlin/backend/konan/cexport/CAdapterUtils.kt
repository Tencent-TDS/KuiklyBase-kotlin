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

package org.jetbrains.kotlin.backend.konan.cexport

import org.jetbrains.kotlin.backend.konan.KonanFqNames
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor

/**
 * Check if the given function is marked as @HiddenFromC.
 */
internal fun FunctionDescriptor.isHiddenFromC(): Boolean = when {
    overriddenDescriptors.isNotEmpty() -> overriddenDescriptors.first().isHiddenFromC()
    else -> annotations.any { annotation -> annotation.fqName == KonanFqNames.hiddenFromC }
}

/**
 * Check if the given class or its enclosing declaration is marked as @HiddenFromC.
 */
internal fun ClassDescriptor.isHiddenFromC(): Boolean = when {
    (this.containingDeclaration as? ClassDescriptor)?.isHiddenFromC() == true -> true
    else -> annotations.any { annotation -> annotation.fqName == KonanFqNames.hiddenFromC }
}