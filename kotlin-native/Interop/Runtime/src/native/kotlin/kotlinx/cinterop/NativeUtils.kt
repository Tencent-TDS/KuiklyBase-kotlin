/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package kotlinx.cinterop

import kotlin.native.internal.GCUnsafeCall
import kotlin.native.internal.Intrinsic
import kotlin.native.internal.TypedIntrinsic
import kotlin.native.internal.IntrinsicType

internal fun encodeToUtf8(str: String): ByteArray = str.encodeToByteArray()

@GCUnsafeCall("Kotlin_CString_toKStringFromUtf8Impl")
@ExperimentalForeignApi
internal external fun CPointer<ByteVar>.toKStringFromUtf8Impl(): String

// region @Tencent: Create Kotlin Strings from owned UTF-8/UTF-16 C buffers with optional free callback.
/**
 * C callback `(mem, context) -> void` invoked to release an owned native memory buffer (`mem`)
 * with an optional opaque user `context`.
 */
@ExperimentalForeignApi
public typealias NativeFreeCallback = CPointer<CFunction<(mem: COpaquePointer?, context: COpaquePointer?) -> Unit>>

/**
 * Converts an owned UTF-8 C string buffer to a Kotlin [String] (zero-copy when pure ASCII).
 *
 * Ownership of [this] buffer is transferred to the runtime: the caller must NOT mutate or free it afterwards.
 * The buffer must have been allocated with standard C `malloc`/`calloc`/`realloc` so it can be released via `free()`.
 * Non-ASCII strings are decoded into a newly allocated UTF-16 [String] and [this] buffer is freed immediately.
 *
 * @param length The byte length of the UTF-8 buffer, or negative to compute length via `strlen`.
 */
@GCUnsafeCall("Kotlin_Interop_toKStringFromOwnedUtf8")
@ExperimentalForeignApi
public external fun CPointer<ByteVar>.toKStringFromOwnedUtf8(length: Int = -1): String

/**
 * Converts an owned UTF-8 C string buffer to a Kotlin [String] (zero-copy when pure ASCII) with a custom release callback.
 *
 * Ownership of [this] buffer is transferred to the runtime: the caller must NOT mutate it afterwards.
 * When the buffer is no longer needed (either immediately on empty/non-ASCII fallback, or when the [String] is garbage collected),
 * if [freeCallback] is non-null, `freeCallback(mem, freeContext)` is invoked; if [freeCallback] is `null`, no deallocation is performed.
 *
 * @param length The byte length of the UTF-8 buffer, or negative to compute length via `strlen`.
 * @param freeCallback Optional C callback invoked to release the buffer, or `null` for no-op release.
 * @param freeContext Optional opaque user context forwarded to [freeCallback].
 */
@GCUnsafeCall("Kotlin_Interop_toKStringFromOwnedUtf8WithFreeCallback")
@ExperimentalForeignApi
public external fun CPointer<ByteVar>.toKStringFromOwnedUtf8(
    length: Int = -1,
    freeCallback: NativeFreeCallback?,
    freeContext: COpaquePointer? = null,
): String

/**
 * Converts an owned UTF-16LE C string buffer to a Kotlin [String] (zero-copy when 2-byte aligned).
 *
 * Ownership of [this] buffer is transferred to the runtime: the caller must NOT mutate or free it afterwards.
 * The buffer must have been allocated with standard C `malloc`/`calloc`/`realloc` so it can be released via `free()`.
 *
 * @param length The number of 16-bit UTF-16 code units (`Char`s), or negative to scan until `0u`.
 */
@GCUnsafeCall("Kotlin_Interop_toKStringFromOwnedUtf16")
@ExperimentalForeignApi
public external fun CPointer<UShortVar>.toKStringFromOwnedUtf16(length: Int = -1): String

/**
 * Converts an owned UTF-16LE C string buffer to a Kotlin [String] (zero-copy when 2-byte aligned) with a custom release callback.
 *
 * Ownership of [this] buffer is transferred to the runtime: the caller must NOT mutate it afterwards.
 * When the buffer is no longer needed (either immediately on empty/unaligned fallback, or when the [String] is garbage collected),
 * if [freeCallback] is non-null, `freeCallback(mem, freeContext)` is invoked; if [freeCallback] is `null`, no deallocation is performed.
 *
 * @param length The number of 16-bit UTF-16 code units (`Char`s), or negative to scan until `0u`.
 * @param freeCallback Optional C callback invoked to release the buffer, or `null` for no-op release.
 * @param freeContext Optional opaque user context forwarded to [freeCallback].
 */
@GCUnsafeCall("Kotlin_Interop_toKStringFromOwnedUtf16WithFreeCallback")
@ExperimentalForeignApi
public external fun CPointer<UShortVar>.toKStringFromOwnedUtf16(
    length: Int = -1,
    freeCallback: NativeFreeCallback?,
    freeContext: COpaquePointer? = null,
): String

/**
 * Converts an owned UTF-16LE C string buffer to a Kotlin [String] (zero-copy when 2-byte aligned).
 *
 * Ownership of [this] buffer is transferred to the runtime: the caller must NOT mutate or free it afterwards.
 * The buffer must have been allocated with standard C `malloc`/`calloc`/`realloc` so it can be released via `free()`.
 *
 * @param length The number of 16-bit UTF-16 code units (`Char`s), or negative to scan until `0`.
 */
@GCUnsafeCall("Kotlin_Interop_toKStringFromOwnedUtf16")
@ExperimentalForeignApi
public external fun CPointer<ShortVar>.toKStringFromOwnedUtf16(length: Int = -1): String

/**
 * Converts an owned UTF-16LE C string buffer to a Kotlin [String] (zero-copy when 2-byte aligned) with a custom release callback.
 *
 * Ownership of [this] buffer is transferred to the runtime: the caller must NOT mutate it afterwards.
 * When the buffer is no longer needed (either immediately on empty/unaligned fallback, or when the [String] is garbage collected),
 * if [freeCallback] is non-null, `freeCallback(mem, freeContext)` is invoked; if [freeCallback] is `null`, no deallocation is performed.
 *
 * @param length The number of 16-bit UTF-16 code units (`Char`s), or negative to scan until `0`.
 * @param freeCallback Optional C callback invoked to release the buffer, or `null` for no-op release.
 * @param freeContext Optional opaque user context forwarded to [freeCallback].
 */
@GCUnsafeCall("Kotlin_Interop_toKStringFromOwnedUtf16WithFreeCallback")
@ExperimentalForeignApi
public external fun CPointer<ShortVar>.toKStringFromOwnedUtf16(
    length: Int = -1,
    freeCallback: NativeFreeCallback?,
    freeContext: COpaquePointer? = null,
): String
// endregion

@ExperimentalForeignApi
@TypedIntrinsic(IntrinsicType.INTEROP_BITS_TO_FLOAT)
public external fun bitsToFloat(bits: Int): Float

@ExperimentalForeignApi
@TypedIntrinsic(IntrinsicType.INTEROP_BITS_TO_DOUBLE)
public external fun bitsToDouble(bits: Long): Double

@Deprecated("Deprecated without replacement as part of the obsolete interop API", level = DeprecationLevel.WARNING)
@TypedIntrinsic(IntrinsicType.INTEROP_SIGN_EXTEND)
public external inline fun <reified R : Number> Number.signExtend(): R

@Deprecated("Deprecated without replacement as part of the obsolete interop API", level = DeprecationLevel.WARNING)
@TypedIntrinsic(IntrinsicType.INTEROP_NARROW)
public external inline fun <reified R : Number> Number.narrow(): R

@ExperimentalForeignApi
@TypedIntrinsic(IntrinsicType.INTEROP_CONVERT)
public external inline fun <reified R : Any> Byte.convert(): R
@ExperimentalForeignApi
@TypedIntrinsic(IntrinsicType.INTEROP_CONVERT)
public external inline fun <reified R : Any> Short.convert(): R
@ExperimentalForeignApi
@TypedIntrinsic(IntrinsicType.INTEROP_CONVERT)
public external inline fun <reified R : Any> Int.convert(): R
@ExperimentalForeignApi
@TypedIntrinsic(IntrinsicType.INTEROP_CONVERT)
public external inline fun <reified R : Any> Long.convert(): R
@ExperimentalForeignApi
@TypedIntrinsic(IntrinsicType.INTEROP_CONVERT)
public external inline fun <reified R : Any> UByte.convert(): R
@ExperimentalForeignApi
@TypedIntrinsic(IntrinsicType.INTEROP_CONVERT)
public external inline fun <reified R : Any> UShort.convert(): R
@ExperimentalForeignApi
@TypedIntrinsic(IntrinsicType.INTEROP_CONVERT)
public external inline fun <reified R : Any> UInt.convert(): R
@ExperimentalForeignApi
@TypedIntrinsic(IntrinsicType.INTEROP_CONVERT)
public external inline fun <reified R : Any> ULong.convert(): R

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER, AnnotationTarget.FILE)
@Retention(AnnotationRetention.SOURCE)
internal annotation class JvmName(val name: String)

@ExperimentalForeignApi
public fun cValuesOf(vararg elements: UByte): CValues<UByteVar> =
        createValues(elements.size) { index -> this.value = elements[index] }

@ExperimentalForeignApi
public fun cValuesOf(vararg elements: UShort): CValues<UShortVar> =
        createValues(elements.size) { index -> this.value = elements[index] }

@ExperimentalForeignApi
public fun cValuesOf(vararg elements: UInt): CValues<UIntVar> =
        createValues(elements.size) { index -> this.value = elements[index] }

@ExperimentalForeignApi
public fun cValuesOf(vararg elements: ULong): CValues<ULongVar> =
        createValues(elements.size) { index -> this.value = elements[index] }

@ExperimentalForeignApi
public fun UByteArray.toCValues(): CValues<UByteVar> = cValuesOf(*this)
@ExperimentalForeignApi
public fun UShortArray.toCValues(): CValues<UShortVar> = cValuesOf(*this)
@ExperimentalForeignApi
public fun UIntArray.toCValues(): CValues<UIntVar> = cValuesOf(*this)
@ExperimentalForeignApi
public fun ULongArray.toCValues(): CValues<ULongVar> = cValuesOf(*this)
