/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

package kotlin.reflect

/**
 * Represents a class and provides introspection capabilities.
 * Instances of this class are obtainable by the `::class` syntax.
 * See the [Kotlin language documentation](https://kotlinlang.org/docs/reference/reflection.html#class-references)
 * for more information.
 *
 * @param T the type of the class.
 */
public actual interface KClass<T : Any> : KDeclarationContainer, KAnnotatedElement, KClassifier {
    /**
     * The simple name of the class as it was declared in the source code,
     * or `null` if the class has no name (if, for example, it is a class of an anonymous object).
     */
    public actual val simpleName: String?

    /**
     * The fully qualified dot-separated name of the class,
     * or `null` if the class is local or a class of an anonymous object.
     */
    public actual val qualifiedName: String?

    /**
     * Returns `true` if [value] is an instance of this class on a given platform.
     */
    @SinceKotlin("1.1")
    public actual fun isInstance(value: Any?): Boolean

    // region @Tencent: Check if this class is a subclass of another KClass in the runtime.
    /**
     * Checks if this class is a subclass of [other] or the same class as [other].
     *
     * Returns `true` if this class is the same as [other], or if this class directly or indirectly
     * inherits from [other]. This includes interface implementation relationships.
     *
     * Example:
     * ```
     * open class Animal
     * class Dog : Animal()
     *
     * Dog::class.isSubclassOf(Animal::class) // true
     * Dog::class.isSubclassOf(Dog::class)    // true
     * Animal::class.isSubclassOf(Dog::class) // false
     * ```
     *
     * @param other the target class to check against
     * @return `true` if this class is a subclass of [other] or the same as [other]; `false` otherwise
     */
    public fun isSubclassOf(other: KClass<*>): Boolean = false
    // endregion

    /**
     * Returns `true` if this [KClass] instance represents the same Kotlin class as the class represented by [other].
     * On JVM this means that all of the following conditions are satisfied:
     *
     * 1. [other] has the same (fully qualified) Kotlin class name as this instance.
     * 2. [other]'s backing [Class] object is loaded with the same class loader as the [Class] object of this instance.
     * 3. If the classes represent [Array], then [Class] objects of their element types are equal.
     *
     * For example, on JVM, [KClass] instances for a primitive type (`int`) and the corresponding wrapper type (`java.lang.Integer`)
     * are considered equal, because they have the same fully qualified name "kotlin.Int".
     */
    actual override fun equals(other: Any?): Boolean // KT-24971

    actual override fun hashCode(): Int // KT-24971
}
