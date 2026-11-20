![](https://img.shields.io/badge/dynamic/xml?label=KuiklyBase-Kotlin&url=https%3A%2F%2Fmirrors.tencent.com%2Fnexus%2Frepository%2Fmaven-tencent%2Forg%2Fjetbrains%2Fkotlin%2Fkotlin-compiler%2Fmaven-metadata.xml&query=%2Fmetadata%2Fversioning%2Flatest%2Ftext())

# KuiklyBase-Kotlin

## Project Overview

**KuiklyBase-Kotlin** is an open-sourced project that is a secondary development based on [Kotlin](https://github.com/jetbrains/kotlin), which is open-sourced by [JetBrains](https://www.jetbrains.com/). It is developed and open-sourced by the Oteam of Tencent's large front-end. Tencent Video and the PCG Device-side technology team were deeply involved in its construction. Compared to the official version, **KuiklyBase-Kotlin** adds support for the HarmonyOS platform and includes optimizations and adjustments to the Kotlin compiler and Kotlin/Native runtime based on practical experience from Tencent Video. It has already been successfully adopted in several Tencent apps.

## Feature Highlights

The updates in **KuiklyBase-Kotlin** are available in the [kuikly-base/2.4.21](tree/kuikly-base/2.4.21) branch, which is based on JetBrains' official [v2.4.21](https://github.com/JetBrains/kotlin/tree/v2.4.21) release and includes all features of [Kotlin 2.4.21](https://github.com/JetBrains/kotlin/releases/tag/v2.4.21). On top of that, the following major features have been added:

### 1. HarmonyOS & Extended Native Target Support

- **HarmonyOS Support (`ohosArm64`)**: This is the most significant addition in **KuiklyBase-Kotlin**. By introducing HarmonyOS (`ohos_arm64`) as a Kotlin/Native target cross-compiled with the unified LLVM 21 toolchain, the platform is integrated as one of Kotlin/Native's supported targets, enabling code sharing across platforms.
- **Extended & Compatible Targets**: Added `mingwX86` and `mingwArm64` Kotlin/Native targets, and retained `KonanTarget.IOS_ARM32` for compatibility with older projects and plugins.

### 2. Performance Optimizations

- **Bidirectional String Proxy**: Implemented bidirectional string proxying between Kotlin `String` and `NSString` on Apple platforms to reduce memory copying during cross-language string handling.
- **Zero-Copy External-Storage Strings**: Added support across all Kotlin/Native targets for creating Kotlin `String` instances directly from owned UTF-8 (ASCII) and UTF-16 buffers via `kotlinx.cinterop` (`toKStringFromOwnedUtf8` / `toKStringFromOwnedUtf16`, with optional custom `NativeFreeCallback`) and C runtime APIs (`CreateStringFromOwnedUtf8*` / `CreateStringFromOwnedUtf16*`), managing external buffer lifetimes through associated objects without copying.
- **Primitive Boxing & Equality Optimizations**: Avoided redundant boxing and unboxing on non-null primitives in safe calls (`?.`), non-null assertions (`!!`), inline function return values, and inlined type arguments; simplified `IfNull` fusion and lowered enum equality (`==`) to reference equality (`===`).
- **Non-blocking CMS Weak Reference Read Barrier**: Optimized the CMS GC weak reference read barrier (`weakRefReadBarrier`) to avoid spinning on `markTerminationMutex_` during concurrent mark termination, eliminating cross-thread tail pauses and Objective-C `SideTable` lock deadlocks (ANRs) on Apple platforms.

### 3. Runtime & Tooling Enhancements

- **Reflection Support**: Added support for `KClass.isSubclassOf` and Objective-C `KClass` reflection in Kotlin/Native.
- **CInterop Enhancement**: Skipped anonymous inner structs containing only bitfields in `StructRendering` to avoid generating invalid bindings.
- **Gradle & CocoaPods Improvements**: Included the `ohos` group (`ohosMain` / `ohosTest`) in the default Kotlin hierarchy template; supported inferring the CocoaPods module name from `modulemap` when not explicitly specified; prepended `group` to the default `commonMain` compilation `moduleName` to avoid conflicts across modules.
- **KLIB Compatibility & Swift Export Fixes**: Recognized legacy `GENERATED_SINGLE_FIELD_VALUE_CLASS_MEMBER` ([KT-90675](https://youtrack.jetbrains.com/issue/KT-90675)) and `SUSPEND_CONVERSION` ([KT-91139](https://youtrack.jetbrains.com/issue/KT-91139)) origins during KLIB deserialization for libraries compiled with older Kotlin versions; fixed passing `Set` and `Dictionary` as `KotlinBridgeable` in Swift Export ([KT-90067](https://youtrack.jetbrains.com/issue/KT-90067)).

### 4. Symbol Export Optimization

- Introduced the `@HiddenFromC` annotation with the same compilation checks and behavior as `@HiddenFromObjC`, enabling the Compose compiler plugin to automatically prevent `@Composable` declarations from being exported as C symbols.

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

Example configuration:

```kotlin
plugins {
    kotlin("multiplatform") version "<latestVersion>"
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
        ohosArm64Main {
            dependencies {
                implementation("...")
            }
        }
    }
}
```

## Build Instructions

Unlike the earlier 2.0.21 version (which required separate LLVM versions for HarmonyOS and Apple platforms), the current version unifies all targets—including HarmonyOS and Apple platforms (iOS, macOS, etc.)—on **LLVM 21** and checks in prebuilt `libclang` interop sources. Therefore, the build process for **KuiklyBase-Kotlin** is aligned with the official version. You can also filter the native targets to build via `-Pkuikly.targets` (which defaults to `mini`).

To simplify the process for developers, we provide a full local build script [publish-local.sh](scripts/kuikly-base/publish-local.sh) (and [publish-local.ps1](scripts/kuikly-base/publish-local.ps1) on Windows), which publishes all compiler and runtime artifacts to the `build/repo` directory:

```bash
./scripts/kuikly-base/publish-local.sh
```

**Build Environment**:

1. **Operating System**: macOS 15.7+
2. **Xcode** (for building all Apple targets): 27.0+
3. **DevEco Studio / OpenHarmony SDK** (for building HarmonyOS): Uses a locally installed DevEco Studio or the OpenHarmony Native SDK specified via `OHOS_SDK_HOME` (minimum API level 26)

---

## Special Thanks

The development and open-sourcing of **KuiklyBase-Kotlin** was a challenging yet rewarding process. Throughout this journey, the Tencent Video team received support from the Kotlin team, the HarmonyOS team, industry peers, and fellow teams within Tencent. We also received encouragement and feedback from the broader developer community. We would like to express our heartfelt thanks to everyone who supported and inspired us.

## License

**KuiklyBase-Kotlin** is licensed under the Apache License 2.0.  
For details, see: [License_KuiklyBase-kotlin](License_KuiklyBase-kotlin.txt)
