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

import platform.Foundation.NSString
import platform.Foundation.stringWithString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StringProxyTest {
    
    private val smallString = "Hello"
    private val smallStringProxy = smallString.asProxy()
    
    private val asciiString = "Hello World!!!!"
    private val asciiStringProxy = asciiString.asProxy()

    private val utf16String = "你好！Kotlin！"
    private val utf16StringProxy = utf16String.asProxy()
    
    private val emojiString = "Have a nice Kotlin! 🤣😁😜"
    private val emojiStringProxy = emojiString.asProxy()


    private val stringTests = StringNativeTest()

    private fun String.asProxy(): String {
        return withStringProxyFromNSString { NSString.stringWithString(this) }
    }

    private inline fun doTests(a: String, b: String, block: (String, String) -> Unit) {
        block(a.asProxy(), b)
        block(a, b.asProxy())
        block(a.asProxy(), b.asProxy())
    }

    private inline fun <T> doTestsWithResult(a: String, b: String, block: (String, String) -> T) {
        val expect = block(a, b)
        println("doTestsWithResult($a, $b), expect=$expect")
        assertEquals(expect, block(a.asProxy(), b))
        assertEquals(expect, block(a, b.asProxy()))
        assertEquals(expect, block(a.asProxy(), b.asProxy()))
    }

    @Test
    fun lowercase() = withStringProxyGlobally {
        stringTests.lowercase()
    }

    @Test
    fun uppercase() = withStringProxyGlobally {
        stringTests.uppercase()
    }

    @Test
    fun capitalize() = withStringProxyGlobally {
        stringTests.capitalize()
    }

    @Test
    fun indexOfString() = withStringProxyGlobally {
        stringTests.indexOfString()

        assertEquals(7, "Hello! \uD83D\uDE01\uD83D\uDE01\uD83D\uDE01".asProxy().indexOf("\uD83D\uDE01", 5))
    }

    @Test
    fun indexOfChar() = withStringProxyGlobally {
        stringTests.indexOfChar()
    }

    @Test
    fun indexOfChar2() {
        assertEquals(2, "bcedef".asProxy().indexOf('e'))
        assertEquals(2, "bcedef".asProxy().indexOf('e', 1))
        assertEquals(2, "bcedef".asProxy().indexOf('e', 2))
        assertEquals(4, "bcedef".asProxy().indexOf('e', 3))
        assertEquals(4, "bcedef".asProxy().indexOf('e', 4))
        assertEquals(-1, "bcedef".asProxy().indexOf('e', 5))

        assertEquals(-1, "".asProxy().indexOf('a', -3))
        assertEquals(-1, "".asProxy().indexOf('a', 10))

        assertEquals(-1, "".asProxy().indexOf(0.toChar(), -3))
        assertEquals(-1, "".asProxy().indexOf(0.toChar(), 10))
    }

    @Test
    fun equalsIgnoreCase() = withStringProxyGlobally {
        stringTests.equalsIgnoreCase()
    }

    @Test
    fun trim() = withStringProxyGlobally {
        stringTests.trim()
    }

    @Test
    fun subSequence() = withStringProxyGlobally {
        stringTests.subSequence()
    }

    @Test
    fun concatKStringAndString() = withStringProxyFromNSString {
        assertEquals("Hello world", "Hello ".asProxy() + "world")
        assertEquals("Hello world", "Hello " + "world".asProxy())
        assertEquals("world", "".asProxy() + "world")
        assertEquals("Hello", "Hello".asProxy() + "")
    }

    @Test
    fun indexOfStringProxy() = withStringProxyFromNSString {
        doTests("bceded", "ced") { a, b -> assertEquals(1, a.indexOf(b, -1)) }

        doTests("bceded", "e") { a, b -> assertEquals(-1, a.indexOf(b, 7)) }
        doTests("bceded", "e") { a, b -> assertEquals(-1, a.indexOf(b, Int.MAX_VALUE)) }
        doTests("bceded", "") { a, b -> assertEquals(6, a.indexOf(b, Int.MAX_VALUE)) }

        doTests("", "a") { a, b -> assertEquals(-1, a.indexOf(b, -3)) }
        doTests("", "") { a, b -> assertEquals(0, a.indexOf(b, 0)) }
    }

    @Test
    fun lastIndexOfStringProxy() {
        val string = "bceded"
        repeat(string.length) { i ->
            doTestsWithResult(string, "ced") { a, b ->
                a.lastIndexOf(b, i) 
            }
        }
    }

    @Test
    fun indexOfCharFromStringProxy() = withStringProxyFromNSString {
        assertEquals(-1, "bcedef".asProxy().indexOf('e', 5))

        assertEquals(-1, "".asProxy().indexOf('a', -3))
        assertEquals(-1, "".asProxy().indexOf('a', 10))

        assertEquals(-1, "".asProxy().indexOf(0.toChar(), -3))
        assertEquals(-1, "".asProxy().indexOf(0.toChar(), 10))
    }

    @Test
    fun hashCodeTest() {
        val doTest = { s: String ->
            assertEquals(s.asProxy().hashCode(), s.hashCode())
        }

        doTest(smallString)
        doTest(asciiString)
        doTest(utf16String)
        doTest(emojiString)
    }

    @Test
    fun encodeToByteArrayTest() {
        val doTest = { s: String ->
            assertTrue(s.asProxy().encodeToByteArray().contentEquals(s.encodeToByteArray()))
            assertTrue(s.asProxy().encodeToByteArray(s.length / 2).contentEquals(s.encodeToByteArray(s.length / 2)))
            assertTrue(s.asProxy().encodeToByteArray(s.length / 4, s.length / 2).contentEquals(s.encodeToByteArray(s.length / 4, s.length / 2)))
        }

        doTest(smallString)
        doTest(asciiString)
        doTest(utf16String)
        doTest(emojiString)
    }
}
