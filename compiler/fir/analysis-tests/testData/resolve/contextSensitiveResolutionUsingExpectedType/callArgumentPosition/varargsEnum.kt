// RUN_PIPELINE_TILL: BACKEND
// ISSUE: KT-75315
// LANGUAGE: +ContextSensitiveResolutionUsingExpectedType

open class Super {
    val superProp: String = ""
    fun superFunc() {}
}

enum class MyEnum {
    EnumValue1, EnumValue2;

    companion object {
        val EnumValue3 = EnumValue1
    }
}

fun <T>receiverVarArg(vararg arg: T) = arg

fun testVarArg() {
    val arg1 = MyEnum.EnumValue1
    val arg2 = MyEnum

    receiverVarArg<MyEnum>(EnumValue1, EnumValue2)
    receiverVarArg<MyEnum>(arg1, <!ARGUMENT_TYPE_MISMATCH!>arg2<!>)
}

