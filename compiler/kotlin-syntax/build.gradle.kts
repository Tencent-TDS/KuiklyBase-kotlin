plugins {
    kotlin("multiplatform")
}

kotlin {
    jvm()
    sourceSets {
        val commonMain by getting {
            dependencies {
                api(kotlinStdlib())
                implementation("org.jetbrains:annotations:24.0.0") // Needed for syntax library
            }
            kotlin {
                srcDir("common/src")
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(project(":compiler:psi"))
                implementation(commonDependency("org.jetbrains.kotlinx:kotlinx-collections-immutable-jvm"))
                implementation(intellijCore())
                runtimeOnly(libs.intellij.fastutil)
                implementation(project(":compiler:test-infrastructure-utils"))
                implementation(libs.junit.jupiter.api)
                runtimeOnly(libs.junit.jupiter.engine)
                api(kotlinTest("junit"))
            }
            kotlin {
                srcDir("jvm/test")
            }
        }
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
