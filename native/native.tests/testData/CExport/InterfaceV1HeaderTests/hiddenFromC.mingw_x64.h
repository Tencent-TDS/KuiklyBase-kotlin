#ifndef KONAN_HIDDENFROMC_H
#define KONAN_HIDDENFROMC_H
#ifdef __cplusplus
extern "C" {
#endif
#ifdef __cplusplus
typedef bool            hiddenFromC_KBoolean;
#else
typedef _Bool           hiddenFromC_KBoolean;
#endif
typedef unsigned short     hiddenFromC_KChar;
typedef signed char        hiddenFromC_KByte;
typedef short              hiddenFromC_KShort;
typedef int                hiddenFromC_KInt;
typedef long long          hiddenFromC_KLong;
typedef unsigned char      hiddenFromC_KUByte;
typedef unsigned short     hiddenFromC_KUShort;
typedef unsigned int       hiddenFromC_KUInt;
typedef unsigned long long hiddenFromC_KULong;
typedef float              hiddenFromC_KFloat;
typedef double             hiddenFromC_KDouble;
#ifndef _MSC_VER
typedef float __attribute__ ((__vector_size__ (16))) hiddenFromC_KVector128;
#else
#include <xmmintrin.h>
typedef __m128 hiddenFromC_KVector128;
#endif
typedef void*              hiddenFromC_KNativePtr;
struct hiddenFromC_KType;
typedef struct hiddenFromC_KType hiddenFromC_KType;

typedef struct {
  hiddenFromC_KNativePtr pinned;
} hiddenFromC_kref_kotlin_Byte;
typedef struct {
  hiddenFromC_KNativePtr pinned;
} hiddenFromC_kref_kotlin_Short;
typedef struct {
  hiddenFromC_KNativePtr pinned;
} hiddenFromC_kref_kotlin_Int;
typedef struct {
  hiddenFromC_KNativePtr pinned;
} hiddenFromC_kref_kotlin_Long;
typedef struct {
  hiddenFromC_KNativePtr pinned;
} hiddenFromC_kref_kotlin_Float;
typedef struct {
  hiddenFromC_KNativePtr pinned;
} hiddenFromC_kref_kotlin_Double;
typedef struct {
  hiddenFromC_KNativePtr pinned;
} hiddenFromC_kref_kotlin_Char;
typedef struct {
  hiddenFromC_KNativePtr pinned;
} hiddenFromC_kref_kotlin_Boolean;
typedef struct {
  hiddenFromC_KNativePtr pinned;
} hiddenFromC_kref_kotlin_Unit;
typedef struct {
  hiddenFromC_KNativePtr pinned;
} hiddenFromC_kref_kotlin_UByte;
typedef struct {
  hiddenFromC_KNativePtr pinned;
} hiddenFromC_kref_kotlin_UShort;
typedef struct {
  hiddenFromC_KNativePtr pinned;
} hiddenFromC_kref_kotlin_UInt;
typedef struct {
  hiddenFromC_KNativePtr pinned;
} hiddenFromC_kref_kotlin_ULong;
typedef struct {
  hiddenFromC_KNativePtr pinned;
} hiddenFromC_kref_tests_native_BaseClass;
typedef struct {
  hiddenFromC_KNativePtr pinned;
} hiddenFromC_kref_tests_native_DerivedClass;


typedef struct {
  /* Service functions. */
  void (*DisposeStablePointer)(hiddenFromC_KNativePtr ptr);
  void (*DisposeString)(const char* string);
  hiddenFromC_KBoolean (*IsInstance)(hiddenFromC_KNativePtr ref, const hiddenFromC_KType* type);
  hiddenFromC_kref_kotlin_Byte (*createNullableByte)(hiddenFromC_KByte);
  hiddenFromC_KByte (*getNonNullValueOfByte)(hiddenFromC_kref_kotlin_Byte);
  hiddenFromC_kref_kotlin_Short (*createNullableShort)(hiddenFromC_KShort);
  hiddenFromC_KShort (*getNonNullValueOfShort)(hiddenFromC_kref_kotlin_Short);
  hiddenFromC_kref_kotlin_Int (*createNullableInt)(hiddenFromC_KInt);
  hiddenFromC_KInt (*getNonNullValueOfInt)(hiddenFromC_kref_kotlin_Int);
  hiddenFromC_kref_kotlin_Long (*createNullableLong)(hiddenFromC_KLong);
  hiddenFromC_KLong (*getNonNullValueOfLong)(hiddenFromC_kref_kotlin_Long);
  hiddenFromC_kref_kotlin_Float (*createNullableFloat)(hiddenFromC_KFloat);
  hiddenFromC_KFloat (*getNonNullValueOfFloat)(hiddenFromC_kref_kotlin_Float);
  hiddenFromC_kref_kotlin_Double (*createNullableDouble)(hiddenFromC_KDouble);
  hiddenFromC_KDouble (*getNonNullValueOfDouble)(hiddenFromC_kref_kotlin_Double);
  hiddenFromC_kref_kotlin_Char (*createNullableChar)(hiddenFromC_KChar);
  hiddenFromC_KChar (*getNonNullValueOfChar)(hiddenFromC_kref_kotlin_Char);
  hiddenFromC_kref_kotlin_Boolean (*createNullableBoolean)(hiddenFromC_KBoolean);
  hiddenFromC_KBoolean (*getNonNullValueOfBoolean)(hiddenFromC_kref_kotlin_Boolean);
  hiddenFromC_kref_kotlin_Unit (*createNullableUnit)(void);
  hiddenFromC_kref_kotlin_UByte (*createNullableUByte)(hiddenFromC_KUByte);
  hiddenFromC_KUByte (*getNonNullValueOfUByte)(hiddenFromC_kref_kotlin_UByte);
  hiddenFromC_kref_kotlin_UShort (*createNullableUShort)(hiddenFromC_KUShort);
  hiddenFromC_KUShort (*getNonNullValueOfUShort)(hiddenFromC_kref_kotlin_UShort);
  hiddenFromC_kref_kotlin_UInt (*createNullableUInt)(hiddenFromC_KUInt);
  hiddenFromC_KUInt (*getNonNullValueOfUInt)(hiddenFromC_kref_kotlin_UInt);
  hiddenFromC_kref_kotlin_ULong (*createNullableULong)(hiddenFromC_KULong);
  hiddenFromC_KULong (*getNonNullValueOfULong)(hiddenFromC_kref_kotlin_ULong);

  /* User functions. */
  struct {
    struct {
      struct {
        struct {
          struct {
            hiddenFromC_KType* (*_type)(void);
            hiddenFromC_kref_tests_native_BaseClass (*BaseClass)();
            hiddenFromC_KInt (*visibleMethod)(hiddenFromC_kref_tests_native_BaseClass thiz);
          } BaseClass;
          struct {
            hiddenFromC_KType* (*_type)(void);
            hiddenFromC_kref_tests_native_DerivedClass (*DerivedClass)();
            hiddenFromC_KInt (*visibleMethod)(hiddenFromC_kref_tests_native_DerivedClass thiz);
          } DerivedClass;
          hiddenFromC_KInt (*get_visibleProperty)();
          void (*set_visibleProperty)(hiddenFromC_KInt set);
          hiddenFromC_KInt (*hiddenFromObjCOnlyFunction)();
          hiddenFromC_KInt (*visibleFunction)();
        } native;
      } tests;
    } root;
  } kotlin;
} hiddenFromC_ExportedSymbols;
extern hiddenFromC_ExportedSymbols* hiddenFromC_symbols(void);
#ifdef __cplusplus
}  /* extern "C" */
#endif
#endif  /* KONAN_HIDDENFROMC_H */
