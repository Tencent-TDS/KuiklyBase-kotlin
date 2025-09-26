/*
 * Copyright 2025-2025 Huawei Technologies Co., Ltd. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

@file:OptIn(ExperimentalForeignApi::class)
package kotlin

import kotlin.native.internal.ExportTypeInfo
import kotlin.native.internal.GCUnsafeCall
import kotlin.native.internal.NativePtr
import kotlin.native.internal.escapeAnalysis.Escapes
import kotlinx.cinterop.*

@GCUnsafeCall("Kotlin_napi_get_kotlin_string_utf16")
@Escapes.Nothing
public external fun napi_get_kotlin_string_utf16(env: NativePtr, value: NativePtr): String

@GCUnsafeCall("Kotlin_napi_create_int32")
public external fun napi_create_kotlin_int32(env: NativePtr, value: Int): NativePtr

@GCUnsafeCall("Kotlin_napi_get_value_int32")
public external fun napi_get_value_kotlin_int32(env: NativePtr, value: NativePtr): Int

@GCUnsafeCall("Kotlin_napi_create_int64")
public external fun napi_create_kotlin_int64(env: NativePtr, value: Long): NativePtr

@GCUnsafeCall("Kotlin_napi_get_value_int64")
public external fun napi_get_value_kotlin_int64(env: NativePtr, value: NativePtr): Long

@GCUnsafeCall("Kotlin_napi_create_bool")
public external fun napi_create_kotlin_bool(env: NativePtr, value: Boolean): NativePtr

@GCUnsafeCall("Kotlin_napi_get_value_bool")
public external fun napi_get_value_kotlin_bool(env: NativePtr, value: NativePtr): Boolean

@GCUnsafeCall("Kotlin_napi_create_double")
public external fun napi_create_kotlin_double(env: NativePtr, value: Double): NativePtr

@GCUnsafeCall("Kotlin_napi_get_value_double")
public external fun napi_get_value_kotlin_double(env: NativePtr, value: NativePtr): Double

@GCUnsafeCall("Kotlin_String_toNapiValue")
@Escapes.Nothing
public external fun String.getNapiValue(env: NativePtr): NativePtr
