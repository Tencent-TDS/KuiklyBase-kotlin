// TARGET_BACKEND: JVM
// WITH_STDLIB
// WORKS_WHEN_VALUE_CLASS
// LANGUAGE: +ValueClasses
// WITH_SIGNATURES

OPTIONAL_JVM_INLINE_ANNOTATION
value class WithArray<T : Any>(val x: Array<T>)

object Example {
    fun <R : Any> WithArray<Int>.genericArgument(x: WithArray<R>) {}
    fun WithArray<Int>.instantiatedArgument(x: WithArray<String>) {}
    fun returnType(x: WithArray<String>): WithArray<Int> = WithArray(arrayOf(1))
    fun nullableReturnType(x: WithArray<String>): WithArray<Int>? = null
    inline fun <reified R : Any> genericReturnType(x: R): WithArray<R> = WithArray(arrayOf(x))

    val instantiatedProperty: WithArray<Int> = WithArray(arrayOf(1))
    val nullableProperty: WithArray<Int>? = null
}