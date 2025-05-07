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
#import <Foundation/Foundation.h>
#import <type_traits>

#import "KStringProxy.h"
#import "KStringProxyCompat.h"
#import "Natives.h"
#import "Porting.h"
#import "utf8.h"
#import "polyhash/PolyHash.h"
#import "polyhash_u8/PolyHash.h"
#import "TmmConfig.h"

/**
 * Implementation of KStringProxy.
 * All parameters with type KString should be a valid KStringProxy.
 */
namespace tmm {

ALWAYS_INLINE CFStringRef KStringProxyGetCFStringRef(KString proxy) {
  return static_cast<CFStringRef>(proxy->obj()->GetAssociatedObject());
}

ALWAYS_INLINE void CFStringGetAllCharacters(CFStringRef string, UniChar *buffer, uint32_t length) {
  CFRange range = CFRangeMake(0, length);
  CFStringGetCharacters(string, range, buffer);
}

ALWAYS_INLINE const unsigned char *CFStringGetAsciiCStringPtr(CFStringRef string) {
  return reinterpret_cast<const unsigned char *>(CFStringGetCStringPtr(string, kCFStringEncodingASCII));
}

ALWAYS_INLINE bool IsKStringProxy(KString string) {
  return string->objcFlags_ & FLAG_KSTRING_PROXY;
}

std::string CreateCppStringFromString(KString stringProxy) {
  CFStringRef thizRef = KStringProxyGetCFStringRef(stringProxy);
  auto u16Chars = CFStringGetCharactersPtr(thizRef);
  if (u16Chars) {
    std::string utf8;
    utf8.reserve(stringProxy->count_);
    utf8::unchecked::utf16to8(u16Chars, u16Chars + stringProxy->count_, back_inserter(utf8));
    return utf8;
  }

  auto asciiChars = CFStringGetCStringPtr(thizRef, kCFStringEncodingASCII);
  if (asciiChars) {
    return {asciiChars, stringProxy->count_};
  }

  auto chars = static_cast<UniChar *>(malloc(sizeof(UniChar) * stringProxy->count_));
  CFStringGetAllCharacters(thizRef, chars, stringProxy->count_);

  std::string utf8;
  utf8.reserve(stringProxy->count_);
  utf8::unchecked::utf16to8(chars, chars + stringProxy->count_, back_inserter(utf8));
  free(chars);

  return utf8;
}

char *CreateCStringFromString(KString stringProxy) {
  std::string utf8 = CreateCppStringFromString(stringProxy);
  char *result = reinterpret_cast<char *>(std::calloc(1, utf8.size() + 1));
  ::memcpy(result, utf8.c_str(), utf8.size());
  return result;
}

OBJ_GETTER(Kotlin_Interop_CreateKStringProxyFromImmutableCFString, CFStringRef stringRef) {
  // For KStringProxy, we just create an empty String object.
  ArrayHeader *result = AllocArrayInstance(theStringTypeInfo, 0, OBJ_RESULT)->array();
  result->objcFlags_ |= FLAG_KSTRING_PROXY;
  result->count_ = CFStringGetLength(stringRef);
  result->obj()->SetAssociatedObject((void *) stringRef);
  RETURN_OBJ(result->obj());
}

OBJ_GETTER(Kotlin_Interop_CreateKStringProxyFromCharArray, const KChar *array, KInt size) {
  // For KStringProxy, we just create an empty String object.
  CFStringRef stringRef = CFStringCreateWithCharacters(kCFAllocatorDefault, array, size);
  ArrayHeader *result = AllocArrayInstance(theStringTypeInfo, 0, OBJ_RESULT)->array();
  result->objcFlags_ |= FLAG_KSTRING_PROXY;
  result->count_ = size;
  result->obj()->SetAssociatedObject((void *) stringRef);
  RETURN_OBJ(result->obj());
}

ALWAYS_INLINE OBJ_GETTER(utf8ToUtf16Impl,
                         const char *rawString,
                         const char *end,
                         uint32_t charCount,
                         utf8to16 conversion) {
  auto rawResult = static_cast<KChar *>(malloc(charCount * sizeof(KChar)));
  conversion(rawString, end, rawResult);

  auto stringRef = CFStringCreateWithCharactersNoCopy(kCFAllocatorDefault,
                                                      rawResult,
                                                      charCount,
                                                      kCFAllocatorMalloc);

  return Kotlin_Interop_CreateKStringProxyFromImmutableCFString(stringRef, OBJ_RESULT);
}

ALWAYS_INLINE OBJ_GETTER(unsafeUtf16ToUtf8Impl, KString thizProxy, KInt start, KInt size, utf16to8 conversion) {
  CFStringRef thizRef = KStringProxyGetCFStringRef(thizProxy);
  auto u16Chars = CFStringGetCharactersPtr(thizRef);
  if (u16Chars) {
    const KChar* utf16 = u16Chars + start;
    std::string utf8;
    utf8.reserve(size);
    conversion(utf16, utf16 + size, back_inserter(utf8));
    ArrayHeader *result = AllocArrayInstance(theByteArrayTypeInfo, utf8.size(), OBJ_RESULT)->array();
    ::memcpy(ByteArrayAddressOfElementAt(result, 0), utf8.c_str(), utf8.size());
    RETURN_OBJ(result->obj());
  }

  auto asciiChars = CFStringGetAsciiCStringPtr(thizRef);
  if (asciiChars) {
    ArrayHeader *result = AllocArrayInstance(theByteArrayTypeInfo, size, OBJ_RESULT)->array();
    ::memcpy(ByteArrayAddressOfElementAt(result, 0), asciiChars + start, size);
    RETURN_OBJ(result->obj());
  }

  auto* utf16 = static_cast<KChar *>(malloc(sizeof(KChar) * size));
  CFStringGetCharacters(thizRef, CFRangeMake(start, size), utf16);
  std::string utf8;
  utf8.reserve(size);
  conversion(utf16, utf16 + size, back_inserter(utf8));
  ArrayHeader* result = AllocArrayInstance(theByteArrayTypeInfo, utf8.size(), OBJ_RESULT)->array();
  ::memcpy(ByteArrayAddressOfElementAt(result, 0), utf8.c_str(), utf8.size());
  free(utf16);

  RETURN_OBJ(result->obj());
}

ALWAYS_INLINE OBJ_GETTER(Kotlin_StringProxy_replace, KString thizProxy, KChar oldChar, KChar newChar) {
  CFStringRef thizRef = KStringProxyGetCFStringRef(thizProxy);
  auto length = thizProxy->count_;
  auto chars = (UniChar *) malloc(length * sizeof(UniChar));
  CFStringGetAllCharacters(thizRef, chars, length);

  for (uint32_t index = 0; index < length; ++index) {
    if (chars[index] == oldChar) {
      chars[index] = newChar;
    }
  }

  auto string = CFStringCreateWithCharactersNoCopy(kCFAllocatorDefault, chars, length, kCFAllocatorMalloc);
  return Kotlin_Interop_CreateKStringProxyFromImmutableCFString(string, OBJ_RESULT);
}

ALWAYS_INLINE OBJ_GETTER(Kotlin_StringProxy_plusStringProxyImpl, KString thizProxy, KString otherProxy) {
  CFStringRef thizRef = KStringProxyGetCFStringRef(thizProxy);
  CFStringRef otherRef = KStringProxyGetCFStringRef(otherProxy);
  CFMutableStringRef mutableString = CFStringCreateMutableCopy(kCFAllocatorDefault, 0, thizRef);

  // Append the second string to the mutable string
  CFStringAppend(mutableString, otherRef);

  CFStringRef immutableCopyOrSameStr = CFStringCreateCopy(nullptr, mutableString);
  auto result = Kotlin_Interop_CreateKStringProxyFromImmutableCFString(immutableCopyOrSameStr, OBJ_RESULT);
  CFRelease(mutableString);
  return result;
}

ALWAYS_INLINE OBJ_GETTER(Kotlin_StringProxy_plusStringImpl, KString thizProxy, KString other) {

  uint32_t result_length = thizProxy->count_ + other->count_;
  ArrayHeader *result = AllocArrayInstance(theStringTypeInfo, result_length, OBJ_RESULT)->array();

  CFStringRef thizRef = KStringProxyGetCFStringRef(thizProxy);
  CFStringGetAllCharacters(thizRef,
                           CharArrayAddressOfElementAt(result, 0),
                           thizProxy->count_);

  memcpy(CharArrayAddressOfElementAt(result, thizProxy->count_),
         CharArrayAddressOfElementAt(other, 0),
         other->count_ * sizeof(KChar));
  RETURN_OBJ(result->obj());
}

ALWAYS_INLINE OBJ_GETTER(Kotlin_String_plusStringProxyImpl, KString thiz, KString otherProxy) {
  uint32_t result_length = thiz->count_ + otherProxy->count_;
  ArrayHeader *result = AllocArrayInstance(theStringTypeInfo, result_length, OBJ_RESULT)->array();

  memcpy(CharArrayAddressOfElementAt(result, 0),
         CharArrayAddressOfElementAt(thiz, 0),
         thiz->count_ * sizeof(KChar));

  CFStringRef otherRef = KStringProxyGetCFStringRef(otherProxy);
  CFStringGetAllCharacters(otherRef,
                           CharArrayAddressOfElementAt(result, thiz->count_),
                           otherProxy->count_);

  RETURN_OBJ(result->obj());
}

ALWAYS_INLINE OBJ_GETTER(Kotlin_StringProxy_unsafeStringProxyFromCharArray, KConstRef thiz, KInt start, KInt size) {
  return Kotlin_Interop_CreateKStringProxyFromCharArray(CharArrayAddressOfElementAt(thiz->array(), start), size, OBJ_RESULT);
}

ALWAYS_INLINE OBJ_GETTER(Kotlin_StringProxy_toCharArray,
                         KString stringProxy,
                         KRef destination,
                         KInt destinationOffset,
                         KInt start,
                         KInt size) {
  CFStringRef stringRef = KStringProxyGetCFStringRef(stringProxy);
  ArrayHeader *destinationArray = destination->array();

  CFRange range = CFRangeMake(start, size);
  CFStringGetCharacters(stringRef, range, CharArrayAddressOfElementAt(destinationArray, destinationOffset));

  RETURN_OBJ(destinationArray->obj());
}

ALWAYS_INLINE OBJ_GETTER(Kotlin_StringProxy_subSequence, KString thizProxy, KInt startIndex, KInt endIndex) {
  CFStringRef stringRef = KStringProxyGetCFStringRef(thizProxy);

  CFStringRef subStringRef = CFStringCreateWithSubstring(kCFAllocatorDefault,
                                                         stringRef,
                                                         CFRangeMake(startIndex, endIndex - startIndex));
  return Kotlin_Interop_CreateKStringProxyFromImmutableCFString(subStringRef, OBJ_RESULT);
}

ALWAYS_INLINE KInt Kotlin_StringProxy_compareToStringProxy(KString thizProxy, KString otherProxy) {
  CFStringRef thizRef = KStringProxyGetCFStringRef(thizProxy);
  CFStringRef otherRef = KStringProxyGetCFStringRef(otherProxy);
  return CFStringCompare(thizRef, otherRef, 0);
}

ALWAYS_INLINE KInt Kotlin_StringProxy_compareToString(KString thizProxy, KString other) {
  CFStringRef thizRef = KStringProxyGetCFStringRef(thizProxy);
  CFStringRef otherRef = CFStringCreateWithCharactersNoCopy(kCFAllocatorDefault,
                                                            CharArrayAddressOfElementAt(other, 0),
                                                            other->count_,
                                                            kCFAllocatorNull);
  auto result = CFStringCompare(thizRef, otherRef, 0);
  CFRelease(otherRef);
  return result;
}

ALWAYS_INLINE KInt Kotlin_String_compareToStringProxy(KString thiz, KString otherProxy) {
  CFStringRef thizRef = CFStringCreateWithCharactersNoCopy(kCFAllocatorDefault,
                                                           CharArrayAddressOfElementAt(thiz, 0),
                                                           thiz->count_,
                                                           kCFAllocatorNull);
  CFStringRef otherRef = KStringProxyGetCFStringRef(otherProxy);
  auto result = CFStringCompare(thizRef, otherRef, 0);
  CFRelease(thizRef);
  return result;
}

ALWAYS_INLINE KChar Kotlin_StringProxy_get(KString thizProxy, KInt index) {
  CFStringRef thizRef = KStringProxyGetCFStringRef(thizProxy);
  return CFStringGetCharacterAtIndex(thizRef, index);
}

// Both parameters are instances of StringProxy.
ALWAYS_INLINE KBoolean Kotlin_StringProxy_equalsWithStringProxy(KString thizProxy, KString otherProxy) {
  CFStringRef thizRef = KStringProxyGetCFStringRef(thizProxy);
  CFStringRef otherRef = KStringProxyGetCFStringRef(otherProxy);
  return CFStringCompare(thizRef, otherRef, 0) == 0;
}

// Only the first parameter is an instance of StringProxy.
ALWAYS_INLINE KBoolean Kotlin_StringProxy_equalsWithString(KString stringProxy, KString string) {
  CFStringRef stringProxyRef = KStringProxyGetCFStringRef(stringProxy);
  CFStringRef stringRef = CFStringCreateWithCharactersNoCopy(kCFAllocatorDefault,
                                                             CharArrayAddressOfElementAt(string, 0),
                                                             string->count_,
                                                             kCFAllocatorNull);

  auto result = CFStringCompare(stringProxyRef, stringRef, 0) == 0;
  CFRelease(stringRef);
  return result;
}

ALWAYS_INLINE KBoolean Kotlin_StringProxy_unsafeRangeEqualsWithStringProxy(KString thizProxy,
                                                                           KInt thizOffset,
                                                                           KString otherProxy,
                                                                           KInt otherOffset,
                                                                           KInt length) {
  CFStringRef thizRef = KStringProxyGetCFStringRef(thizProxy);
  CFStringRef otherRef = KStringProxyGetCFStringRef(otherProxy);

  CFStringInlineBuffer thizBuffer;
  CFStringInitInlineBuffer(thizRef, &thizBuffer, CFRangeMake(thizOffset, length));

  CFStringInlineBuffer otherBuffer;
  CFStringInitInlineBuffer(otherRef, &otherBuffer, CFRangeMake(otherOffset, length));

  for (int i = 0; i < length; ++i) {
    auto thizChar = CFStringGetCharacterFromInlineBuffer(&thizBuffer, i);
    auto otherChar = CFStringGetCharacterFromInlineBuffer(&otherBuffer, i);
    if (thizChar != otherChar) return false;
  }
  return true;
}

ALWAYS_INLINE KBoolean Kotlin_StringProxy_unsafeRangeEqualsWithString(KString stringProxy,
                                                                      KInt stringProxyOffset,
                                                                      KString string,
                                                                      KInt stringOffset,
                                                                      KInt length) {
  CFStringRef stringProxyRef = KStringProxyGetCFStringRef(stringProxy);

  CFStringInlineBuffer stringProxyBuffer;
  CFStringInitInlineBuffer(stringProxyRef, &stringProxyBuffer, CFRangeMake(stringProxyOffset, length));

  auto stringCharsPtr = CharArrayAddressOfElementAt(string, stringOffset);
  for (int i = 0; i < length; ++i) {
    auto thizChar = CFStringGetCharacterFromInlineBuffer(&stringProxyBuffer, i);
    auto otherChar = stringCharsPtr[i];
    if (thizChar != otherChar) return false;
  }
  return true;
}

// region indexOfChar
template<typename CharType>
ALWAYS_INLINE KInt CharArray_indexOfChar(CharType *array, uint32_t size, KInt fromIndex, KChar ch) {
  using UnsignedCharType = typename std::make_unsigned<CharType>::type;
  auto unsignedCharArray = reinterpret_cast<UnsignedCharType *>(array);
  auto thizRaw = unsignedCharArray + fromIndex;
  while (static_cast<uint32_t>(fromIndex) < size) {
    if (*thizRaw++ == ch) return fromIndex;
    fromIndex++;
  }
  return -1;
}

ALWAYS_INLINE KInt CFString_indexOfChar(CFStringRef stringRef, uint32_t size, KInt fromIndex, KChar ch) {
  CFStringRef otherRef = CFStringCreateWithCharactersNoCopy(kCFAllocatorDefault, &ch, 1, kCFAllocatorNull);
  CFRange range;
  KInt result = -1;
  if (CFStringFindWithOptions(stringRef, otherRef, CFRangeMake(fromIndex, size - fromIndex), 0, &range)) {
    result = range.location;
  }
  CFRelease(otherRef);
  return result;
}

ALWAYS_INLINE KInt Kotlin_StringProxy_indexOfChar(KString thizProxy, KChar ch, KInt fromIndex) {
  CFStringRef thizRef = KStringProxyGetCFStringRef(thizProxy);
  auto u16Chars = CFStringGetCharactersPtr(thizRef);
  if (u16Chars) {
    return CharArray_indexOfChar(u16Chars, thizProxy->count_, fromIndex, ch);
  }
  auto asciiChars = CFStringGetAsciiCStringPtr(thizRef);
  if (asciiChars) {
    return CharArray_indexOfChar(asciiChars, thizProxy->count_, fromIndex, ch);
  }
  return CFString_indexOfChar(thizRef, thizProxy->count_, fromIndex, ch);
}
// endregion

// region lastIndexOfChar
template<typename CharType>
ALWAYS_INLINE KInt CharArray_lastIndexOfChar(CharType *array, uint32_t size, KInt fromIndex, KChar ch) {
  using UnsignedCharType = typename std::make_unsigned<CharType>::type;
  auto unsignedCharArray = reinterpret_cast<UnsignedCharType *>(array);
  KInt index = fromIndex;
  auto thizRaw = unsignedCharArray + fromIndex;
  while (index >= 0) {
    if (*thizRaw-- == ch) return index;
    index--;
  }
  return -1;
}

ALWAYS_INLINE KInt CFString_lastIndexOfChar(CFStringRef stringRef, uint32_t size, KInt fromIndex, KChar ch) {
  CFStringRef otherRef = CFStringCreateWithCharactersNoCopy(kCFAllocatorDefault, &ch, 1, kCFAllocatorNull);
  CFRange range;
  KInt result = -1;
  if (CFStringFindWithOptions(stringRef, otherRef, CFRangeMake(0, fromIndex + 1), kCFCompareBackwards, &range)) {
    result = range.location;
  }
  CFRelease(otherRef);
  return result;
}

ALWAYS_INLINE KInt Kotlin_StringProxy_lastIndexOfChar(KString thizProxy, KChar ch, KInt fromIndex) {
  CFStringRef thizRef = KStringProxyGetCFStringRef(thizProxy);
  auto u16Chars = CFStringGetCharactersPtr(thizRef);
  if (u16Chars) {
    return CharArray_lastIndexOfChar(u16Chars, thizProxy->count_, fromIndex, ch);
  }

  auto asciiChars = CFStringGetAsciiCStringPtr(thizRef);
  if (asciiChars) {
    return CharArray_lastIndexOfChar(asciiChars, thizProxy->count_, fromIndex, ch);
  }

  return CFString_lastIndexOfChar(thizRef, thizProxy->count_, fromIndex, ch);
}
// endregion

// indexOfString
ALWAYS_INLINE KInt Kotlin_StringProxy_indexOfStringProxy(KString thizProxy, KString otherProxy, KInt fromIndex) {
  CFStringRef thizRef = KStringProxyGetCFStringRef(thizProxy);
  CFStringRef otherRef = KStringProxyGetCFStringRef(otherProxy);

  CFRange range;
  if (CFStringFindWithOptions(thizRef,
                              otherRef,
                              CFRangeMake(fromIndex, thizProxy->count_ - fromIndex),
                              0,
                              &range)) {
    return range.location;
  }
  return -1;
}

ALWAYS_INLINE KInt Kotlin_StringProxy_indexOfString(KString thizProxy, KString other, KInt fromIndex) {
  CFStringRef thizRef = KStringProxyGetCFStringRef(thizProxy);
  CFStringRef otherRef = CFStringCreateWithCharactersNoCopy(kCFAllocatorDefault,
                                                            CharArrayAddressOfElementAt(other, 0),
                                                            other->count_,
                                                            kCFAllocatorNull);

  CFRange range;
  KInt result = -1;
  if (CFStringFindWithOptions(thizRef,
                              otherRef,
                              CFRangeMake(fromIndex, thizProxy->count_ - fromIndex),
                              0,
                              &range)) {
    result = range.location;
  }

  CFRelease(otherRef);
  return result;
}

ALWAYS_INLINE KInt Kotlin_String_indexOfStringProxy(KString thiz, KString otherProxy, KInt fromIndex) {
  CFStringRef thizRef = CFStringCreateWithCharactersNoCopy(kCFAllocatorDefault,
                                                           CharArrayAddressOfElementAt(thiz, 0),
                                                           thiz->count_,
                                                           kCFAllocatorNull);
  CFStringRef otherRef = KStringProxyGetCFStringRef(otherProxy);

  CFRange range;
  KInt result = -1;
  if (CFStringFindWithOptions(thizRef,
                              otherRef,
                              CFRangeMake(fromIndex, thiz->count_ - fromIndex),
                              0,
                              &range)) {
    result = range.location;
  }

  CFRelease(thizRef);
  return result;
}
// endregion

// lastIndexOfString
ALWAYS_INLINE KInt Kotlin_StringProxy_lastIndexOfStringProxy(KString thizProxy, KString otherProxy, KInt fromIndex) {
  CFStringRef thizRef = KStringProxyGetCFStringRef(thizProxy);
  CFStringRef otherRef = KStringProxyGetCFStringRef(otherProxy);

  CFRange result;
  if (CFStringFindWithOptions(thizRef,
                              otherRef,
                              CFRangeMake(0, MIN(fromIndex + otherProxy->count_, thizProxy->count_)),
                              kCFCompareBackwards,
                              &result)) {
    return result.location;
  }
  return -1;
}

ALWAYS_INLINE KInt Kotlin_StringProxy_lastIndexOfString(KString thizProxy, KString other, KInt fromIndex) {
  CFStringRef thizRef = KStringProxyGetCFStringRef(thizProxy);
  CFStringRef otherRef = CFStringCreateWithCharactersNoCopy(kCFAllocatorDefault,
                                                            CharArrayAddressOfElementAt(other, 0),
                                                            other->count_,
                                                            kCFAllocatorNull);

  CFRange range;
  KInt result = -1;
  if (CFStringFindWithOptions(thizRef,
                              otherRef,
                              CFRangeMake(0, MIN(fromIndex + other->count_, thizProxy->count_)),
                              kCFCompareBackwards,
                              &range)) {
    result = range.location;
  }

  CFRelease(otherRef);
  return result;
}

ALWAYS_INLINE KInt Kotlin_String_lastIndexOfStringProxy(KString thiz, KString otherProxy, KInt fromIndex) {
  CFStringRef thizRef = CFStringCreateWithCharactersNoCopy(kCFAllocatorDefault,
                                                            CharArrayAddressOfElementAt(thiz, 0),
                                                            thiz->count_,
                                                            kCFAllocatorNull);
  CFStringRef otherRef = KStringProxyGetCFStringRef(otherProxy);

  CFRange range;
  KInt result = -1;
  if (CFStringFindWithOptions(thizRef,
                              otherRef,
                              CFRangeMake(0, MIN(fromIndex + otherProxy->count_, thiz->count_)),
                              kCFCompareBackwards,
                              &range)) {
    result = range.location;
  }

  CFRelease(thizRef);
  return result;
}
// endregion

ALWAYS_INLINE KInt Kotlin_StringProxy_hashCode(KString thizProxy) {
  CFStringRef thizRef = KStringProxyGetCFStringRef(thizProxy);
  auto u16Chars = CFStringGetCharactersPtr(thizRef);
  if (u16Chars) {
    return polyHash(static_cast<int>(thizProxy->count_), u16Chars);
  }

  auto asciiChars = CFStringGetAsciiCStringPtr(thizRef);
  if (asciiChars) {
    return polyHash(static_cast<int>(thizProxy->count_), asciiChars);
  }

  auto chars = static_cast<UniChar *>(malloc(sizeof(UniChar) * thizProxy->count_));
  CFStringGetAllCharacters(thizRef, chars, thizProxy->count_);
  auto hashCode = polyHash(static_cast<int>(thizProxy->count_), chars);
  free(chars);

  return hashCode;
}

ALWAYS_INLINE KInt Kotlin_StringBuilder_insertStringProxy(ArrayHeader *toArray,
                                                          KInt distIndex,
                                                          KString fromStringProxy,
                                                          KInt sourceIndex,
                                                          KInt count) {
  auto stringRef = KStringProxyGetCFStringRef(fromStringProxy);
  auto buffer = CharArrayAddressOfElementAt(toArray, distIndex);
  CFStringGetCharacters(stringRef, CFRangeMake(sourceIndex, count), buffer);

  return count;
}
// endregion

// region NativeCharArray
ALWAYS_INLINE KInt Kotlin_NativeCharArrayImpl_insertStringProxy(KChar *array,
                                                                KInt size,
                                                                KInt distIndex,
                                                                KString fromStringProxy,
                                                                KInt sourceIndex,
                                                                KInt count) {
  auto stringRef = KStringProxyGetCFStringRef(fromStringProxy);
  CFStringGetCharacters(stringRef, CFRangeMake(sourceIndex, count), array + distIndex);

  return count;
}

ALWAYS_INLINE void Kotlin_NativeCharArrayImpl_fillStringProxy(KChar *array,
                                                              KInt size,
                                                              KInt offset,
                                                              KString stringProxy,
                                                              KInt start,
                                                              KInt count) {
  auto stringRef = KStringProxyGetCFStringRef(stringProxy);
  CFStringGetCharacters(stringRef, CFRangeMake(start, count), array + offset);
}
// endregion

// region Console
ALWAYS_INLINE void Kotlin_io_Console_printStringProxy(KString stringProxy) {
  auto utf8 = CreateCppStringFromString(stringProxy);
  kotlin::ThreadStateGuard guard(kotlin::ThreadState::kNative);
  konan::consoleWriteUtf8(utf8.c_str(), utf8.size());
}

ALWAYS_INLINE void Kotlin_io_Console_printStringProxyToStdErr(KString stringProxy) {
  auto utf8 = CreateCppStringFromString(stringProxy);
  kotlin::ThreadStateGuard guard(kotlin::ThreadState::kNative);
  konan::consoleErrorUtf8(utf8.c_str(), utf8.size());
}
// endregion

} // namespace tmm

#endif // KONAN_OBJC_INTEROP
