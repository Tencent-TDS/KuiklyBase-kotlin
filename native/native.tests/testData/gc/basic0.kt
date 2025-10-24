//import kotlin.test.*
//
//class A {
//    var field: B? = null
//}
//
//class B(var field: Int)
//
//@Test fun runTest() {
//    val a = A()
//    a.field = B(2)
//}
import kotlin.test.*
import kotlinx.cinterop.*

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
class PasteboardSmokeTest {
    @Test
    fun runTest() {

        val str: String? = null
        println(str!!.length)
    }
}