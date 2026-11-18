/*
 * Copyright 2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

#include "KString.h"

#include <cstdlib>

#include "gtest/gtest.h"
#include "gmock/gmock.h"

#include "Natives.h"
// region @Tencent: Include runtime test helpers for Owned UTF-8/UTF-16 string tests.
#include <cstring>
#include "FinalizerHooks.hpp"
#include "GlobalData.hpp"
#include "TestSupport.hpp"
// endregion

using namespace kotlin;

template <typename Unit>
void checkContentsEquality(const char* ascii, const Unit* expected) {
    auto actual = CreatePermanentStringFromCString(ascii);
    auto header = StringHeader::of(actual);
    EXPECT_THAT(header->encoding(), sizeof(Unit) == 1 ? StringEncoding::kLatin1 : StringEncoding::kUTF16);
    size_t size = header->size() / sizeof(Unit);
    EXPECT_THAT(size, std::char_traits<Unit>::length(expected));
    const Unit* data = reinterpret_cast<const Unit*>(header->data());
    for (size_t i=0; i<size; i++) {
        EXPECT_THAT(data[i], expected[i]);
    }
    EXPECT_THAT(actual->permanent(), true);
    FreePermanentStringForTests(actual);  // to prevent Address Sanitizer test failures, permanently allocated strings must be deallocated before test end
}

TEST(KStringTest, CreatePermanentStringFromCString_ascii) {
    const char* ascii = "Ascii";
    EXPECT_THAT(strlen(ascii), 5);
    checkContentsEquality(ascii, ascii);
}

TEST(KStringTest, CreatePermanentStringFromCString_misc) {
    const char* non_ascii = "-£öü²ソニーΣℜ∫♣‰€";
    EXPECT_THAT(strlen(non_ascii), 35);
    const char16_t* expected = u"-£öü²ソニーΣℜ∫♣‰€";
    EXPECT_THAT(std::char_traits<char16_t>::length(expected), 14);
    checkContentsEquality(non_ascii, expected);
}

TEST(KStringTest, CreatePermanentStringFromCString_surrogates) {
    const char* surrogates = "😃𓄀🌀🐀𝜀";
    EXPECT_THAT(strlen(surrogates), 20);
    const char16_t* expected = u"😃𓄀🌀🐀𝜀";
    EXPECT_THAT(std::char_traits<char16_t>::length(expected), 10);
    checkContentsEquality(surrogates, expected);
}

TEST(KStringTest, CreatePermanentStringFromCString_empty) {
    const char* empty = "";
    EXPECT_THAT(strlen(empty), 0);
    checkContentsEquality(empty, empty);
}

TEST(KStringTest, CreatePermanentStringFromCString_impossible) {
    const char* impossible = "\xff";
    EXPECT_THAT(strlen(impossible), 1);
    const char16_t* expected = u"\xfffd";
    EXPECT_THAT(std::char_traits<char16_t>::length(expected), 1);
    checkContentsEquality(impossible, expected);
}

TEST(KStringTest, CreatePermanentStringFromCString_overlong) {
    const char* overlong = "\xc0\xaf";
    EXPECT_THAT(strlen(overlong), 2);
    const char16_t* expected = u"\xfffd\xfffd";
    EXPECT_THAT(std::char_traits<char16_t>::length(expected), 2);
    checkContentsEquality(overlong, expected);
}

// region @Tencent: Unit tests for CreateStringFromOwnedUtf8/Utf16 and WithFreeCallback variants.
extern "C" KInt Kotlin_String_hashCode(KRef thiz);

namespace {

struct FreeRecord {
    int count = 0;
    void* lastPtr = nullptr;
    void* expectedBasePtr = nullptr;
    bool wasNativeThreadState = false;
    bool freeUnderlying = false;
};

void recordFreeCallback(void* mem, void* context) {
    auto* record = static_cast<FreeRecord*>(context);
    record->count++;
    record->lastPtr = mem;
    record->wasNativeThreadState = !kotlin::mm::IsCurrentThreadRegistered() || kotlin::GetThreadState() == kotlin::ThreadState::kNative;
    if (record->freeUnderlying) {
        std::free(record->expectedBasePtr != nullptr ? record->expectedBasePtr : mem);
    }
}

class OwnedKStringTest : public testing::Test {
protected:
    void TearDown() override {
        mm::GlobalsRegistry::Instance().ClearForTests();
        mm::GlobalData::Instance().gc().ClearForTests();
        mm::GlobalData::Instance().allocator().clearForTests();
    }
};

} // namespace

TEST_F(OwnedKStringTest, OwnedUtf8_AsciiZeroCopyAndDeferredCustomFree) {
    RunInNewThread([]() {
        const char* src = "Hello"; // Odd length (5) to exercise IGNORE_LAST_BYTE
        const size_t srcLen = std::strlen(src);
        char* owned = static_cast<char*>(std::malloc(srcLen + 1));
        std::memcpy(owned, src, srcLen + 1);

        FreeRecord record{};
        record.freeUnderlying = true;

        ObjHolder holder;
        KRef str = CreateStringFromOwnedUtf8WithFreeCallback(owned, -1, &recordFreeCallback, &record, holder.slot());
        ASSERT_NE(str, nullptr);

        auto* header = StringHeader::of(str);
        EXPECT_EQ(header->type(), StringType::kExternal);
        EXPECT_EQ(header->encoding(), StringEncoding::kLatin1);
        EXPECT_EQ(header->data(), owned);
        EXPECT_EQ(header->size(), 5U);
        EXPECT_EQ(Kotlin_String_getStringLength(str), 5);
        EXPECT_EQ(kotlin::to_string<KStringConversionMode::UNCHECKED>(str), "Hello");
        EXPECT_EQ(record.count, 0);

        ObjHolder regularHolder;
        KRef regular = CreateStringFromCString(src, regularHolder.slot());
        EXPECT_EQ(Kotlin_String_hashCode(str), Kotlin_String_hashCode(regular));

        holder.clear();
        RunFinalizers(str);

        EXPECT_EQ(record.count, 1);
        EXPECT_EQ(record.lastPtr, owned);
        EXPECT_TRUE(record.wasNativeThreadState);
    });
}

TEST_F(OwnedKStringTest, OwnedUtf8_NonAsciiFallbackFreesImmediately) {
    RunInNewThread([]() {
        const char* src = "你好，Kotlin 🚀";
        const size_t len = std::strlen(src);
        char* owned = static_cast<char*>(std::malloc(len));
        std::memcpy(owned, src, len);

        FreeRecord record{};
        record.freeUnderlying = true;

        ObjHolder holder;
        KRef str = CreateStringFromOwnedUtf8WithFreeCallback(
            owned, static_cast<int32_t>(len), &recordFreeCallback, &record, holder.slot());
        ASSERT_NE(str, nullptr);

        EXPECT_EQ(record.count, 1);
        EXPECT_EQ(record.lastPtr, owned);
        EXPECT_TRUE(record.wasNativeThreadState);

        auto* header = StringHeader::of(str);
        EXPECT_EQ(header->type(), StringType::kDefault);
        EXPECT_EQ(header->encoding(), StringEncoding::kUTF16);
        EXPECT_EQ(kotlin::to_string<KStringConversionMode::UNCHECKED>(str), src);
    });
}

TEST_F(OwnedKStringTest, OwnedUtf8_NullEmptyAndNullCallback) {
    RunInNewThread([]() {
        FreeRecord record{};

        ObjHolder nullHolder;
        EXPECT_EQ(CreateStringFromOwnedUtf8WithFreeCallback(nullptr, -1, &recordFreeCallback, &record, nullHolder.slot()), nullptr);
        EXPECT_EQ(record.count, 0);

        char emptyBuf[1] = {'\0'};
        ObjHolder emptyHolder1;
        KRef empty1 = CreateStringFromOwnedUtf8WithFreeCallback(&emptyBuf[0], -1, &recordFreeCallback, &record, emptyHolder1.slot());
        EXPECT_EQ(Kotlin_String_getStringLength(empty1), 0);
        EXPECT_EQ(record.count, 1);
        EXPECT_EQ(record.lastPtr, &emptyBuf[0]);

        char nonEmptySlice[4] = {'a', 'b', 'c', '\0'};
        ObjHolder emptyHolder2;
        KRef empty2 = CreateStringFromOwnedUtf8WithFreeCallback(&nonEmptySlice[0], 0, &recordFreeCallback, &record, emptyHolder2.slot());
        EXPECT_EQ(Kotlin_String_getStringLength(empty2), 0);
        EXPECT_EQ(record.count, 2);
        EXPECT_EQ(record.lastPtr, &nonEmptySlice[0]);

        // Null freeCallback: zero-copy wraps static buffer without freeing on finalization
        static char staticLiteral[] = "StaticOwnedAscii";
        ObjHolder staticHolder;
        KRef staticStr = CreateStringFromOwnedUtf8WithFreeCallback(&staticLiteral[0], -1, nullptr, &record, staticHolder.slot());
        EXPECT_EQ(StringHeader::of(staticStr)->type(), StringType::kExternal);
        EXPECT_EQ(StringHeader::of(staticStr)->data(), &staticLiteral[0]);
        staticHolder.clear();
        RunFinalizers(staticStr);
        EXPECT_EQ(record.count, 2);
    });
}

TEST_F(OwnedKStringTest, OwnedUtf16_ZeroCopyAllCharsAndDeferredCustomFree) {
    RunInNewThread([]() {
        const char16_t* src = u"你好 Kotlin 🤣😁😜";
        const size_t len = std::char_traits<char16_t>::length(src);
        auto* owned = static_cast<KChar*>(std::malloc((len + 1) * sizeof(KChar)));
        std::memcpy(owned, src, (len + 1) * sizeof(KChar));

        FreeRecord record{};
        record.freeUnderlying = true;

        ObjHolder holder;
        KRef str = CreateStringFromOwnedUtf16WithFreeCallback(owned, -1, &recordFreeCallback, &record, holder.slot());
        ASSERT_NE(str, nullptr);

        auto* header = StringHeader::of(str);
        EXPECT_EQ(header->type(), StringType::kExternal);
        EXPECT_EQ(header->encoding(), StringEncoding::kUTF16);
        EXPECT_EQ(reinterpret_cast<const KChar*>(header->data()), owned);
        EXPECT_EQ(static_cast<size_t>(Kotlin_String_getStringLength(str)), len);
        EXPECT_EQ(record.count, 0);

        ObjHolder pinnedHolder;
        EXPECT_EQ(ConvertStringToUtf16(str, pinnedHolder.slot()), str);

        ObjHolder regularHolder;
        KRef regular = CreateStringFromUtf16(reinterpret_cast<const KChar*>(src), static_cast<uint32_t>(len), regularHolder.slot());
        EXPECT_EQ(Kotlin_String_hashCode(str), Kotlin_String_hashCode(regular));
        EXPECT_EQ(kotlin::to_string<KStringConversionMode::UNCHECKED>(str),
                  kotlin::to_string<KStringConversionMode::UNCHECKED>(regular));

        pinnedHolder.clear();
        holder.clear();
        RunFinalizers(str);

        EXPECT_EQ(record.count, 1);
        EXPECT_EQ(record.lastPtr, owned);
        EXPECT_TRUE(record.wasNativeThreadState);
    });
}

TEST_F(OwnedKStringTest, OwnedUtf16_UnalignedFallbackFreesImmediately) {
    RunInNewThread([]() {
        const char16_t* src = u"Unaligned 鸿蒙 🎉";
        const size_t len = std::char_traits<char16_t>::length(src);
        auto* rawAlloc = static_cast<uint8_t*>(std::malloc((len + 1) * sizeof(KChar) + 1));
        auto* unalignedPtr = reinterpret_cast<KChar*>(rawAlloc + 1);
        ASSERT_NE(reinterpret_cast<uintptr_t>(unalignedPtr) % alignof(KChar), 0U);
        std::memcpy(unalignedPtr, src, (len + 1) * sizeof(KChar));

        FreeRecord record{};
        record.expectedBasePtr = rawAlloc;
        record.freeUnderlying = true;

        ObjHolder holder;
        KRef str = CreateStringFromOwnedUtf16WithFreeCallback(unalignedPtr, -1, &recordFreeCallback, &record, holder.slot());
        ASSERT_NE(str, nullptr);

        EXPECT_EQ(record.count, 1);
        EXPECT_EQ(record.lastPtr, unalignedPtr);
        EXPECT_TRUE(record.wasNativeThreadState);

        auto* header = StringHeader::of(str);
        EXPECT_EQ(header->type(), StringType::kDefault);
        EXPECT_EQ(header->encoding(), StringEncoding::kUTF16);

        ObjHolder regularHolder;
        KRef regular = CreateStringFromUtf16(reinterpret_cast<const KChar*>(src), static_cast<uint32_t>(len), regularHolder.slot());
        EXPECT_EQ(kotlin::to_string<KStringConversionMode::UNCHECKED>(str),
                  kotlin::to_string<KStringConversionMode::UNCHECKED>(regular));
    });
}

TEST_F(OwnedKStringTest, OwnedUtf8AndUtf16_DefaultFree) {
    RunInNewThread([]() {
        const char* utf8Src = "World";
        const size_t utf8Len = std::strlen(utf8Src);
        char* utf8Ascii = static_cast<char*>(std::malloc(utf8Len + 1));
        std::memcpy(utf8Ascii, utf8Src, utf8Len + 1);
        ObjHolder utf8Holder;
        KRef utf8Str = CreateStringFromOwnedUtf8(utf8Ascii, static_cast<int32_t>(utf8Len), utf8Holder.slot());
        EXPECT_EQ(kotlin::to_string<KStringConversionMode::UNCHECKED>(utf8Str), "World");

        const char16_t* u16Src = u"OwnedUtf16Default";
        const size_t u16Len = std::char_traits<char16_t>::length(u16Src);
        auto* u16Buf = static_cast<KChar*>(std::malloc(u16Len * sizeof(KChar)));
        std::memcpy(u16Buf, u16Src, u16Len * sizeof(KChar));
        ObjHolder utf16Holder;
        KRef utf16Str = CreateStringFromOwnedUtf16(u16Buf, static_cast<int32_t>(u16Len), utf16Holder.slot());
        EXPECT_EQ(kotlin::to_string<KStringConversionMode::UNCHECKED>(utf16Str), "OwnedUtf16Default");
    });
}
// endregion
