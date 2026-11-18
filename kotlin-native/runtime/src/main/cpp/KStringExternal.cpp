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

#include <cstdint>
#include <cstdlib>
#include <cstring>
#include <limits>
#include <string>

#include "Exceptions.h"
#include "KString.h"
#include "Memory.h"
#include "Natives.h"

#if KONAN_OBJC_INTEROP
#include "NSStringWithExternalStorage.h"
#else
#include "AssociatedObject.hpp"
#endif

namespace {

constexpr size_t kMaxStringHeaderChars =
    StringHeader::extraLength(0) / sizeof(KChar);
constexpr size_t kMaxExternalStringUnits =
    static_cast<size_t>(std::numeric_limits<int32_t>::max()) - kMaxStringHeaderChars;

void defaultFreeCallback(void* mem, void* /*context*/) noexcept {
    std::free(mem);
}

void invokeFreeCallback(void* mem, KStringFreeCallback freeCallback, void* freeContext) noexcept {
    if (freeCallback != nullptr) {
        const kotlin::NativeOrUnregisteredThreadGuard guard(/*reentrant=*/ true);
        freeCallback(mem, freeContext);
    }
}

size_t utf16NullTerminatedLength(const KChar* utf16) noexcept {
    if (reinterpret_cast<uintptr_t>(utf16) % alignof(KChar) == 0) {
        return std::char_traits<char16_t>::length(reinterpret_cast<const char16_t*>(utf16));
    }
    const auto* bytes = reinterpret_cast<const uint8_t*>(utf16);
    size_t length = 0;
    while (bytes[0] != 0 || bytes[1] != 0) {
        bytes += sizeof(KChar);
        ++length;
    }
    return length;
}

void attachExternalStorage(
    KRef kstring,
    void* data,
    [[maybe_unused]] uint32_t lengthInUnits,
    [[maybe_unused]] bool isLatin1,
    KStringFreeCallback freeCallback,
    void* freeContext
) {
#if KONAN_OBJC_INTEROP
    void* wrapper = CreateNSStringWithExternalStorage(
        data, lengthInUnits, isLatin1, freeCallback, freeContext);
    kstring->SetAssociatedObject(wrapper);
#else
    kstring->SetAssociatedObject(new kotlin::mm::ExternalBufferAssociatedObject(data, freeCallback, freeContext));
#endif
}

} // namespace

extern "C" RUNTIME_EXPORT OBJ_GETTER(
    CreateStringFromOwnedUtf8WithFreeCallback,
    char* utf8,
    int32_t length,
    KStringFreeCallback freeCallback,
    void* freeContext
) {
    if (utf8 == nullptr) RETURN_OBJ(nullptr);

    const size_t lengthBytes = length < 0 ? std::strlen(utf8) : static_cast<size_t>(length);

    if (lengthBytes > kMaxExternalStringUnits) {
        invokeFreeCallback(utf8, freeCallback, freeContext);
        ThrowOutOfMemoryError();
    }

    if (lengthBytes == 0) {
        invokeFreeCallback(utf8, freeCallback, freeContext);
        RETURN_RESULT_OF0(TheEmptyString);
    }

    if (kotlin::internal::utf8StringIsASCII(utf8, lengthBytes)) {
        ObjHolder holder;
        auto* result = allocateStringDefault(StringEncoding::kLatin1, StringType::kExternal, 0, holder.slot());
        auto* header = StringHeader::of(result);
        header->adjustForExternal(lengthBytes);
        attachExternalStorage(result, utf8, static_cast<uint32_t>(lengthBytes), /*isLatin1=*/true, freeCallback, freeContext);
        RETURN_OBJ(holder.obj());
    }

    ObjHolder holder;
    CreateStringFromUtf8(utf8, static_cast<uint32_t>(lengthBytes), holder.slot());
    invokeFreeCallback(utf8, freeCallback, freeContext);
    RETURN_OBJ(holder.obj());
}

extern "C" RUNTIME_EXPORT OBJ_GETTER(CreateStringFromOwnedUtf8, char* utf8, int32_t length) {
    RETURN_RESULT_OF(CreateStringFromOwnedUtf8WithFreeCallback, utf8, length, &defaultFreeCallback, nullptr);
}

extern "C" RUNTIME_EXPORT OBJ_GETTER(
    CreateStringFromOwnedUtf16WithFreeCallback,
    KChar* utf16,
    int32_t length,
    KStringFreeCallback freeCallback,
    void* freeContext
) {
    if (utf16 == nullptr) RETURN_OBJ(nullptr);

    const size_t lengthChars = length < 0 ? utf16NullTerminatedLength(utf16) : static_cast<size_t>(length);

    if (lengthChars > kMaxExternalStringUnits) {
        invokeFreeCallback(utf16, freeCallback, freeContext);
        ThrowOutOfMemoryError();
    }

    if (lengthChars == 0) {
        invokeFreeCallback(utf16, freeCallback, freeContext);
        RETURN_RESULT_OF0(TheEmptyString);
    }

    if (reinterpret_cast<uintptr_t>(utf16) % alignof(KChar) == 0) {
        ObjHolder holder;
        auto* result = allocateStringDefault(StringEncoding::kUTF16, StringType::kExternal, 0, holder.slot());
        auto* header = StringHeader::of(result);
        header->adjustForExternal(static_cast<uint64_t>(lengthChars) * sizeof(KChar));
        attachExternalStorage(result, utf16, static_cast<uint32_t>(lengthChars), /*isLatin1=*/false, freeCallback, freeContext);
        RETURN_OBJ(holder.obj());
    }

    // Unaligned UTF-16 pointer: copy to aligned heap KString and release original buffer immediately.
    ObjHolder holder;
    auto* result = CreateUninitializedString(StringEncoding::kUTF16, static_cast<uint32_t>(lengthChars), holder.slot());
    std::memcpy(StringHeader::of(result)->data(), utf16, lengthChars * sizeof(KChar));
    invokeFreeCallback(utf16, freeCallback, freeContext);
    RETURN_OBJ(holder.obj());
}

extern "C" RUNTIME_EXPORT OBJ_GETTER(CreateStringFromOwnedUtf16, KChar* utf16, int32_t length) {
    RETURN_RESULT_OF(CreateStringFromOwnedUtf16WithFreeCallback, utf16, length, &defaultFreeCallback, nullptr);
}
