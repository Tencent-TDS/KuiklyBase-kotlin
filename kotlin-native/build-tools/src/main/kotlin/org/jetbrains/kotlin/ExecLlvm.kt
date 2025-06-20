/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin

import org.gradle.api.Action
import org.gradle.process.ExecOperations
import org.gradle.process.ExecResult
import org.gradle.process.ExecSpec
import org.jetbrains.kotlin.konan.target.PlatformManager
import org.jetbrains.kotlin.konan.target.KonanTarget

fun PlatformManager.resolveLlvmUtility(utility: String, target: KonanTarget? = null): String {
    val platform = target?.let { platform(target) } ?: hostPlatform
    return "${platform.absoluteLlvmHome(target)}/bin/$utility"
}

fun ExecOperations.execLlvmUtility(
        platformManager: PlatformManager,
        utility: String,
        target: KonanTarget = platformManager.targetByName("host"),
        action: Action<in ExecSpec>
): ExecResult {
    return exec {
        action.execute(this)
        executable = platformManager.resolveLlvmUtility(utility, target)
        println("${executable} ${args.joinToString(separator = " ")})")
    }
}