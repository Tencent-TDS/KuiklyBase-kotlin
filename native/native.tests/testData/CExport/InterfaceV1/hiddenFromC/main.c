#include "hiddenFromC_api.h"

#define __ hiddenFromC_symbols()->
#define T_(x) hiddenFromC_kref_ ## x

int main(void) {
    if (__ kotlin.root.hiddenFromC.visibleFunction() != 10) return 1;
    if (__ kotlin.root.hiddenFromC.hiddenFromObjCOnlyFunction() != 30) return 2;
    if (__ kotlin.root.hiddenFromC.get_visibleProperty() != 50) return 3;
    __ kotlin.root.hiddenFromC.set_visibleProperty(55);
    if (__ kotlin.root.hiddenFromC.get_visibleProperty() != 55) return 4;

    T_(hiddenFromC_DerivedClass) derived = __ kotlin.root.hiddenFromC.DerivedClass.DerivedClass();
    int res = __ kotlin.root.hiddenFromC.DerivedClass.visibleMethod(derived);
    __ DisposeStablePointer(derived.pinned);
    if (res != 81) return 5;

    return 0;
}
