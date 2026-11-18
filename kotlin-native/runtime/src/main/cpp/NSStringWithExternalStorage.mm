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

#if KONAN_OBJC_INTEROP

#include <algorithm>
#include <cstdlib>
#include <cstring>

#import <Foundation/Foundation.h>
#include "KAssert.h"
#include "Memory.h"
#import "NSStringWithExternalStorage.h"

namespace {

void defaultFreeCallback(void* mem, void* /*context*/) noexcept {
    std::free(mem);
}

} // namespace

@implementation NSStringWithExternalStorage {
    char* _data;
    BOOL _isLatin1;
    uint32_t _length;
    KStringFreeCallback _freeCallback;
    void* _freeContext;
}

- (instancetype)initWithLength:(uint32_t)length isLatin1:(BOOL)isLatin1 {
    if (self = [super init]) {
        _isLatin1 = isLatin1;
        _length = length;
        _freeCallback = &defaultFreeCallback;
        _freeContext = nullptr;
        const size_t sizeInBytes = _isLatin1 ? _length : static_cast<size_t>(_length) * sizeof(UniChar);
        _data = static_cast<char*>(std::malloc(sizeInBytes));
        RuntimeCheck(_data != nullptr, "Failed to allocate %zu bytes", sizeInBytes);
        std::memset(_data, 0, sizeInBytes);
    }
    return self;
}

- (instancetype)initWithOwnedData:(void*)data
                           length:(uint32_t)length
                         isLatin1:(BOOL)isLatin1
                     freeCallback:(KStringFreeCallback)freeCallback
                      freeContext:(void*)freeContext {
    if (self = [super init]) {
        _data = static_cast<char*>(data);
        _isLatin1 = isLatin1;
        _length = length;
        _freeCallback = freeCallback;
        _freeContext = freeContext;
    }
    return self;
}

- (NSUInteger)length {
    return _length;
}

- (unichar)characterAtIndex:(NSUInteger)index {
    return _isLatin1 ? reinterpret_cast<uint8_t*>(_data)[index] : reinterpret_cast<UniChar*>(_data)[index];
}

- (void)getCharacters:(unichar*)buffer range:(NSRange)range {
    if (_isLatin1) {
        std::copy_n(reinterpret_cast<uint8_t*>(_data) + range.location, range.length, buffer);
    } else {
        std::copy_n(reinterpret_cast<UniChar*>(_data) + range.location, range.length, buffer);
    }
}

- (void)dealloc {
    if (_data != nullptr) {
        char* data = _data;
        _data = nullptr;
        if (_freeCallback != nullptr) {
            const kotlin::NativeOrUnregisteredThreadGuard guard(/*reentrant=*/ true);
            _freeCallback(data, _freeContext);
        }
    }
    [super dealloc];
}

- (char*)data {
    return _data;
}

@end

void* CreateNSStringWithExternalStorage(
    void* data,
    uint32_t length,
    bool isLatin1,
    KStringFreeCallback freeCallback,
    void* freeContext
) {
    return [[NSStringWithExternalStorage alloc] initWithOwnedData:data
                                                           length:length
                                                         isLatin1:isLatin1 ? YES : NO
                                                     freeCallback:freeCallback
                                                      freeContext:freeContext];
}

#endif // KONAN_OBJC_INTEROP
