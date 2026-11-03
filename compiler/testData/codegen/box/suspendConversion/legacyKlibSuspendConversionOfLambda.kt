// ISSUE: KT-91139
// TARGET_BACKEND: JS_IR, JS_IR_ES6, WASM, NATIVE
// IGNORE_KLIB_RUNTIME_ERRORS_WITH_CUSTOM_FIRST_STAGE: ANY:1.9,2.0,2.1,2.2
// KLIBs built by compilers up to 2.2 store a suspend conversion of a function-typed expression as a block with the
// legacy SUSPEND_CONVERSION origin, renamed to FUNCTION_TYPE_EXPRESSION_CONVERSION in 2.4.20. A consumer which doesn't
// recognize the legacy name lowers the block as a plain reference to the synthetic `suspendConversion0` adapter: the
// converted values become equal to each other and reflectable under the adapter's name, whereas the compiler creates
// the conversion without a reflection target.
// The test is KLIB-only: on the JVM, a suspend conversion is an adapted function reference bound to the converted value,
// so two conversions of the same lambda are equal there by design.
// See also compiler/testData/codegen/box/nonLocalReturns/suspendConversion.kt
// WITH_STDLIB
// WITH_COROUTINES

import helpers.*
import kotlin.coroutines.*
import kotlin.reflect.KCallable

fun convert(f: suspend () -> String): suspend () -> String = f

fun toSuspend(f: () -> String): suspend () -> String = convert(f)

val lambda: () -> String = { "lambda" }

fun builder(c: suspend () -> Unit) {
    c.startCoroutine(EmptyContinuation)
}

fun box(): String {
    val a = toSuspend(lambda)
    val b = toSuspend(lambda)

    var result = ""
    builder { result = a() }
    if (result != "lambda") return "Fail: $result"

    if (a == b) return "Fail: two suspend conversions of the same lambda are equal"
    val name = (a as? KCallable<*>)?.name
    if (name != null) return "Fail: a suspend conversion of a lambda is reflectable, with name '$name'"
    return "OK"
}
