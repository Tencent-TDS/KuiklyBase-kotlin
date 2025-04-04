/*
 * Copyright 2010-2025 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.common

import org.jetbrains.kotlin.library.BaseKotlinLibrary
import org.jetbrains.kotlin.library.builtInsPlatform
import org.jetbrains.kotlin.library.impl.BuiltInsPlatform
import org.jetbrains.kotlin.library.nativeTargets
import org.jetbrains.kotlin.library.wasmTargets

interface KlibPlatformChecker {
    fun check(library: BaseKotlinLibrary): Boolean

    /**
     * Checks if a library is a Kotlin/Native library.
     * If [target] is not null, then additionally checks that the given target is supported in the library.
     */
    class Native(private val target: String? = null) : KlibPlatformChecker {
        override fun check(library: BaseKotlinLibrary): Boolean {
            return library.builtInsPlatform == BuiltInsPlatform.NATIVE && target?.let { it in library.nativeTargets } != false
        }
    }

    /**
     * Checks if a library is a Kotlin/JS library.
     */
    object JS : KlibPlatformChecker {
        override fun check(library: BaseKotlinLibrary): Boolean {
            return library.builtInsPlatform == BuiltInsPlatform.JS
        }
    }

    /**
     * Checks if a library is a Kotlin/Wasm library.
     * If [target] is not null, then additionally checks that the given target is supported in the library.
     */
    class Wasm(private val target: String? = null) : KlibPlatformChecker {
        override fun check(library: BaseKotlinLibrary): Boolean {
            return library.builtInsPlatform == BuiltInsPlatform.WASM && target?.let { it in library.wasmTargets } != false
        }
    }
}
