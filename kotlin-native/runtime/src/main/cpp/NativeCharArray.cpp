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
 
#include <cstring>

#include "Memory.h"
#include "Types.h"
#import "Natives.h"
#if KONAN_OBJC_INTEROP
#include "TmmConfig.h"
#include "KStringProxyCompat.h"
#endif

struct NativeArrayHolder {
  KChar *array;
};

extern "C" {

ALWAYS_INLINE void *Kotlin_NativeCharArrayImpl_init(KInt size) {
  auto holder = new NativeArrayHolder;
  holder->array = (KChar *) calloc(size, sizeof(KChar));
  return holder;
}

ALWAYS_INLINE void Kotlin_NativeCharArrayImpl_release(void *ptr) {
  auto holder = reinterpret_cast<NativeArrayHolder *>(ptr);
  free(holder->array);
  delete holder;
}

ALWAYS_INLINE KChar Kotlin_NativeCharArrayImpl_get(NativeArrayHolder *holder, KInt index) {
  return holder->array[index];
}

ALWAYS_INLINE void Kotlin_NativeCharArrayImpl_set(NativeArrayHolder *holder, KInt index, KChar value) {
  holder->array[index] = value;
}

ALWAYS_INLINE void Kotlin_NativeCharArrayImpl_fillString(NativeArrayHolder *holder,
                                                         KInt size,
                                                         KInt offset,
                                                         KString string,
                                                         KInt start,
                                                         KInt count) {
  RuntimeAssert(size >= 0 && offset >= 0 && start >= 0 && offset + count <= size, "must be true");
  RuntimeAssert(start + count <= static_cast<KInt>(string->count_), "must be true");

#ifdef KONAN_OBJC_INTEROP
  if (tmm::IsKStringProxy(string)) {
    tmm::Kotlin_NativeCharArrayImpl_fillStringProxy(holder->array, size, offset, string, start, count);
    return;
  }
#endif

  memcpy(holder->array + offset,
         CharArrayAddressOfElementAt(string, start),
         size * sizeof(KChar));
}

OBJ_GETTER(Kotlin_String_unsafeStringFromNativeCharArrayImpl, NativeArrayHolder *holder, KInt start, KInt size) {
  if (size == 0) {
    RETURN_RESULT_OF0(TheEmptyString);
  }

#ifdef KONAN_OBJC_INTEROP
  if (Kotlin_TmmConfig_isStringProxyEnabledGlobally()) {
    return tmm::Kotlin_Interop_CreateKStringProxyFromCharArray(holder->array + start, size, OBJ_RESULT);
  }
#endif

  ArrayHeader *result = AllocArrayInstance(theStringTypeInfo, size, OBJ_RESULT)->array();
  memcpy(CharArrayAddressOfElementAt(result, 0),
         holder->array + start,
         size * sizeof(KChar));
  RETURN_OBJ(result->obj());
}

KInt Kotlin_NativeCharArrayImpl_insertString(NativeArrayHolder *holder,
                                             KInt size,
                                             KInt destinationIndex,
                                             KString fromString,
                                             KInt sourceIndex,
                                             KInt count) {

  RuntimeAssert(sourceIndex >= 0 && static_cast<uint32_t>(sourceIndex + count) <= fromString->count_, "must be true");
  RuntimeAssert(destinationIndex >= 0 && destinationIndex + count <= size, "must be true");

#ifdef KONAN_OBJC_INTEROP
  if (tmm::IsKStringProxy(fromString)) {
    return tmm::Kotlin_NativeCharArrayImpl_insertStringProxy(holder->array,
                                                             size,
                                                             destinationIndex,
                                                             fromString,
                                                             sourceIndex,
                                                             count);
  }
#endif

  // It is safe to use memcpy since src and dst will never overlap each other.
  memcpy(holder->array + destinationIndex,
         CharArrayAddressOfElementAt(fromString, sourceIndex),
         count * sizeof(KChar));
  return count;
}

KInt Kotlin_NativeCharArrayImpl_insertInt(NativeArrayHolder *holder, KInt size, KInt position, KInt value) {
  RuntimeAssert(size >= 11 + position, "must be true");
  char cstring[12];
  auto length = std::snprintf(cstring, sizeof(cstring), "%d", value);
  RuntimeAssert(length >= 0, "This should never happen"); // may be overkill
  RuntimeAssert(static_cast<size_t>(length) < sizeof(cstring),
                "Unexpectedly large value"); // Can't be, but this is what sNprintf for
  auto *from = &cstring[0];
  auto *to = holder->array + position;
  while (*from) {
    *to++ = *from++;
  }
  return from - cstring;
}

void Kotlin_NativeCharArrayImpl_copyFromCharArray(KConstRef thiz,
                                                  KInt fromIndex,
                                                  NativeArrayHolder *destinationHolder,
                                                  KInt size,
                                                  KInt toIndex,
                                                  KInt count) {
  const ArrayHeader *array = thiz->array();
  if (count < 0 ||
      fromIndex < 0 || static_cast<uint32_t>(count) + fromIndex > array->count_ ||
      toIndex < 0 || count + toIndex > size) {
    ThrowArrayIndexOutOfBoundsException();
  }
  memcpy(destinationHolder->array + toIndex,
         PrimitiveArrayAddressOfElementAt<KChar>(array, fromIndex),
         count * sizeof(KChar));
}

void Kotlin_NativeCharArrayImpl_copyToCharArray(const NativeArrayHolder *holder,
                                                KInt size,
                                                KInt fromIndex,
                                                KRef destination,
                                                KInt toIndex,
                                                KInt count) {
  ArrayHeader *destinationArray = destination->array();
  if (count < 0 ||
      fromIndex < 0 || count + fromIndex > size ||
      toIndex < 0 || static_cast<uint32_t>(count) + toIndex > destinationArray->count_) {
    ThrowArrayIndexOutOfBoundsException();
  }
  memcpy(PrimitiveArrayAddressOfElementAt<KChar>(destinationArray, toIndex),
         holder->array + fromIndex,
         count * sizeof(KChar));
}

void Kotlin_NativeCharArrayImpl_copy(const NativeArrayHolder *holder,
                                     KInt size,
                                     KInt fromIndex,
                                     NativeArrayHolder *destination,
                                     KInt destinationSize,
                                     KInt toIndex,
                                     KInt count) {
  if (count < 0 ||
      fromIndex < 0 || count + fromIndex > size ||
      toIndex < 0 || count + toIndex > destinationSize) {
    ThrowArrayIndexOutOfBoundsException();
  }
  memcpy(destination->array + toIndex,
         holder->array + fromIndex,
         count * sizeof(KChar));
}

void Kotlin_NativeCharArrayImpl_fill(NativeArrayHolder *holder, KInt size, KInt fromIndex, KInt toIndex, KChar value) {
  if (fromIndex < 0 || toIndex > size || fromIndex > toIndex) {
    ThrowArrayIndexOutOfBoundsException();
  }
  KChar *address = holder->array;
  for (KInt index = fromIndex; index < toIndex; ++index) {
    *address++ = value;
  }
}

void Kotlin_NativeCharArrayImpl_resizeTo(NativeArrayHolder *holder, KInt size, KInt newSize) {
  auto newArray = (KChar *) realloc(holder->array, newSize * sizeof(KChar));
  if (newArray) {
    holder->array = newArray;
    if (newSize > size) {
      memset(newArray + size, 0, (newSize - size) * sizeof(KChar));
    }
  } else {
    ThrowOutOfMemoryError();
  }
}

} // extern "C"