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

package org.jetbrains.kotlin

import org.gradle.api.Project
import org.jetbrains.kotlin.konan.target.KuiklyTargetsFilter
import org.jetbrains.kotlin.konan.target.parseKuiklyTargetsFilter

// region @Tencent: kuikly build configuration.
/**
 * Gradle property name that limits the set of built targets.
 */
const val KUIKLY_TARGETS_PROPERTY = "kuikly.targets"

/**
 * Reads and parses the [KUIKLY_TARGETS_PROPERTY] Gradle property.
 */
fun Project.kuiklyTargetsFilter(): KuiklyTargetsFilter =
        parseKuiklyTargetsFilter(findProperty(KUIKLY_TARGETS_PROPERTY) as String?)
// endregion
