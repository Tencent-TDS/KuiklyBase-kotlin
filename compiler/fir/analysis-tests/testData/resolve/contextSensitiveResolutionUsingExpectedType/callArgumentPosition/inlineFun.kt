// RUN_PIPELINE_TILL: FRONTEND
// ISSUE: KT-75315
// LANGUAGE: +ContextSensitiveResolutionUsingExpectedType

enum class MyEnum {
    EnumValue1;
}

open class MyClass {
    object InheritorObject: MyClass() {
    }
}

<!NOTHING_TO_INLINE!>inline<!> fun inlineEnumConsumer(arg: MyEnum) {}
<!NOTHING_TO_INLINE!>inline<!> fun inlineConsumer(arg: MyClass) {}

fun testInline() {
    inlineEnumConsumer(EnumValue1)
    inlineEnumConsumer(<!UNRESOLVED_REFERENCE!>InheritorObject<!>)
    inlineConsumer(InheritorObject)
}
