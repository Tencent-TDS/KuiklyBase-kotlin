// RUN_PIPELINE_TILL: FRONTEND
// DISABLE_NEXT_PHASE_SUGGESTION

fun foo1(arg: Any?) {}

fun <T> foo4(arg: T) {}

fun <A : Any?> foo5(arg: A) {}

fun <B : Any> foo6(arg: B) {}

fun <T> foo2(arg: T) {
    foo1(<!ARGUMENT_TYPE_MISMATCH!>arg<!>)
    foo4(arg)
    <!CANNOT_INFER_PARAMETER_TYPE!>foo5<!>(<!ARGUMENT_TYPE_MISMATCH!>arg<!>)
    <!CANNOT_INFER_PARAMETER_TYPE!>foo6<!>(<!ARGUMENT_TYPE_MISMATCH!>arg<!>)
}

fun foo3(arg: Any?) {
    foo2(arg)
}

fun <T> foo(): T = throw Exception("")
