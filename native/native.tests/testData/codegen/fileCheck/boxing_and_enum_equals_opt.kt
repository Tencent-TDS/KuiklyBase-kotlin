/*
 * Tencent is pleased to support the open source community by making Kotlin available.
 * Copyright (C) 2026 THL A29 Limited, a Tencent company. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// TARGET_BACKEND: NATIVE
// FILECHECK_STAGE: CStubs

enum class Color {
    RED, GREEN, BLUE
}

// CHECK-LABEL: define {{i1|zeroext i1}} @"kfun:#testEnumEquals(Color;Color){}kotlin.Boolean"
// CHECK-NOT: kfun:kotlin.Enum#equals
// CHECK: icmp eq ptr
// CHECK-LABEL: epilogue:
fun testEnumEquals(a: Color, b: Color): Boolean {
    return a == b
}

// CHECK-LABEL: define i32 @"kfun:#testCheckNotNullPrimitive(kotlin.Int){}kotlin.Int"
// CHECK-NOT: <Int-box>
// CHECK-NOT: <Int-unbox>
// CHECK-LABEL: epilogue:
@Suppress("UNNECESSARY_NOT_NULL_ASSERTION")
fun testCheckNotNullPrimitive(x: Int): Int {
    return x!! + 1
}

inline fun inlineReturnInt(x: Int): Int {
    return x + 1
}

// CHECK-LABEL: define i32 @"kfun:#testInlineReturnNonnullPrimitive(kotlin.Int){}kotlin.Int"
// CHECK-NOT: <Int-box>
// CHECK-NOT: <Int-unbox>
// CHECK-LABEL: epilogue:
fun testInlineReturnNonnullPrimitive(x: Int): Int {
    return inlineReturnInt(x)
}

inline fun <T> inlineWithTypeParam(x: T, block: (T) -> Int): Int {
    val tmp: T = x
    return block(tmp)
}

// CHECK-LABEL: define i32 @"kfun:#testInlineNonnullPrimitiveTypeArg(kotlin.Int){}kotlin.Int"
// CHECK-NOT: <Int-box>
// CHECK-NOT: <Int-unbox>
// CHECK-LABEL: epilogue:
fun testInlineNonnullPrimitiveTypeArg(x: Int): Int {
    return inlineWithTypeParam(x) { it + 1 }
}

// CHECK-LABEL: define ptr @"kfun:#box(){}kotlin.String"
fun box(): String {
    if (!testEnumEquals(Color.RED, Color.RED)) return "FAIL enum =="
    if (testEnumEquals(Color.RED, Color.BLUE)) return "FAIL enum !="

    if (testCheckNotNullPrimitive(41) != 42) return "FAIL checkNotNull"

    if (testInlineReturnNonnullPrimitive(41) != 42) return "FAIL inlineReturnNonnullPrimitive"
    if (testInlineNonnullPrimitiveTypeArg(41) != 42) return "FAIL inlineNonnullPrimitiveTypeArg"

    return "OK"
}
