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

@file:OptIn(InternalForKotlinNative::class, ExperimentalForeignApi::class, ExperimentalNativeApi::class)

package kotlin.text

import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.internal.ExportTypeInfo
import kotlin.native.internal.GCUnsafeCall
import kotlin.native.internal.InternalForKotlinNative
import kotlin.native.ref.createCleaner
import kotlin.tmm.TmmConfig

internal actual sealed interface NativeCharArray {
    actual val size: Int
    actual operator fun get(index: Int): Char
    actual fun iterator(): CharIterator
    actual operator fun set(index: Int, value: Char)
}

private value class CharArrayWrapper(val value: CharArray) : NativeCharArray {
    override val size: Int
        get() = value.size

    override fun get(index: Int): Char {
        return value[index]
    }

    override fun iterator(): CharIterator {
        return value.iterator()
    }

    override fun set(index: Int, value: Char) {
        return this.value.set(index, value)
    }
}

@GCUnsafeCall("Kotlin_NativeCharArrayImpl_init")
private external fun nativeCharArrayInit(size: Int): COpaquePointer

@GCUnsafeCall("Kotlin_NativeCharArrayImpl_release")
private external fun nativeCharArrayRelease(ptr: COpaquePointer)

@GCUnsafeCall("Kotlin_NativeCharArrayImpl_get")
private external fun nativeCharArrayGetChar(nativePtr: COpaquePointer, index: Int): Char

@GCUnsafeCall("Kotlin_NativeCharArrayImpl_set")
private external fun nativeCharArraySetChar(nativePtr: COpaquePointer, index: Int, value: Char)

@OptIn(ExperimentalNativeApi::class, ExperimentalForeignApi::class, ExperimentalForeignApi::class)
private class NativeCharArrayImpl(override var size: Int) : NativeCharArray {

    val nativeHolder: COpaquePointer = nativeCharArrayInit(size)

    private val cleaner = createCleaner(nativeHolder) {
        nativeCharArrayRelease(it)
    }

    override operator fun get(index: Int): Char {
        return nativeCharArrayGetChar(nativeHolder, index)
    }

    override operator fun set(index: Int, value: Char) {
        nativeCharArraySetChar(nativeHolder, index, value)
    }

    override operator fun iterator(): CharIterator {
        return NativeCharIterator(this)
    }
}

private class NativeCharIterator(private val array: NativeCharArrayImpl) : CharIterator() {
    private var current: Int = 0

    override fun nextChar(): Char {
        return array[current++]
    }

    override fun hasNext(): Boolean {
        return current < array.size
    }
}

internal actual fun NativeCharArray(size: Int): NativeCharArray {
    return if (TmmConfig.isNativeStringBuilderEnabled) NativeCharArrayImpl(size) else CharArrayWrapper(CharArray(size))
}

@OptIn(ExperimentalForeignApi::class)
private fun String.toNativeCharArrayImpl(): NativeCharArrayImpl {
    val array = NativeCharArrayImpl(length)
    nativeArrayImplFill(array.nativeHolder, length, 0, this, 0, length)
    return array
}

@GCUnsafeCall("Kotlin_NativeCharArrayImpl_fillString")
private external fun nativeArrayImplFill(array: COpaquePointer, size: Int, offset: Int, string: String, start: Int, count: Int)


@OptIn(ExperimentalForeignApi::class)
internal actual fun unsafeStringFromNativeCharArray(array: NativeCharArray, start: Int, size: Int): String {
    return when (array) {
        is NativeCharArrayImpl -> {
            unsafeStringFromNativeCharArrayImpl(array.nativeHolder, start, size)
        }
        is CharArrayWrapper -> {
            unsafeStringFromCharArray(array.value, start, size)
        }
    }
}

@GCUnsafeCall("Kotlin_String_unsafeStringFromNativeCharArrayImpl")
private external fun unsafeStringFromNativeCharArrayImpl(array: COpaquePointer, start: Int, size: Int): String

internal actual fun String.toNativeCharArray(): NativeCharArray {
    return if (TmmConfig.isNativeStringBuilderEnabled) toNativeCharArrayImpl() else CharArrayWrapper(toCharArray())
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun unsafeStringFromCharArray(array: NativeCharArray, start: Int, size: Int): String {
    return unsafeStringFromNativeCharArray(array, start, size)
}

@GCUnsafeCall("Kotlin_NativeCharArrayImpl_insertString")
private external fun insertString(array: COpaquePointer, size: Int, distIndex: Int, value: String, sourceIndex: Int, count: Int): Int

@OptIn(ExperimentalForeignApi::class)
internal actual fun insertString(array: NativeCharArray, destinationIndex: Int, value: String, sourceIndex: Int, count: Int): Int {
    return when (array) {
        is NativeCharArrayImpl -> {
            insertString(array.nativeHolder, array.size, destinationIndex, value, sourceIndex, count)
        }
        is CharArrayWrapper -> {
            insertString(array.value, destinationIndex, value, sourceIndex, count)
        }
    }
}

@GCUnsafeCall("Kotlin_NativeCharArrayImpl_insertInt")
private external fun insertInt(array: COpaquePointer, size: Int, start: Int, value: Int): Int

@OptIn(ExperimentalForeignApi::class)
internal actual fun insertInt(array: NativeCharArray, start: Int, value: Int): Int {
    return when (array) {
        is NativeCharArrayImpl -> {
            insertInt(array.nativeHolder, array.size, start, value)
        }
        is CharArrayWrapper -> {
            insertInt(array.value, start, value)
        }
    }
}

@GCUnsafeCall("Kotlin_NativeCharArrayImpl_copyFromCharArray")
private external fun nativeArrayImplCopyFromCharArray(array: CharArray, fromIndex: Int, destination: COpaquePointer, size: Int, toIndex: Int, count: Int)

@OptIn(ExperimentalForeignApi::class)
internal actual fun CharArray.copyInto(destination: NativeCharArray, destinationOffset: Int, startIndex: Int, endIndex: Int): NativeCharArray {
    when (destination) {
        is CharArrayWrapper -> arrayCopy(this, startIndex, destination.value, destinationOffset, endIndex - startIndex)
        is NativeCharArrayImpl -> nativeArrayImplCopyFromCharArray(this, startIndex, destination.nativeHolder, destination.size, destinationOffset, endIndex - startIndex)
    }
    return destination
}

@GCUnsafeCall("Kotlin_NativeCharArrayImpl_copyToCharArray")
private external fun nativeArrayImplCopyToCharArray(array: COpaquePointer, size: Int, fromIndex: Int, destination: CharArray, toIndex: Int, count: Int)

@OptIn(ExperimentalForeignApi::class)
internal actual fun NativeCharArray.copyInto(destination: CharArray, destinationOffset: Int, startIndex: Int, endIndex: Int): CharArray {
    when (this) {
        is CharArrayWrapper -> arrayCopy(this.value, startIndex, destination, destinationOffset, endIndex - startIndex)
        is NativeCharArrayImpl -> nativeArrayImplCopyToCharArray(this.nativeHolder, this.size, startIndex, destination, destinationOffset, endIndex - startIndex)
    }
    return destination
}

@GCUnsafeCall("Kotlin_NativeCharArrayImpl_copy")
private external fun nativeArrayCopy(array: COpaquePointer, size: Int, fromIndex: Int, destination: COpaquePointer, destinationSize: Int, toIndex: Int, count: Int)

@OptIn(ExperimentalForeignApi::class)
internal actual fun NativeCharArray.copyInto(destination: NativeCharArray, destinationOffset: Int, startIndex: Int, endIndex: Int): NativeCharArray {
    when (this) {
        is CharArrayWrapper -> value.copyInto(destination, destinationOffset, startIndex, endIndex)
        is NativeCharArrayImpl -> {
            when (destination) {
                is CharArrayWrapper -> nativeArrayImplCopyToCharArray(this.nativeHolder, this.size, startIndex, destination.value, destinationOffset, endIndex - startIndex)
                is NativeCharArrayImpl -> nativeArrayCopy(this.nativeHolder, this.size, startIndex, destination.nativeHolder, destination.size, destinationOffset, endIndex - startIndex)
            }
        }
    }
    return destination
}

@GCUnsafeCall("Kotlin_NativeCharArrayImpl_fill")
private external fun nativeArrayImplFill(array: COpaquePointer, size: Int, fromIndex: Int, toIndex: Int, value: Char)

@OptIn(ExperimentalForeignApi::class)
internal actual fun NativeCharArray.fill(element: Char, fromIndex: Int, toIndex: Int) {
    when (this) {
        is CharArrayWrapper -> arrayFill(this.value, fromIndex, toIndex, element)
        is NativeCharArrayImpl -> nativeArrayImplFill(this.nativeHolder, this.size, fromIndex, toIndex, element)
    }
}

@GCUnsafeCall("Kotlin_NativeCharArrayImpl_resizeTo")
private external fun nativeArrayImplResizeTo(array: COpaquePointer, size: Int, newSize: Int)

@OptIn(ExperimentalForeignApi::class)
internal actual fun NativeCharArray.resizeTo(newSize: Int): NativeCharArray {
    return when (this) {
        is CharArrayWrapper -> copyOf(newSize)
        is NativeCharArrayImpl -> {
            if (newSize < 0) {
                throw IllegalArgumentException("$newSize < 0")
            }
            if (newSize != this.size) {
                nativeArrayImplResizeTo(this.nativeHolder, this.size, newSize)
                this.size = newSize
            }
            this
        }
    }
}