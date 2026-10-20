import org.gradle.kotlin.dsl.mavenCentral
import org.gradle.kotlin.dsl.repositories

rootProject.name = "native-build-tools"

pluginManagement {
    includeBuild("../../repo/kotlin-build-helpers")
    includeBuild("../../repo/gradle-settings-conventions")

    repositories {
        maven("https://mirrors.tencent.com/nexus/repository/maven-public")
        maven("https://mirrors.tencent.com/nexus/repository/gradle-plugins")
        maven("https://redirector.kotlinlang.org/maven/kotlin-dependencies")
    }
}

plugins {
    id("kotlin-build-helpers")
    id("kotlin-bootstrap")
    id("jvm-toolchain-provisioning")
    id("develocity")
    id("kotlin-daemon-config")
    id("cache-redirector")
}

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("../../gradle/libs.versions.toml"))
        }
    }
    repositories {
        maven("https://mirrors.tencent.com/nexus/repository/maven-public")
        maven("https://mirrors.tencent.com/nexus/repository/gradle-plugins")
        maven("https://redirector.kotlinlang.org/maven/kotlin-dependencies") {
            name = "kotlin-dependencies"
        }
    }
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS

}
