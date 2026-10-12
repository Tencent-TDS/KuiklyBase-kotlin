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

package test.reflect

import platform.Foundation.NSArray
import platform.Foundation.NSCopyingProtocol
import platform.Foundation.NSDictionary
import platform.Foundation.NSString
import platform.darwin.NSInteger
import platform.darwin.NSObject
import platform.darwin.NSObjectProtocol
import platform.darwin.NSUInteger
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals

class KClassTest {

    class KotlinNSArray : NSArray() {
        override fun count(): NSUInteger {
            return 0U
        }
    }

    @Test
    fun instance() {
        val lambda = { 42 }
        listOf<Triple<Any, KClass<*>, Boolean>>(
                Triple("Hello", String::class, true),
                Triple("Hello", NSString::class, false),
                Triple("Hello", Any::class, true),
                Triple("Hello", Comparable::class, true),
                Triple(1, Int::class, true),
                Triple(1, Long::class, false),
                Triple(1L, Int::class, false),
                Triple(1L, Long::class, true),
                Triple(1.0, Double::class, true),
                Triple(1f, Number::class, true),

                Triple(lambda, Function0::class, true),
                Triple(lambda, Function::class, true),
                Triple(lambda, Function1::class, false),
                Triple("Hello", Function0::class, false),

                // NSArray() will be compiled into NSArrayAsKList
                Triple(NSArray(), NSArray::class, false),
                Triple(NSArray(), NSCopyingProtocol::class, false),
                Triple(NSArray(), NSObject::class, false),
                Triple(NSArray(), Any::class, true),
                Triple(NSArray(), NSDictionary::class, false),
                Triple(NSArray(), List::class, true),

                // NSDictionary() will be compiled into NSDictionaryAsKMap
                Triple(NSDictionary(), NSArray::class, false),
                Triple(NSDictionary(), NSDictionary::class, false),
                Triple(NSDictionary(), Map::class, true),

                Triple(NSObject(), NSString::class, false),
                Triple(NSObject(), NSObjectProtocol::class, true),
                Triple(NSObject(), Any::class, true),

                Triple(KotlinNSArray(), Any::class, true),
                Triple(KotlinNSArray(), KotlinNSArray::class, true),
                Triple(KotlinNSArray(), NSArray::class, true),
                Triple(KotlinNSArray(), NSObject::class, true),
                Triple(KotlinNSArray(), Comparable::class, false),

                Triple(Any(), NSObject::class, false),
        ).forEach { (obj, cls, result) ->
            assertEquals(cls.isInstance(obj), result, "$cls.isInstance($obj) != $result")
        }
    }

    @Test
    fun inheritance() {
        val lambda = { 42 }
        listOf<Triple<KClass<*>, KClass<*>, Boolean>>(
                Triple(String::class, String::class, true),
                Triple(String::class, NSString::class, false),
                Triple(String::class, Any::class, true),
                Triple(String::class, Comparable::class, true),
                Triple(Int::class, Int::class, true),
                Triple(Int::class, NSInteger::class, false),
                Triple(Long::class, NSInteger::class, true),
                Triple(Float::class, Number::class, true),
                Triple(1::class, Number::class, true),

                Triple(lambda::class, Function0::class, true),
                Triple(lambda::class, Function::class, true),
                Triple(lambda::class, Function1::class, false),
                Triple(String::class, Function0::class, false),

                Triple(NSArray::class, NSArray::class, true),

                // NSArray() will be compiled into NSArrayAsKList
                Triple(NSArray()::class, NSArray::class, false),
                Triple(NSArray()::class, List::class, true),

                Triple(NSArray::class, NSObject::class, true),
                Triple(NSArray()::class, NSObject::class, false),

                Triple(NSArray::class, NSCopyingProtocol::class, true),
                Triple(NSArray()::class, NSCopyingProtocol::class, false),

                Triple(NSArray::class, Any::class, true),
                Triple(NSArray()::class, Any::class, true),

                Triple(NSDictionary::class, NSDictionary::class, true),

                Triple(NSObject::class, NSString::class, false),
                Triple(NSObject::class, NSObjectProtocol::class, true),
                Triple(NSObject()::class, Any::class, true),
                Triple(NSObject::class, NSObject()::class, true),
                Triple(NSArray::class, NSObject()::class, true),
                Triple(NSObject::class, NSArray::class, false),
                Triple(NSCopyingProtocol::class, NSObject()::class, false),

                Triple(KotlinNSArray::class, Any::class, true),
                Triple(KotlinNSArray::class, NSObject::class, true),
                Triple(KotlinNSArray::class, NSObject()::class, true),
                Triple(KotlinNSArray::class, KotlinNSArray()::class, true),
                Triple(KotlinNSArray()::class, KotlinNSArray::class, true),
                Triple(KotlinNSArray()::class, NSArray::class, true),
                Triple(KotlinNSArray()::class, NSObject::class, true),
                Triple(KotlinNSArray()::class, NSCopyingProtocol::class, true),
                Triple(KotlinNSArray::class, NSDictionary::class, false),
                Triple(KotlinNSArray()::class, NSDictionary::class, false),

                Triple(Any::class, NSObject::class, false),
        ).forEach { (subclass, superclass, result) ->
            assertEquals(subclass.isSubclassOf(superclass), result, "$subclass.isSubclassOf($superclass) != $result")
        }
    }

    @Test
    fun equality() {
        listOf<Triple<KClass<*>, KClass<*>, Boolean>>(
                Triple(String::class, String::class, true),
                Triple(String::class, NSString::class, false),
                Triple(String::class, Any::class, false),
                Triple(String::class, Comparable::class, false),
                Triple(Comparable::class, Comparable::class, true),
                Triple(Int::class, Int::class, true),
                Triple(Int::class, NSInteger::class, false),
                Triple(Long::class, NSInteger::class, true),
                Triple(Float::class, Number::class, false),
                Triple(1::class, Number::class, false),

                Triple(NSArray::class, NSArray::class, true),
                Triple(NSArray()::class, NSArray::class, false),
                Triple(NSArray::class, NSObject::class, false),
                Triple(NSArray()::class, NSObject::class, false),
                Triple(NSArray::class, NSCopyingProtocol::class, false),
                Triple(NSArray()::class, NSCopyingProtocol::class, false),
                Triple(NSArray::class, Any::class, false),
                Triple(NSArray()::class, Any::class, false),

                Triple(NSDictionary::class, NSDictionary::class, true),

                Triple(NSObject::class, NSString::class, false),
                Triple(NSObject::class, NSObjectProtocol::class, false),
                Triple(NSObject()::class, Any::class, false),

                Triple(KotlinNSArray::class, Any::class, false),
                Triple(KotlinNSArray::class, NSObject::class, false),
                Triple(KotlinNSArray()::class, KotlinNSArray::class, false),
                Triple(KotlinNSArray()::class, KotlinNSArray()::class, true),
                Triple(KotlinNSArray()::class, NSArray::class, false),
                Triple(KotlinNSArray()::class, NSObject::class, false),
                Triple(KotlinNSArray()::class, NSCopyingProtocol::class, false),
                Triple(KotlinNSArray::class, NSDictionary::class, false),
                Triple(KotlinNSArray()::class, NSDictionary::class, false),

                Triple(Any::class, NSObject::class, false),
        ).forEach { (clsA, clsB, result) ->
            assertEquals(clsA == clsB, result, "($clsA == $clsB) != $result")
            assertEquals(clsB == clsA, result, "($clsB == $clsA) != $result")
        }
    }

}
