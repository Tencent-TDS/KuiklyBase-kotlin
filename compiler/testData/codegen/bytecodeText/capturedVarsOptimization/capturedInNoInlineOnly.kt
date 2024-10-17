fun runNoInline(f: () -> Unit) = f()

fun test() {
    var x = 1
    runNoInline { ++x }
}

// 1 NEW kotlin/internal/Ref\$IntRef
// 2 GETFIELD kotlin/internal/Ref\$IntRef\.element
// 2 PUTFIELD kotlin/internal/Ref\$IntRef\.element
