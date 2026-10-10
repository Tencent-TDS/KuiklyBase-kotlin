@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package hiddenFromC

import kotlin.native.HiddenFromC
import kotlin.native.HiddenFromObjC

fun visibleFunction(): Int = 10

@HiddenFromC
fun hiddenFromCFunction(): Int = 20

@HiddenFromObjC
fun hiddenFromObjCOnlyFunction(): Int = 30

@HiddenFromC
var hiddenProperty: Int = 40

var visibleProperty: Int = 50

@HiddenFromC
class HiddenClass {
    fun foo(): Int = 60
}

open class BaseClass {
    @HiddenFromC
    open fun hiddenMethod(): Int = 70

    open fun visibleMethod(): Int = 80
}

class DerivedClass : BaseClass() {
    override fun hiddenMethod(): Int = 71

    override fun visibleMethod(): Int = 81
}
