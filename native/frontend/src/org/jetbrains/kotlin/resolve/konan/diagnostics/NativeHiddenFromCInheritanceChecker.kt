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

package org.jetbrains.kotlin.resolve.konan.diagnostics

import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.resolve.checkers.DeclarationChecker
import org.jetbrains.kotlin.resolve.checkers.DeclarationCheckerContext
import org.jetbrains.kotlin.resolve.descriptorUtil.getSuperClassNotAny
import org.jetbrains.kotlin.resolve.descriptorUtil.getSuperInterfaces

/**
 * Check that the given class does not inherit from class or implements interface that is
 * marked as HiddenFromC.
 */
object NativeHiddenFromCInheritanceChecker : DeclarationChecker {
    private val hiddenFromCFqName = FqName("kotlin.native.HiddenFromC")
    
    override fun check(declaration: KtDeclaration, descriptor: DeclarationDescriptor, context: DeclarationCheckerContext) {
        if (descriptor !is ClassDescriptor) return
        // Enum entries inherit from their enum class.
        if (descriptor.kind == ClassKind.ENUM_ENTRY) return
        // Non-public types do not leak to Objective-C API surface, so it is OK for them
        // to inherit from hidden types.
        if (!descriptor.visibility.isPublicAPI) return
        // No need to report anything on class that is hidden itself.
        if (checkClassIsHiddenFromC(descriptor)) return

        val isSubtypeOfHiddenFromC = descriptor.getSuperInterfaces().any { checkClassIsHiddenFromC(it) } ||
                descriptor.getSuperClassNotAny()?.let { checkClassIsHiddenFromC(it) } == true
        if (isSubtypeOfHiddenFromC) {
            context.trace.report(ErrorsNative.SUBTYPE_OF_HIDDEN_FROM_C.on(declaration))
        }
    }

    private fun checkContainingClassIsHidden(currentClass: ClassDescriptor): Boolean {
        return (currentClass.containingDeclaration as? ClassDescriptor)?.let {
            if (checkClassIsHiddenFromC(it)) {
                true
            } else {
                checkContainingClassIsHidden(it)
            }
        } ?: false
    }

    private fun checkClassIsHiddenFromC(clazz: ClassDescriptor): Boolean {
        clazz.annotations.forEach { annotation ->
            if (annotation.fqName == hiddenFromCFqName) return true
        }
        // If outer class is hidden then inner/nested class is hidden as well.
        return checkContainingClassIsHidden(clazz)
    }
}