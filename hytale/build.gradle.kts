plugins {
    id("com.gradleup.shadow")
}

val hytaleVersion = providers.gradleProperty("hytaleVersion").get()

repositories {
    maven(if ("-pre" in hytaleVersion) "https://maven.hytale.com/pre-release" else "https://maven.hytale.com/release")
}

dependencies {
    implementation(project(":core"))
    // Hytale doesn't ship SnakeYAML; bundle it (relocated below). Gson comes from the server.
    implementation("org.yaml:snakeyaml:2.6")
    compileOnly("com.hypixel.hytale:Server:$hytaleVersion")
    compileOnly("com.google.code.gson:gson:2.13.2")

    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("com.hypixel.hytale:Server:$hytaleVersion")
}

tasks.processResources {
    filesMatching("manifest.json") {
        expand("version" to project.version)
    }
}

tasks.shadowJar {
    archiveBaseName = "jev-guard-hytale"
    archiveClassifier = ""
    relocate("org.yaml.snakeyaml", "dev.jevguard.libs.snakeyaml")
}

tasks.jar {
    enabled = false
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}
