/*
 * Copyright 2010-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

#include "Natives.h"

#if KONAN_OBJC_INTEROP

#import <objc/runtime.h>
#import <CoreFoundation/CoreFoundation.h>
#import <Foundation/Foundation.h>
#import "CompilerConstants.hpp"
#import "Memory.h"
#import "KString.h"
#import "ObjCInteropUtils.h"
#import "ObjCInteropUtilsPrivate.h"
#import "ObjCInterop.h"
#import "ObjCExportPrivate.h"
#import "WritableTypeInfo.hpp"

namespace {
  Class nsStringClass = nullptr;

  Class getNSStringClass() {
    Class result = nsStringClass;
    if (result == nullptr) {
      // Lookup dynamically to avoid direct reference to Foundation:
      result = objc_getClass("NSString");
      RuntimeAssert(result != nullptr, "NSString class not found");
      nsStringClass = result;
    }
    return result;
  }

  // Note: using @"foo" string literals leads to linkage dependency on frameworks.
  NSString* cStringToNS(const char* str) {
    return [getNSStringClass() stringWithCString:str encoding:NSUTF8StringEncoding];
  }
}

extern "C" {

id Kotlin_ObjCExport_CreateRetainedNSStringFromKString(ObjHeader* str);

extern "C" id objc_autorelease(id self);

id Kotlin_Interop_CreateNSStringFromKString(ObjHeader* str) {
  // Note: this function is just a bit specialized [Kotlin_Interop_refToObjC].
  if (str == nullptr) {
    return nullptr;
  }

  if (void* associatedObject = str->GetAssociatedObject()) {
    return (id)associatedObject;
  }

  return objc_autorelease(Kotlin_ObjCExport_CreateRetainedNSStringFromKString(str));
}

OBJ_GETTER(Kotlin_Interop_CreateKStringFromNSString, NSString* str) {
  if (str == nullptr) {
    RETURN_OBJ(nullptr);
  }

  KRef result;
  auto immutableCopyOrSameStr = CFStringCreateCopy(nullptr, (CFStringRef)str);
  auto length = CFStringGetLength(immutableCopyOrSameStr);
  if (length == 0) RETURN_RESULT_OF0(TheEmptyString);

  auto encoding = CFStringGetFastestEncoding(immutableCopyOrSameStr);
  switch (encoding) {
    case kCFStringEncodingASCII:
    case kCFStringEncodingNonLossyASCII:
    case kCFStringEncodingISOLatin1:
      if (kotlin::compiler::latin1Strings()) {
        result = CreateUninitializedString(StringEncoding::kLatin1, length, OBJ_RESULT);
        CFStringGetBytes(immutableCopyOrSameStr, {0, length}, encoding, '?', false,
          reinterpret_cast<UInt8*>(StringHeader::of(result)->data()), length, nullptr);
        break;
      }
    default:
      result = CreateUninitializedString(StringEncoding::kUTF16, length, OBJ_RESULT);
      CFStringGetCharacters(immutableCopyOrSameStr, {0, length},
        reinterpret_cast<UniChar*>(StringHeader::of(result)->data()));
      break;
  }
  result->SetAssociatedObject((void*)immutableCopyOrSameStr);
  RETURN_OBJ(result);
}

// Note: this body is used for init methods with signatures differing from this;
// it is correct on arm64 and x86_64, because the body uses only the first two arguments which are fixed,
// and returns pointers.
id MissingInitImp(id self, SEL _cmd) {
  const char* className = object_getClassName(self);
  [self release]; // Since init methods receive ownership on the receiver.

  // Lookup dynamically to avoid direct reference to Foundation:
  Class nsExceptionClass = objc_getClass("NSException");
  RuntimeAssert(nsExceptionClass != nullptr, "NSException class not found");

  [nsExceptionClass raise:cStringToNS("Initializer is not implemented")
    format:cStringToNS("%s is not implemented in %s"),
    sel_getName(_cmd), className];

  return nullptr;
}

// Initialized in [ObjCInteropUtilsClasses.mm].
id (*Kotlin_Interop_createKotlinObjectHolder_ptr)(KRef any) = nullptr;
KRef (*Kotlin_Interop_unwrapKotlinObjectHolder_ptr)(id holder) = nullptr;

id Kotlin_Interop_createKotlinObjectHolder(KRef any) {
  return Kotlin_Interop_createKotlinObjectHolder_ptr(any);
}

KRef Kotlin_Interop_unwrapKotlinObjectHolder(id holder) {
  return Kotlin_Interop_unwrapKotlinObjectHolder_ptr(holder);
}

KBoolean Kotlin_Interop_DoesObjectConformToProtocol(id obj, void* prot, KBoolean isMeta) {
  BOOL objectIsClass = class_isMetaClass(object_getClass(obj));
  if ((isMeta && !objectIsClass) || (!isMeta && objectIsClass)) return false;
  // TODO: handle root classes properly.

  return [((id<NSObject>)obj) conformsToProtocol:(Protocol*)prot];
}

// This basically returns `objc_getProtocol(protName)`, but caches the result using `protCache`.
static Protocol* getProtocolByName(const char* protName, Protocol** protCache) {
    // This value stored into the `*protCache` indicates that `objc_getProtocol(protName) == nullptr`.
    static Protocol* const notFoundMarker = reinterpret_cast<Protocol*>(1);

    Protocol* cachedValue = *protCache;
    if (cachedValue != nullptr) return cachedValue == notFoundMarker ? nullptr : cachedValue;

    Protocol* result = objc_getProtocol(protName);
    *protCache = result == nullptr ? notFoundMarker : result;

    RuntimeAssert(result != notFoundMarker, "protocol pointer equals %p", notFoundMarker);

    return result;
}

KBoolean Kotlin_Interop_DoesObjectConformToProtocolByName(
    ObjHeader* kotlinObj,
    id obj,
    const char* protName,
    Protocol** protCache,
    KBoolean isMeta
) {
  BOOL objectIsClass = class_isMetaClass(object_getClass(obj));
  if ((isMeta && !objectIsClass) || (!isMeta && objectIsClass)) return false;
  // TODO: handle root classes properly.

  Protocol* prot = getProtocolByName(protName, protCache);
  if (prot == nullptr) {
    /*
    This doesn't mean that the object doesn't conform to the protocol.
    If `IsKotlinObjCClass` is true for the class, it can still conform to the protocol even if `prot == nullptr`.
    See the comment in `IsInstanceOfKotlinClassImplementingObjCProtocol` for the details.

    On the other hand, since `prot == nullptr`, if the object conforms to the protocol,
    this can't be a regular Objective-C conformance, since the latter makes the protocol runtime
    info exist. So it is safe to assume that checking the Kotlin case is enough.
    */

    return IsInstanceOfKotlinClassImplementingObjCProtocol(kotlinObj, obj, protName);
  }

  return [((id<NSObject>)obj) conformsToProtocol:(Protocol*)prot];
}

KBoolean Kotlin_Interop_IsObjectKindOfClass(id obj, void* cls) {
  return [((id<NSObject>)obj) isKindOfClass:(Class)cls];
}

// region @Tencent: Native helpers for KClass.isSubclassOf and ObjC KClass implementations.
/**
 * Checks whether a runtime `KClassImpl` (`typeInfo`, e.g. from `obj::class` or `PureKotlinClass::class`)
 * is a subtype of an external Objective-C class or protocol (`KClassImplExternalObjC`, e.g. `NSObject::class`
 * or `NSCopyingProtocol::class`).
 *
 * Why `IsSubtype(typeInfo, theForeignObjCObjectTypeInfo)` is required:
 * In both ObjCExport and SwiftExport, exported Kotlin classes (and Swift subclasses of exported Kotlin classes)
 * have an associated ObjC `Class` stored in `kotlin::objCExport(typeInfo).objCClass` that inherits from
 * `KotlinBase : NSObject` at the Objective-C runtime level:
 *   1. For a Swift subclass of an exported Kotlin class (`class SwiftSub : KotlinOpenClass`),
 *      `createTypeInfo` sets `objCExport(patchedTypeInfo).objCClass = SwiftSub`, which inherits from `KotlinBase : NSObject`.
 *   2. In SwiftExport, calling `anyWrapperClass()` or `createExistentialWrapperClass()` populates
 *      `objCExport(theAnyTypeInfo).objCClass = KotlinBase` and `objCExport(privateKotlinClass).objCClass = _kobjcc0`
 *      (both of which inherit from `KotlinBase : NSObject`).
 * Without checking `IsSubtype(typeInfo, theForeignObjCObjectTypeInfo)`, `[(Class)subtype isSubclassOfClass:NSObject]`
 * would evaluate `[KotlinBase isSubclassOfClass:NSObject]` == YES and falsely report that pure Kotlin classes,
 * `Any::class`, and Swift subclasses of Kotlin classes are subclasses of `NSObject`!
 *
 * Only genuine Objective-C objects (and Kotlin/Swift subclasses of Objective-C classes) have `TypeInfo`s that
 * inherit from `theForeignObjCObjectTypeInfo`.
 */
KBoolean Kotlin_Interop_KClassImpl_isSubTypeOf(const TypeInfo *typeInfo,
                                               ObjHeader *supertypeName,
                                               bool isSuperProtocol) {
  if (!IsSubtype(typeInfo, theForeignObjCObjectTypeInfo)) {
    return false;
  }
  auto subtype = kotlin::objCExport(typeInfo).objCClass;
  if (isSuperProtocol) {
    auto supertype = NSProtocolFromString(Kotlin_Interop_CreateNSStringFromKString(supertypeName));
    return [(Class) subtype conformsToProtocol:(Protocol *) supertype];
  } else {
    auto supertype = NSClassFromString(Kotlin_Interop_CreateNSStringFromKString(supertypeName));
    return [(Class) subtype isSubclassOfClass:(Class) supertype];
  }
}

KBoolean Kotlin_Interop_KClassImpl_isSubTypeOf_classInfo(const TypeInfo *typeInfo,
                                                         const KotlinObjCClassInfo *superClassInfo) {
  auto subtype = kotlin::objCExport(typeInfo).objCClass;
  auto supertype = static_cast<Class>(CreateKotlinObjCClass(superClassInfo));
  return [(Class) subtype isSubclassOfClass:(Class) supertype];
}

/**
 * Fallback subtype check in SwiftExport when `IsSubtype(typeInfo, superTypeInfo)` returns `false`.
 *
 * Why needed:
 * Unlike `Kotlin_ObjCExport_getOrCreateTypeInfo` (which recursively walks `class_getSuperclass`),
 * `Kotlin_SwiftExport_getOrCreateTypeInfoForSwiftSubclass` creates a synthesized `TypeInfo`
 * (flagged with `TF_OBJC_DYNAMIC`) whose `superType_` points directly to the bound Kotlin base class,
 * flattening multi-level Swift inheritance (`class SwiftLevel2 : SwiftLevel1` where `class SwiftLevel1 : Base, Speaker`).
 * Furthermore, `class_copyProtocolList(SwiftLevel2)` only reports protocols directly declared on `SwiftLevel2`,
 * not those inherited from `SwiftLevel1`.
 *
 * Instead of invasive changes to upstream `TypeInfo` synthesis in `ObjCExport.mm`, when `swiftExport` is enabled
 * and `typeInfo` is a dynamically synthesized Swift subclass (`TF_OBJC_DYNAMIC`), we fall back to the Objective-C/Swift runtime:
 *   1. If `superTypeInfo` is also a synthesized Swift subclass (`TF_OBJC_DYNAMIC`), compare their
 *      underlying `Class` objects directly via `[subtype isSubclassOfClass:supertype]`.
 *   2. If `superTypeInfo` is an exported Kotlin interface (`TF_INTERFACE`), look up its exported `@objc`
 *      protocol via `typeAdapter->objCName` and check `[subtype conformsToProtocol:proto]` (which walks
 *      the entire Swift superclass chain).
 */
KBoolean Kotlin_Interop_KClassImpl_isSubTypeOf_swiftExport(const TypeInfo *typeInfo,
                                                           const TypeInfo *superTypeInfo) {
  if (!kotlin::compiler::swiftExport() || (typeInfo->flags_ & TF_OBJC_DYNAMIC) == 0) {
    return false;
  }
  auto subtype = static_cast<Class>(kotlin::objCExport(typeInfo).objCClass);
  if (subtype == nullptr) return false;
  if ((superTypeInfo->flags_ & TF_OBJC_DYNAMIC) != 0) {
    auto supertype = static_cast<Class>(kotlin::objCExport(superTypeInfo).objCClass);
    return supertype != nullptr && [subtype isSubclassOfClass:supertype];
  }
  if ((superTypeInfo->flags_ & TF_INTERFACE) != 0) {
    auto superAdapter = kotlin::objCExport(superTypeInfo).typeAdapter;
    if (superAdapter != nullptr && superAdapter->objCName != nullptr) {
      auto proto = objc_getProtocol(superAdapter->objCName);
      return proto != nullptr && [subtype conformsToProtocol:proto];
    }
  }
  return false;
}

KBoolean Kotlin_Interop_KClassImpl_equals(const TypeInfo *typeInfo,
                                          ObjHeader *otherTypeName,
                                          bool isProtocol) {
  if (isProtocol) {
    // thisType should never be a protocol.
    return false;
  } else {
    auto thisType = kotlin::objCExport(typeInfo).objCClass;
    auto otherType = NSClassFromString(Kotlin_Interop_CreateNSStringFromKString(otherTypeName));
    return thisType == otherType;
  }
}

KBoolean Kotlin_Interop_KClassImplObjC_isSubTypeOf(const KotlinObjCClassInfo *classInfo,
                                                   ObjHeader *supertypeName,
                                                   bool isSuperProtocol) {
  auto subtype = static_cast<Class>(CreateKotlinObjCClass(classInfo));
  if (isSuperProtocol) {
    auto supertype = NSProtocolFromString(Kotlin_Interop_CreateNSStringFromKString(supertypeName));
    return [(Class) subtype conformsToProtocol:(Protocol *) supertype];
  } else {
    auto supertype = NSClassFromString(Kotlin_Interop_CreateNSStringFromKString(supertypeName));
    return [(Class) subtype isSubclassOfClass:(Class) supertype];
  }
}

/**
 * Checks whether a Kotlin-defined Objective-C subclass literal (`KClassImplObjC`, e.g. `KotlinNSArray::class`)
 * is a subtype of a runtime `KClassImpl` (`superTypeInfo`, e.g. `NSObject()::class` or `KotlinNSArray()::class`).
 *
 * Why needed:
 * `classInfo->typeInfo` on `KotlinObjCClassInfo` only points to the compile-time Objective-C base `TypeInfo`
 * (typically `theForeignObjCObjectTypeInfo`), NOT the dynamically created runtime `TypeInfo` attached to the
 * synthesized Objective-C `Class` when an instance is allocated (`KotlinNSArray()::class`).
 * Therefore `IsSubtype(classInfo->typeInfo, superTypeInfo)` would fail for `KotlinNSArray::class.isSubclassOf(KotlinNSArray()::class)`.
 * Comparing the underlying Objective-C `Class` objects via `isSubclassOfClass:` resolves the actual hierarchy.
 */
KBoolean Kotlin_Interop_KClassImplObjC_isSubTypeOf_typeInfo(const KotlinObjCClassInfo *classInfo,
                                                            const TypeInfo *superTypeInfo) {
  if (!IsSubtype(superTypeInfo, theForeignObjCObjectTypeInfo)) {
    return false;
  }
  auto subtype = static_cast<Class>(CreateKotlinObjCClass(classInfo));
  auto supertype = kotlin::objCExport(superTypeInfo).objCClass;
  return [(Class) subtype isSubclassOfClass:(Class) supertype];
}

/**
 * Checks whether an external Objective-C class literal (`KClassImplExternalObjC`, e.g. `NSLock::class` or `NSObject::class`)
 * is a subtype of a runtime `KClassImpl` (`superTypeInfo`, e.g. `NSObject()::class` or `NSLock()::class`).
 *
 * Why needed:
 * Obtaining `::class` from an Objective-C object instance at runtime (such as `NSObject()::class`) yields a
 * `KClassImpl` wrapping the dynamically created `TypeInfo` for that `Class`, whereas the class literal
 * `NSObject::class` is a `KClassImplExternalObjC`. Without this check, `NSObject::class.isSubclassOf(NSObject()::class)`
 * and `NSLock::class.isSubclassOf(NSObject()::class)` would return `false` even though `NSObject::class == NSObject()::class`
 * is `true`.
 */
KBoolean Kotlin_Interop_KClassImplExternalObjC_isSubTypeOf_typeInfo(ObjHeader *subtypeName,
                                                                    const TypeInfo *superTypeInfo) {
  if (!IsSubtype(superTypeInfo, theForeignObjCObjectTypeInfo)) {
    return false;
  }
  auto subtype = NSClassFromString(Kotlin_Interop_CreateNSStringFromKString(subtypeName));
  auto supertype = kotlin::objCExport(superTypeInfo).objCClass;
  return [(Class) subtype isSubclassOfClass:(Class) supertype];
}

KBoolean Kotlin_Interop_KClassImplExternalObjC_isSubTypeOf(ObjHeader *subtypeName,
                                                           bool isSubProtocol,
                                                           ObjHeader *supertypeName,
                                                           bool isSuperProtocol) {
  if (isSubProtocol && isSuperProtocol) {
    auto subtype = NSProtocolFromString(Kotlin_Interop_CreateNSStringFromKString(subtypeName));
    auto supertype = NSProtocolFromString(Kotlin_Interop_CreateNSStringFromKString(supertypeName));
    return protocol_conformsToProtocol((Protocol *) subtype, (Protocol *) supertype);
  } else if (!isSubProtocol && isSuperProtocol) {
    auto subtype = NSClassFromString(Kotlin_Interop_CreateNSStringFromKString(subtypeName));
    auto supertype = NSProtocolFromString(Kotlin_Interop_CreateNSStringFromKString(supertypeName));
    return [(Class) subtype conformsToProtocol:(Protocol *) supertype];
  } else if (isSubProtocol && !isSuperProtocol) {
    return false;
  } else {
    auto subtype = NSClassFromString(Kotlin_Interop_CreateNSStringFromKString(subtypeName));
    auto supertype = NSClassFromString(Kotlin_Interop_CreateNSStringFromKString(supertypeName));
    return [(Class) subtype isSubclassOfClass:(Class) supertype];
  }
}

KBoolean Kotlin_Interop_KClassImplExternalObjC_isInstance(id obj, ObjHeader *name, bool isProtocol) {
  NSString *typeName = Kotlin_Interop_CreateNSStringFromKString(name);
  if (isProtocol) {
    auto proto = NSProtocolFromString((NSString *) typeName);
    return [((id <NSObject>) obj) conformsToProtocol:proto];
  } else {
    auto cls = NSClassFromString((NSString *) typeName);
    return [((id <NSObject>) obj) isKindOfClass:(Class) cls];
  }
}

KInt Kotlin_Interop_KClassImplExternalObjC_hashCode(ObjHeader *name, bool isProtocol) {
  NSString *typeName = Kotlin_Interop_CreateNSStringFromKString(name);
  if (isProtocol) {
    auto proto = NSProtocolFromString((NSString *) typeName);
    return (KInt) [proto hash];
  } else {
    auto cls = NSClassFromString((NSString *) typeName);
    return (KInt) [cls hash];
  }
}

OBJ_GETTER(Kotlin_Interop_KClassImplObjC_className, const KotlinObjCClassInfo *classInfo) {
  auto objCClass = static_cast<Class>(CreateKotlinObjCClass(classInfo));
  NSString *className = NSStringFromClass(objCClass);
  RETURN_RESULT_OF(Kotlin_Interop_CreateKStringFromNSString, className);
}

const TypeInfo * Kotlin_Interop_KClassImplObjC_typeInfo(const KotlinObjCClassInfo *classInfo) {
  return classInfo->typeInfo;
}
// endregion

OBJ_GETTER((*Konan_ObjCInterop_getWeakReference_ptr), KRef ref) = nullptr;
void (*Konan_ObjCInterop_initWeakReference_ptr)(KRef ref, id objcPtr) = nullptr;

OBJ_GETTER(Konan_ObjCInterop_getWeakReference, KRef ref) {
  RETURN_RESULT_OF(Konan_ObjCInterop_getWeakReference_ptr, ref);
}

void Konan_ObjCInterop_initWeakReference(KRef ref, id objcPtr) {
  Konan_ObjCInterop_initWeakReference_ptr(ref, objcPtr);
}

} // extern "C"

#else // KONAN_OBJC_INTEROP

extern "C" {

void* Kotlin_Interop_CreateNSStringFromKString(const ArrayHeader* str) {
  RuntimeAssert(false, "Objective-C interop is disabled");
  return nullptr;
}

OBJ_GETTER(Kotlin_Interop_CreateKStringFromNSString, void* str) {
  RuntimeAssert(false, "Objective-C interop is disabled");
  RETURN_OBJ(nullptr);
}

void* Kotlin_Interop_createKotlinObjectHolder(KRef any) {
  RuntimeAssert(false, "Objective-C interop is disabled");
  return nullptr;
}

KRef Kotlin_Interop_unwrapKotlinObjectHolder(void* holder) {
  RuntimeAssert(false, "Objective-C interop is disabled");
  return nullptr;
}
  
OBJ_GETTER(Konan_ObjCInterop_getWeakReference, KRef ref) {
  RuntimeAssert(false, "Objective-C interop is disabled");
  RETURN_OBJ(nullptr);
}

void Konan_ObjCInterop_initWeakReference(KRef ref, void* objcPtr) {
  RuntimeAssert(false, "Objective-C interop is disabled");
}

// region @Tencent: Stub symbols for ObjC KClass implementations on non-Apple targets.
KBoolean Kotlin_Interop_KClassImpl_isSubTypeOf(const TypeInfo *typeInfo,
                                               ObjHeader *supertypeName,
                                               bool isSuperProtocol) {
  RuntimeAssert(false, "Objective-C interop is disabled");
  return false;
}

KBoolean Kotlin_Interop_KClassImpl_isSubTypeOf_classInfo(const TypeInfo *typeInfo,
                                                         const void *superClassInfo) {
  RuntimeAssert(false, "Objective-C interop is disabled");
  return false;
}

KBoolean Kotlin_Interop_KClassImpl_isSubTypeOf_swiftExport(const TypeInfo *typeInfo,
                                                           const TypeInfo *superTypeInfo) {
  return false;
}

KBoolean Kotlin_Interop_KClassImpl_equals(const TypeInfo *typeInfo,
                                          ObjHeader *otherTypeName,
                                          bool isProtocol) {
  RuntimeAssert(false, "Objective-C interop is disabled");
  return false;
}

KBoolean Kotlin_Interop_KClassImplObjC_isSubTypeOf(const void *classInfo,
                                                   ObjHeader *supertypeName,
                                                   bool isSuperProtocol) {
  RuntimeAssert(false, "Objective-C interop is disabled");
  return false;
}

KBoolean Kotlin_Interop_KClassImplObjC_isSubTypeOf_typeInfo(const void *classInfo,
                                                            const TypeInfo *superTypeInfo) {
  RuntimeAssert(false, "Objective-C interop is disabled");
  return false;
}

KBoolean Kotlin_Interop_KClassImplExternalObjC_isSubTypeOf_typeInfo(ObjHeader *subtypeName,
                                                                    const TypeInfo *superTypeInfo) {
  RuntimeAssert(false, "Objective-C interop is disabled");
  return false;
}

KBoolean Kotlin_Interop_KClassImplExternalObjC_isSubTypeOf(ObjHeader *subtypeName,
                                                           bool isSubProtocol,
                                                           ObjHeader *supertypeName,
                                                           bool isSuperProtocol) {
  RuntimeAssert(false, "Objective-C interop is disabled");
  return false;
}

KBoolean Kotlin_Interop_KClassImplExternalObjC_isInstance(id obj, ObjHeader *name, bool isProtocol) {
  RuntimeAssert(false, "Objective-C interop is disabled");
  return false;
}

KInt Kotlin_Interop_KClassImplExternalObjC_hashCode(ObjHeader *name, bool isProtocol) {
  RuntimeAssert(false, "Objective-C interop is disabled");
  return 0;
}

OBJ_GETTER(Kotlin_Interop_KClassImplObjC_className, const void *classInfo) {
  RuntimeAssert(false, "Objective-C interop is disabled");
  RETURN_OBJ(nullptr);
}

const TypeInfo * Kotlin_Interop_KClassImplObjC_typeInfo(const void *classInfo) {
  RuntimeAssert(false, "Objective-C interop is disabled");
  return nullptr;
}
// endregion

} // extern "C"

#endif // KONAN_OBJC_INTEROP
