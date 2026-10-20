/*
 * Copyright 2010-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

pluginManagement {
    includeBuild("../../repo/gradle-settings-conventions")

    repositories {
        // region @Tencent: Prioritize Tencent mirrors for Maven Central and Gradle Plugin Portal.
        maven("https://mirrors.tencent.com/nexus/repository/maven-public")
        maven("https://mirrors.tencent.com/nexus/repository/gradle-plugins")
        // endregion
        maven("https://redirector.kotlinlang.org/maven/kotlin-dependencies")
        mavenCentral { setUrl("https://cache-redirector.jetbrains.com/maven-central") }
        gradlePluginPortal()
    }
}

plugins {
    id("kotlin-bootstrap")
    id("jvm-toolchain-provisioning")
    id("kotlin-daemon-config")
    id("cache-redirector")
}

val knownGroups = buildList {
    add("ring")
    add("cinterop")
    add("helloworld")
    add("numerical")
    add("startup")
    add("logging")
    if (System.getProperty("os.name") == "Mac OS X") {
        add("objcinterop")
        add("swiftinterop")
    }
}

dependencyResolutionManagement {
    repositories {
        // region @Tencent: Use the Tencent mirror for Maven Central.
        mavenCentral { setUrl("https://mirrors.tencent.com/nexus/repository/maven-public") }
        // endregion
    }
}
gradle.beforeProject {
    extra["knownGroups"] = knownGroups
}

include(":benchmarksAnalyzer")
include(":benchmarksKotlinxAdapter")
include(":benchmarksLauncher")
include(":benchmarksReports")
knownGroups.forEach {
    include(":$it")
}
