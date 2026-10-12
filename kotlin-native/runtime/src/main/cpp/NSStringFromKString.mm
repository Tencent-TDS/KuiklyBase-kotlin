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

#import "NSStringFromKString.h"
#include "ExternalRCRef.hpp"
#include "KString.h"
#include "Natives.h"
#include "Types.h"

@implementation NSStringFromKString {
    KRef _kstring;
    kotlin::mm::OwningExternalRCRef _holder;
}

- (instancetype)initWithKString:(ObjHeader*)kstring {
    if (self = [super init]) {
        _kstring = kstring;
        _holder.reset(kstring);
    }
    return self;
}

- (ObjHeader*)kstring {
    return _kstring;
}

- (NSUInteger)length {
    return static_cast<NSUInteger>(Kotlin_String_getStringLength(_kstring));
}

- (unichar)characterAtIndex:(NSUInteger)index {
    return Kotlin_String_get(_kstring, static_cast<KInt>(index));
}

- (void)getCharacters:(unichar*)buffer range:(NSRange)range {
    Kotlin_String_overwriteRawArray(_kstring, buffer, static_cast<KInt>(range.location), static_cast<KInt>(range.length));
}

- (void)dealloc {
    _holder.reset();
    [super dealloc];
}

- (ObjHeader*)toKotlin:(ObjHeader**)OBJ_RESULT {
    RETURN_OBJ(_kstring);
}

@end

#endif // KONAN_OBJC_INTEROP
