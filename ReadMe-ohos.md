# 鸿蒙适配

本分支拓展2.2版本kotlin native以支持鸿蒙真机，**当前尚未经过生产环境测试**，实现仅供参考。

## 版本构建

### 构建 llvm

2.2版本Kotlin/Native使用[llvm 19.1.4](https://github.com/Kotlin/llvm-project/blob/kotlin/llvm-19-apple/cmake/Modules/LLVMVersion.cmake)生成二进制产物。本分支使用[llvm 19.1.7](https://gitee.com/openharmony/third_party_llvm-project/blob/llvm-19.1.7/cmake/Modules/LLVMVersion.cmake)打包鸿蒙二进制，LLVM代码来自 [https://gitee.com/openharmony/third_party_llvm-project/tree/llvm-19.1.7](https://gitee.com/openharmony/third_party_llvm-project/tree/llvm-19.1.7)。当前Windows/Mac/Linux三端均有可用LLVM产物，项目使用本版本Kotlin，构建过程中会自动下载。Mac/Linux产物构建过程已开源，Windows产物构建过程将于近期开源。

为避免环境问题影响构建我们提供了用于打包LLVM的[GitHub Action脚本](/.github/workflows/build-llvm.yaml)。大部分的LLVM构建工作在Linux Docker中执行，只有最终在Mac上运行的二进制需要在Mac上构建。Github Action提供的runner单次构建最多运行6h不够在Linux上完成LLVM构建，因此您需要按照[Github 文档](https://docs.github.com/en/actions/how-tos/manage-runners/self-hosted-runners/add-runners)在项目中添加一个Linux的自托管执行机（self hosted runner）。Linux执行机上只需要安装并启动docker，启动Github Action Runner，Action脚本执行过程中会配置构建LLVM所需的环境

### 构建 Kotlin

构建Kotlin过程中本项目对环境的要求[和上游社区一致](/kotlin-native/README.md#building-from-source)，主要需要注意的是java和xcode版本

- Kotlin构建的gradle任务执行过程中会使用[Gradle toolchains](https://docs.gradle.org/current/userguide/toolchains.html)功能下载 Temurin 版本 JDK，不过此外仍需手动安装[Zulu 8](https://www.azul.com/downloads/?version=java-8-lts&architecture=x86-64-bit&package=jdk#zulu)和[Temurin 21](https://adoptium.net/temurin/releases?version=21&os=any&arch=any)两个JDK。Mac上可以用 `brew install zulu@8 temurin@21` 命令安装，Windows/Linux上的安装方式请参考官网指导
  - 如果您在项目构建过程中遇到 ` Could not find artifact com.sun:tools:iar:1.8.0 at specified path /path/to/java/lib/tools.jar ` 原因应为构建工具没有找到JDK 8，需要您在运行脚本前手动设置 `export JDK_18=/path/to/zulu-8/java_home`
- Mac上需使用 Xcode 16+ 版本。您可以从 [https://xcodereleases.com](https://xcodereleases.com) 搜索下载最新的发布版本 Xcode（需要苹果账号），并通过 `sudo xcode-select -s /path/to/Xcode.app` 命令指定构建过程中使用的Xcode
- Windows上构建Kotlin需安装Microsoft Build Tools和Windows SDK，Kotlin文档中推荐的2019版本已被微软下线没有官方的下载方式，如找不到2019版本安装包可以使用[Visual Studio 2022 Community 版本](https://visualstudio.microsoft.com/downloads/)，在安装过程中需要勾选使用C++的桌面开发下MSVC最新编译器和库和Windows 10 SDK两个组件

构建命令

```shell
# export JDK_18=/path/to/zulu-8.jdk/Contents/Home # 如果maven构建过程中报找不到 com.sun:tools:iar:1.8.0，需要手动指定JDK 8地址
bash scripts/build-ohos.sh
```

发布产物在 [build/repo](./build/repo) 目录下

技巧：
- 修改 [EnabledTargets.kt](/kotlin-native/build-tools/src/main/kotlin/org/jetbrains/kotlin/konan/target/EnabledTargets.kt) 可以出只支持鸿蒙的Kotlin版本加快构建。使用这种Kotlin版本的项目中只能声明打包鸿蒙产物，否则构建会失败。
  ```kotlin
  fun enabledTargets(platformManager: PlatformManager) = listOf(KonanTarget.OHOS_ARM64) // 单出鸿蒙
  ```
- 调试[Kotlin runtime部分](/kotlin-native/runtime/)时可以使用 [scripts/build-ohos-runtime.sh](/scripts/build-ohos-runtime.sh) 脚本快速单独打包runtime部分，提升效率。注意要让修改生效，需要修改下游样例项目根目录的gradle.properties，添加一行 `kotlin.native.home=/path/to/kotiln-native/dist` 配置的值为本项目中 [kotlin-native/dist](/kotlin-native/dist) 文件夹的绝对路径
- Kotlin/Native打包过程中需要设置 kotlin.native.enabled=true，因此拉取项目后在IDEA中直接sync的范围不包含Kotlin/Native相关代码，表现为项目中kotlin-native文件夹下的代码不支持点击跳转定义等功能。您可以在使用 `bash scripts/build-ohos.sh` 命令完成本地出包后，在本项目根目录 [gradle.properties](/gradle.properties)中添加下述配置，存在重复定义的设置将项目中原来的设置注释掉，之后进行sync，让IDEA支持在kotlin-native部分代码中进行跳转定义等操作
```
bootstrap.kotlin.default.version=2.2.255-SNAPSHOT
deployVersion=2.2.255-SNAPSHOT
versions.kotlin-native=2.2.255-SNAPSHOT
konanVersion=2.2.255-SNAPSHOT
bootstrap.kotlin.version=2.2.255-SNAPSHOT
kotlin.native.enabled=true
bootstrap.local=true
bootstrap.local.version=2.2.255-SNAPSHOT
```

## 在线发布

通过在线方式使用本项目需要发布两部分产物：`build/repo` 目录下的maven仓库和llvm。最简单的发布方式需要一个域名和一台运行nginx的服务器。

服务器搭建以debian为例，为了让Konan下载依赖时可以按照nginx配置进行重定向需要提供https网址，因此需要一个域名指向当前服务器

```shell
sudo apt update
sudo apt upgrade -y

sudo apt install nginx -y
sudo systemctl start nginx
sudo systemctl enable nginx
sudo systemctl status nginx
sudo ufw allow 'Nginx Full'

sudo mkdir -p /var/www/artifact # llvm相关产物
sudo mkdir -p /var/www/maven # maven仓库

sudo apt install certbot python3-certbot-nginx -y
sudo certbot --nginx --register-unsafely-without-email # 按照命令行提示配置https
```

参考nginx配置，主要功能为
1. 在 http://yourdomainnamne.com/maven/ 托管 /var/www/maven 下的 maven 仓库
2. 在 http://yourdomainnamne.com/download/ 托管 /var/www/artifact 下的 llvm 产物，本项目只需提供少量几个产物，其他产物重定向到上游JB cdk进行下载

https相关配置由letsencrypt Certbot自动生成和管理

```
server {
  access_log /var/log/nginx/access.log;
	root /var/www/html;
	index index.html index.htm index.nginx-debian.html;
  server_name kotlinnativeohos.online www.kotlinnativeohos.online;

	location / {
		try_files $uri $uri/ =404;
	}

  location /download/ {
    alias /var/www/artifact/;  # path to llvm artifacts
    try_files $uri $uri/ @cdn_redirect; # if the artifact is not found on our sever, redirect to upstream jetbrains cdn
  }
  
  location @cdn_redirect {
      rewrite ^/download/(.*)$ https://download-cdn.jetbrains.com/kotlin/native/$1 permanent;
  }
	
  location /maven/ {
    alias /var/www/maven/;  # path to maven repository
    try_files $uri $uri/ =404;
  }

  listen [::]:443 ssl ipv6only=on; # managed by Certbot
  listen 443 ssl; # managed by Certbot
  ssl_certificate /etc/letsencrypt/live/kotlinnativeohos.online/fullchain.pem; # managed by Certbot
  ssl_certificate_key /etc/letsencrypt/live/kotlinnativeohos.online/privkey.pem; # managed by Certbot
  include /etc/letsencrypt/options-ssl-nginx.conf; # managed by Certbot
  ssl_dhparam /etc/letsencrypt/ssl-dhparams.pem; # managed by Certbot
}

server {
  if ($host = kotlinnativeohos.online) {
      return 301 https://$host$request_uri;
  } # managed by Certbot

  listen 80 default_server;
  listen [::]:80 default_server;
  server_name kotlinnativeohos.online www.kotlinnativeohos.online;
  return 404; # managed by Certbot
}
```

配置完成后检查配置格式并重启

```shell
sudo nginx -t
sudo systemctl reload nginx
sudo systemctl restart nginx
```

上传 maven 产物

```shell
rsync -avz --delete ./build/repo/ root@kotlinnativeohos.online:/var/www/maven
```

上传llvm相关产物，包含llvm 19和ohos sysroot。konan的依赖会被直接解压到 `$KONAN_DATA_DIR/dependencies` 下（不会给解压出来的文件产物创建一个文件夹放进去），因此需要压缩一个和压缩包同名的目录。sysroot当前需要使用 5.0.11.110 command line tools中（或对应DevecoStudio中）的版本，路径在 `command-line-tools-5.0.11.110/sdk/default/openharmony/native/sysroot`

```shell
tar -czf llvm-19.1.7-aarch64-macos-ohos-2.tar.gz llvm-19.1.7-aarch64-macos-ohos-2
tar -czf sysroot-ohos-aarch64-5.0.11.110.tar.gz sysroot-ohos-aarch64-5.0.11.110

scp llvm-19.1.7-aarch64-macos-ohos-2.tar.gz  root@kotlinnativeohos.online:/var/www/artifact/
scp sysroot-ohos-aarch64-5.0.11.110.tar.gz  root@kotlinnativeohos.online:/var/www/artifact/
```

## 使用 2.2.0-ohos 版本 

### 在线版本

1. 在原来项目中指定maven仓的地方添加

```shell
maven("https://kotlinnativeohos.online/maven")
```

> *注意这个maven仓库仅用于临时传递产物，随时有可能下线。这个仓库中的产物只经过极其有限的测试不能用于生产*

1. 切换kotlin版本到本项目版本

### 本地版本

1. 与在线版本一样需要添加maven仓库，仓库地址为本项目 [build/repo](/build/repo/) 文件夹
2. 切换kotlin版本，打包脚本默认版本为 [2.2.255-SNAPSHOT](/scripts/build-ohos.sh#L20)
3. Kotlin Native 编译器 Konan 的依赖不受gradle管理，修改runtime，platformdef等组件的内容后**默认konan不会更新依赖**导致修改在下游项目中不生效，调试这一部分需要在下游项目根目录的 gradle.properties 中设置 `kotlin.native.home=` 本项目中 [kotlin-native/dist/](/kotlin-native/dist/) 文件夹地址
```
kotlin.native.home=/path/to/kotlin-native/dist/
```

## 鸿蒙化修改梳理

总体思路：
- 鸿蒙平台和其他平台共LLVM前端，使用支持打鸿蒙二进制的LLVM后端
- 大多数列出了所有平台的地方需要补充一个OHOS
- 鸿蒙编译相关的配置与Linux较为接近
- 提供封装鸿蒙系统API的platform def文件

修改点：
- 构建脚本：[本地构建](./scripts/build-ohos.sh)，[Action流水线](./.github/workflows/)
- 修改Kotlin Native LLVM相关依赖下载地址：[konan.properties](kotlin-native/konan/konan.properties#L18)，[build.gradle.kts](kotlin-native/dependencies/build.gradle.kts#L15)，这个端点的详细介绍见[在线发布](#在线发布)章节
- 配置编译鸿蒙二进制使用的llvm后端和编译选项：[konan.properties](kotlin-native/konan/konan.properties#L920)
- 鸿蒙系统接口 platform cinterop klib 库定义：[kotlin-native/platformLibs/src/platform/ohos](kotlin-native/platformLibs/src/platform/ohos)，针对 5.0.11.110 版本，sdk 15
- 鸿蒙平台相关配置项定义：[Configurables.kt](native/utils/src/org/jetbrains/kotlin/konan/target/Configurables.kt)，[ConfigurablesImpl.kt](native/utils/src/org/jetbrains/kotlin/konan/target/ConfigurablesImpl.kt)
- Kotlin侧支持hilog和hitrace：[Porting.cpp](kotlin-native/runtime/src/main/cpp/Porting.cpp#L87)
- KMP插件增加鸿蒙目标定义：[KotlinTargetContainerWithPresetFunctions.kt](libraries/tools/kotlin-gradle-plugin/src/common/kotlin/org/jetbrains/kotlin/gradle/dsl/KotlinTargetContainerWithPresetFunctions.kt#L30)，
- [额外的header搜索路径](native/utils/src/org/jetbrains/kotlin/konan/target/ClangArgs.kt#L180)，[鸿蒙链接参数配置](native/utils/src/org/jetbrains/kotlin/konan/target/Linker.kt#L180)，[鸿蒙平台使用单独的llvm后端](kotlin-native/build-tools/src/main/kotlin/org/jetbrains/kotlin/ExecClang.kt#L110)
- 其余的修改主要为零散的参照其他平台添加鸿蒙平台配置
