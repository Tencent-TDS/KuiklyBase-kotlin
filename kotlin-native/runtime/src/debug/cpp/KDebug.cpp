/*
 * Copyright 2010-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "KDebug.h"

#include <string>
#include <cstring>

#include "KAssert.h"
#include "KString.h"
#include "Memory.h"
#include "Natives.h"
#include "Porting.h"
#include "Types.h"
#include <cstdlib>
#include <string>
#include <stdio.h>
#include <stdint.h>

#ifndef KONAN_NO_DEBUG_API

extern "C" OBJ_GETTER(KonanObjectToUtf8Array, KRef object);

namespace {

char debugBuffer[4096];

constexpr int runtimeTypeSize[] = {
    -1,                  // INVALID
    sizeof(ObjHeader*),  // OBJECT
    1,                   // INT8
    2,                   // INT16
    4,                   // INT32
    8,                   // INT64
    4,                   // FLOAT32
    8,                   // FLOAT64
    sizeof(void*),       // NATIVE_PTR
    1,                   // BOOLEAN
    16                   // VECTOR128
};

constexpr int runtimeTypeAlignment[] = {
    -1,                  // INVALID
    alignof(ObjHeader*), // OBJECT
    alignof(int8_t),     // INT8
    alignof(int16_t),    // INT16
    alignof(int32_t),    // INT32
    alignof(int64_t),    // INT64
    alignof(float),      // FLOAT32
    alignof(double),     // FLOAT64
    alignof(void*),      // NATIVE_PTR
    1,                   // BOOLEAN
    16                   // VECTOR128
};

// Never ever change numbering in this enum, as it will break debugging of older binaries.
enum Konan_DebugOperation {
  DO_DebugBuffer = 1,
  DO_DebugBufferSize = 2,
  DO_DebugBufferWithObject = 3,
  DO_DebugBufferSizeWithObject = 4,
  DO_DebugObjectToUtf8Array = 5,
  DO_DebugPrint = 6,
  DO_DebugIsArray = 7,
  DO_DebugGetFieldCount = 8,
  DO_DebugGetFieldType = 9,
  DO_DebugGetFieldAddress = 10,
  DO_DebugGetFieldName = 11,
  DO_DebugGetTypeName = 12,
  DO_DebugGetFieldsNameList = 13,
};

template <typename F>
F getImpl(KRef obj, Konan_DebugOperation operation) {
  if (obj == nullptr) {
    return nullptr;
  }

  auto* typeInfo = obj->type_info();

  auto* extendedTypeInfo = typeInfo->extendedInfo_;

  if (extendedTypeInfo == nullptr) {
    return nullptr;
  }

  if (static_cast<int32_t>(operation) >= extendedTypeInfo->debugOperationsCount_) {
    return nullptr;
  }

  F returnF = reinterpret_cast<F>(extendedTypeInfo->debugOperations_[operation]);

  return returnF;
}

// Buffer that can be used by debugger for inspections.
char* Konan_DebugBufferImpl() {
  return debugBuffer;
}

int Konan_DebugBufferSizeImpl() {
  return sizeof(debugBuffer);
}

char* Konan_DebugBufferWithObjectImpl(KRef obj) {
  return debugBuffer;
}

int Konan_DebugBufferSizeWithObjectImpl(KRef obj) {
  return sizeof(debugBuffer);
}

// Auxilary function which can be called by developer/debugger to inspect an object.
int32_t Konan_DebugObjectToUtf8ArrayImpl(KRef obj, char* buffer, int32_t bufferSize) {
  // We need the runnable thread state to call the Kotlin function 'KonanObjectToUtf8Array' and operate with it's result.
  // But the current thread can be in any state when this function is called by the debugger. So we use the reentrant state switch.
  // Finally, let's be on the safe side and assume that current thread might be uninitialized,
  // so use CalledFromNativeGuard instead of ThreadStateGuard.
  kotlin::CalledFromNativeGuard guard(/* reentrant */ true);
  ObjHolder stringHolder;
  // Kotlin call.
  ArrayHeader* data = KonanObjectToUtf8Array(obj, stringHolder.slot())->array();
  if (data == nullptr) {
      return 0;
  }
  if (bufferSize < 1) {
      return 0;
  }
  KInt toCopy = data->count_ > static_cast<uint32_t>(bufferSize - 1) ? bufferSize - 1 : data->count_;
  ::memcpy(buffer, ByteArrayAddressOfElementAt(data, 0), toCopy);
  buffer[toCopy] = '\0';
  return toCopy + 1;
}

int32_t Konan_DebugPrintImpl(KRef obj) {
  int32_t size = Konan_DebugObjectToUtf8Array(obj, Konan_DebugBuffer(), Konan_DebugBufferSize());
  if (size > 1) {
    konan::consoleWriteUtf8(Konan_DebugBuffer(), size - 1);
  }
  return 0;
}

int32_t Konan_DebugIsArrayImpl(KRef obj) {
  return obj == nullptr || IsArray(obj) ? 1 : 0;
}

int32_t Konan_DebugGetFieldCountImpl(KRef obj) {
  if (obj == nullptr) {
    return 0;
  }
  auto* typeInfo = obj->type_info();
  auto* extendedTypeInfo = typeInfo->extendedInfo_;

  if (extendedTypeInfo == nullptr) {
    return 0;
  }

  if (IsArray(obj)) {
    return obj->array()->count_;
  }

  return extendedTypeInfo->fieldsCount_;
}

int32_t Konan_DebugGetFieldTypeImpl(KRef obj, int32_t index) {
  if (obj == nullptr || index < 0) {
    return Konan_RuntimeType::RT_INVALID;
  }

  auto typeInfo = obj->type_info();
  auto extendedTypeInfo = typeInfo->extendedInfo_;

  if (extendedTypeInfo == nullptr) {
    return Konan_RuntimeType::RT_INVALID;
  }

  if (extendedTypeInfo->fieldsCount_ < 0) {
    return -extendedTypeInfo->fieldsCount_;
  }

  if (index >= extendedTypeInfo->fieldsCount_) {
    return Konan_RuntimeType::RT_INVALID;
  }

  return extendedTypeInfo->fieldTypes_[index];
}

void* Konan_DebugGetFieldAddressImpl(KRef obj, int32_t index) {
  if (obj == nullptr || index < 0) {
    return nullptr;
  }

  auto typeInfo = obj->type_info();
  auto extendedTypeInfo = typeInfo->extendedInfo_;

  if (extendedTypeInfo == nullptr) {
    return nullptr;
  }

   if (extendedTypeInfo->fieldsCount_ < 0) {
     if (static_cast<uint32_t>(index) > obj->array()->count_) {
        return nullptr;
     }
      int32_t typeIndex = -extendedTypeInfo->fieldsCount_;
      return reinterpret_cast<uint8_t*>(obj->array())
          + alignUp(sizeof(struct ArrayHeader), runtimeTypeAlignment[typeIndex])
          + index * runtimeTypeSize[typeIndex];
   }

   if (index >= extendedTypeInfo->fieldsCount_) {
     return nullptr;
   }

   return reinterpret_cast<uint8_t*>(obj) + extendedTypeInfo->fieldOffsets_[index];
}

// Compute address of field or an array element at the index, or null, if incorrect.
const char* Konan_DebugGetFieldNameImpl(KRef obj, int32_t index) {
  if (obj == nullptr || index < 0) {
    return nullptr;
  }

  auto typeInfo = obj->type_info();
  auto extendedTypeInfo = typeInfo->extendedInfo_;

  if (extendedTypeInfo == nullptr) {
    return nullptr;
  }

  // For arrays, field name makes not much sense.
  if (extendedTypeInfo->fieldsCount_ < 0) {
    return "";
  }

  if (index >= extendedTypeInfo->fieldsCount_) {
    return nullptr;
  }

  return extendedTypeInfo->fieldNames_[index];
}

const std::string Konan_DebugGetFieldsTypeListString(KRef obj, int32_t count) {
    std::string result;
    for (int32_t i = 0; i < count; ++i) {
        int32_t fieldType = Konan_DebugGetFieldTypeImpl(obj, i);
        result += std::to_string(fieldType);
        if (i < count - 1) {
            result += ",";
        }
    }
    return result;
}

const std::string Konan_DebugGetFieldsAddressListString(KRef obj, int32_t count) {
    std::string result;
    for (int32_t i = 0; i < count; ++i) {
        void* addr = Konan_DebugGetFieldAddressImpl(obj, i);
        if (addr == nullptr) {
            result += "null";
        } else {
           result += std::to_string(reinterpret_cast<uintptr_t>(addr));
        }
        if (i < count - 1) {
            result += ",";
        }
    }
    return result;
}

const char* Konan_DebugGetFieldsNameListImpl(KRef obj, int32_t count) {
    const char** tmpFieldNames = new const char*[count];
    for (int32_t i = 0; i < count; ++i) {
        tmpFieldNames[i] = Konan_DebugGetFieldNameImpl(obj, i);
    }

    size_t totalLength = 0;
    for (int32_t i = 0; i < count; ++i) {
        totalLength += strlen(tmpFieldNames[i]);
    }

    totalLength += count - 1 + 1; // count - 1 个 | 和 1 个空终止符

    char* result = new char[totalLength];

    for (int32_t i = 0; i < count; ++i) {
        strcat(result, tmpFieldNames[i]);
        if (i < count - 1) {
            strcat(result, ",");
        }
    }

    delete[] tmpFieldNames;
    return result;
}

const char* Konan_DebugGetTypeNameImpl(KRef obj) {
  if (obj == nullptr) {
    return nullptr;
  }

  auto type_info = obj->type_info();
  if (type_info == nullptr) {
    return "<unknown>";
  }

  return CreateCStringFromString(type_info->relativeName_);
}

const char* Konan_DebugCompleteTypeInitFlowImpl(KRef obj, const TypeInfo* typeInfo) {
    int32_t isInstance = IsInstanceInternal(obj, typeInfo);
    if (isInstance == 1) {
        // 是对应typeInfo类型的实例
        char* buff_addr =  Konan_DebugBufferImpl();
        int bufferSize = Konan_DebugBufferSizeImpl();
        int32_t buff_len = Konan_DebugObjectToUtf8ArrayImpl(obj, buff_addr, bufferSize);
        std::string result_string = "1|" + std::to_string(reinterpret_cast<uintptr_t>(buff_addr)) + "|" + std::to_string(buff_len);
        char* result = new char[result_string.length() + 1];
        strcpy(result, result_string.c_str());

        return result;
    }
    int32_t children_count = Konan_DebugGetFieldCountImpl(obj);;
    int32_t isArray = Konan_DebugIsArrayImpl(obj);
    if (isArray == 1) {
        std::string result_string = "2|" + std::to_string(children_count);
        char* result = new char[result_string.length() + 1];
        strcpy(result, result_string.c_str());
        return result;
    }
    const char* name_list = Konan_DebugGetFieldsNameListImpl(obj, children_count);
    const std::string fields_type_list_string = Konan_DebugGetFieldsTypeListString(obj, children_count);
    const std::string fields_address_list_string = Konan_DebugGetFieldsAddressListString(obj, children_count);

    std::string result_string = "3|" + std::to_string(children_count) + "|" + std::string(name_list) + "|" + fields_type_list_string
            + "|" + fields_address_list_string ;
    delete[] name_list;
    char* result = new char[result_string.length() + 1];
    strcpy(result, result_string.c_str());

    return result;
}

const char* Konan_DebugGetFieldsTypeAndAddressImpl(KRef obj, int32_t count) {
    const std::string fields_type_list_string = Konan_DebugGetFieldsTypeListString(obj, count);
    const std::string fields_address_list_string = Konan_DebugGetFieldsAddressListString(obj, count);

    std::string result_string = fields_type_list_string + "|" + fields_address_list_string;
    char* result = new char[result_string.length() + 1];
    strcpy(result, result_string.c_str());

    return result;
}


}  // namespace

extern "C" {

RUNTIME_EXPORT RUNTIME_WEAK char* Konan_DebugBuffer() {
  return Konan_DebugBufferImpl();
}

RUNTIME_EXPORT RUNTIME_WEAK int32_t Konan_DebugBufferSize() {
  return Konan_DebugBufferSizeImpl();
}

RUNTIME_EXPORT RUNTIME_WEAK char* Konan_DebugBufferWithObject(KRef obj) {
  auto* impl = getImpl<char* (*)(KRef)>(obj, DO_DebugBufferWithObject);
  if (impl == nullptr) {
      return nullptr;
  }
  return impl(obj);
}

RUNTIME_EXPORT RUNTIME_WEAK int32_t Konan_DebugBufferSizeWithObject(KRef obj) {
  auto* impl = getImpl<int32_t (*)(KRef)>(obj, DO_DebugBufferSizeWithObject);
  if (impl == nullptr) {
      return 0;
  }
  return impl(obj);
}

// Auxilary function which can be called by developer/debugger to inspect an object.
RUNTIME_EXPORT RUNTIME_WEAK int32_t Konan_DebugObjectToUtf8Array(KRef obj, char* buffer, int32_t bufferSize) {
  auto* impl = getImpl<int32_t (*)(KRef, char*, int32_t)>(obj, DO_DebugObjectToUtf8Array);
  if (impl == nullptr) {
      return 0;
  }
  return impl(obj, buffer, bufferSize);
}

RUNTIME_EXPORT RUNTIME_WEAK int32_t Konan_DebugPrint(KRef obj) {
  auto* impl = getImpl<int32_t (*)(KRef)>(obj, DO_DebugPrint);
  if (impl == nullptr) {
      return 0;
  }
  return impl(obj);
}

RUNTIME_EXPORT RUNTIME_WEAK int32_t Konan_DebugIsArray(KRef obj) {
  auto* impl = getImpl<int32_t (*)(KRef)>(obj, DO_DebugIsArray);
  if (impl == nullptr) {
      return 0;
  }
  return impl(obj);
}

RUNTIME_EXPORT RUNTIME_WEAK int32_t Konan_DebugIsInstance(KRef obj, const TypeInfo* typeInfo) {
  int32_t debugIsInstance = IsInstanceInternal(obj, typeInfo);
  return debugIsInstance;
}

RUNTIME_EXPORT RUNTIME_WEAK int32_t Konan_DebugGetFieldCount(KRef obj) {
  auto* impl = getImpl<int32_t (*)(KRef)>(obj, DO_DebugGetFieldCount);
  if (impl == nullptr) {
      return 0;
  }
  return impl(obj);
}

RUNTIME_EXPORT RUNTIME_WEAK int32_t Konan_DebugGetFieldType(KRef obj, int32_t index) {
  auto* impl = getImpl<int32_t (*)(KRef, int32_t)>(obj, DO_DebugGetFieldType);
  if (impl == nullptr) {
      return 0;
  }
  return impl(obj, index);
}

RUNTIME_EXPORT RUNTIME_WEAK void* Konan_DebugGetFieldAddress(KRef obj, int32_t index) {
  auto* impl = getImpl<void* (*)(KRef, int32_t)>(obj, DO_DebugGetFieldAddress);
  if (impl == nullptr) {
      return nullptr;
  }
  return impl(obj, index);
}

// Compute address of field or an array element at the index, or null, if incorrect.
RUNTIME_EXPORT RUNTIME_WEAK const char* Konan_DebugGetFieldName(KRef obj, int32_t index) {
  auto* impl = getImpl<const char* (*)(KRef, int32_t)>(obj, DO_DebugGetFieldName);
  if (impl == nullptr) {
      return nullptr;
  }
  const char* returnImpl = impl(obj, index);
  return returnImpl;
}

// Handle Konan_DebugGetFieldName in range count and concatenate results with ','
RUNTIME_EXPORT RUNTIME_WEAK const char* Konan_DebugGetFieldsNameList(KRef obj, int32_t count) {
    auto* impl = getImpl<const char* (*)(KRef, int32_t)>(obj, DO_DebugGetFieldsNameList);
    if (impl == nullptr) {
        return nullptr;
    }
    const char* returnImpl = impl(obj, count);
    return returnImpl;
}

RUNTIME_EXPORT RUNTIME_WEAK const char* Konan_DebugGetTypeName(KRef obj) {
  auto* impl = getImpl<const char* (*)(KRef)>(obj, DO_DebugGetTypeName);
  if (impl == nullptr) {
      return nullptr;
  }
  return impl(obj);
}

RUNTIME_EXPORT RUNTIME_WEAK const char* Konan_DebugCompleteTypeInitFlow(KRef obj, const TypeInfo* typeInfo) {
    const char* result = Konan_DebugCompleteTypeInitFlowImpl(obj, typeInfo);
    return result;
}

RUNTIME_EXPORT RUNTIME_WEAK const char* Konan_DebugGetFieldsTypeAndAddress(KRef obj, int32_t count) {
    const char* result = Konan_DebugGetFieldsTypeAndAddressImpl(obj, count);
    return result;
}

// The following works around an issue in ld.gold (KT-69206), where the retain
// attribute causes functions to end up in wrong sections. Remove after linker
// upgrade (KT-69207), and add retain attribute to RUNTIME_USED.

RUNTIME_WEAK RUNTIME_EXPORT
const void* Konan_indirectRetainAnnotation[] = {
  reinterpret_cast<const void*>(&Konan_DebugBuffer),
  reinterpret_cast<const void*>(&Konan_DebugBufferSize),
  reinterpret_cast<const void*>(&Konan_DebugBufferWithObject),
  reinterpret_cast<const void*>(&Konan_DebugBufferSizeWithObject),
  reinterpret_cast<const void*>(&Konan_DebugObjectToUtf8Array),
  reinterpret_cast<const void*>(&Konan_DebugPrint),
  reinterpret_cast<const void*>(&Konan_DebugIsArray),
  reinterpret_cast<const void*>(&Konan_DebugIsInstance),
  reinterpret_cast<const void*>(&Konan_DebugGetFieldCount),
  reinterpret_cast<const void*>(&Konan_DebugGetFieldType),
  reinterpret_cast<const void*>(&Konan_DebugGetFieldAddress),
  reinterpret_cast<const void*>(&Konan_DebugGetFieldName),
  reinterpret_cast<const void*>(&Konan_DebugGetTypeName)
};

const void* Konan_debugOperationsList[] = {
  nullptr,
  reinterpret_cast<const void*>(&Konan_DebugBufferImpl),
  reinterpret_cast<const void*>(&Konan_DebugBufferSizeImpl),
  reinterpret_cast<const void*>(&Konan_DebugBufferWithObjectImpl),
  reinterpret_cast<const void*>(&Konan_DebugBufferSizeWithObjectImpl),
  reinterpret_cast<const void*>(&Konan_DebugObjectToUtf8ArrayImpl),
  reinterpret_cast<const void*>(&Konan_DebugPrintImpl),
  reinterpret_cast<const void*>(&Konan_DebugIsArrayImpl),
  reinterpret_cast<const void*>(&Konan_DebugGetFieldCountImpl),
  reinterpret_cast<const void*>(&Konan_DebugGetFieldTypeImpl),
  reinterpret_cast<const void*>(&Konan_DebugGetFieldAddressImpl),
  reinterpret_cast<const void*>(&Konan_DebugGetFieldNameImpl),
  reinterpret_cast<const void*>(&Konan_DebugGetTypeNameImpl),
  reinterpret_cast<const void*>(&Konan_DebugGetFieldsNameListImpl)
};

}  // extern "C"

#endif // !KONAN_NO_DEBUG_API
