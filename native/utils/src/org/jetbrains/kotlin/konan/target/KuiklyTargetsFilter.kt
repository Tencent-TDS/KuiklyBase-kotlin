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

package org.jetbrains.kotlin.konan.target

// region @Tencent: Parsed representation of the `kuikly.targets` filter.
/**
 * Parsed representation of the `kuikly.targets` Gradle property.
 */
sealed class KuiklyTargetsFilter {
    /** Build all targets supported by the host. */
    object All : KuiklyTargetsFilter()

    /** Build only the minimal (`mini`) set of targets. */
    object Mini : KuiklyTargetsFilter()

    /** Build the minimal set plus all targets of the requested [families]. */
    data class Families(val families: Set<Family>) : KuiklyTargetsFilter()
}

/**
 * Parses the raw `kuikly.targets` value into a [KuiklyTargetsFilter].
 *
 * - `null`, blank or `"all"` → [KuiklyTargetsFilter.All]
 * - `"mini"` → [KuiklyTargetsFilter.Mini]
 * - comma-separated family names (e.g. `"mingw,osx"`) → [KuiklyTargetsFilter.Families]
 */
fun parseKuiklyTargetsFilter(raw: String?): KuiklyTargetsFilter {
    val filter = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return KuiklyTargetsFilter.All
    if (filter.equals("all", ignoreCase = true)) return KuiklyTargetsFilter.All
    if (filter.equals("mini", ignoreCase = true)) return KuiklyTargetsFilter.Mini

    val families = filter.split(',').mapNotNull { token ->
        val name = token.trim()
        Family.values().firstOrNull { it.name.equals(name, ignoreCase = true) }
    }.toSet()
    return KuiklyTargetsFilter.Families(families)
}
// endregion
