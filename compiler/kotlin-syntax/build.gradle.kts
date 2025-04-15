plugins {
    kotlin("multiplatform")
}

kotlin {
    jvm()
    sourceSets {
        val commonMain by getting {
            dependencies {
                api(kotlinStdlib())
                implementation("org.jetbrains:annotations:24.0.0")
            }
            kotlin {
                srcDir("common/src")
            }
        }
        val jvmMain by getting {
            kotlin {
                srcDir("jvm/src")
            }
        }
    }
}
