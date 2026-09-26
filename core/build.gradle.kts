plugins {
    `java-library`
}

// Provided by the server (Paper bundles both) or shaded by the platform module.
val gsonVersion = "2.14.0"
val snakeyamlVersion = "2.6"

dependencies {
    compileOnly("com.google.code.gson:gson:$gsonVersion")
    compileOnly("org.yaml:snakeyaml:$snakeyamlVersion")

    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("com.google.code.gson:gson:$gsonVersion")
    testImplementation("org.yaml:snakeyaml:$snakeyamlVersion")
}
