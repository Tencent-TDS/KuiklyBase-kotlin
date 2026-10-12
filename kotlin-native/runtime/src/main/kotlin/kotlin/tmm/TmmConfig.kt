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

package kotlin.tmm

import kotlin.native.internal.GCUnsafeCall

@GCUnsafeCall("Kotlin_TmmConfig_setStringProxyEnabledCreatingKStringFromNSString")
private external fun setStringProxyEnabledCreatingKStringFromNSString(isEnabled: Boolean)

@GCUnsafeCall("Kotlin_TmmConfig_isStringProxyEnabledCreatingKStringFromNSString")
private external fun isStringProxyEnabledCreatingKStringFromNSString(): Boolean

@GCUnsafeCall("Kotlin_TmmConfig_setStringProxyEnabledCreatingNSStringFromKString")
private external fun setStringProxyEnabledCreatingNSStringFromKString(isEnabled: Boolean)

@GCUnsafeCall("Kotlin_TmmConfig_isStringProxyEnabledCreatingNSStringFromKString")
private external fun isStringProxyEnabledCreatingNSStringFromKString(): Boolean

public object TmmConfig {

    /**
     * Create StringProxies instead of KStrings with values from NSString only if true.
     */
    public var isStringProxyEnabledCreatingKStringFromNSString: Boolean
        get() = isStringProxyEnabledCreatingKStringFromNSString()
        set(value) = setStringProxyEnabledCreatingKStringFromNSString(value)

    /**
     * Create StringProxies instead of copying characters to new NSStrings.
     */
    public var isStringProxyEnabledCreatingNSStringFromKString: Boolean
        get() = isStringProxyEnabledCreatingNSStringFromKString()
        set(value) = setStringProxyEnabledCreatingNSStringFromKString(value)

}
