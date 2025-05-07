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

/**
 * Created by benny.
 */
internal expect sealed interface NativeCharArray {

    public val size: Int

    public operator fun get(index: Int): Char

    public fun iterator(): CharIterator

    public operator fun set(index: Int, value: Char)
    
}

internal expect fun NativeCharArray(size: Int): NativeCharArray

internal expect fun CharArray.copyInto(destination: NativeCharArray, destinationOffset: Int = 0, startIndex: Int = 0, endIndex: Int = size): NativeCharArray

internal expect fun NativeCharArray.copyInto(destination: CharArray, destinationOffset: Int = 0, startIndex: Int = 0, endIndex: Int = size): CharArray

internal expect fun NativeCharArray.copyInto(destination: NativeCharArray, destinationOffset: Int = 0, startIndex: Int = 0, endIndex: Int = size): NativeCharArray

internal expect fun NativeCharArray.fill(element: Char, fromIndex: Int = 0, toIndex: Int = size)

internal fun NativeCharArray.copyOf(newSize: Int): NativeCharArray {
    if (newSize < 0) {
        throw IllegalArgumentException("$newSize < 0")
    }
    val result = NativeCharArray(newSize)
    this.copyInto(result, 0, 0, newSize.coerceAtMost(size))
    return result
}

internal expect fun NativeCharArray.resizeTo(newSize: Int): NativeCharArray

internal expect fun String.toNativeCharArray(): NativeCharArray

internal expect fun unsafeStringFromNativeCharArray(array: NativeCharArray, start: Int, size: Int) : String 