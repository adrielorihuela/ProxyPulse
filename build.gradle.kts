plugins {
    `java-library`
    id("com.gradleup.shadow") version "9.2.2"
}

group = property("group") as String
version = property("version") as String

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") { name = "papermc" }
    maven("https://repo.codemc.io/repository/maven-releases/") { name = "codemc" }
}

dependencies {
    // Provided by the proxy at runtime -- never shaded.
    compileOnly("com.velocitypowered:velocity-api:${property("velocityApiVersion")}")
    annotationProcessor("com.velocitypowered:velocity-api:${property("velocityApiVersion")}")

    // Bundled inside the final jar so a single file is all an admin has to drop in.
    implementation("com.github.retrooper:packetevents-velocity:${property("packetEventsVersion")}")
    implementation("org.spongepowered:configurate-yaml:4.1.2")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("com.velocitypowered:velocity-api:${property("velocityApiVersion")}")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(25)
    options.compilerArgs.add("-Xlint:all,-processing,-serial")
}

tasks.test {
    useJUnitPlatform()
    testLogging { events("passed", "skipped", "failed") }
}

tasks.shadowJar {
    archiveClassifier.set("")
    // Relocated so a proxy that already runs PacketEvents (or another plugin that bundles it)
    // never collides with our copy.
    relocate("com.github.retrooper.packetevents", "dev.adriel.proxypulse.libs.packetevents.api")
    relocate("io.github.retrooper.packetevents", "dev.adriel.proxypulse.libs.packetevents.impl")
    relocate("org.spongepowered.configurate", "dev.adriel.proxypulse.libs.configurate")
    relocate("org.yaml.snakeyaml", "dev.adriel.proxypulse.libs.snakeyaml")
    mergeServiceFiles()
    minimize {
        exclude(dependency("com.github.retrooper:.*:.*"))
        exclude(dependency("org.spongepowered:.*:.*"))
    }
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
