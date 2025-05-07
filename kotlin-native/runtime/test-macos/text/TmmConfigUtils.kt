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

package test.text

import kotlin.tmm.TmmConfig

internal inline fun withNativeCharArray(block: () -> Unit) {
    TmmConfig.isNativeStringBuilderEnabled = true
    block()
    TmmConfig.isNativeStringBuilderEnabled = false
}

internal inline fun <T> withStringProxyGlobally(block: () -> T): T {
    val originalConfig = TmmConfig.isStringProxyEnabledGlobally
    TmmConfig.isStringProxyEnabledGlobally = true
    val result = block()
    TmmConfig.isStringProxyEnabledGlobally = originalConfig
    return result
}

internal inline fun <T> withStringProxyFromNSString(block: () -> T): T {
    val originalConfig = TmmConfig.isStringProxyEnabledCreatingKStringFromNSString
    TmmConfig.isStringProxyEnabledCreatingKStringFromNSString = true
    val result = block()
    TmmConfig.isStringProxyEnabledCreatingKStringFromNSString = originalConfig
    return result
}