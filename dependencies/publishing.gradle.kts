buildscript {
    repositories {
        // region @Tencent: Use the Tencent mirror for Gradle Plugin Portal.
        maven("https://mirrors.tencent.com/nexus/repository/gradle-plugins")
        // endregion
    }
}

apply(plugin = "maven-publish")

val archives by configurations

configure<PublishingExtension> {
    publications {
        create<MavenPublication>("maven") {
            setArtifacts(archives.artifacts)
        }
    }

    repositories {
        maven {
            name = "kotlinSpace"
            url = uri("https://redirector.kotlinlang.org/maven/kotlin-dependencies")
            credentials(org.gradle.api.artifacts.repositories.PasswordCredentials::class)
        }
    }
}