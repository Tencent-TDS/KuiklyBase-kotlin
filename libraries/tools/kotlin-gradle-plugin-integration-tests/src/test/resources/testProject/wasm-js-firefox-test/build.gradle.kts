import com.github.gradle.node.npm.task.NpxTask

plugins {
    kotlin("multiplatform")
    id("com.github.node-gradle.node") version "7.1.0"
}

repositories {
    mavenLocal()
    mavenCentral()
}

kotlin {
    wasmJs {
        browser {
        }
        binaries.executable()
    }

    sourceSets {
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

node {
    download.set(true)
}

val downloadFirefox by tasks.registering(NpxTask::class) {
    command.set("get-firefox")
    args.set(listOf("-t", "firefox"))
    val firefoxExec = projectDir.resolve("firefox")
    outputs.file(firefoxExec)

    doLast {
        firefoxExec.setExecutable(true)
    }
}