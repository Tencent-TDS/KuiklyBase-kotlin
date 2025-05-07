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

#ifndef KOTLIN_NATIVE_KSTRINGPROXYCOMPAT_H
#define KOTLIN_NATIVE_KSTRINGPROXYCOMPAT_H

// This file is also included into cpp files, so do not import any Objc headers.
#include "Memory.h"
#include "Types.h"
#include <string>

#define FLAG_KSTRING_PROXY 0x1

namespace {
typedef std::back_insert_iterator<std::string> KStdStringInserter;
typedef KChar *utf8to16(const char *, const char *, KChar *);
typedef KStdStringInserter utf16to8(const KChar *, const KChar *, KStdStringInserter);
} // namespace

namespace tmm {

ALWAYS_INLINE bool IsKStringProxy(KString string);

char *CreateCStringFromString(KString stringProxy);

OBJ_GETTER(Kotlin_Interop_CreateKStringProxyFromCharArray, const KChar *array, KInt size);

ALWAYS_INLINE OBJ_GETTER(utf8ToUtf16Impl,
                         const char *rawString,
                         const char *end,
                         uint32_t charCount,
                         utf8to16 conversion);

ALWAYS_INLINE OBJ_GETTER(unsafeUtf16ToUtf8Impl, KString thizProxy, KInt start, KInt size, utf16to8 conversion);

// region member functions of kotlin.String
ALWAYS_INLINE OBJ_GETTER(Kotlin_StringProxy_replace, KString thizProxy, KChar oldChar, KChar newChar);

ALWAYS_INLINE OBJ_GETTER(Kotlin_StringProxy_plusStringProxyImpl, KString thizProxy, KString otherProxy);

ALWAYS_INLINE OBJ_GETTER(Kotlin_StringProxy_plusStringImpl, KString thizProxy, KString other);

ALWAYS_INLINE OBJ_GETTER(Kotlin_String_plusStringProxyImpl, KString thiz, KString otherProxy);

ALWAYS_INLINE OBJ_GETTER(Kotlin_StringProxy_unsafeStringProxyFromCharArray, KConstRef thiz, KInt start, KInt size);

ALWAYS_INLINE OBJ_GETTER(Kotlin_StringProxy_toCharArray,
                         KString stringProxy,
                         KRef destination,
                         KInt destinationOffset,
                         KInt start,
                         KInt size);

ALWAYS_INLINE OBJ_GETTER(Kotlin_StringProxy_subSequence, KString thizProxy, KInt startIndex, KInt endIndex);

ALWAYS_INLINE KInt Kotlin_StringProxy_compareToStringProxy(KString thizProxy, KString otherProxy);

ALWAYS_INLINE KInt Kotlin_StringProxy_compareToString(KString thizProxy, KString other);

ALWAYS_INLINE KInt Kotlin_String_compareToStringProxy(KString thiz, KString otherProxy);

ALWAYS_INLINE KChar Kotlin_StringProxy_get(KString thizProxy, KInt index);

ALWAYS_INLINE KBoolean Kotlin_StringProxy_equalsWithStringProxy(KString thizProxy, KString otherProxy);

ALWAYS_INLINE KBoolean Kotlin_StringProxy_equalsWithString(KString stringProxy, KString string);

ALWAYS_INLINE KBoolean Kotlin_StringProxy_unsafeRangeEqualsWithStringProxy(KString thizProxy,
                                                                           KInt thizOffset,
                                                                           KString otherProxy,
                                                                           KInt otherOffset,
                                                                           KInt length);

ALWAYS_INLINE KBoolean Kotlin_StringProxy_unsafeRangeEqualsWithString(KString stringProxy,
                                                                      KInt stringProxyOffset,
                                                                      KString string,
                                                                      KInt stringOffset,
                                                                      KInt length);

ALWAYS_INLINE KInt Kotlin_StringProxy_indexOfChar(KString thizProxy, KChar ch, KInt fromIndex);

ALWAYS_INLINE KInt Kotlin_StringProxy_lastIndexOfChar(KString thizProxy, KChar ch, KInt fromIndex);

ALWAYS_INLINE KInt Kotlin_StringProxy_indexOfStringProxy(KString thizProxy, KString otherProxy, KInt fromIndex);

ALWAYS_INLINE KInt Kotlin_StringProxy_indexOfString(KString thizProxy, KString other, KInt fromIndex);

ALWAYS_INLINE KInt Kotlin_String_indexOfStringProxy(KString thiz, KString otherProxy, KInt fromIndex);

ALWAYS_INLINE KInt Kotlin_StringProxy_lastIndexOfStringProxy(KString thizProxy, KString otherProxy, KInt fromIndex);

ALWAYS_INLINE KInt Kotlin_StringProxy_lastIndexOfString(KString thizProxy, KString other, KInt fromIndex);

ALWAYS_INLINE KInt Kotlin_String_lastIndexOfStringProxy(KString thiz, KString otherProxy, KInt fromIndex);

ALWAYS_INLINE KInt Kotlin_StringProxy_hashCode(KString thizProxy);

ALWAYS_INLINE KInt Kotlin_StringBuilder_insertStringProxy(ArrayHeader *toArray,
                                                          KInt distIndex,
                                                          KString fromStringProxy,
                                                          KInt sourceIndex,
                                                          KInt count);
// endregion

// region NativeCharArray
ALWAYS_INLINE KInt Kotlin_NativeCharArrayImpl_insertStringProxy(KChar *array,
                                                                KInt size,
                                                                KInt distIndex,
                                                                KString fromStringProxy,
                                                                KInt sourceIndex,
                                                                KInt count);

ALWAYS_INLINE void Kotlin_NativeCharArrayImpl_fillStringProxy(KChar *array,
                                                              KInt size,
                                                              KInt offset,
                                                              KString stringProxy,
                                                              KInt start,
                                                              KInt count);
// endregion

// region used in Console
ALWAYS_INLINE void Kotlin_io_Console_printStringProxy(KString stringProxy);

ALWAYS_INLINE void Kotlin_io_Console_printStringProxyToStdErr(KString stringProxy);
// endregion
} // namespace tmm

#endif // KOTLIN_NATIVE_KSTRINGPROXYCOMPAT_H
