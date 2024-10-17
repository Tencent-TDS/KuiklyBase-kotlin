fun test() {
    var x = 1
    run {
        val obj = object {
            fun foo() { ++x }
        }
        obj.foo()
    }
}

// 1 NEW kotlin/internal/Ref\$IntRef
// 2 GETFIELD kotlin/internal/Ref\$IntRef\.element
// 2 PUTFIELD kotlin/internal/Ref\$IntRef\.element
