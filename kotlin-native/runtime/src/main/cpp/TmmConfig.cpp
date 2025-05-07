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

namespace tmm {

struct TmmConfig {
  bool isStringProxyEnabledGlobally: 1;
  bool isStringProxyEnabledCreatingKStringFromNSString: 1;
  bool isStringProxyAssociatedWithNSString: 1;
  bool isStringProxyEnabledCreatingNSStringFromKString: 1;
  bool isNativeStringBuilderEnabled: 1;
};

static TmmConfig config = {
    .isStringProxyEnabledGlobally = false,
    .isStringProxyEnabledCreatingKStringFromNSString = false,
    .isStringProxyAssociatedWithNSString = false,
    .isStringProxyEnabledCreatingNSStringFromKString = false,
    .isNativeStringBuilderEnabled = false
};

} // namespace tmm

extern "C" {

RUNTIME_EXPORT ALWAYS_INLINE void Kotlin_TmmConfig_setStringProxyEnabledGlobally(bool isEnabled) {
  tmm::config.isStringProxyEnabledGlobally = isEnabled;
}

RUNTIME_EXPORT ALWAYS_INLINE bool Kotlin_TmmConfig_isStringProxyEnabledGlobally() {
#ifdef KONAN_OBJC_INTEROP
  return tmm::config.isStringProxyEnabledGlobally;
#endif
  return false;
}

RUNTIME_EXPORT ALWAYS_INLINE void Kotlin_TmmConfig_setStringProxyEnabledCreatingKStringFromNSString(bool isEnabled) {
  tmm::config.isStringProxyEnabledCreatingKStringFromNSString = isEnabled;
}

RUNTIME_EXPORT ALWAYS_INLINE bool Kotlin_TmmConfig_isStringProxyEnabledCreatingKStringFromNSString() {
#ifdef KONAN_OBJC_INTEROP
  return tmm::config.isStringProxyEnabledCreatingKStringFromNSString || tmm::config.isStringProxyEnabledGlobally;
#endif
  return false;
}

RUNTIME_EXPORT ALWAYS_INLINE void Kotlin_TmmConfig_setStringProxyAssociatedWithNSString(bool isEnabled) {
  tmm::config.isStringProxyAssociatedWithNSString = isEnabled;
}

RUNTIME_EXPORT ALWAYS_INLINE bool Kotlin_TmmConfig_isStringProxyAssociatedWithNSString() {
#ifdef KONAN_OBJC_INTEROP
  return tmm::config.isStringProxyAssociatedWithNSString;
#endif
  return false;
}

RUNTIME_EXPORT ALWAYS_INLINE void Kotlin_TmmConfig_setStringProxyEnabledCreatingNSStringFromKString(bool isEnabled) {
  tmm::config.isStringProxyEnabledCreatingNSStringFromKString = isEnabled;
}

RUNTIME_EXPORT ALWAYS_INLINE bool Kotlin_TmmConfig_isStringProxyEnabledCreatingNSStringFromKString() {
#ifdef KONAN_OBJC_INTEROP
  return tmm::config.isStringProxyEnabledCreatingNSStringFromKString || tmm::config.isStringProxyEnabledGlobally;
#endif
  return false;
}

RUNTIME_EXPORT ALWAYS_INLINE void Kotlin_TmmConfig_setNativeStringBuilderEnabled(bool isEnabled) {
  tmm::config.isNativeStringBuilderEnabled = isEnabled;
}

RUNTIME_EXPORT ALWAYS_INLINE bool Kotlin_TmmConfig_isNativeStringBuilderEnabled() {
  return tmm::config.isNativeStringBuilderEnabled;
}

}