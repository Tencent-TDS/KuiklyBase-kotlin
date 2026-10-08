/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin

import org.gradle.api.Plugin
import org.gradle.api.Project

open class PlatformManagerPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val platformManager = project.objects.platformManagerProvider(project).platformManager.get()
        // region @Tencent: Allow limiting the set of built targets via the `kuikly.targets` property.
        platformManager.kuiklyTargetsFilter = project.kuiklyTargetsFilter()
        // endregion
        project.extensions.add("platformManager", platformManager)
    }
}
