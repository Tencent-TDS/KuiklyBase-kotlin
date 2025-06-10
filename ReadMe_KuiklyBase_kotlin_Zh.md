![](https://img.shields.io/badge/dynamic/xml?label=KuiklyBase-Kotlin&url=https%3A%2F%2Fmirrors.tencent.com%2Fnexus%2Frepository%2Fmaven-tencent%2Forg%2Fjetbrains%2Fkotlin%2Fkotlin-native-prebuilt%2Fmaven-metadata.xml&query=%2Fmetadata%2Fversioning%2Flatest%2Ftext())

# KuiklyBase-Kotlin

## 项目简介

**KuiklyBase-Kotlin** 是腾讯大前端Oteam基于 [JetBrains](https://www.jetbrains.com/zh-cn/) 公司开源的 [Kotlin](https://github.com/jetbrains/kotlin) 二次开发并开源的项目，由腾讯视频、PCG端技术深度参与建设完成。与原版相比，**KuiklyBase-Kotlin** 新增了对鸿蒙平台的支持，同时基于腾讯视频团队的应用实践对 Kotlin 编译器和 Kotlin Native 运行时进行了优化和调整，目前已经在腾讯公司多个线上应用中成功落地。

## 功能概述

**KuiklyBase-Kotlin** 的更新内容提交于 [kuikly-base/2.0.20](tree/kuikly-base/2.0.20) 分支上，该分支基于官方的 [2.0.20](tree/2.0.20) 分支进行修改，包含了 [Kotlin 2.0.21](https://github.com/JetBrains/kotlin/releases/tag/v2.0.21) 版本的所有功能。在此基础上，我们新增了以下核心功能：

### 1. 支持鸿蒙系统

这是 **KuiklyBase-Kotlin** 最大的功能修改。我们通过新增 Kotlin Native 目标平台的方式，使得鸿蒙平台成为 Kotlin Native 众多目标平台中的一员，实现与其他平台共享代码。支持鸿蒙 SDK API 15。

### 2. 性能优化

1. GC 参数调优，解决特定场景下 GC 频繁触发的问题
2. 支持可挂起的 GC，可实现对 GC 调度的精细化控制
3. 实现 Apple 平台的字符串代理，减少字符串跨语言传递时的内存复制

### 3. 编译优化

1. 引入 Kotlin 2.1 版本的性能内联选项并优化运行时链接策略
2. 优化 `klib` 依赖解析失败的报错信息，方便开发者快速定位依赖问题

### 4. 符号导出优化

1. 支持 Kotlin 的 public 符号默认不导出为 Objective-C 符号。该功能可降低导出的符号数量，减小导出的头文件大小，降低编译耗时。
2. 支持 `@HiddenFromC` 注解， 编译检查和实现逻辑完全与 `@HiddenFromObjC` 注解对齐，配合 Compose 编译器插件实现禁止 `@Composable` 函数导出为 C 符号的功能。  


## 集成方法

### 配置仓库

为了尽可能与官方 Kotlin 的使用体验保持一致，**KuiklyBase-Kotlin** 的产物会以同样的方式发布于腾讯软件源的 Maven 仓库中，开发者在使用前需要先在项目中添加腾讯软件源仓库：

```
https://mirrors.tencent.com/nexus/repository/maven-tencent/
https://mirrors.tencent.com/nexus/repository/maven-public/
```

>**说明**：`maven-tencent` 仓库用于发布由腾讯开源且发布的产物；`maven-public` 仓库则代理了十多个常用的国内外 Maven 仓库，包括 Maven 中央仓库、Google Maven 仓库、JitPack Maven 仓库、华为 Maven 仓库等等，以求推广自由软件的价值，提升国内开发者的开发体验。腾讯软件源同时也提供了对常见的 Linux 发行版、Gradle、npm、Go、Flutter、Rust 等仓库的镜像服务，详见[腾讯软件源](https://mirrors.tencent.com/)官网。

以 Gradle 为例：

方式一：通过 **`settings.gradle.kts`** 配置仓库

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

方式二：通过 **`build.gradle.kts`** 配置仓库

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

### 版本说明

**KuiklyBase-Kotlin** 与官方 Kotlin 通过版本号区分，命名规则为：`<官方版本号>-KBA-<构建号>`，其中 `KBA` 表示 “KuiklyBase All Targets”。 配置示例如下：

```kotlin
plugins {
    kotlin("multiplatform") version "<最新版本>"
}
```

### 配置鸿蒙平台

鸿蒙是 Kotlin Native 的目标平台，与 iOS 平台的配置方式类似，如下：

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

## 编译方法

由于鸿蒙平台依赖的 LLVM 版本与 Apple 平台（包括 iOS、macOS 等）的不一致，**KuiklyBase-Kotlin** 的编译方式与官方版本略有不同。

为了方便各位开发者，我们提供了一个完整的[构建脚本](scripts/kuikly-base/publish-local.sh)，可将 Kotlin 编译器和运行时的所有产物发布到 `build/repo` 目录下。

编译环境：

1. 操作系统：macOS 15
2. Xcode（用于编译 Apple 的所有目标平台）：默认版本需设置为 16，同时将 Xcode 15 安装于 `/Applications/Xcode-15.0.app` 目录下
3. DevEco Studio（用于编译鸿蒙平台）：5.0.4

>**说明**：
> 1. Xcode 15 只用于构建 Apple 的 x64 平台，详见 [KT-69094](https://youtrack.jetbrains.com/issue/KT-69094)。
> 2. 编译鸿蒙平台使用的 LLVM 12 基于 [openharmony/third_party_llvm-project](https://gitee.com/openharmony/third_party_llvm-project/commits/master-llvm12-backup) 项目构建而来。

# 特别感谢

**KuiklyBase-Kotlin** 的开发和开源实属不易。在整个过程中，腾讯视频团队受到了来自 Kotlin 团队、鸿蒙团队、业内同行和公司内部兄弟团队的帮助和支持，也收到了来自外部同行的鼓舞和期待，在此对所有帮助和鼓舞过我们的组织和个人表示感谢。

# License

**KuiklyBase-Kotlin** 基于 Apache 2.0 协议发布，详见：[License_KuiklyBase-kotlin](License_KuiklyBase-kotlin.txt). 

