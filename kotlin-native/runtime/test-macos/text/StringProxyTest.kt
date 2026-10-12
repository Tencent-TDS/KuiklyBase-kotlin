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

import platform.Foundation.NSMakeRange
import platform.Foundation.NSString
import platform.Foundation.stringWithString
import platform.Foundation.substringWithRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
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

    private fun String.asProxy(): String {
        return withStringProxyFromNSString { NSString.stringWithString(this) }
    }

    private inline fun doTests(a: String, block: (String) -> Unit) {
        block(a)
        block(a.asProxy())
    }

    private inline fun doTests(a: String, b: String, block: (String, String) -> Unit) {
        block(a.asProxy(), b)
        block(a, b.asProxy())
        block(a.asProxy(), b.asProxy())
    }

    private inline fun <T> doTestsWithResult(a: String, b: String, block: (String, String) -> T) {
        val expect = block(a, b)
        assertEquals(expect, block(a.asProxy(), b))
        assertEquals(expect, block(a, b.asProxy()))
        assertEquals(expect, block(a.asProxy(), b.asProxy()))
    }

    @Test
    fun lowercase() {
        // Non-ASCII
        doTests("\u214E\uA7B5\u2CEF", "\u2132\uA7B4\u2CEF") { a, b -> assertEquals(a, b.lowercase()) }

        // Surrogate pairs
        doTests("\uD801\uDC4F\uD806\uDCD0\uD81B\uDE7F", "\uD801\uDC27\uD806\uDCB0\uD81B\uDE5F") { a, b -> assertEquals(a, b.lowercase()) }
        doTests("\ud801\udc28", "\ud801\udc28") { a, b -> assertEquals(a, b.lowercase()) }
        doTests("\ud801\udc28", "\ud801\udc00") { a, b -> assertEquals(a, b.lowercase()) }

        // Special Casing
        // LATIN CAPITAL LETTER I WITH DOT ABOVE
        doTests("\u0069\u0307", "\u0130") { a, b -> assertEquals(a, b.lowercase()) }
        doTests("a\u0069\u0307z", "a\u0130Z") { a, b -> assertEquals(a, b.lowercase()) }
    }

    @Test
    fun uppercase() {
        // Non-ASCII
        doTests("\u00DE\u03A9\u0403\uA779", "\u00FE\u03A9\u0453\uA77A".uppercase()) { a, b -> assertEquals(a, b.uppercase()) }

        // Surrogate pairs
        doTests("\ud801\udc00", "\ud801\udc28".uppercase()) { a, b -> assertEquals(a, b.uppercase()) }
        doTests("\ud801\udc00", "\ud801\udc00".uppercase()) { a, b -> assertEquals(a, b.uppercase()) }
    }

    @Test
    fun capitalize() {
        // Non-ASCII
        doTests("\u00DE\u03A9\u0453\uA77A", "\u00FE\u03A9\u0453\uA77A") { a, b -> assertEquals(a, b.capitalize()) }
    }

    @Test
    fun indexOfString() {
        doTests("bceded", "ced") { a, b -> assertEquals(1, a.indexOf(b, -1)) }
        doTests("bceded", "e") { a, b -> assertEquals(-1, a.indexOf(b, 7)) }
        doTests("bceded", "e") { a, b -> assertEquals(-1, a.indexOf(b, Int.MAX_VALUE)) }
        doTests("bceded", "") { a, b -> assertEquals(6, a.indexOf(b, Int.MAX_VALUE)) }
        doTests("", "a") { a, b -> assertEquals(-1, a.indexOf(b, -3)) }
        doTests("", "") { a, b -> assertEquals(0, a.indexOf(b, 0)) }
        doTests("Hello! \uD83D\uDE01\uD83D\uDE01\uD83D\uDE01", "\uD83D\uDE01") { a, b -> assertEquals(7, a.indexOf(b, 5)) }
    }

    @Test
    fun indexOfChar() {
        doTests("bcedef") { assertEquals(-1, it.indexOf('e', 5)) }

        doTests("") { assertEquals(-1, it.indexOf('a', -3)) }
        doTests("") { assertEquals(-1, it.indexOf('a', 10)) }

        doTests("") { assertEquals(-1, it.indexOf(0.toChar(), -3)) }
        doTests("") { assertEquals(-1, it.indexOf(0.toChar(), 10)) }
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
    fun equalsIgnoreCase() {
        doTestsWithResult("hello", "HElLo") { a, b -> a.equals(b, true) }
        doTestsWithResult("Привет", "прИВет") { a, b -> a.equals(b, true) }
    }

    @Test
    fun trim() {
        doTests("String", "\u0020 \u202FString\u2028\u2029") { a, b -> assertEquals(a, b.trim()) }
        doTests("\u1FFFString", "\u00A0  \u1FFFString") { a, b -> assertEquals(a, b.trim()) }
    }

    @Test
    fun subSequence() {
        doTests("ello", "Hello world") { a, b -> assertEquals(a, b.subSequence(1, 5).toString()) }
        doTests("", "Hello world") { a, b -> assertEquals(a, b.subSequence(1, 1).toString()) }
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

    @Test
    fun parsingTest() {
        val strings = arrayOf(
                "true",
                "false",
                "0",
                "-0",
                "123",
                "-456",
                "3.14159",
                "-0.001",
                "1e10",
                "-2.5E-4",
                "0000123",
                "+42",
                "9223372036854775807",
                "",
                " ",
                "abc",
                "12abc",
                "1.2.3",
                "--5",
                "NaN",
                "Infinity",
                "0xFF",
                "999999999999999999999999999",
        )

        strings.forEach { s ->
            assertEquals(s.toBoolean(), s.asProxy().toBoolean())
            assertEquals(s.toBooleanStrictOrNull(), s.asProxy().toBooleanStrictOrNull())
            assertEquals(s.toShortOrNull(), s.asProxy().toShortOrNull())
            assertEquals(s.toIntOrNull(), s.asProxy().toIntOrNull())
            assertEquals(s.toFloatOrNull(), s.asProxy().toFloatOrNull())
            assertEquals(s.toDoubleOrNull(), s.asProxy().toDoubleOrNull())
        }
    }

    @Test
    fun nsStringFromKStringProxyTest() = withNSStringFromKStringProxy {
        val checkReverseProxy = { original: String ->
            // Create a non-permanent KString on heap so CreateRetainedNSStringFromKString uses NSStringFromKString
            val dynamicKString = original.toCharArray().concatToString()
            val nsString = (dynamicKString as Any) as NSString

            assertEquals(dynamicKString.length.toULong(), nsString.length)
            for (i in dynamicKString.indices) {
                assertEquals(dynamicKString[i].code.toUShort(), nsString.characterAtIndex(i.toULong()))
            }
            if (dynamicKString.length >= 2) {
                val sub = nsString.substringWithRange(NSMakeRange(1u, (dynamicKString.length - 2).toULong()))
                assertEquals(dynamicKString.substring(1, dynamicKString.length - 1), sub)
            }
            val roundTrip = (nsString as Any) as String
            assertSame(dynamicKString, roundTrip)
            assertEquals(dynamicKString, roundTrip)
            assertEquals(dynamicKString, NSString.stringWithString(dynamicKString))
        }

        checkReverseProxy(smallString)
        checkReverseProxy(asciiString)
        checkReverseProxy("Latin1 Extended: \u00E9\u00FC\u00F1")
        checkReverseProxy(utf16String)
        checkReverseProxy(emojiString)
    }
}
