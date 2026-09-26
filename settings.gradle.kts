rootProject.name = "jev-guard"

include("core", "paper")

// The Hytale server API is not published to a Maven repository, so the hytale module compiles
// against a local server jar: -PhytaleServerJar=/path/to/HytaleServer.jar or HYTALE_SERVER_JAR.
val hytaleServerJar = providers.gradleProperty("hytaleServerJar")
    .orElse(providers.environmentVariable("HYTALE_SERVER_JAR"))
    .orNull
if (hytaleServerJar != null && file(hytaleServerJar).isFile) {
    include("hytale")
    gradle.extra["hytaleServerJar"] = hytaleServerJar
} else {
    println("jev-guard: skipping :hytale (set -PhytaleServerJar=/path/to/HytaleServer.jar to build it)")
}
