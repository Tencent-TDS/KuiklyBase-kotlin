// WITH_STDLIB

fun box(): String {
    val x: String
    run {
        x = "OK"
        val y = x
    }
    return x
}

// 2 ObjectRef
// 1 INNERCLASS kotlin.internal.Ref\$ObjectRef kotlin.internal.Ref ObjectRef
