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

#import <objc/runtime.h>
#import "KStringProxyWeakRef.h"
#import "MemorySharedRefs.hpp"
#import "TmmConfig.h"

extern "C" id objc_autorelease(id self);

@interface KStringProxyWeakRef : NSObject

- (instancetype)initWithStringProxy:(ObjHeader *)stringProxy;

- (ObjHeader *)getStringProxyOrNull;

@end

@implementation KStringProxyWeakRef {
  KWeakRefSharedHolder _holder;
}

- (instancetype)initWithStringProxy:(ObjHeader *)stringProxy {
  if (self = [super init]) {
    _holder.init(stringProxy);
  }
  return self;
}

- (ObjHeader *)getStringProxyOrNull {
  return _holder.tryRef();
}

- (void)dealloc {
  _holder.dispose();
  [super dealloc];
}

@end

static char KEY_STRING_PROXY;

namespace tmm {
ALWAYS_INLINE ObjHeader *GetAssociatedKStringProxy(NSString *str) {
  KStringProxyWeakRef *stringProxyWeakRef = objc_getAssociatedObject(str, &KEY_STRING_PROXY);
  return [stringProxyWeakRef getStringProxyOrNull];
}

ALWAYS_INLINE void AssociateNSStringWithKStringProxy(NSString *str, ObjHeader *stringProxy) {
  if (Kotlin_TmmConfig_isStringProxyAssociatedWithNSString()) {
    objc_setAssociatedObject(str,
                             &KEY_STRING_PROXY,
                             objc_autorelease([[KStringProxyWeakRef alloc] initWithStringProxy:stringProxy]),
                             OBJC_ASSOCIATION_RETAIN_NONATOMIC);
  }
}
}

#endif