/*
 * Copyright 2010-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

#import "Types.h"
#import "Memory.h"
#include "ObjCInterop.h"
#include "KString.h"
#include "TmmConfig.h"
#include "NSStringFromKString.h"

#if KONAN_OBJC_INTEROP

#include <cstdlib>
#include <map>
#import <mutex>
#include <string>
#include <unordered_set>
#include <vector>

#import <Foundation/Foundation.h>
#import <objc/message.h>
#import <objc/runtime.h>
#import <objc/objc-exception.h>
#import <dispatch/dispatch.h>

#import "ObjCExport.h"
#import "ObjCExportInit.h"
#import "ObjCExportPrivate.h"
#import "ObjCMMAPI.h"
#import "Runtime.h"
#import "concurrent/Mutex.hpp"
#import "Exceptions.h"
#import "Natives.h"

using namespace kotlin;

namespace {

template <typename T>
inline T* konanAllocArray(size_t length) {
    return reinterpret_cast<T*>(std::calloc(length, sizeof(T)));
}

}

typedef id (*convertReferenceToRetainedObjC)(ObjHeader* obj);
typedef OBJ_GETTER((*convertReferenceFromObjC), id obj);


static char associatedTypeInfoKey;

extern "C" const TypeInfo* Kotlin_ObjCExport_getAssociatedTypeInfo(Class clazz) {
  return (const TypeInfo*)[objc_getAssociatedObject(clazz, &associatedTypeInfoKey) pointerValue];
}

static void setAssociatedTypeInfo(Class clazz, const TypeInfo* typeInfo) {
  kotlin::NativeOrUnregisteredThreadGuard threadStateGuard(/* reentrant = */ true);

  // Note: [NSValue valueWithPointer:] uses autorelease (without possibility to eliminate this at the call site),
  // so using alloc-init sequence to avoid this.
  NSValue* value = [[NSValue alloc] initWithBytes:&typeInfo objCType:@encode(void*)];

  // Note: OBJC_ASSOCIATION_ASSIGN below means that this NSValue will leak. On the other hand,
  // TypeInfo will "leak" anyway, and Class will likely "leak" too. So not a big deal.
  // Also, alternative modes have problems:
  //   OBJC_ASSOCIATION_COPY and OBJC_ASSOCIATION_RETAIN make corresponding objc_getAssociatedObject
  //     use autorelease (without possibility to eliminate this at the call site).
  //   OBJC_ASSOCIATION_RETAIN_NONATOMIC and OBJC_ASSOCIATION_COPY_NONATOMIC are not thread safe and
  //     might require explicit synchronization on objc_getAssociatedObject.
  //
  // So OBJC_ASSOCIATION_ASSIGN seems the best option here.

  // Note: implementation for OBJC_ASSOCIATION_ASSIGN allows value not to be an Obj-C reference at all,
  // because it simply stores a pointer without any memory management operations.
  // But this is undocumented, and thus unsafe.
  objc_setAssociatedObject(clazz, &associatedTypeInfoKey, value, OBJC_ASSOCIATION_ASSIGN);
}

extern "C" id Kotlin_ObjCExport_GetAssociatedObject(ObjHeader* obj) {
  return GetAssociatedObject(obj);
}

extern "C" OBJ_GETTER(Kotlin_ObjCExport_AllocInstanceWithAssociatedObject,
                      const TypeInfo* typeInfo, id associatedObject) RUNTIME_NOTHROW;

RUNTIME_NOTHROW extern "C" OBJ_GETTER(Kotlin_ObjCExport_AllocInstanceWithAssociatedObject,
                            const TypeInfo* typeInfo, id associatedObject) {
  RETURN_RESULT_OF(AllocInstanceWithAssociatedObject, typeInfo, associatedObject);
}

static Class getOrCreateClass(const TypeInfo* typeInfo);

namespace {

ALWAYS_INLINE void send_releaseAsAssociatedObject(void* associatedObject) {
  auto msgSend = reinterpret_cast<void (*)(void* self, SEL cmd)>(&objc_msgSend);
  msgSend(associatedObject, Kotlin_ObjCExport_releaseAsAssociatedObjectSelector);
}

} // namespace

extern "C" ALWAYS_INLINE void Kotlin_ObjCExport_releaseAssociatedObject(void* associatedObject) {
  RuntimeAssert(associatedObject != nullptr, "Kotlin_ObjCExport_releaseAssociatedObject(nullptr)");
  // May already be in the native state if was scheduled on the main queue.
  NativeOrUnregisteredThreadGuard guard(/*reentrant=*/ true);
  send_releaseAsAssociatedObject(associatedObject);
}

extern "C" id Kotlin_ObjCExport_convertUnitToRetained(ObjHeader* unitInstance) {
  static dispatch_once_t onceToken;
  static id instance = nullptr;
  dispatch_once(&onceToken, ^{
    Class unitClass = getOrCreateClass(unitInstance->type_info());
    instance = [unitClass createRetainedWrapper:unitInstance];
  });
  return objc_retain(instance);
}

extern "C" id Kotlin_ObjCExport_CreateRetainedNSStringFromKString(ObjHeader* str) {
  KChar* utf16Chars = CharArrayAddressOfElementAt(str->array(), 0);
  auto numBytes = str->array()->count_ * sizeof(KChar);

  if (str->permanent()) {
    return [[NSString alloc] initWithBytesNoCopy:utf16Chars
        length:numBytes
        encoding:NSUTF16LittleEndianStringEncoding
        freeWhenDone:NO];
  } else {
    // region @Tencent
    if (Kotlin_TmmConfig_isStringProxyEnabledCreatingNSStringFromKString()) {
      return [[NSStringFromKString alloc] initWithKString:str];
    }
    // endregion

    // TODO: consider making NSString subclass to avoid copying here.
    NSString* candidate = [[NSString alloc] initWithBytes:utf16Chars
      length:numBytes
      encoding:NSUTF16LittleEndianStringEncoding];

    if (!isShareable(str)) {
      SetAssociatedObject(str, candidate);
    } else {
      id old = AtomicCompareAndSwapAssociatedObject(str, nullptr, candidate);
      if (old != nullptr) {
        objc_release(candidate);
        return objc_retain(old);
      }
    }

    return objc_retain(candidate);
  }
}
static const ObjCTypeAdapter* findAdapterByName(
      const char* name,
      const ObjCTypeAdapter** sortedAdapters,
      int adapterNum
) {

  int left = 0, right = adapterNum - 1;

  while (right >= left) {
    int mid = (left + right) / 2;
    int cmp = strcmp(name, sortedAdapters[mid]->objCName);
    if (cmp < 0) {
      right = mid - 1;
    } else if (cmp > 0) {
      left = mid + 1;
    } else {
      return sortedAdapters[mid];
    }
  }

  return nullptr;
}

__attribute__((weak)) const ObjCTypeAdapter** Kotlin_ObjCExport_sortedClassAdapters = nullptr;
__attribute__((weak)) int Kotlin_ObjCExport_sortedClassAdaptersNum = 0;

__attribute__((weak)) const ObjCTypeAdapter** Kotlin_ObjCExport_sortedProtocolAdapters = nullptr;
__attribute__((weak)) int Kotlin_ObjCExport_sortedProtocolAdaptersNum = 0;

__attribute__((weak)) bool Kotlin_ObjCExport_initTypeAdapters = false;

static const ObjCTypeAdapter* findClassAdapter(Class clazz) {
  return findAdapterByName(class_getName(clazz),
        Kotlin_ObjCExport_sortedClassAdapters,
        Kotlin_ObjCExport_sortedClassAdaptersNum
  );
}

static const ObjCTypeAdapter* findProtocolAdapter(Protocol* prot) {
  return findAdapterByName(protocol_getName(prot),
        Kotlin_ObjCExport_sortedProtocolAdapters,
        Kotlin_ObjCExport_sortedProtocolAdaptersNum
  );
}

static const ObjCTypeAdapter* getTypeAdapter(const TypeInfo* typeInfo) {
  return typeInfo->writableInfo_->objCExport.typeAdapter;
}

static void addProtocolForAdapter(Class clazz, const ObjCTypeAdapter* protocolAdapter) {
  Protocol* protocol = objc_getProtocol(protocolAdapter->objCName);
  if (protocol != nullptr) {
    class_addProtocol(clazz, protocol);
    class_addProtocol(object_getClass(clazz), protocol);
  } else {
    // TODO: construct the protocol in compiler instead, because this case can't be handled easily.
  }
}

static void addProtocolForInterface(Class clazz, const TypeInfo* interfaceInfo) {
  const ObjCTypeAdapter* protocolAdapter = getTypeAdapter(interfaceInfo);
  if (protocolAdapter != nullptr) {
    addProtocolForAdapter(clazz, protocolAdapter);
  }
}

extern "C" const TypeInfo* Kotlin_ObjCInterop_getTypeInfoForClass(Class clazz) {
  const TypeInfo* candidate = Kotlin_ObjCExport_getAssociatedTypeInfo(clazz);

  if (candidate != nullptr && (candidate->flags_ & TF_OBJC_DYNAMIC) == 0) {
    return candidate;
  } else {
    return nullptr;
  }
}

extern "C" const TypeInfo* Kotlin_ObjCInterop_getTypeInfoForProtocol(Protocol* protocol) {
  const ObjCTypeAdapter* typeAdapter = findProtocolAdapter(protocol);

  return (typeAdapter != nullptr) ? typeAdapter->kotlinTypeInfo : nullptr;
}

static const TypeInfo* getOrCreateTypeInfo(Class clazz);

extern "C" void Kotlin_ObjCExport_initializeClass(Class clazz) {
  if (kotlin::mm::IsCurrentThreadRegistered()) {
    // ObjC runtime might have taken a lock in Runnable context on the way here. Safe-points are unwelcome in the code below.
    // We can't make it all the way in a Runnable state as well, because some of the operations below might take a while.
    AssertThreadState(kotlin::ThreadState::kNative);
  }

  const ObjCTypeAdapter* typeAdapter = findClassAdapter(clazz);
  if (typeAdapter == nullptr) {
    getOrCreateTypeInfo(clazz);
    return;
  }

  // We aren't really sure we've checked all the cases when initialize is called, and guarded them with a switch to native.
  // If panic asserts above are disabled, let's ensure the native state here,
  // to avoid potentially more frequent deadlock cases.
  kotlin::NativeOrUnregisteredThreadGuard threadStateGuard(/* reentrant = */ true);

  const TypeInfo* typeInfo = typeAdapter->kotlinTypeInfo;
  bool isClassForPackage = typeInfo == nullptr;
  if (!isClassForPackage) {
    setAssociatedTypeInfo(clazz, typeInfo);
  }

  for (int i = 0; i < typeAdapter->directAdapterNum; ++i) {
    const ObjCToKotlinMethodAdapter* adapter = typeAdapter->directAdapters + i;
    SEL selector = sel_registerName(adapter->selector);
    class_addMethod(clazz, selector, adapter->imp, adapter->encoding);
    // The method above may fail if there is a matching Swift/Obj-C extension method for this Kotlin class.
    // This is pretty much ok, and we shouldn't replace that method with our own.
  }

  for (int i = 0; i < typeAdapter->classAdapterNum; ++i) {
    const ObjCToKotlinMethodAdapter* adapter = typeAdapter->classAdapters + i;
    SEL selector = sel_registerName(adapter->selector);
    class_addMethod(object_getClass(clazz), selector, adapter->imp, adapter->encoding);
  }

  if (isClassForPackage) return;

  for (int i = 0; i < typeInfo->implementedInterfacesCount_; ++i) {
    addProtocolForInterface(clazz, typeInfo->implementedInterfaces_[i]);
  }

}

extern "C" ALWAYS_INLINE OBJ_GETTER(Kotlin_ObjCExport_convertUnmappedObjCObject, id obj) {
  const TypeInfo* typeInfo = getOrCreateTypeInfo(object_getClass(obj));
  RETURN_RESULT_OF(AllocInstanceWithAssociatedObject, typeInfo, objc_retain(obj));
}

// Initialized by [ObjCExportClasses.mm].
extern "C" SEL Kotlin_ObjCExport_toKotlinSelector = nullptr;
extern "C" SEL Kotlin_ObjCExport_releaseAsAssociatedObjectSelector = nullptr;

static OBJ_GETTER(blockToKotlinImp, id self, SEL cmd);
static OBJ_GETTER(boxedBooleanToKotlinImp, NSNumber* self, SEL cmd);

static OBJ_GETTER(SwiftObject_toKotlinImp, id self, SEL cmd);
static void SwiftObject_releaseAsAssociatedObjectImp(id self, SEL cmd);

static void initTypeAdaptersFrom(const ObjCTypeAdapter** adapters, int count) {
  for (int index = 0; index < count; ++index) {
    const ObjCTypeAdapter* adapter = adapters[index];
    const TypeInfo* typeInfo = adapter->kotlinTypeInfo;
    if (typeInfo != nullptr) {
      typeInfo->writableInfo_->objCExport.typeAdapter = adapter;
    }
  }
}

static void initTypeAdapters() {
  if (!Kotlin_ObjCExport_initTypeAdapters) return;

  initTypeAdaptersFrom(Kotlin_ObjCExport_sortedClassAdapters, Kotlin_ObjCExport_sortedClassAdaptersNum);
  initTypeAdaptersFrom(Kotlin_ObjCExport_sortedProtocolAdapters, Kotlin_ObjCExport_sortedProtocolAdaptersNum);
}

static void Kotlin_ObjCExport_initializeImpl() {
  RuntimeCheck(Kotlin_ObjCExport_toKotlinSelector != nullptr, "unexpected initialization order");
  RuntimeCheck(Kotlin_ObjCExport_releaseAsAssociatedObjectSelector != nullptr, "unexpected initialization order");

  kotlin::NativeOrUnregisteredThreadGuard threadStateGuard(/* reentrant = */ true);

  initTypeAdapters();

  SEL toKotlinSelector = Kotlin_ObjCExport_toKotlinSelector;
  Method toKotlinMethod = class_getClassMethod([NSObject class], toKotlinSelector);
  RuntimeAssert(toKotlinMethod != nullptr, "");
  const char* toKotlinTypeEncoding = method_getTypeEncoding(toKotlinMethod);

  SEL releaseAsAssociatedObjectSelector = Kotlin_ObjCExport_releaseAsAssociatedObjectSelector;
  Method releaseAsAssociatedObjectMethod = class_getClassMethod([NSObject class], releaseAsAssociatedObjectSelector);
  RuntimeAssert(releaseAsAssociatedObjectMethod != nullptr, "");
  const char* releaseAsAssociatedObjectTypeEncoding = method_getTypeEncoding(releaseAsAssociatedObjectMethod);

  Class nsBlockClass = objc_getClass("NSBlock");
  RuntimeAssert(nsBlockClass != nullptr, "NSBlock class not found");

  // Note: can't add it with category, because it would be considered as private API usage.
  BOOL added = class_addMethod(nsBlockClass, toKotlinSelector, (IMP)blockToKotlinImp, toKotlinTypeEncoding);
  RuntimeAssert(added, "Unable to add 'toKotlin:' method to NSBlock class");

  // Note: the boolean class is not visible to linker, so this case can't be handled with a category too.
  // Referring it directly is also undesirable, because this is "private API" (see e.g. KT-62091).
  // Get the class from an object instead:
  Class booleanClass = [[NSNumber numberWithBool:YES] class];
  RuntimeAssert(booleanClass != nullptr, "The NS boolean class not found");

  if (booleanClass != [[NSNumber numberWithInt:1] class]) {
    added = class_addMethod(booleanClass, toKotlinSelector, (IMP)boxedBooleanToKotlinImp, toKotlinTypeEncoding);
    RuntimeAssert(added, "Unable to add 'toKotlin:' method to the NS boolean class");
  } else {
    // Shouldn't really happen unless something changed in the implementation.
    // Play safe in that case, don't botch the numbers case.
    RuntimeAssert(false, "NSNumber uses the same class for booleans and numbers: %s", class_getName(booleanClass));
  }

  for (const char* swiftRootClassName : { "SwiftObject", "_TtCs12_SwiftObject" }) {
    Class swiftRootClass = objc_getClass(swiftRootClassName);
    if (swiftRootClass != nullptr) {
      added = class_addMethod(swiftRootClass, toKotlinSelector, (IMP)SwiftObject_toKotlinImp, toKotlinTypeEncoding);
      RuntimeAssert(added, "Unable to add 'toKotlin:' method to SwiftObject class");

      added = class_addMethod(
        swiftRootClass, releaseAsAssociatedObjectSelector,
        (IMP)SwiftObject_releaseAsAssociatedObjectImp, releaseAsAssociatedObjectTypeEncoding
      );
      RuntimeAssert(added, "Unable to add 'releaseAsAssociatedObject' method to SwiftObject class");
    }
  }
}

// Initializes ObjCExport for current process (if not initialized yet).
// Generally this is equal to some "binary patching" (which is usually done at link time
// but postponed until runtime here due to various reasons):
// adds methods to Objective-C classes, initializes static memory with "constant" values etc.
extern "C" void Kotlin_ObjCExport_initialize() {
  static dispatch_once_t onceToken;
  dispatch_once(&onceToken, ^{
    Kotlin_ObjCExport_initializeImpl();
  });
}

static OBJ_GETTER(SwiftObject_toKotlinImp, id self, SEL cmd) {
  RETURN_RESULT_OF(Kotlin_ObjCExport_convertUnmappedObjCObject, self);
}

static void SwiftObject_releaseAsAssociatedObjectImp(id self, SEL cmd) {
  objc_release(self);
}


extern "C" OBJ_GETTER(Kotlin_boxBoolean, KBoolean value);

static OBJ_GETTER(boxedBooleanToKotlinImp, NSNumber* self, SEL cmd) {
  RETURN_RESULT_OF(Kotlin_boxBoolean, self.boolValue);
}

struct Block_literal_1 exportBlockLiteral;

static const char* getBlockEncoding(id block) {
  Block_literal_1* literal = reinterpret_cast<Block_literal_1*>(block);

  int flags = literal->flags;
  RuntimeAssert((flags & (1 << 30)) != 0, "block has no signature stored");
  return (flags & (1 << 25)) != 0 ?
      literal->descriptor->signature :
      reinterpret_cast<struct Block_descriptor_1_without_helpers*>(literal->descriptor)->signature;
}

// Note: defined by compiler.
extern "C" convertReferenceFromObjC* Kotlin_ObjCExport_blockToFunctionConverters;
extern "C" int Kotlin_ObjCExport_blockToFunctionConverters_size;

static OBJ_GETTER(blockToKotlinImp, id block, SEL cmd) {
  const char* encoding = getBlockEncoding(block);

  // TODO: optimize:
  NSMethodSignature *signature = [NSMethodSignature signatureWithObjCTypes:encoding];
  int parameterCount = signature.numberOfArguments - 1; // 1 for the block itself.

  for (int i = 1; i <= parameterCount; ++i) {
    const char* argEncoding = [signature getArgumentTypeAtIndex:i];
    if (argEncoding[0] != '@') {
      kotlin::ThreadStateGuard guard(kotlin::ThreadState::kNative);
      [NSException raise:NSGenericException
            format:@"Converting Obj-C blocks with non-reference-typed arguments to kotlin.Any is not supported (%s)", argEncoding];
    }
  }

  const char* returnTypeEncoding = signature.methodReturnType;
  if (returnTypeEncoding[0] != '@') {
    kotlin::ThreadStateGuard guard(kotlin::ThreadState::kNative);
    [NSException raise:NSGenericException
          format:@"Converting Obj-C blocks with non-reference-typed return value to kotlin.Any is not supported (%s)", returnTypeEncoding];
  }

  auto converter = parameterCount < Kotlin_ObjCExport_blockToFunctionConverters_size
          ? Kotlin_ObjCExport_blockToFunctionConverters[parameterCount]
          : nullptr;

  if (converter != nullptr) {
    RETURN_RESULT_OF(converter, block);
  } else {
    // There is no function class for this arity, so resulting object will not be cast to FunctionN class,
    // and it is enough to convert block to arbitrary object conforming Function.
    RETURN_RESULT_OF(AllocInstanceWithAssociatedObject, theOpaqueFunctionTypeInfo, objc_retainBlock(block));
  }
}

static id Kotlin_ObjCExport_refToRetainedObjC_slowpath(ObjHeader* obj);

extern "C" id objc_autorelease(id self);

// retain = true means that it returns retained result, which must be eventually released by the caller.
//
// retain = false means that it returns unretained result, which is not guaranteed to outlive [obj],
// but doesn't require any balancing release operation.
// It might use autorelease though, which will be suboptimal.
template <bool retain>
static ALWAYS_INLINE id Kotlin_ObjCExport_refToObjCImpl(ObjHeader* obj) {
  kotlin::AssertThreadState(kotlin::ThreadState::kRunnable);

  if (obj == nullptr) return nullptr;

  id associatedObject = GetAssociatedObject(obj);
  if (associatedObject != nullptr) {
    return retain ? objc_retain(associatedObject) : associatedObject;
  }

  // TODO: propagate [retainAutorelease] to the code below.

  convertReferenceToRetainedObjC convertToRetained = (convertReferenceToRetainedObjC)obj->type_info()->writableInfo_->objCExport.convertToRetained;

  id retainedResult;
  if (convertToRetained != nullptr) {
    retainedResult = convertToRetained(obj);
  } else {
    retainedResult = Kotlin_ObjCExport_refToRetainedObjC_slowpath(obj);
  }

  // Balance retain with objc_autorelease if required:
  return retain ? retainedResult : objc_autorelease(retainedResult);
}

extern "C" id Kotlin_ObjCExport_refToRetainedObjC(ObjHeader* obj) {
  return Kotlin_ObjCExport_refToObjCImpl<true>(obj);
}

extern "C" id Kotlin_ObjCExport_refToObjC(ObjHeader* obj) {
  return objc_autorelease(Kotlin_ObjCExport_refToObjCImpl<true>(obj));
}

extern "C" id Kotlin_ObjCExport_refToLocalObjC(ObjHeader* obj) {
  return Kotlin_ObjCExport_refToObjCImpl<false>(obj);
}

// The function is marked with noexcept, so any exception reaching it will cause std::terminate.
extern "C" ALWAYS_INLINE id Kotlin_Interop_refToObjC(ObjHeader* obj) noexcept {
  return Kotlin_ObjCExport_refToObjCImpl<false>(obj);
}

// The function is marked with noexcept, so any exception reaching it will cause std::terminate.
extern "C" ALWAYS_INLINE OBJ_GETTER(Kotlin_Interop_refFromObjC, id obj) noexcept {
  // TODO: consider removing this function.
  RETURN_RESULT_OF(Kotlin_ObjCExport_refFromObjC, obj);
}

extern "C" OBJ_GETTER(Kotlin_Interop_CreateObjCObjectHolder, id obj) {
  RuntimeAssert(obj != nullptr, "wrapped object must not be null");
  const TypeInfo* typeInfo = theForeignObjCObjectTypeInfo;
  RETURN_RESULT_OF(AllocInstanceWithAssociatedObject, typeInfo, objc_retain(obj));
}

extern "C" OBJ_GETTER(Kotlin_ObjCExport_refFromObjC, id obj) {
  kotlin::AssertThreadState(kotlin::ThreadState::kRunnable);

  if (obj == nullptr) RETURN_OBJ(nullptr);
  auto msgSend = reinterpret_cast<ObjHeader* (*)(id self, SEL cmd, ObjHeader** slot)>(&objc_msgSend);
  RETURN_RESULT_OF(msgSend, obj, Kotlin_ObjCExport_toKotlinSelector);
}

static id convertKotlinObjectToRetained(ObjHeader* obj) {
  Class clazz = obj->type_info()->writableInfo_->objCExport.objCClass;
  RuntimeAssert(clazz != nullptr, "");
  return [clazz createRetainedWrapper:obj];
}

static convertReferenceToRetainedObjC findConvertToRetainedFromInterfaces(const TypeInfo* typeInfo) {
  const TypeInfo* foundTypeInfo = nullptr;

  for (int i = 0; i < typeInfo->implementedInterfacesCount_; ++i) {
    const TypeInfo* interfaceTypeInfo = typeInfo->implementedInterfaces_[i];
    if ((interfaceTypeInfo->flags_ & TF_SUSPEND_FUNCTION) != 0) {
      // interfaceTypeInfo is a SuspendFunction$N interface.
      // So any instance of typeInfo is a suspend lambda or a suspend callable reference
      // (user-defined Kotlin classes implementing SuspendFunction$N are prohibited by the compiler).
      //
      // Such types also actually implement Function${N+1} interface as an optimization
      // (see e.g. [startCoroutineUninterceptedOrReturn implementation).
      // This fact is not user-visible, so ignoring Function${N+1} interface here
      // (and thus not converting such objects to Obj-C blocks) should be safe enough
      // (because such objects aren't expected to be passed from Kotlin to Swift
      // under formal Function${N+1} type).
      //
      // On the other hand, this fixes support for SuspendFunction$N type: it is mapped as
      // regular Kotlin interface, so its instances should be converted on a general basis
      // (i.e. to objects implementing Obj-C representation of SuspendFunction$N, not to Obj-C blocks).
      //
      // "If typeInfo is a suspend lambda or callable reference type, convert its instances on a regular basis":
      return nullptr;
    }

    if (interfaceTypeInfo->writableInfo_->objCExport.convertToRetained != nullptr) {
      if (foundTypeInfo == nullptr || IsSubInterface(interfaceTypeInfo, foundTypeInfo)) {
        foundTypeInfo = interfaceTypeInfo;
      } else if (!IsSubInterface(foundTypeInfo, interfaceTypeInfo)) {
        [NSException raise:NSGenericException
            format:@"Can't convert to Objective-C Kotlin object that is '%@' and '%@' and the same time",
            Kotlin_Interop_CreateNSStringFromKString(foundTypeInfo->relativeName_),
            Kotlin_Interop_CreateNSStringFromKString(interfaceTypeInfo->relativeName_)];
      }
    }
  }

  return foundTypeInfo == nullptr ?
    nullptr :
    (convertReferenceToRetainedObjC)foundTypeInfo->writableInfo_->objCExport.convertToRetained;
}

static id Kotlin_ObjCExport_refToRetainedObjC_slowpath(ObjHeader* obj) {
  const TypeInfo* typeInfo = obj->type_info();
  convertReferenceToRetainedObjC convertToRetained = findConvertToRetainedFromInterfaces(typeInfo);

  if (convertToRetained == nullptr) {
    getOrCreateClass(typeInfo);
    convertToRetained = (typeInfo == theUnitTypeInfo) ? &Kotlin_ObjCExport_convertUnitToRetained : &convertKotlinObjectToRetained;
  }

  typeInfo->writableInfo_->objCExport.convertToRetained = (void*)convertToRetained;

  return convertToRetained(obj);
}

static void buildITable(TypeInfo* result, const std::map<ClassId, std::vector<VTableElement>>& interfaceVTables) {
  // Check if can use fast optimistic version - check if the size of the itable could be 2^k and <= 32.
  bool useFastITable;
  int itableSize = 1;
  for (; itableSize <= 32; itableSize <<= 1) {
    useFastITable = true;
    bool used[32];
    memset(used, 0, sizeof(used));
    for (auto& pair : interfaceVTables) {
      auto interfaceId = pair.first;
      auto index = interfaceId & (itableSize - 1);
      if (used[index]) {
        useFastITable = false;
        break;
      }
      used[index] = true;
    }
    if (useFastITable) break;
  }
  if (!useFastITable)
    itableSize = interfaceVTables.size();

  auto itable_ = konanAllocArray<InterfaceTableRecord>(itableSize);
  result->interfaceTable_ = itable_;
  result->interfaceTableSize_ = useFastITable ? itableSize - 1 : -itableSize;

  if (useFastITable) {
    for (auto& pair : interfaceVTables) {
      auto interfaceId = pair.first;
      auto index = interfaceId & (itableSize - 1);
      itable_[index].id = interfaceId;
    }
  } else {
    // Otherwise: conservative version.
    // The table will be sorted since we're using std::map.
    int index = 0;
    for (auto& pair : interfaceVTables) {
      auto interfaceId = pair.first;
      itable_[index++].id = interfaceId;
    }
  }

  for (int i = 0; i < itableSize; ++i) {
    auto interfaceId = itable_[i].id;
    if (interfaceId == kInvalidInterfaceId) continue;
    auto interfaceVTableIt = interfaceVTables.find(interfaceId);
    RuntimeAssert(interfaceVTableIt != interfaceVTables.end(), "");
    auto const& interfaceVTable = interfaceVTableIt->second;
    int interfaceVTableSize = interfaceVTable.size();
    auto interfaceVTable_ = interfaceVTableSize == 0 ? nullptr : konanAllocArray<VTableElement>(interfaceVTableSize);
    for (int j = 0; j < interfaceVTableSize; ++j)
      interfaceVTable_[j] = interfaceVTable[j];
    itable_[i].vtable = interfaceVTable_;
    itable_[i].vtableSize = interfaceVTableSize;
  }
}

static const TypeInfo* createTypeInfo(
  const char* className,
  const TypeInfo* superType,
  const std::vector<const TypeInfo*>& superInterfaces,
  const std::vector<VTableElement>& vtable,
  const std::map<ClassId, std::vector<VTableElement>>& interfaceVTables,
  const InterfaceTableRecord* superItable,
  int superItableSize,
  bool itableEqualsSuper,
  const TypeInfo* fieldsInfo
) {
  TypeInfo* result = (TypeInfo*)std::calloc(1, sizeof(TypeInfo) + vtable.size() * sizeof(void*));
  result->typeInfo_ = result;

  result->flags_ = TF_OBJC_DYNAMIC;

  result->superType_ = superType;
  if (fieldsInfo == nullptr) {
    result->instanceSize_ = superType->instanceSize_;
    result->instanceAlignment_ = superType->instanceAlignment_;
    result->objOffsets_ = superType->objOffsets_;
    result->objOffsetsCount_ = superType->objOffsetsCount_; // So TF_IMMUTABLE can also be inherited:
    if ((superType->flags_ & TF_IMMUTABLE) != 0) {
      result->flags_ |= TF_IMMUTABLE;
    }
    result->processObjectInMark = superType->processObjectInMark;
  } else {
    result->instanceSize_ = fieldsInfo->instanceSize_;
    result->instanceAlignment_ = fieldsInfo->instanceAlignment_;
    result->objOffsets_ = fieldsInfo->objOffsets_;
    result->objOffsetsCount_ = fieldsInfo->objOffsetsCount_;
    result->processObjectInMark = fieldsInfo->processObjectInMark;
  }

  result->classId_ = superType->classId_;

  std::vector<const TypeInfo*> implementedInterfaces(
    superType->implementedInterfaces_, superType->implementedInterfaces_ + superType->implementedInterfacesCount_
  );
  std::unordered_set<const TypeInfo*> usedInterfaces(implementedInterfaces.begin(), implementedInterfaces.end());

  for (const TypeInfo* interface : superInterfaces) {
    if (usedInterfaces.insert(interface).second) {
      implementedInterfaces.push_back(interface);
    }
  }

  const TypeInfo** implementedInterfaces_ = konanAllocArray<const TypeInfo*>(implementedInterfaces.size());
  for (size_t i = 0; i < implementedInterfaces.size(); ++i) {
    implementedInterfaces_[i] = implementedInterfaces[i];
  }

  result->implementedInterfaces_ = implementedInterfaces_;
  result->implementedInterfacesCount_ = implementedInterfaces.size();
  if (superItable != nullptr) {
    if (itableEqualsSuper) {
      result->interfaceTableSize_ = superItableSize;
      result->interfaceTable_ = superItable;
    } else {
      buildITable(result, interfaceVTables);
    }
  }

  result->packageName_ = nullptr;
  result->relativeName_ = CreatePermanentStringFromCString(className);
  result->writableInfo_ = (WritableTypeInfo*)std::calloc(1, sizeof(WritableTypeInfo));

  for (size_t i = 0; i < vtable.size(); ++i) result->vtable()[i] = vtable[i];

  return result;
}

static void addDefinedSelectors(Class clazz, std::unordered_set<SEL>& result) {
  unsigned int objcMethodCount;
  Method* objcMethods = class_copyMethodList(clazz, &objcMethodCount);

  for (unsigned int i = 0; i < objcMethodCount; ++i) {
    result.insert(method_getName(objcMethods[i]));
  }

  if (objcMethods != nullptr) free(objcMethods);
}

static std::vector<const TypeInfo*> getProtocolsAsInterfaces(Class clazz) {
  std::vector<const TypeInfo*> result;
  std::unordered_set<Protocol*> handledProtocols;
  std::vector<Protocol*> protocolsToHandle;

  {
    unsigned int protocolCount;
    Protocol** protocols = class_copyProtocolList(clazz, &protocolCount);
    if (protocols != nullptr) {
      protocolsToHandle.insert(protocolsToHandle.end(), protocols, protocols + protocolCount);
      free(protocols);
    }
  }

  while (!protocolsToHandle.empty()) {
    Protocol* proto = protocolsToHandle[protocolsToHandle.size() - 1];
    protocolsToHandle.pop_back();

    if (handledProtocols.insert(proto).second) {
      const ObjCTypeAdapter* typeAdapter = findProtocolAdapter(proto);
      if (typeAdapter != nullptr) result.push_back(typeAdapter->kotlinTypeInfo);

      unsigned int protocolCount;
      Protocol** protocols = protocol_copyProtocolList(proto, &protocolCount);
      if (protocols != nullptr) {
        protocolsToHandle.insert(protocolsToHandle.end(), protocols, protocols + protocolCount);
        free(protocols);
      }
    }
  }

  return result;
}

static int getVtableSize(const TypeInfo* typeInfo) {
  for (const TypeInfo* current = typeInfo; current != nullptr; current = current->superType_) {
    auto typeAdapter = getTypeAdapter(current);
    if (typeAdapter != nullptr) return typeAdapter->kotlinVtableSize;
  }

  RuntimeAssert(false, "");
  return -1;
}

static void throwIfCantBeOverridden(Class clazz, const KotlinToObjCMethodAdapter* adapter) {
  if (adapter->kotlinImpl == nullptr) {
    NSString* reason;
    switch (adapter->vtableIndex) {
      case -1: reason = @"it is final"; break;
      case -2: reason = @"original Kotlin method has more than one selector"; break;
      default: reason = @""; break;
    }
    [NSException raise:NSGenericException
        format:@"[%s %s] can't be overridden: %@",
        class_getName(clazz), adapter->selector, reason];
  }
}

static const TypeInfo* createTypeInfo(Class clazz, const TypeInfo* superType, const TypeInfo* fieldsInfo) {
  kotlin::NativeOrUnregisteredThreadGuard threadStateGuard(/* reentrant = */ true);

  std::unordered_set<SEL> definedSelectors;
  addDefinedSelectors(clazz, definedSelectors);

  const ObjCTypeAdapter* superTypeAdapter = getTypeAdapter(superType);

  const void * const * superVtable = nullptr;
  int superVtableSize = getVtableSize(superType);

  InterfaceTableRecord const* superITable = nullptr;
  int superITableSize = 0;

  if (superTypeAdapter != nullptr) {
    // Then super class is Kotlin class.

    // And if it is abstract, then vtable and method table are not available from TypeInfo,
    // but present in type adapter instead:
    superVtable = superTypeAdapter->kotlinVtable;
    superITable = superTypeAdapter->kotlinItable;
    superITableSize = superTypeAdapter->kotlinItableSize;
  }

  if (superVtable == nullptr) superVtable = superType->vtable();

  std::vector<const void*> vtable(
        superVtable,
        superVtable + superVtableSize
  );

  if (superITable == nullptr) {
    superITable = superType->interfaceTable_;
    superITableSize = superType->interfaceTableSize_;
  }
  std::map<ClassId, std::vector<VTableElement>> interfaceVTables;
  if (superITable != nullptr) {
    int actualItableSize = superITableSize >= 0 ? superITableSize + 1 : -superITableSize;
    for (int i = 0; i < actualItableSize; ++i) {
      auto& record = superITable[i];
      auto interfaceId = record.id;
      if (interfaceId == kInvalidInterfaceId) continue;
      int vtableSize = record.vtableSize;
      std::vector<VTableElement> interfaceVTable(vtableSize);
      for (int j = 0; j < vtableSize; ++j)
        interfaceVTable[j] = record.vtable[j];
      interfaceVTables.emplace(interfaceId, std::move(interfaceVTable));
    }
  }

  std::vector<const TypeInfo*> addedInterfaces = getProtocolsAsInterfaces(clazz);

  std::vector<const TypeInfo*> supers(
        superType->implementedInterfaces_,
        superType->implementedInterfaces_ + superType->implementedInterfacesCount_
  );

  for (const TypeInfo* t = superType; t != nullptr; t = t->superType_) {
    supers.push_back(t);
  }

  bool itableEqualsSuper = true;

  auto addToITable = [&interfaceVTables](ClassId interfaceId, int methodIndex, VTableElement entry) {
    RuntimeAssert(interfaceId != kInvalidInterfaceId, "");
    auto interfaceVTableIt = interfaceVTables.find(interfaceId);
    RuntimeAssert(interfaceVTableIt != interfaceVTables.end(), "");
    auto& interfaceVTable = interfaceVTableIt->second;
    RuntimeAssert(methodIndex >= 0 && static_cast<size_t>(methodIndex) < interfaceVTable.size(), "");
    interfaceVTable[methodIndex] = entry;
  };

  auto addITable = [&interfaceVTables, &itableEqualsSuper](ClassId interfaceId, int itableSize) {
    RuntimeAssert(itableSize >= 0, "");
    auto interfaceVTablesIt = interfaceVTables.find(interfaceId);
    if (interfaceVTablesIt == interfaceVTables.end()) {
      itableEqualsSuper = false;
      interfaceVTables.emplace(interfaceId, std::vector<VTableElement>(itableSize));
    } else {
      auto const& interfaceVTable = interfaceVTablesIt->second;
      RuntimeAssert(interfaceVTable.size() == static_cast<size_t>(itableSize), "");
    }
  };

  // Compiler relies on using reverse adapters here from all supertypes
  // in [ObjCExportCodeGenerator.createReverseAdapters].
  for (const TypeInfo* t : supers) {
    const ObjCTypeAdapter* typeAdapter = getTypeAdapter(t);
    if (typeAdapter == nullptr) continue;

    for (int i = 0; i < typeAdapter->reverseAdapterNum; ++i) {
      const KotlinToObjCMethodAdapter* adapter = &typeAdapter->reverseAdapters[i];
      if (definedSelectors.find(sel_registerName(adapter->selector)) == definedSelectors.end()) continue;

      throwIfCantBeOverridden(clazz, adapter);

      itableEqualsSuper = false;
      if (adapter->vtableIndex != -1) vtable[adapter->vtableIndex] = adapter->kotlinImpl;

      if (adapter->itableIndex != -1 && superITable != nullptr)
        addToITable(adapter->interfaceId, adapter->itableIndex, adapter->kotlinImpl);
    }
  }

  // Compiler relies on using reverse adapters here from all supertypes
  // in [ObjCExportCodeGenerator.createReverseAdapters].
  for (const TypeInfo* typeInfo : addedInterfaces) {
    const ObjCTypeAdapter* typeAdapter = getTypeAdapter(typeInfo);

    if (typeAdapter == nullptr) continue;

    if (superITable != nullptr) {
      // The interface vtable has to be created always in order for type checks to work.
      addITable(typeInfo->classId_, typeAdapter->kotlinItableSize);
    }

    for (int i = 0; i < typeAdapter->reverseAdapterNum; ++i) {
      itableEqualsSuper = false;
      const KotlinToObjCMethodAdapter* adapter = &typeAdapter->reverseAdapters[i];
      throwIfCantBeOverridden(clazz, adapter);

      RuntimeAssert(adapter->vtableIndex == -1, "");

      if (adapter->itableIndex != -1 && superITable != nullptr) {
        // In general, [adapter->interfaceId] might not be equal to [typeInfo->classId_].
        auto interfaceId = adapter->interfaceId;
        addITable(interfaceId, adapter->itableSize);
        addToITable(interfaceId, adapter->itableIndex, adapter->kotlinImpl);
      }
    }
  }

  // TODO: consider forbidding the class being abstract.

  const TypeInfo* result = createTypeInfo(class_getName(clazz), superType, addedInterfaces, vtable, interfaceVTables,
                                          superITable, superITableSize, itableEqualsSuper, fieldsInfo);

  // TODO: it will probably never be requested, since such a class can't be instantiated in Kotlin.
  result->writableInfo_->objCExport.objCClass = clazz;
  return result;
}

static kotlin::SpinLock<kotlin::MutexThreadStateHandling::kSwitchIfRegistered> typeInfoCreationMutex;

static const TypeInfo* getOrCreateTypeInfo(Class clazz) {
  const TypeInfo* result = Kotlin_ObjCExport_getAssociatedTypeInfo(clazz);
  if (result != nullptr) {
    return result;
  }

  Class superClass = class_getSuperclass(clazz);

  const TypeInfo* superType = superClass == nullptr ?
    theForeignObjCObjectTypeInfo :
    getOrCreateTypeInfo(superClass);

  std::lock_guard lockGuard(typeInfoCreationMutex);

  result = Kotlin_ObjCExport_getAssociatedTypeInfo(clazz); // double-checking.
  if (result == nullptr) {
    result = createTypeInfo(clazz, superType, nullptr);
    setAssociatedTypeInfo(clazz, result);
  }

  return result;
}

const TypeInfo* Kotlin_ObjCExport_createTypeInfoWithKotlinFieldsFrom(Class clazz, const TypeInfo* fieldsInfo) {
  Class superClass = class_getSuperclass(clazz);
  RuntimeCheck(superClass != nullptr, "");

  const TypeInfo* superType = getOrCreateTypeInfo(superClass);

  return createTypeInfo(clazz, superType, fieldsInfo);
}

static kotlin::SpinLock<kotlin::MutexThreadStateHandling::kSwitchIfRegistered> classCreationMutex;
static int anonymousClassNextId = 0;

static void addVirtualAdapters(Class clazz, const ObjCTypeAdapter* typeAdapter) {
  for (int i = 0; i < typeAdapter->virtualAdapterNum; ++i) {
    const ObjCToKotlinMethodAdapter* adapter = typeAdapter->virtualAdapters + i;
    SEL selector = sel_registerName(adapter->selector);

    class_addMethod(clazz, selector, adapter->imp, adapter->encoding);
  }
}

static Class createClass(const TypeInfo* typeInfo, Class superClass) {
  RuntimeAssert(typeInfo->superType_ != nullptr, "");

  kotlin::NativeOrUnregisteredThreadGuard threadStateGuard(/* reentrant = */ true);

  int classIndex = (anonymousClassNextId++);
  std::string className = Kotlin_ObjCInterop_getUniquePrefix();
  className += "_kobjcc";
  className += std::to_string(classIndex);

  Class result = objc_allocateClassPair(superClass, className.c_str(), 0);
  RuntimeCheck(result != nullptr, "");

  // TODO: optimize by adding virtual adapters only for overridden methods.

  if (getTypeAdapter(typeInfo->superType_) == nullptr) {
    // class for super type is also synthesized, no need to add class adapters;
  } else {
    for (const TypeInfo* superType = typeInfo->superType_; superType != nullptr; superType = superType->superType_) {
      const ObjCTypeAdapter* typeAdapter = getTypeAdapter(superType);
      if (typeAdapter != nullptr) {
        addVirtualAdapters(result, typeAdapter);
      }
    }
  }

  std::unordered_set<const TypeInfo*> superImplementedInterfaces(
          typeInfo->superType_->implementedInterfaces_,
          typeInfo->superType_->implementedInterfaces_ + typeInfo->superType_->implementedInterfacesCount_
  );

  for (int i = 0; i < typeInfo->implementedInterfacesCount_; ++i) {
    const TypeInfo* interface = typeInfo->implementedInterfaces_[i];
    const ObjCTypeAdapter* typeAdapter = getTypeAdapter(interface);
    if (typeAdapter != nullptr) {
      // Note: we could avoid adding virtual adapters if inherited from super type,
      // but what's the point?
      addVirtualAdapters(result, typeAdapter);
      if (superImplementedInterfaces.find(interface) == superImplementedInterfaces.end()) {
        addProtocolForAdapter(result, typeAdapter);
      }
    }
  }

  objc_registerClassPair(result);

  // TODO: it will probably never be requested, since such a class can't be instantiated in Objective-C.
  setAssociatedTypeInfo(result, typeInfo);

  return result;
}

static void setClassEnsureInitialized(const TypeInfo* typeInfo, Class cls) {
  RuntimeAssert(cls != nullptr, "");

  // ObjC runtime calls +initialize under a global lock on the first access to an object.
  // `[result self]` below will ensure ahead of time initialization
  // we only have to make it happen in the native state
  kotlin::NativeOrUnregisteredThreadGuard threadStateGuard(true);
  [cls self];

  typeInfo->writableInfo_->objCExport.objCClass = cls;
}

static Class getOrCreateClass(const TypeInfo* typeInfo) {
  Class result = typeInfo->writableInfo_->objCExport.objCClass;
  if (result != nullptr) {
    return result;
  }

  const ObjCTypeAdapter* typeAdapter = getTypeAdapter(typeInfo);
  if (typeAdapter != nullptr) {
    result = objc_getClass(typeAdapter->objCName);
    setClassEnsureInitialized(typeInfo, result);
  } else {
    Class superClass = getOrCreateClass(typeInfo->superType_);

    std::lock_guard lockGuard(classCreationMutex); // Note: non-recursive

    result = typeInfo->writableInfo_->objCExport.objCClass; // double-checking.
    if (result == nullptr) {
        result = createClass(typeInfo, superClass);
        // Don't have to be a release store –
        // the operations above are synchronized and thus might not be reordered after this store.
        setClassEnsureInitialized(typeInfo, result);
    }
  }

  return result;
}

extern "C" void Kotlin_ObjCExport_AbstractMethodCalled(id self, SEL selector) {
  [NSException raise:NSGenericException
        format:@"[%s %s] is abstract",
        class_getName(object_getClass(self)), sel_getName(selector)];
}

extern "C" void Kotlin_ObjCExport_AbstractClassConstructorCalled(id self, const TypeInfo *clazz) {
    Class objectClass = object_getClass(self);
    Class constructorClass = getOrCreateClass(clazz);
    if (objectClass == constructorClass) {
        [NSException raise:NSGenericException format:@"Class %s is abstract and can't be instantiated", class_getName(objectClass)];
    }
}

extern "C" NSInteger Kotlin_ObjCExport_NSIntegerTypeProvider() {
    return 0;
}

#else

extern "C" ALWAYS_INLINE void* Kotlin_Interop_refToObjC(ObjHeader* obj) {
  RuntimeAssert(false, "Unavailable operation");
  return nullptr;
}

extern "C" ALWAYS_INLINE OBJ_GETTER(Kotlin_Interop_refFromObjC, void* obj) {
  RuntimeAssert(false, "Unavailable operation");
  RETURN_OBJ(nullptr);
}

#endif // KONAN_OBJC_INTEROP
