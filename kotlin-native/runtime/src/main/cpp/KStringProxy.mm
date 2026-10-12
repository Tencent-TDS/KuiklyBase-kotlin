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

#include "KString.h"

#if KONAN_OBJC_INTEROP

#import <Foundation/Foundation.h>

#import "KStringProxy.h"
#import "NSStringWithExternalStorage.h"

namespace {

ALWAYS_INLINE const char *KStringProxyGetUtf16Ptr(const StringHeader *header) {
    const auto *stringRef = static_cast<CFStringRef>(reinterpret_cast<const ObjHeader *>(header)->GetAssociatedObject());
    return reinterpret_cast<const char *>(CFStringGetCharactersPtr(stringRef));
}

ALWAYS_INLINE const char *KStringProxyGetLatin1Ptr(const StringHeader *header) {
    const auto *stringRef = static_cast<CFStringRef>(reinterpret_cast<const ObjHeader *>(header)->GetAssociatedObject());
    return CFStringGetCStringPtr(stringRef, kCFStringEncodingISOLatin1);
}

ALWAYS_INLINE char *KStringExternalGetDataPtr(const StringHeader *header) {
    auto *string = static_cast<NSStringWithExternalStorage *>(reinterpret_cast<const ObjHeader *>(header)->GetAssociatedObject());
    return string.data;
}

ALWAYS_INLINE void CFStringGetAllLatin1Bytes(CFStringRef string, UInt8* buffer, uint32_t length) {
    const CFRange range = CFRangeMake(0, length);
    CFIndex usedLength = 0;
    CFStringGetBytes(string, range, kCFStringEncodingISOLatin1, 0, false, buffer, length, &usedLength);
}

ALWAYS_INLINE void CFStringGetAllCharacters(CFStringRef string, UniChar* buffer, uint32_t length) {
    const CFRange range = CFRangeMake(0, length);
    CFStringGetCharacters(string, range, buffer);
}

} // namespace

OBJ_GETTER(Kotlin_Interop_CreateKStringProxyFromImmutableCFString, CFStringRef stringRef) {
    StringEncoding encoding = StringEncoding::kUTF16;
    StringType type = StringType::kDefault;
    void *associateObj = nullptr;

    const auto stringLength = CFStringGetLength(stringRef);
    const auto *utf16Ptr = CFStringGetCharactersPtr(stringRef);
    if (utf16Ptr != nullptr) {
        encoding = StringEncoding::kUTF16;
        type = StringType::kCFString;
        associateObj = const_cast<void *>(static_cast<const void *>(stringRef));
    } else {
        const auto *latin1Ptr = CFStringGetCStringPtr(stringRef, kCFStringEncodingISOLatin1);
        if (latin1Ptr != nullptr) {
            encoding = StringEncoding::kLatin1;
            type = StringType::kCFString;
            associateObj = const_cast<void *>(static_cast<const void *>(stringRef));
        } else {
            NSStringWithExternalStorage *wrapper = nil;
            const auto cfEncoding = CFStringGetFastestEncoding(stringRef);
            switch (cfEncoding) {
                case kCFStringEncodingASCII:
                case kCFStringEncodingNonLossyASCII:
                case kCFStringEncodingISOLatin1: {
                    encoding = StringEncoding::kLatin1;
                    wrapper = [[NSStringWithExternalStorage alloc] initWithLength:stringLength isLatin1:YES];
                    CFStringGetAllLatin1Bytes(stringRef, reinterpret_cast<UInt8 *>(wrapper.data), stringLength);
                    break;
                }
                default:
                    encoding = StringEncoding::kUTF16;
                    wrapper = [[NSStringWithExternalStorage alloc] initWithLength:stringLength isLatin1:NO];
                    CFStringGetAllCharacters(stringRef, reinterpret_cast<UniChar *>(wrapper.data), stringLength);
            }

            type = StringType::kExternal;
            associateObj = wrapper;
            CFRelease(stringRef);
        }
    }

    auto *result = allocateStringDefault(encoding, type, 0, OBJ_RESULT);
    auto *header = StringHeader::of(result);
    const auto unitSize = encoding == StringEncoding::kUTF16 ? sizeof(KChar) : 1;
    header->adjustForExternal(unitSize * stringLength);
    result->SetAssociatedObject(associateObj);
    return result;
}

#endif // KONAN_OBJC_INTEROP

char *StringHeader::dataProxy() const {
    switch (type()) {
#if KONAN_OBJC_INTEROP
        case StringType::kExternal:
            return KStringExternalGetDataPtr(this);
        case StringType::kCFString:
            if (encoding() == StringEncoding::kUTF16) {
                return const_cast<char*>(KStringProxyGetUtf16Ptr(this));
            } else {
                return const_cast<char*>(KStringProxyGetLatin1Ptr(this));
            }
#else
        case StringType::kExternal:
        case StringType::kCFString:
#endif
        case StringType::kDefault:
            // Unreachable. Always handled on call-site.
            return nullptr;
    }
}
