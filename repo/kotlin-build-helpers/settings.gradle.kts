dependencyResolutionManagement {
    repositories {
        // region @Tencent: Use the Tencent mirror for Maven Central.
        mavenCentral { setUrl("https://mirrors.tencent.com/nexus/repository/maven-public") }
        // endregion
    }
}

rootProject.name = "kotlin-build-helpers"
