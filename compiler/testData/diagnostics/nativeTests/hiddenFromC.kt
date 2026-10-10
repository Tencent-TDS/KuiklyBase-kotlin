// RUN_PIPELINE_TILL: FRONTEND
// IGNORE_FIR_DIAGNOSTICS
// ALLOW_KOTLIN_PACKAGE
// FILE: kotlin.kt
package kotlin.native

@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
@MustBeDocumented
annotation class HiddenFromC

// FILE: test.kt
import kotlin.native.HiddenFromC

@HiddenFromC
open class OpenHiddenClass

<!SUBTYPE_OF_HIDDEN_FROM_C!>class InheritsFromOpenHiddenClass : OpenHiddenClass()<!>

@HiddenFromC
class HiddenInheritsFromOpenHiddenClass : OpenHiddenClass()

@HiddenFromC
interface HiddenInterface

interface NotHiddenInterface

<!SUBTYPE_OF_HIDDEN_FROM_C!>class ImplementsHiddenInterface : NotHiddenInterface, HiddenInterface<!>

@HiddenFromC
class HiddenImplementsHiddenInterface : NotHiddenInterface, HiddenInterface

<!SUBTYPE_OF_HIDDEN_FROM_C!>class InheritsFromOpenHiddenClass2 : NotHiddenInterface, OpenHiddenClass()<!>

@HiddenFromC
class OuterHidden {
    class Nested {
        open class Nested
    }
}

<!SUBTYPE_OF_HIDDEN_FROM_C!>class InheritsFromNested : OuterHidden.Nested.Nested()<!>

@HiddenFromC
class HiddenInheritsFromNested : OuterHidden.Nested.Nested()

private class PrivateInheritsFromNested : OuterHidden.Nested.Nested()

internal class InternalInheritsFromNested : OuterHidden.Nested.Nested()

fun produceInstanceOfHidden(): OuterHidden.Nested.Nested {
    return object : OuterHidden.Nested.Nested() {}
}

@HiddenFromC
enum class MyHiddenEnum {
    A,
    B,
    C {
        override fun sayCheese(): String = "Boo :("
    };

    open fun sayCheese(): String = "Cheese!"
}
