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

package test.text

import kotlin.test.*

// Native-specific part of stdlib/test/text/StringBuilderTest.kt
class StringBuilderNativeCharArrayTest {

    private val stringBuilderTests = StringBuilderNativeTest()
    
    @Test
    fun insertCharSequence() = withNativeCharArray {
        stringBuilderTests.insertCharSequence()
    }

    @Test
    fun insertString() = withNativeCharArray {
        stringBuilderTests.insertString()
    }

    @Test
    fun insertByte() = withNativeCharArray {
        stringBuilderTests.insertByte()
    }

    @Test
    fun insertShort() = withNativeCharArray {
        stringBuilderTests.insertShort()
    }

    @Test
    fun insertInt() = withNativeCharArray {
        stringBuilderTests.insertInt()
    }

    @Test
    fun insertLong() = withNativeCharArray {
        stringBuilderTests.insertLong()
    }

    @Test
    fun insertFloat() = withNativeCharArray {
        stringBuilderTests.insertFloat()
    }

    @Test
    fun insertDouble() = withNativeCharArray {
        stringBuilderTests.insertDouble()
    }

    @Test
    fun testReverse() = withNativeCharArray {
        stringBuilderTests.testReverse()
    }

    @Test
    fun testDoubleReverse() = withNativeCharArray {
        stringBuilderTests.testDoubleReverse()
    }

    @Test
    fun appendLong() = withNativeCharArray {
        stringBuilderTests.appendLong()
    }

    @Test
    fun appendNullCharSequence() = withNativeCharArray {
        stringBuilderTests.appendNullCharSequence()
    }

    @Test
    fun appendLine() = withNativeCharArray {
        stringBuilderTests.appendLine()
    }
}