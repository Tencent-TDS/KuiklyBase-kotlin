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

import org.jetbrains.kotlin.konan.properties.KonanPropertiesLoader
import org.jetbrains.kotlin.konan.properties.Properties
import org.jetbrains.kotlin.konan.util.ProgressCallback
import java.io.File
import java.lang.IllegalStateException

class OhosConfigurablesImpl(
    target: KonanTarget,
    properties: Properties,
    dependenciesRoot: String?,
    progressCallback: ProgressCallback,
) : OhosConfigurables, KonanPropertiesLoader(target, properties, dependenciesRoot, progressCallback = progressCallback) {

    override val targetSysRoot: String? by lazy {
        // Prefer to use internal packaged sysroot. 
        val internalSysRoot = super<OhosConfigurables>.targetSysRoot
        // The file '.invalid' indicates that the package is invalid.
        if (!File(absolute(internalSysRoot), ".invalid").exists()) {
            internalSysRoot
        } else {
            // Use sysroot from local SDK when the internal packaged is not available. 
            val sdkRoot = getLocalSdkPath()
            if (File(sdkRoot).exists()) {
                checkOhosSdkVersion(sdkRoot)
                File(sdkRoot, "native/sysroot").path
            } else {
                error(
                    "OHOS SDK is not found. It is required to build platform libs for OHOS.\n" +
                            "Set 'OHOS_SDK_HOME=/path/to/openharmony' or 'DEVECO_STUDIO_HOME=/path/to/DevEco-Studio' in the system properties" +
                            "or install DevEco Studio in the default location '/Applications/DevEco-Studio.app'. "
                )
            }
        }
    }

    private fun getLocalSdkPath(): String {
        if (HostManager.host.family.isAppleFamily) {
            return getSystemValue("OHOS_SDK_HOME") ?: File(
                getSystemValue("DEVECO_STUDIO_HOME") ?: "/Applications/DevEco-Studio.app",
                "Contents/sdk/default/openharmony"
            ).path
        } else {
            throw IllegalStateException("Unsupported host: ${HostManager.host}")
        }
    }

    private fun checkOhosSdkVersion(sdkRoot: String) {
        if (properties.getProperty("ignoreOhosSdkVersionCheck") != "true") {
            properties.getProperty("minimalOhosSdkVersion")?.toInt()?.let { minimalOhosSdkVersion ->
                val sdkPkg = File(sdkRoot, "native/oh-uni-package.json").readText()
                val apiVersion = Regex(""""apiVersion": "(\d+)"""").find(sdkPkg)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: Int.MAX_VALUE
                if (apiVersion < minimalOhosSdkVersion) {
                    error("Unsupported OHOS SDK version $apiVersion(bundled in $sdkRoot), minimal supported version is $minimalOhosSdkVersion.")
                }
            }
        }
    }

    private fun getSystemValue(key: String): String? {
        return (System.getProperty(key) ?: System.getenv(key))?.takeIf { it.isNotBlank() }
    }
}