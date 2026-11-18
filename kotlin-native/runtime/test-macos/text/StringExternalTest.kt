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

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.NativeFreeCallback
import kotlinx.cinterop.ShortVar
import kotlinx.cinterop.UShortVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.free
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.pin
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.toKStringFromOwnedUtf16
import kotlinx.cinterop.toKStringFromOwnedUtf8
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.Foundation.NSString
import platform.Foundation.stringWithString
import platform.posix.O_WRONLY
import platform.posix.close
import platform.posix.free
import platform.posix.malloc
import platform.posix.open
import kotlin.native.runtime.Debugging
import kotlin.native.runtime.GC
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private val customHeapFreeCallback: NativeFreeCallback = staticCFunction { ptr, ctx ->
    if (ctx != null) {
        val counter = ctx.reinterpret<IntVar>()
        counter.pointed.value += 1
    }
    if (ptr != null) {
        nativeHeap.free(ptr)
    }
}

private val customMallocFreeCallback: NativeFreeCallback = staticCFunction { ptr, ctx ->
    if (ctx != null) {
        val counter = ctx.reinterpret<IntVar>()
        counter.pointed.value += 1
    }
    if (ptr != null) {
        free(ptr)
    }
}

class StringExternalTest {

    private val smallString = "Hello"
    private val asciiString = "Hello World!!!!"
    private val utf16String = "你好！Kotlin！"
    private val emojiString = "Have a nice Kotlin! 🤣😁😜"

    private fun String.asOwnedUtf8(nullTerminated: Boolean = false): String {
        val bytes = encodeToByteArray()
        val allocSize = (bytes.size + if (nullTerminated) 1 else 1).coerceAtLeast(1).toULong()
        val buf = malloc(allocSize)!!.reinterpret<ByteVar>()
        bytes.forEachIndexed { index, byte ->
            buf[index] = byte
        }
        return if (nullTerminated) {
            buf[bytes.size] = 0
            buf.toKStringFromOwnedUtf8()
        } else {
            buf.toKStringFromOwnedUtf8(bytes.size)
        }
    }

    private fun String.asOwnedUtf16(nullTerminated: Boolean = false): String {
        val allocUnits = (length + if (nullTerminated) 1 else 1).coerceAtLeast(1)
        val buf = malloc((allocUnits * 2).toULong())!!.reinterpret<UShortVar>()
        for (i in indices) {
            buf[i] = this[i].code.toUShort()
        }
        return if (nullTerminated) {
            buf[length] = 0u
            buf.toKStringFromOwnedUtf16()
        } else {
            buf.toKStringFromOwnedUtf16(length)
        }
    }

    private fun String.variants(): List<String> = listOf(
        this,
        asOwnedUtf8(nullTerminated = false),
        asOwnedUtf8(nullTerminated = true),
        asOwnedUtf16(nullTerminated = false),
        asOwnedUtf16(nullTerminated = true),
    )

    private inline fun doTests(a: String, block: (String) -> Unit) {
        for (va in a.variants()) {
            block(va)
        }
    }

    private inline fun doTests(a: String, b: String, block: (String, String) -> Unit) {
        val variantsA = a.variants()
        val variantsB = b.variants()
        for (va in variantsA) {
            for (vb in variantsB) {
                block(va, vb)
            }
        }
    }

    private inline fun <T> doTestsWithResult(a: String, b: String, block: (String, String) -> T) {
        val expect = block(a, b)
        doTests(a, b) { va, vb ->
            assertEquals(expect, block(va, vb))
        }
    }

    @Test
    fun lowercase() {
        doTests("\u214E\uA7B5\u2CEF", "\u2132\uA7B4\u2CEF") { a, b -> assertEquals(a, b.lowercase()) }
        doTests(
            "\uD801\uDC4F\uD806\uDCD0\uD81B\uDE7F",
            "\uD801\uDC27\uD806\uDCB0\uD81B\uDE5F"
        ) { a, b -> assertEquals(a, b.lowercase()) }
        doTests("\ud801\udc28", "\ud801\udc28") { a, b -> assertEquals(a, b.lowercase()) }
        doTests("\ud801\udc28", "\ud801\udc00") { a, b -> assertEquals(a, b.lowercase()) }
        doTests("\u0069\u0307", "\u0130") { a, b -> assertEquals(a, b.lowercase()) }
        doTests("a\u0069\u0307z", "a\u0130Z") { a, b -> assertEquals(a, b.lowercase()) }
    }

    @Test
    fun uppercase() {
        doTests("\u00DE\u03A9\u0403\uA779", "\u00FE\u03A9\u0453\uA77A".uppercase()) { a, b ->
            assertEquals(a, b.uppercase())
        }
        doTests("\ud801\udc00", "\ud801\udc28".uppercase()) { a, b -> assertEquals(a, b.uppercase()) }
        doTests("\ud801\udc00", "\ud801\udc00".uppercase()) { a, b -> assertEquals(a, b.uppercase()) }
    }

    @Test
    fun capitalize() {
        doTests("\u00DE\u03A9\u0453\uA77A", "\u00FE\u03A9\u0453\uA77A") { a, b ->
            @Suppress("DEPRECATION")
            assertEquals(a, b.capitalize())
        }
    }

    @Test
    fun indexOfString() {
        doTests("bceded", "ced") { a, b -> assertEquals(1, a.indexOf(b, -1)) }
        doTests("bceded", "e") { a, b -> assertEquals(-1, a.indexOf(b, 7)) }
        doTests("bceded", "e") { a, b -> assertEquals(-1, a.indexOf(b, Int.MAX_VALUE)) }
        doTests("bceded", "") { a, b -> assertEquals(6, a.indexOf(b, Int.MAX_VALUE)) }
        doTests("", "a") { a, b -> assertEquals(-1, a.indexOf(b, -3)) }
        doTests("", "") { a, b -> assertEquals(0, a.indexOf(b, 0)) }
        doTests("Hello! \uD83D\uDE01\uD83D\uDE01\uD83D\uDE01", "\uD83D\uDE01") { a, b ->
            assertEquals(7, a.indexOf(b, 5))
        }
    }

    @Test
    fun indexOfChar() {
        doTests("bcedef") {
            assertEquals(2, it.indexOf('e'))
            assertEquals(2, it.indexOf('e', 1))
            assertEquals(2, it.indexOf('e', 2))
            assertEquals(4, it.indexOf('e', 3))
            assertEquals(4, it.indexOf('e', 4))
            assertEquals(-1, it.indexOf('e', 5))
        }
        doTests("") {
            assertEquals(-1, it.indexOf('a', -3))
            assertEquals(-1, it.indexOf('a', 10))
            assertEquals(-1, it.indexOf(0.toChar(), -3))
            assertEquals(-1, it.indexOf(0.toChar(), 10))
        }
    }

    @Test
    fun equalsAndCompareToCrossEncoding() {
        for (s in listOf("", smallString, asciiString, utf16String, emojiString)) {
            val vs = s.variants()
            for (a in vs) {
                for (b in vs) {
                    assertEquals(a, b)
                    assertEquals(0, a.compareTo(b))
                    assertEquals(s.hashCode(), a.hashCode())
                }
            }
        }
        doTestsWithResult("hello", "HElLo") { a, b -> a.equals(b, true) }
        doTestsWithResult("Привет", "прИВет") { a, b -> a.equals(b, true) }
        doTestsWithResult("abc", "abd") { a, b -> a.compareTo(b) }
    }

    @Test
    fun trimAndSubSequenceAndConcat() = withStringProxyFromNSString {
        doTests("String", "\u0020 \u202FString\u2028\u2029") { a, b -> assertEquals(a, b.trim()) }
        doTests("\u1FFFString", "\u00A0  \u1FFFString") { a, b -> assertEquals(a, b.trim()) }
        doTests("ello", "Hello world") { a, b -> assertEquals(a, b.subSequence(1, 5).toString()) }
        doTests("", "Hello world") { a, b -> assertEquals(a, b.subSequence(1, 1).toString()) }

        assertEquals("Hello world", "Hello ".asOwnedUtf8() + "world".asOwnedUtf16())
        assertEquals("Hello world", "Hello ".asOwnedUtf16() + "world".asOwnedUtf8())
        assertEquals("world", "".asOwnedUtf8() + "world")
        assertEquals("Hello", "Hello".asOwnedUtf16() + "")
    }

    @Test
    fun lastIndexOfTest() {
        val string = "bceded"
        repeat(string.length) { i ->
            doTestsWithResult(string, "ced") { a, b ->
                a.lastIndexOf(b, i)
            }
        }
    }

    @Test
    fun encodeToByteArrayAndParsingTest() {
        for (s in listOf(smallString, asciiString, utf16String, emojiString)) {
            doTests(s) { ext ->
                assertTrue(ext.encodeToByteArray().contentEquals(s.encodeToByteArray()))
                assertTrue(ext.encodeToByteArray(s.length / 2).contentEquals(s.encodeToByteArray(s.length / 2)))
                assertTrue(
                    ext.encodeToByteArray(s.length / 4, s.length / 2)
                        .contentEquals(s.encodeToByteArray(s.length / 4, s.length / 2))
                )
            }
        }

        val numbers = arrayOf(
            "true", "false", "0", "-0", "123", "-456", "3.14159", "-0.001",
            "1e10", "-2.5E-4", "0000123", "+42", "9223372036854775807", "",
            " ", "abc", "12abc", "1.2.3", "--5", "NaN", "Infinity", "0xFF",
            "999999999999999999999999999",
        )
        numbers.forEach { s ->
            doTests(s) { ext ->
                assertEquals(s.toBoolean(), ext.toBoolean())
                assertEquals(s.toBooleanStrictOrNull(), ext.toBooleanStrictOrNull())
                assertEquals(s.toShortOrNull(), ext.toShortOrNull())
                assertEquals(s.toIntOrNull(), ext.toIntOrNull())
                assertEquals(s.toFloatOrNull(), ext.toFloatOrNull())
                assertEquals(s.toDoubleOrNull(), ext.toDoubleOrNull())
            }
        }
    }

    @Test
    fun pinZeroCopyVerificationTest() {
        val latin1Owned = "Hello".asOwnedUtf8()
        assertEquals("Hello", latin1Owned.pin().get())
        assertFalse(latin1Owned.pin().get() === latin1Owned)

        // Owned UTF-16 is already UTF-16 encoded: pin() and addressOf(0) are 100% zero-copy!
        for (sample in listOf(asciiString, utf16String, emojiString)) {
            val buf = malloc((sample.length * 2).toULong())!!.reinterpret<UShortVar>()
            for (i in sample.indices) {
                buf[i] = sample[i].code.toUShort()
            }
            val u16Owned = buf.toKStringFromOwnedUtf16(sample.length)
            assertTrue(u16Owned.pin().get() === u16Owned)
            u16Owned.usePinned { pinned ->
                assertEquals(buf.rawValue, pinned.addressOf(0).rawValue)
            }
            assertEquals(sample, u16Owned)
        }
    }

    private fun allocateAndDropOwnedStrings(counterPtr: COpaquePointer) {
        // 1. ASCII UTF-8 (zero-copy -> freed on GC)
        val asciiBytes = "OwnedAsciiWithCustomFree".encodeToByteArray()
        val asciiBuf = nativeHeap.allocArray<ByteVar>(asciiBytes.size + 1)
        asciiBytes.forEachIndexed { i, b -> asciiBuf[i] = b }
        asciiBuf[asciiBytes.size] = 0
        val s1 = asciiBuf.toKStringFromOwnedUtf8(freeCallback = customHeapFreeCallback, freeContext = counterPtr)
        assertEquals("OwnedAsciiWithCustomFree", s1)

        // 2. UTF-16 with CJK + Emoji (zero-copy -> freed on GC)
        val u16Sample = "鸿蒙与iOS双向零拷贝 🎉"
        val u16Buf = nativeHeap.allocArray<UShortVar>(u16Sample.length)
        for (i in u16Sample.indices) {
            u16Buf[i] = u16Sample[i].code.toUShort()
        }
        val s2 = u16Buf.toKStringFromOwnedUtf16(
            length = u16Sample.length,
            freeCallback = customHeapFreeCallback,
            freeContext = counterPtr,
        )
        assertEquals(u16Sample, s2)

        // 3. ShortVar receiver overload for UTF-16
        val shortBuf = nativeHeap.allocArray<ShortVar>(u16Sample.length + 1)
        for (i in u16Sample.indices) {
            shortBuf[i] = u16Sample[i].code.toShort()
        }
        shortBuf[u16Sample.length] = 0
        val s3 = shortBuf.toKStringFromOwnedUtf16(
            freeCallback = customHeapFreeCallback,
            freeContext = counterPtr,
        )
        assertEquals(u16Sample, s3)
    }

    @Test
    fun customFreeCallbackAndContextTest() = memScoped {
        val counter = alloc<IntVar>()
        counter.value = 0

        // Immediate free cases: non-ASCII UTF-8 fallback & empty strings
        val nonAsciiBytes = utf16String.encodeToByteArray()
        val nonAsciiBuf = malloc(nonAsciiBytes.size.toULong())!!.reinterpret<ByteVar>()
        nonAsciiBytes.forEachIndexed { i, b -> nonAsciiBuf[i] = b }
        val decoded = nonAsciiBuf.toKStringFromOwnedUtf8(
            length = nonAsciiBytes.size,
            freeCallback = customMallocFreeCallback,
            freeContext = counter.ptr,
        )
        assertEquals(utf16String, decoded)
        assertEquals(1, counter.value)

        val emptyUtf8Buf = malloc(1u)!!.reinterpret<ByteVar>()
        emptyUtf8Buf[0] = 0
        assertEquals("", emptyUtf8Buf.toKStringFromOwnedUtf8(freeCallback = customMallocFreeCallback, freeContext = counter.ptr))
        assertEquals(2, counter.value)

        val emptyUtf16Buf = malloc(2u)!!.reinterpret<UShortVar>()
        emptyUtf16Buf[0] = 0u
        assertEquals("", emptyUtf16Buf.toKStringFromOwnedUtf16(freeCallback = customMallocFreeCallback, freeContext = counter.ptr))
        assertEquals(3, counter.value)

        // Deferred GC finalization cases (3 zero-copy strings)
        allocateAndDropOwnedStrings(counter.ptr)
        repeat(3) {
            GC.collect()
        }
        assertEquals(6, counter.value)
    }

    @Test
    fun nullFreeCallbackAndNSStringInteropTest() = memScoped {
        val scopedAscii = allocArray<ByteVar>(6)
        "Hello".encodeToByteArray().forEachIndexed { i, b -> scopedAscii[i] = b }
        scopedAscii[5] = 0
        val zeroCopyStaticUtf8 = scopedAscii.toKStringFromOwnedUtf8(length = 5, freeCallback = null)
        assertEquals("Hello", zeroCopyStaticUtf8)

        val scopedUtf16 = allocArray<UShortVar>(emojiString.length)
        for (i in emojiString.indices) {
            scopedUtf16[i] = emojiString[i].code.toUShort()
        }
        val zeroCopyStaticUtf16 = scopedUtf16.toKStringFromOwnedUtf16(length = emojiString.length, freeCallback = null)
        assertEquals(emojiString, zeroCopyStaticUtf16)

        // Verify NSString bridging on Apple shares NSStringWithExternalStorage seamlessly
        val ns1 = (zeroCopyStaticUtf8 as Any) as NSString
        val ns2 = (zeroCopyStaticUtf16 as Any) as NSString
        assertEquals("Hello", NSString.stringWithString(ns1 as String))
        assertEquals(emojiString, NSString.stringWithString(ns2 as String))
    }

    @Test
    fun memoryDumpWithExternalStringsTest() {
        val oddLatin1 = "Hello".asOwnedUtf8()
        val evenLatin1 = "Hello!".asOwnedUtf8()
        val utf16Ext = emojiString.asOwnedUtf16()
        val fd = open("/dev/null", O_WRONLY)
        assertTrue(fd >= 0)
        try {
            assertTrue(Debugging.dumpMemory(fd.toLong()))
        } finally {
            close(fd)
        }
        assertEquals("Hello", oddLatin1)
        assertEquals("Hello!", evenLatin1)
        assertEquals(emojiString, utf16Ext)
    }
}
