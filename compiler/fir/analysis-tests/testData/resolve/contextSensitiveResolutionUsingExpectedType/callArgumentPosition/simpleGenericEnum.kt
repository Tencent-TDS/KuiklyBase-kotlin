// RUN_PIPELINE_TILL: FRONTEND
// ISSUE: KT-75315
// LANGUAGE: +ContextSensitiveResolutionUsingExpectedType

enum class MyEnum {
    EnumValue1, EnumValue2;
}

fun consumeEnum(arg: MyEnum) {}

fun <T> test(arg: T): T = arg

fun testGeneric() {
    val arg1 = MyEnum.EnumValue1
    val arg2 = <!UNRESOLVED_REFERENCE!>EnumValue2<!>

    consumeEnum(test(EnumValue2))
    consumeEnum(test(arg1))
    consumeEnum(test(arg2))
    consumeEnum(test(<!UNRESOLVED_REFERENCE!>arg3<!>))
}