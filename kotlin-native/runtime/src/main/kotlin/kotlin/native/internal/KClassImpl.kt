/*
 * Copyright 2010-2023 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */
@file:OptIn(ExperimentalForeignApi::class)

package kotlin.native.internal

import kotlin.reflect.KClass
import kotlinx.cinterop.*
import kotlin.internal.UsedFromCompilerGeneratedCode
import kotlin.native.internal.escapeAnalysis.Escapes

@ExportForCompiler
@UsedFromCompilerGeneratedCode
internal class KClassImpl<T : Any>(override val typeInfo: NativePtr) : KClass<T>, TypeInfoHolder {

    @ExportForCompiler
    @ConstantConstructorIntrinsic("KCLASS_IMPL")
    @Suppress("UNREACHABLE_CODE")
    constructor() : this(TODO("This is intrinsic constructor and it shouldn't be used directly"))

    override val simpleName: String?
        get() = TypeInfoNames(typeInfo).simpleName

    override val qualifiedName: String?
        get() = TypeInfoNames(typeInfo).qualifiedName

    // region @Tencent: Implement isSubclassOf and equals for ObjC KClass implementations.
    override fun isInstance(value: Any?): Boolean = value != null && value::class.isSubclassOf(this)

    override fun isSubclassOf(other: KClass<*>): Boolean {
        return when(other) {
            is KClassImpl<*> -> isSubTypeOf(typeInfo, other.typeInfo) || kClassImplIsSubTypeOfSwiftExport(typeInfo, other.typeInfo)
            is KClassImplObjC<*> -> kClassImplIsSubTypeOf(typeInfo, other.classInfo)
            /*
             * val obj = NSObject()
             * obj::class will be instance of KClass<*>.
             */
            is KClassImplExternalObjC<*> -> kClassImplIsSubTypeOf(typeInfo, other.binaryName, other.isProtocol)
            else -> false
        }
    }

    override fun equals(other: Any?): Boolean {
        return when (other) {
            is KClassImpl<*> -> this.typeInfo == other.typeInfo
            is KClassImplObjC<*> -> this.typeInfo == other.typeInfo
            /*
             * val viewController = UIViewController()
             * viewController::class == UIViewController::class
             * this: KClass          == other: KClassImplExternalObjC
             */
            is KClassImplExternalObjC<*> -> kClassImplEquals(typeInfo, other.binaryName, other.isProtocol)
            else -> other is KClass<*> && this.typeInfo == other.typeInfoPtr
        }
    }
    // endregion

    override fun hashCode(): Int = typeInfo.hashCode()

    override fun toString(): String = "class ${fullName ?: "<anonymous>"}"
}

internal val KClass<*>.typeInfoPtr: NativePtr
    get() = (this as? TypeInfoHolder)?.typeInfo ?: NativePtr.NULL

internal val KClass<*>.fullName: String?
    get() = typeInfoPtr.takeUnless { it.isNull() }?.let { TypeInfoNames(it).fullName }

@PublishedApi
internal fun KClass<*>.findAssociatedObject(keyClass: KClass<*>): Any? {
    val typeInfo = this.typeInfoPtr.takeUnless { it.isNull() } ?: return null
    val key = keyClass.typeInfoPtr.takeUnless { it.isNull() } ?: return null
    return findAssociatedObjectImpl(typeInfo, key)
}

// region @Tencent: KClassImpls for ObjC classes.
/**
 * KClassImpl for external ObjC classes.
 *
 * e.g. UIViewController::class
 */
@ExportForCompiler
@PublishedApi
@UsedFromCompilerGeneratedCode
internal class KClassImplExternalObjC<T : Any>(
        internal val binaryName: String,
        internal val isProtocol: Boolean
) : KClass<T> {

    @ExportForCompiler
    @ConstantConstructorIntrinsic("KCLASS_IMPL_EXTERNAL_OBJC")
    @Suppress("UNREACHABLE_CODE")
    constructor() : this(TODO("This is intrinsic constructor and it shouldn't be used directly"), false)

    override val simpleName: String? get() = binaryName

    override val qualifiedName: String? get() = binaryName

    override fun isInstance(value: Any?): Boolean = value != null && value::class.isSubclassOf(this)

    override fun isSubclassOf(other: KClass<*>): Boolean {
        return when (other) {
            Any::class -> true
            is KClassImplExternalObjC<*> ->
                kClassImplExternalObjCIsSubTypeOf(binaryName, isProtocol, other.binaryName, other.isProtocol)
            is KClassImpl<*> ->
                !isProtocol && kClassImplExternalObjCIsSubTypeOfTypeInfo(binaryName, other.typeInfo)
            else -> false
        }
    }

    override fun equals(other: Any?): Boolean {
        return when (other) {
            is KClassImpl<*> -> kClassImplEquals(other.typeInfo, this.binaryName, this.isProtocol)
            is KClassImplObjC<*> -> !isProtocol && binaryName == other.simpleName
            is KClassImplExternalObjC<*> -> binaryName == other.binaryName && isProtocol == other.isProtocol
            else -> false
        }
    }

    override fun hashCode(): Int = kClassImplExternalObjCHashCode(binaryName, isProtocol)

    override fun toString(): String = "External ObjC ${if (isProtocol) "protocol" else "class"} $qualifiedName"
}

/**
 * KClassImpl for Kotlin classes inheriting from NSObject.
 *
 * For example:
 *  class MyViewController: UIViewController() { ... }
 *  MyViewController::class
 */
@ExportForCompiler
@PublishedApi
@UsedFromCompilerGeneratedCode
internal class KClassImplObjC<T : Any>(
        internal val classInfo: NativePtr, // KotlinObjCClassInfo *
) : KClass<T>, TypeInfoHolder {

    @ExportForCompiler
    @ConstantConstructorIntrinsic("KCLASS_IMPL_OBJC")
    @Suppress("UNREACHABLE_CODE")
    constructor() : this(TODO("This is intrinsic constructor and it shouldn't be used directly"))

    override val simpleName: String? get() = kClassImplObjCClassName(classInfo)

    override val qualifiedName: String? get() = kClassImplObjCClassName(classInfo)

    override val typeInfo: NativePtr
        get() = kClassImplObjCTypeInfo(classInfo)

    override fun isInstance(value: Any?): Boolean = value != null && value::class.isSubclassOf(this)

    override fun isSubclassOf(other: KClass<*>): Boolean {
        return when (other) {
            Any::class -> true
            is KClassImplExternalObjC<*> -> {
                kClassImplObjCIsSubTypeOfObjC(classInfo, other.binaryName, other.isProtocol)
            }
            is KClassImplObjC<*> -> {
                // Kotlin classes inheriting from Objective-C classes cannot be subclasses of another Kotlin class or interface.
                this == other
            }
            is KClassImpl<*> -> {
                val typeInfo = kClassImplObjCTypeInfo(classInfo)
                isSubTypeOf(typeInfo, other.typeInfo) || kClassImplObjCIsSubTypeOfTypeInfo(classInfo, other.typeInfo)
            }
            else -> {
                false
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        return when (other) {
            is KClassImpl<*> -> typeInfo == other.typeInfo
            is KClassImplObjC<*> -> classInfo == other.classInfo
            is KClassImplExternalObjC<*> -> simpleName == other.binaryName
            else -> false
        }
    }

    override fun hashCode(): Int = classInfo.hashCode()

    override fun toString(): String = "Kotlin ObjC class $qualifiedName"
}
// endregion

@PublishedApi
@UsedFromCompilerGeneratedCode
internal class KClassUnsupportedImpl(private val message: String) : KClass<Any> {
    override val simpleName: String? get() = error(message)

    override val qualifiedName: String? get() = error(message)

    override fun isInstance(value: Any?): Boolean = error(message)

    // region @Tencent: Always throw error for this unsupported type.
    override fun isSubclassOf(other: KClass<*>): Boolean = error(message)
    // endregion

    override fun equals(other: Any?): Boolean = error(message)

    override fun hashCode(): Int = error(message)

    override fun toString(): String = "unreflected class ($message)"
}

@GCUnsafeCall("Kotlin_TypeInfo_findAssociatedObject")
@Escapes(0b100) // The return value is stored in a global.
private external fun findAssociatedObjectImpl(typeInfo: NativePtr, key: NativePtr): Any?

@ExportForCompiler
@GCUnsafeCall("Kotlin_Any_getTypeInfo")
@Escapes.Nothing
@UsedFromCompilerGeneratedCode
internal external fun getObjectTypeInfo(obj: Any): NativePtr


@GCUnsafeCall("Kotlin_TypeInfo_isInstance")
@Escapes.Nothing
internal external fun isInstance(obj: Any, typeInfo: NativePtr): Boolean

// region @Tencent: Native declarations for KClassImpls.
@GCUnsafeCall("Kotlin_TypeInfo_isSubTypeOf")
private external fun isSubTypeOf(subtype: NativePtr, supertype: NativePtr): Boolean

@GCUnsafeCall("Kotlin_Interop_KClassImpl_isSubTypeOf")
@Escapes.Nothing
private external fun kClassImplIsSubTypeOf(typeInfo: NativePtr, supertypeName: String, isSuperProtocol: Boolean): Boolean

@GCUnsafeCall("Kotlin_Interop_KClassImpl_isSubTypeOf_classInfo")
private external fun kClassImplIsSubTypeOf(typeInfo: NativePtr, superClassInfo: NativePtr): Boolean

@GCUnsafeCall("Kotlin_Interop_KClassImpl_isSubTypeOf_swiftExport")
private external fun kClassImplIsSubTypeOfSwiftExport(typeInfo: NativePtr, superTypeInfo: NativePtr): Boolean

@GCUnsafeCall("Kotlin_Interop_KClassImpl_equals")
@Escapes.Nothing
private external fun kClassImplEquals(typeInfo: NativePtr, otherTypeName: String, isProtocol: Boolean): Boolean

@GCUnsafeCall("Kotlin_Interop_KClassImplExternalObjC_isInstance")
@Escapes.Nothing
private external fun kClassImplExternalObjCIsInstance(obj: Any, typeName: String, isProtocol: Boolean): Boolean

@GCUnsafeCall("Kotlin_Interop_KClassImplExternalObjC_isSubTypeOf")
@Escapes.Nothing
private external fun kClassImplExternalObjCIsSubTypeOf(subtypeName: String, isSubProtocol: Boolean, supertypeName: String, isSuperProtocol: Boolean): Boolean

@GCUnsafeCall("Kotlin_Interop_KClassImplExternalObjC_hashCode")
@Escapes.Nothing
private external fun kClassImplExternalObjCHashCode(typeName: String, isProtocol: Boolean): Int

@GCUnsafeCall("Kotlin_Interop_KClassImplObjC_className")
@Escapes.Nothing
private external fun kClassImplObjCClassName(classInfo: NativePtr): String

@GCUnsafeCall("Kotlin_Interop_KClassImplObjC_typeInfo")
private external fun kClassImplObjCTypeInfo(classInfo: NativePtr): NativePtr

@GCUnsafeCall("Kotlin_Interop_KClassImplObjC_isSubTypeOf")
@Escapes.Nothing
private external fun kClassImplObjCIsSubTypeOfObjC(classInfo: NativePtr, supertypeName: String, isSuperProtocol: Boolean): Boolean

@GCUnsafeCall("Kotlin_Interop_KClassImplObjC_isSubTypeOf_typeInfo")
private external fun kClassImplObjCIsSubTypeOfTypeInfo(classInfo: NativePtr, superTypeInfo: NativePtr): Boolean

@GCUnsafeCall("Kotlin_Interop_KClassImplExternalObjC_isSubTypeOf_typeInfo")
@Escapes.Nothing
private external fun kClassImplExternalObjCIsSubTypeOfTypeInfo(subtypeName: String, superTypeInfo: NativePtr): Boolean
// endregion

@ExportForCompiler
@TransparentForDebugger
@UsedFromCompilerGeneratedCode
internal fun <T : Any> checkNotNull(obj: T?): T {
    if (obj == null)
        throw NullPointerException()
    return obj
}

@ExportForCompiler
@TransparentForDebugger
@UsedFromCompilerGeneratedCode
internal fun <T : Any> downcast(obj: T?, typeInfo: NativePtr, nullable: Boolean): T? {
    if (obj == null) {
        if (nullable) return obj
        throw NullPointerException()
    }
    if (!isInstance(obj, typeInfo))
        ThrowClassCastException(obj, typeInfo)
    return obj
}
