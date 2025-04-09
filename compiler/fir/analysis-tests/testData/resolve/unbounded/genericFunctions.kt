// RUN_PIPELINE_TILL: FRONTEND
// DISABLE_NEXT_PHASE_SUGGESTION
// WITH_STDLIB

fun <T> foo(arg: T, arg2: T) {
    val tmp1 = arg.toString()
    val tmp12 = arg!!.toString()
    val tmp13 = arg?.toString()
    val tmp2 = arg.hashCode()
    val tmp22 = arg!!.hashCode()
    val tmp23 = arg?.hashCode()
    val tmp3 = arg == null
    val tmp4 = arg == arg2
    val tmp5 = arg.equals(arg2)
    val tmp52 = arg!!.equals(arg2!!)
}

fun <T, V> foo(arg: T, arg2: V {
    val t = if (true) arg else arg2
}
