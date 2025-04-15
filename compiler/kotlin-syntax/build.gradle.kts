plugins {
    kotlin("multiplatform")
}

kotlin {
    jvm()
    sourceSets {
        val commonMain by getting {
            dependencies {
                api(kotlinStdlib())
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
