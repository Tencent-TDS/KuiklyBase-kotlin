![](https://img.shields.io/badge/dynamic/xml?label=KuiklyBase-Kotlin&url=https%3A%2F%2Fmirrors.tencent.com%2Fnexus%2Frepository%2Fmaven-tencent%2Forg%2Fjetbrains%2Fkotlin%2Fkotlin-compiler%2Fmaven-metadata.xml&query=%2Fmetadata%2Fversioning%2Flatest%2Ftext())

# KuiklyBase-Kotlin

## 项目简介

**KuiklyBase-Kotlin** 是腾讯大前端Oteam基于 [JetBrains](https://www.jetbrains.com/zh-cn/) 公司开源的 [Kotlin](https://github.com/jetbrains/kotlin) 二次开发并开源的项目，由腾讯视频、PCG端技术深度参与建设完成。与原版相比，**KuiklyBase-Kotlin** 新增了对鸿蒙平台的支持，同时基于腾讯视频团队的应用实践对 Kotlin 编译器和 Kotlin Native 运行时进行了优化和调整，目前已经在腾讯公司多个线上应用中成功落地。

## 功能概述

**KuiklyBase-Kotlin** 的更新内容提交于 [kuikly-base/2.4.21](tree/kuikly-base/2.4.21) 分支上，该分支基于官方的 [v2.4.21](https://github.com/JetBrains/kotlin/tree/v2.4.21) 版本进行修改，包含了 [Kotlin 2.4.21](https://github.com/JetBrains/kotlin/releases/tag/v2.4.21) 版本的所有功能。在此基础上，我们新增了以下核心功能：

### 1. 支持鸿蒙系统及扩展目标平台

1. **支持鸿蒙系统（`ohosArm64`）**：这是 **KuiklyBase-Kotlin** 最大的功能修改。我们通过新增 Kotlin/Native 目标平台的方式，基于统一的 LLVM 21 工具链实现交叉编译，使得鸿蒙平台成为 Kotlin/Native 众多目标平台中的一员，实现与其他平台共享代码。
2. **扩展与兼容目标平台**：新增 `mingwX86` 与 `mingwArm64` Kotlin/Native 目标平台，并保留 `KonanTarget.IOS_ARM32` 枚举以兼容旧版项目与插件。

### 2. 性能优化

1. **双向字符串代理**：实现 Apple 平台 Kotlin `String` 与 `NSString` 的双向字符串代理，减少字符串跨语言传递时的内存复制。
2. **零拷贝外部存储字符串**：在全目标平台上支持从自有 UTF-8（ASCII）与 UTF-16 缓冲区零拷贝创建 Kotlin `String`（提供 `kotlinx.cinterop` 扩展 `toKStringFromOwnedUtf8` / `toKStringFromOwnedUtf16` 及 C 运行时 API `CreateStringFromOwnedUtf8*` / `CreateStringFromOwnedUtf16*`，支持传入自定义释放回调 `NativeFreeCallback`），通过关联对象自动管理外部缓冲区生命周期。
3. **基础类型拆装箱与判等优化**：避免非空基础类型在安全调用（`?.`）、非空断言（`!!`）、内联函数返回值及泛型实参内联时的冗余装箱/拆箱，优化 `IfNull` 表达式融合，并将枚举判等（`==`）降级为引用相等（`===`）。
4. **CMS 弱引用读屏障非阻塞优化**：优化 CMS 垃圾回收器的弱引用读屏障（`weakRefReadBarrier`），消除并发标记终止阶段等待 `markTerminationMutex_` 自旋导致的跨线程停顿及 Apple 平台 `SideTable` 双重锁死锁（ANR）风险。

### 3. 运行时与工具链增强

1. **反射能力增强**：支持 Kotlin/Native 下的 `KClass.isSubclassOf` 以及 Objective-C 类的 `KClass` 反射能力。
2. **CInterop 优化**：在 `StructRendering` 中跳过仅包含位域（bitfields）的匿名内部结构体，避免生成无效绑定代码。
3. **Gradle 与 CocoaPods 优化**：在默认 Kotlin Hierarchy 模板中纳入 `ohos` 分组（`ohosMain` / `ohosTest`）；未显式指定模块名时支持从 `modulemap` 自动推断 CocoaPods 模块名；为 `commonMain` 编译任务的默认 `moduleName` 拼接 `group` 前缀以避免多模块同名冲突。
4. **KLIB 兼容性与 Swift Export 修复**：在 KLIB 反序列化阶段兼容旧版 Kotlin 编译产物中的遗留标识 `GENERATED_SINGLE_FIELD_VALUE_CLASS_MEMBER`（[KT-90675](https://youtrack.jetbrains.com/issue/KT-90675)）与 `SUSPEND_CONVERSION`（[KT-91139](https://youtrack.jetbrains.com/issue/KT-91139)）；修复 Swift Export 将 `Set` 与 `Dictionary` 作为 `KotlinBridgeable` 传递时的问题（[KT-90067](https://youtrack.jetbrains.com/issue/KT-90067)）。

### 4. 符号导出优化

支持 `@HiddenFromC` 注解，编译检查和实现逻辑完全与 `@HiddenFromObjC` 注解对齐，配合 Compose 编译器插件实现禁止 `@Composable` 函数导出为 C 符号的功能。


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
        ohosArm64Main {
            dependencies {
                implementation("...")
            }
        }
    }
}
```

## 编译方法

不同于早期的 2.0.21 版本（鸿蒙与 Apple 平台分别依赖不同版本的 LLVM），当前版本已经实现了鸿蒙与 Apple 平台（包括 iOS、macOS 等）及其他目标平台的 **LLVM 21** 统一，并内置了预生成的 `libclang` 互操作代码，因此 **KuiklyBase-Kotlin** 的编译方式与官方版本保持一致。同时支持通过 `-Pkuikly.targets` 参数（默认为 `mini`）按需过滤参与构建的 Native 目标平台。

为了方便各位开发者，我们提供了一个完整的本地发布脚本 [publish-local.sh](scripts/kuikly-base/publish-local.sh)（Windows 下为 [publish-local.ps1](scripts/kuikly-base/publish-local.ps1)），可将 Kotlin 编译器和运行时的所有产物发布到 `build/repo` 目录下：

```bash
./scripts/kuikly-base/publish-local.sh
```

编译环境：

1. 操作系统：macOS 15.7+
2. Xcode（用于编译 Apple 的所有目标平台）：27.0+
3. DevEco Studio / OpenHarmony SDK（用于编译鸿蒙平台）：支持通过本地安装的 DevEco Studio 或环境变量 `OHOS_SDK_HOME` 指定鸿蒙 Native SDK（最低要求 API 26）

## 特别感谢

**KuiklyBase-Kotlin** 的开发和开源实属不易。在整个过程中，腾讯视频团队受到了来自 Kotlin 团队、鸿蒙团队、业内同行和公司内部兄弟团队的帮助和支持，也收到了来自外部同行的鼓舞和期待，在此对所有帮助和鼓舞过我们的组织和个人表示感谢。

## License

**KuiklyBase-Kotlin** 基于 Apache 2.0 协议发布，详见：[License_KuiklyBase-kotlin](License_KuiklyBase-kotlin.txt).
