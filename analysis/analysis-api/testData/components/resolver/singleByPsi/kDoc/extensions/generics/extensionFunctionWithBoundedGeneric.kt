interface Bound
class DerivedBound : Bound

open class Base<T>
class Derived : Base<DerivedBound>()

fun <T: Bound> Base<T>.someExtension() {}

/**
 * [Derived.someE<caret>xtension]
 */
fun foo() {
}
