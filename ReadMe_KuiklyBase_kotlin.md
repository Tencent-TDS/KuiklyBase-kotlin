# KuiklyBase-Kotlin

## Project Overview

**KuiklyBase-Kotlin** is an open-sourced project that is a secondary development based on [Kotlin](https://github.com/jetbrains/kotlin), which is open-sourced by [JetBrains](https://www.jetbrains.com/). It is developed and open-sourced by the Oteam of Tencent's large front-end. Tencent Video and the PCG Device-side technology team were deeply involved in its construction. Compared to the official version, **KuiklyBase-Kotlin** adds support for the HarmonyOS platform and includes optimizations and adjustments to the Kotlin compiler and Kotlin/Native runtime based on practical experience from Tencent Video. It has already been successfully adopted in several Tencent apps.

## Feature Highlights

The updates in **KuiklyBase-Kotlin** are available in the [kuikly-base/2.0.20](tree/kuikly-base/2.0.20) branch, which is based on JetBrains' official [2.0.20](tree/2.0.20) branch and includes all features of [Kotlin 2.0.21](https://github.com/JetBrains/kotlin/releases/tag/v2.0.21). On top of that, the following major features have been added:

### 1. HarmonyOS Support

This is the most significant addition in **KuiklyBase-Kotlin**. By introducing HarmonyOS as a Kotlin/Native target, the platform is now integrated as one of Kotlin/Native’s supported targets, enabling code sharing across platforms. HarmonyOS SDK API level 15 is supported.

### 2. Performance Optimizations

- Tuned GC parameters to mitigate frequent GC triggers in specific scenarios.
- Introduced suspendable GC for fine-grained control over garbage collection scheduling.
- Implemented string proxying on Apple platforms to reduce memory copying during cross-language string handling.

### 3. Compilation Enhancements

- Integrated performance inlining options from Kotlin 2.1 and improved runtime linking strategies.
- Enhanced error messages for `klib` dependency resolution failures, making it easier for developers to troubleshoot issues.

### 4. Symbol Export Optimization

- Public Kotlin symbols are no longer exported to Objective-C by default, reducing the size of exported headers and speeding up compilation.
- Introduced the `@HiddenFromC` annotation with the same compilation checks and behavior as `@HiddenFromObjC`, enabling Compose compiler plugins to prevent `@Composable` functions from being exported as C symbols.

## Integration Guide

### Repository Configuration

To maintain consistency with the official Kotlin experience, **KuiklyBase-Kotlin** artifacts are published to Tencent's Maven repository in the same manner. Before using it, add the following repositories to your project:

```
https://mirrors.tencent.com/nexus/repository/maven-tencent/
https://mirrors.tencent.com/nexus/repository/maven-public/
```

> **Note**: The `maven-tencent` repository is used to publish artifacts open-sourced and maintained by Tencent. The `maven-public` repository proxies over a dozen popular domestic and international Maven repositories, including Maven Central, Google Maven, JitPack, Huawei Maven, and more. It aims to promote free software and improve the developer experience in China. Tencent Mirror also provides mirror services for popular package managers and platforms such as Linux distributions, Gradle, npm, Go, Flutter, and Rust. For details, visit the [Tencent Software Mirror](https://mirrors.tencent.com/).

**Gradle Example:**

**Option 1: `settings.gradle.kts`**

```kotlin
pluginManagement {
    repositories {
        maven("https://mirrors.tencent.com/nexus/repository/maven-tencent/")
        maven("https://mirrors.tencent.com/nexus/repository/maven-public/")
        ...
    }
}

dependencyResolutionManagement {
    repositories {
        maven("https://mirrors.tencent.com/nexus/repository/maven-tencent/")
        maven("https://mirrors.tencent.com/nexus/repository/maven-public/")
        ...
    }
}
```

**Option 2: `build.gradle.kts`**

```kotlin
buildscript {
    repositories {
        maven("https://mirrors.tencent.com/nexus/repository/maven-tencent/")
        maven("https://mirrors.tencent.com/nexus/repository/maven-public/")
        ...
    }
}

allprojects {
    repositories {
        maven("https://mirrors.tencent.com/nexus/repository/maven-tencent/")
        maven("https://mirrors.tencent.com/nexus/repository/maven-public/")
        ...
    }
}
```

### Versioning

**KuiklyBase-Kotlin** uses a distinct versioning scheme from the official Kotlin release, in the format:  
`<Official-Version>-KBA-<Build-Number>`  
Here, `KBA` stands for *KuiklyBase All Targets*.  
The latest version is: **2.0.21-KBA-004**

Example configuration:

```kotlin
plugins {
    kotlin("multiplatform") version "2.0.21-KBA-004"
}
```

### Configuring HarmonyOS

HarmonyOS is treated as a Kotlin/Native target, similar to iOS. Example configuration:

```kotlin
kotlin {
    ...
    iosArm64 {
        ...
    }
    ohosArm64 {
        binaries.sharedLib {
            baseName = "shared"

            linkerOpts("-L${projectDir}/libs/", "-lskia")
        }
        val main by compilations.getting
        val sampleInterop by main.cinterops.creating {
            defFile = file("...")
            includeDirs(file("..."))
        }

        compilations.all {
            compilerOptions.options.apply {
                freeCompilerArgs.add("...")
                optIn.addAll(
                    "kotlinx.cinterop.ExperimentalForeignApi",
                    "kotlin.experimental.ExperimentalNativeApi"
                )
            }
        }
    }

    sourceSets {
        ...
        val ohosArm64Main by getting {
            dependencies {
                implementation("...")
            }
        }
    }
}
```

## Build Instructions

Due to the LLVM version differences between HarmonyOS and Apple platforms (e.g., iOS, macOS), **KuiklyBase-Kotlin** requires a slightly different build process compared to the official Kotlin.

To simplify the process, we provide a full [build script](scripts/kuikly-base/publish-local.sh), which publishes all compiler and runtime artifacts to the `build/repo` directory.

**Build Environment**：

1. **Operating System**: macOS 15
2. **Xcode** (for building Apple targets): Set the default version to 16, and install Xcode 15 into `/Applications/Xcode-15.0.app`
3. **DevEco Studio** (for HarmonyOS): Version 5.0.4

> **Note**:
> 1. Xcode 15 is only used for building Apple x64 targets. See [KT-69094](https://youtrack.jetbrains.com/issue/KT-69094) for more details.
> 2. LLVM 12 used for HarmonyOS builds is based on the [openharmony/third_party_llvm-project](https://gitee.com/openharmony/third_party_llvm-project/commits/master-llvm12-backup) project.

---

## Special Thanks

The development and open-sourcing of **KuiklyBase-Kotlin** was a challenging yet rewarding process. Throughout this journey, the Tencent Video team received support from the Kotlin team, the HarmonyOS team, industry peers, and fellow teams within Tencent. We also received encouragement and feedback from the broader developer community. We would like to express our heartfelt thanks to everyone who supported and inspired us.

## License

**KuiklyBase-Kotlin** is licensed under the Apache License 2.0.  
For details, see: [License_KuiklyBase-kotlin](License_KuiklyBase-kotlin.txt)
