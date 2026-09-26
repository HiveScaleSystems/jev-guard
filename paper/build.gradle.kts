plugins {
    id("com.gradleup.shadow")
}

dependencies {
    implementation(project(":core"))
    // Also supplies Gson and SnakeYAML at runtime, so core's copies are not shaded.
    compileOnly("io.papermc.paper:paper-api:26.2.build.129-stable")
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.shadowJar {
    archiveBaseName = "jev-guard-paper"
    archiveClassifier = ""
}

tasks.jar {
    enabled = false
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}
