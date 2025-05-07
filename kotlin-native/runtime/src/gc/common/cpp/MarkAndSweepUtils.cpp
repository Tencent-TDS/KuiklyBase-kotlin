/*
 * Copyright 2010-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

#include "MarkAndSweepUtils.hpp"
// region Tencent Code
#ifdef KONAN_OHOS
#include "qos/qos.h"
#endif
// endregion

void kotlin::gc::stopTheWorld(GCHandle gcHandle, const char* reason) noexcept {
    // region Tencent Code
#ifdef KONAN_OHOS
    // STW 期间提升 qos 优先级，提升 CPU 调度资源，减少优先级反转问题
    OH_QoS_SetThreadQoS(QOS_USER_INTERACTIVE);
#endif
    // endregion
    konan::startTrace("stopTheWorld");
    mm::RequestThreadsSuspension(reason);
    gcHandle.suspensionRequested();

    mm::WaitForThreadsSuspension();
    gcHandle.threadsAreSuspended();
    // region Tencent Code
    konan::finishTrace();
    // endregion
}

void kotlin::gc::resumeTheWorld(kotlin::gc::GCHandle gcHandle) noexcept {
    // region Tencent Code
    konan::startTrace("resumeTheWorld");
    // endregion
    mm::ResumeThreads();
    gcHandle.threadsAreResumed();
    // region Tencent Code
    konan::finishTrace();
#ifdef KONAN_OHOS
    // RTW 重置 qos 优先级
    OH_QoS_ResetThreadQoS();
#endif
    // endregion
}
