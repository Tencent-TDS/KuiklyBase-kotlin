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
#import "Types.h"
#import "Natives.h"

@implementation NSStringFromKString {
  ArrayHeader *_array;
  KRefSharedHolder _holder;
}

- (instancetype)initWithKString:(ObjHeader *)kstring {
  if (self = [super init]) {
    _array = kstring->array();
    _holder.init(kstring);
  }
  return self;
}

- (ObjHeader *)kstring {
  return _array->obj();
}

- (NSUInteger)length {
  return _array->count_;
}

- (unichar)characterAtIndex:(NSUInteger)index {
  if (static_cast<uint32_t>(index) >= _array->count_) {
    ThrowArrayIndexOutOfBoundsException();
  }
  return *CharArrayAddressOfElementAt(_array, static_cast<KInt>(index));
}

- (void)getCharacters:(unichar *)buffer range:(NSRange)range {
  if (static_cast<uint32_t>(range.location + range.length) > _array->count_) {
    ThrowArrayIndexOutOfBoundsException();
  }
  memmove(buffer,
          CharArrayAddressOfElementAt(_array, static_cast<KInt>(range.location)),
          sizeof(unichar) * range.length);
}

- (void)dealloc {
  _holder.dispose();
  [super dealloc];
}

- (ObjHeader *)toKotlin:(ObjHeader **)OBJ_RESULT {
  RETURN_OBJ(_array->obj());
}
@end

#endif