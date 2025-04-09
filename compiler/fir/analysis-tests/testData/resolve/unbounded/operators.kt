// RUN_PIPELINE_TILL: FRONTEND
// DISABLE_NEXT_PHASE_SUGGESTION

fun Any?.baz() {}

fun Any.bazz() {}

fun <T> T.bar() {}

fun <T> foo(arg: T): Any {
    arg.<!UNRESOLVED_REFERENCE_WRONG_RECEIVER!>baz<!>()
    arg!!.<!UNRESOLVED_REFERENCE_WRONG_RECEIVER!>bazz<!>()
    arg?.bar()
    return <!RETURN_TYPE_MISMATCH!>arg<!>
}

fun <T> foo2(arg: List<T>): List<Any?> {
    val tmp: List<Any?> = <!INITIALIZER_TYPE_MISMATCH!>arg<!>
    val tmp2: List<*> = arg
    val tmp3: Any? = <!INITIALIZER_TYPE_MISMATCH, TYPE_MISMATCH!>arg[0]<!>
    val tmp4 = arg[1]
    return <!RETURN_TYPE_MISMATCH!>tmp2<!>
}

interface Inv<T> {
    fun get(): T
    fun set(arg: T)
}

interface Out<out T> {
    fun get(): T
}

interface In<in T> {
    fun set(arg: T)
}

fun <T> test1(inv: Inv<T>): Any? {
    val invStar: Inv<*> = inv
    val tmp1 = invStar.get()
    return <!RETURN_TYPE_MISMATCH!>tmp1<!>
}

fun <T> test2(inv: Inv<T>): Any? {
    val tmp1 = inv.get()
    return <!RETURN_TYPE_MISMATCH!>tmp1<!>
}

fun <T> test3(arg: T) {
    arg!! // should be an error
    arg?.bar() // should be an error
}
