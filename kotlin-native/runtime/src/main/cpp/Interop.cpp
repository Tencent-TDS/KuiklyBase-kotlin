/*
 * Copyright 2010-2017 JetBrains s.r.o.
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

#include "KString.h"
#include "Memory.h"

using namespace kotlin;

extern "C" OBJ_GETTER(Kotlin_CString_toKStringFromUtf8Impl, const char* cstring) {
  RETURN_RESULT_OF(CreateStringFromCString, cstring);
}

extern "C" OBJ_GETTER(Kotlin_Interop_pinnable, KRef any) {
    if (any != nullptr && any->type_info() == theStringTypeInfo) {
        RETURN_RESULT_OF(ConvertStringToUtf16, any);
    }
    RETURN_OBJ(any);
}

// region @Tencent: Bindings for creating Kotlin Strings from owned UTF-8/UTF-16 C buffers.
extern "C" OBJ_GETTER(Kotlin_Interop_toKStringFromOwnedUtf8, char* utf8, KInt length) {
    RETURN_RESULT_OF(CreateStringFromOwnedUtf8, utf8, length);
}

extern "C" OBJ_GETTER(
    Kotlin_Interop_toKStringFromOwnedUtf8WithFreeCallback,
    char* utf8,
    KInt length,
    KStringFreeCallback freeCallback,
    void* freeContext
) {
    RETURN_RESULT_OF(CreateStringFromOwnedUtf8WithFreeCallback, utf8, length, freeCallback, freeContext);
}

extern "C" OBJ_GETTER(Kotlin_Interop_toKStringFromOwnedUtf16, KChar* utf16, KInt length) {
    RETURN_RESULT_OF(CreateStringFromOwnedUtf16, utf16, length);
}

extern "C" OBJ_GETTER(
    Kotlin_Interop_toKStringFromOwnedUtf16WithFreeCallback,
    KChar* utf16,
    KInt length,
    KStringFreeCallback freeCallback,
    void* freeContext
) {
    RETURN_RESULT_OF(CreateStringFromOwnedUtf16WithFreeCallback, utf16, length, freeCallback, freeContext);
}
// endregion
