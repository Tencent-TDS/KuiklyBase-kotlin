pluginManagement {
    includeBuild("../../../repo/kotlin-build-helpers")
    includeBuild("../../../repo/gradle-settings-conventions")

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

dependencyResolutionManagement {
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
    id("kotlin-build-helpers")
    id("kotlin-bootstrap")
    id("develocity")
    id("jvm-toolchain-provisioning")
    id("kotlin-daemon-config")
    id("cache-redirector")
}
