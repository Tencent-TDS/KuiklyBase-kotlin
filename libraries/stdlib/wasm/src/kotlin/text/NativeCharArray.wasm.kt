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

package kotlin.text

import kotlin.wasm.internal.WasmCharArray

internal actual sealed interface NativeCharArray {
    actual val size: Int
    val value: CharArray
    val storage: WasmCharArray
    actual operator fun get(index: Int): Char
    actual fun iterator(): CharIterator
    actual operator fun set(index: Int, value: Char)
}

private value class CharArrayWrapper(override val value: CharArray) : NativeCharArray {
    override val size: Int
        get() = value.size
    override val storage: WasmCharArray
        get() = value.storage

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

internal actual fun NativeCharArray(size: Int): NativeCharArray {
    return CharArrayWrapper(CharArray(size))
}

internal actual fun CharArray.copyInto(destination: NativeCharArray, destinationOffset: Int, startIndex: Int, endIndex: Int): NativeCharArray {
    copyInto(destination.value, destinationOffset, startIndex, endIndex)
    return destination
}

internal actual fun NativeCharArray.copyInto(destination: CharArray, destinationOffset: Int, startIndex: Int, endIndex: Int): CharArray {
    return value.copyInto(destination, destinationOffset, startIndex, endIndex)
}

internal actual fun NativeCharArray.copyInto(destination: NativeCharArray, destinationOffset: Int, startIndex: Int, endIndex: Int): NativeCharArray {
    value.copyInto(destination.value, destinationOffset, startIndex, endIndex)
    return destination
}

internal actual fun NativeCharArray.fill(element: Char, fromIndex: Int, toIndex: Int) {
    value.fill(element, fromIndex, toIndex)
}

internal actual fun NativeCharArray.resizeTo(newSize: Int): NativeCharArray {
    if (newSize < 0) {
        throw IllegalArgumentException("$newSize < 0")
    }
    val result = NativeCharArray(newSize)
    this.copyInto(result, 0, 0, newSize.coerceAtMost(size))
    return result
}

internal actual fun String.toNativeCharArray(): NativeCharArray = CharArrayWrapper(toCharArray())

internal actual fun unsafeStringFromNativeCharArray(array: NativeCharArray, start: Int, size: Int): String {
    return unsafeStringFromCharArray(array, start, size)
}