plugins {
    id("com.gradleup.shadow") version "9.6.1" apply false
}

subprojects {
    apply(plugin = "java")

    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
    }

    extensions.configure<JavaPluginExtension> {
        toolchain.languageVersion = JavaLanguageVersion.of(25)
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}
