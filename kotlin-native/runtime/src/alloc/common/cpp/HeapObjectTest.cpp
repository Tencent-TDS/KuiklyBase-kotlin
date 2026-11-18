/*
 * Copyright 2010-2024 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

#include "HeapObject.hpp"

#include "gtest/gtest.h"
#include "gmock/gmock.h"

#include "Natives.h"
#include "Types.h"
// region @Tencent: Include <array> for HeapObjectStringSizeTest.
#include <array>
// endregion

using namespace kotlin;

namespace {

inline constexpr uint32_t kArraySize = 11;

struct ObjectArrayOps {
    static const TypeInfo* arrayTypeInfo() { return theArrayTypeInfo; }
    static uint8_t* addressOfElementAt(ArrayHeader* arr, uint32_t index) {
        return reinterpret_cast<uint8_t*>(ArrayAddressOfElementAt(arr, index));
    }
};

struct ByteArrayOps {
    static const TypeInfo* arrayTypeInfo() { return theByteArrayTypeInfo; }
    static uint8_t* addressOfElementAt(ArrayHeader* arr, uint32_t index) {
        return reinterpret_cast<uint8_t*>(ByteArrayAddressOfElementAt(arr, index));
    }
};

struct LongArrayOps {
    static const TypeInfo* arrayTypeInfo() { return theLongArrayTypeInfo; }
    static uint8_t* addressOfElementAt(ArrayHeader* arr, uint32_t index) {
        return reinterpret_cast<uint8_t*>(LongArrayAddressOfElementAt(arr, index));
    }
};

template <typename HeapHeader, typename ArrayOps>
struct Mode {
    using HeapArray = alloc::HeapArray<HeapHeader>;
    using HeapObject = alloc::HeapObject<HeapHeader>;

    static const TypeInfo* arrayTypeInfo() { return ArrayOps::arrayTypeInfo(); }
    static uint8_t* addressOfElementAt(ArrayHeader* arr, uint32_t index) {
        return ArrayOps::addressOfElementAt(arr, index);
    }
};

} // namespace

template <typename T>
class HeapObjectTest : public testing::Test {};

using TestModes = testing::Types<
    Mode<uint8_t, ObjectArrayOps>,
    Mode<uint8_t, ByteArrayOps>,
    Mode<uint8_t, LongArrayOps>,
    Mode<uint32_t, ObjectArrayOps>,
    Mode<uint32_t, ByteArrayOps>,
    Mode<uint32_t, LongArrayOps>,
    Mode<uint64_t, ObjectArrayOps>,
    Mode<uint64_t, ByteArrayOps>,
    Mode<uint64_t, LongArrayOps>>;

TYPED_TEST_SUITE(HeapObjectTest, TestModes);

TYPED_TEST(HeapObjectTest, ArrayAlignment) {
    auto descriptor = TypeParam::HeapArray::descriptorFrom(TypeParam::arrayTypeInfo(), kArraySize);
    ArrayHeader fakeArray;
    fakeArray.typeInfoOrMeta_ = const_cast<TypeInfo*>(TypeParam::arrayTypeInfo());
    fakeArray.count_ = kArraySize;
    auto& heapArray = TypeParam::HeapArray::from(&fakeArray);
    EXPECT_THAT(heapArray.array(), &fakeArray);
    EXPECT_THAT(static_cast<typename TypeParam::HeapObject&>(heapArray).object()->array(), &fakeArray);
    EXPECT_THAT(
            reinterpret_cast<uint8_t*>(TypeParam::addressOfElementAt(heapArray.array(), kArraySize)),
            testing::Le(reinterpret_cast<uint8_t*>(&heapArray) + descriptor.size()));
}

// region @Tencent: Verify HeapObject::size() returns physical header size for external/proxy strings.
TEST(HeapObjectStringSizeTest, ExternalStringReportsHeaderOnlySize) {
    using HeapObj = alloc::HeapObject<uint64_t>;
    using HeapArr = alloc::HeapArray<uint64_t>;
    constexpr size_t kStorageSize = 64;
    constexpr uint64_t kExternalLengthBytes = 4096;
    constexpr uint32_t kInlineElementCount = 100;

    alignas(kObjectAlignment) std::array<uint8_t, kStorageSize> storage{};
    auto* fakeString = reinterpret_cast<StringHeader*>(storage.data() + sizeof(uint64_t));
    fakeString->typeInfoOrMeta_ = const_cast<TypeInfo*>(theStringTypeInfo);
    fakeString->flags_ = static_cast<uint16_t>(StringType::kExternal) << StringHeader::TYPE_OFFSET;
    fakeString->adjustForExternal(kExternalLengthBytes);

    auto& heapObj = HeapObj::from(reinterpret_cast<ObjHeader*>(fakeString));
    const auto expectedPhysicalSize = HeapArr::descriptorFrom(
            theStringTypeInfo, StringHeader::extraLength(0) / sizeof(KChar)).size();
    EXPECT_EQ(heapObj.size(), expectedPhysicalSize);

    // Default inline string reports full count_-based size.
    fakeString->flags_ = 0;
    fakeString->count_ = kInlineElementCount;
    const auto expectedInlineSize = HeapArr::descriptorFrom(theStringTypeInfo, kInlineElementCount).size();
    EXPECT_EQ(heapObj.size(), expectedInlineSize);
}
// endregion
