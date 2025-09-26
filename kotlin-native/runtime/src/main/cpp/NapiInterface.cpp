/*
 * Copyright 2025-2025 Huawei Technologies Co., Ltd. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

#if KONAN_OHOS

#include <cstdint>
#include <limits>

#include "KAssert.h"
#include "KString.h"
#include "Memory.h"
#include "Natives.h"
#include "Types.h"

#include <napi/native_api.h>

using namespace kotlin;

extern "C" {

OBJ_GETTER(Kotlin_napi_get_kotlin_string_utf16, KNativePtr env, KNativePtr value) {
    size_t str_size;
    size_t str_size_read;
    napi_get_value_string_utf16(
        (napi_env)env,
        (napi_value)value,
        NULL, 0, &str_size
    );

    RuntimeAssert(str_size < INT_MAX, "utf16 string cannot be too long for kotlin");

    ArrayHeader* result = AllocArrayInstance(theStringTypeInfo, (int32_t)str_size, OBJ_RESULT)->array();
    napi_get_value_string_utf16(
        (napi_env)env,
        (napi_value)value,
        (char16_t *)CharArrayAddressOfElementAt(result, 0),
        str_size, &str_size_read
    );

    RuntimeAssert(str_size == str_size_read, "utf16 string isn't read completely");

    RETURN_OBJ(result->obj());
}

KNativePtr Kotlin_napi_create_int32(KNativePtr env, KInt value) {
    napi_value result;
    napi_create_int32((napi_env) env, value, &result);
    return (KNativePtr)result;
}

KInt Kotlin_napi_get_value_int32(KNativePtr env, KNativePtr value) {
    int32_t result;
    napi_get_value_int32((napi_env) env, (napi_value) value, &result);
    return result;
}

KNativePtr Kotlin_napi_create_int64(KNativePtr env, KLong value) {
    napi_value result;
    napi_create_int64((napi_env) env, value, &result);
    return (KNativePtr)result;
}

KLong Kotlin_napi_get_value_int64(KNativePtr env, KNativePtr value) {
    int64_t result;
    napi_get_value_int64((napi_env) env, (napi_value) value, &result);
    return result;
}

KNativePtr Kotlin_napi_create_bool(KNativePtr env, KBoolean value) {
    napi_value result;
    napi_get_boolean((napi_env) env, value, &result);
    return (KNativePtr)result;
}

KBoolean Kotlin_napi_get_value_bool(KNativePtr env, KNativePtr value) {
    bool result;
    napi_get_value_bool((napi_env) env, (napi_value) value, &result);
    return result;
}

KNativePtr Kotlin_napi_create_double(KNativePtr env, KDouble value) {
    napi_value result;
    napi_create_double((napi_env) env, value, &result);
    return (KNativePtr)result;
}

KDouble Kotlin_napi_get_value_double(KNativePtr env, KNativePtr value) {
    double result;
    napi_get_value_double((napi_env) env, (napi_value) value, &result);
    return (KDouble)result;
}

KNativePtr Kotlin_String_toNapiValue(KConstRef thiz, KNativePtr env) {
    auto header = StringHeader::of(thiz);
    const char16_t* utf16Chars = (const char16_t*)(header->data());
    size_t length = header->count_ - 1; // Exclude the null terminator
    napi_value result;
    napi_create_string_utf16((napi_env)env, utf16Chars, length, &result);
    return (KNativePtr)result;
}

}

#endif
