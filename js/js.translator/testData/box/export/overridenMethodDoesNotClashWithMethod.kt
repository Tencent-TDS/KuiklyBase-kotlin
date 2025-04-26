@JsExport
abstract class Foo1 {
    abstract fun ok(a: Int): String
}

class Foo2 : Foo1() {
    override fun ok(a: Int): String {
        return "OK"
    }

    fun ok(): String {
        return "OK"
    }
}

fun box(): String {
    return if (Foo2().ok(1) == "OK" && Foo2().ok() == "OK") "OK" else "fail"
}
