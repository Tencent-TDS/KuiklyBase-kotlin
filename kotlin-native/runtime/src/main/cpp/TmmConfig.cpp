/*
 * Tencent is pleased to support the open source community by making TDS-KuiklyBase available.
 * Copyright (C) 2025 THL A29 Limited, a Tencent company. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "TmmConfig.h"

namespace {

struct TmmConfig {
    bool isStringProxyEnabledCreatingKStringFromNSString : 1;
    bool isStringProxyEnabledCreatingNSStringFromKString : 1;
};

TmmConfig config = {
        .isStringProxyEnabledCreatingKStringFromNSString = false,
        .isStringProxyEnabledCreatingNSStringFromKString = false};

} // namespace

extern "C" {

RUNTIME_EXPORT ALWAYS_INLINE void Kotlin_TmmConfig_setStringProxyEnabledCreatingKStringFromNSString(bool isEnabled) noexcept {
    config.isStringProxyEnabledCreatingKStringFromNSString = isEnabled;
}

RUNTIME_EXPORT ALWAYS_INLINE bool Kotlin_TmmConfig_isStringProxyEnabledCreatingKStringFromNSString() noexcept {
#if KONAN_OBJC_INTEROP
    return config.isStringProxyEnabledCreatingKStringFromNSString;
#else
    return false;
#endif
}

RUNTIME_EXPORT ALWAYS_INLINE void Kotlin_TmmConfig_setStringProxyEnabledCreatingNSStringFromKString(bool isEnabled) noexcept {
    config.isStringProxyEnabledCreatingNSStringFromKString = isEnabled;
}

RUNTIME_EXPORT ALWAYS_INLINE bool Kotlin_TmmConfig_isStringProxyEnabledCreatingNSStringFromKString() noexcept {
#if KONAN_OBJC_INTEROP
    return config.isStringProxyEnabledCreatingNSStringFromKString;
#else
    return false;
#endif
}

}
