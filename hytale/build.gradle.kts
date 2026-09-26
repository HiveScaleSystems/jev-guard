plugins {
    id("com.gradleup.shadow")
}

val hytaleServerJar = files(gradle.extra["hytaleServerJar"] as String)

dependencies {
    implementation(project(":core"))
    // Hytale doesn't ship SnakeYAML; bundle it (relocated below). Gson comes from the server.
    implementation("org.yaml:snakeyaml:2.6")
    compileOnly(hytaleServerJar)
    compileOnly("com.google.code.gson:gson:2.13.2")

    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation(hytaleServerJar)
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
