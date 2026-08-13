import java.security.MessageDigest

plugins {
    `java-library`
    id("com.gradleup.shadow") version "9.2.2"
}

group = property("group") as String
version = property("version") as String

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") { name = "papermc" }
}

// The proxy jar is not published to any Maven repository, but ProxyPulse needs its internals
// (packet classes, the Netty pipeline, StateRegistry) to rewrite packets at all. It is downloaded
// once, pinned by SHA-256, and used at compile time only -- it is never bundled.
val velocityProxyJar = layout.buildDirectory.file("velocity/velocity-proxy.jar")

val velocityProxyUrl = property("velocityProxyUrl") as String
val velocityProxySha256 = property("velocityProxySha256") as String

val downloadVelocityProxy by tasks.registering {
    val url = velocityProxyUrl
    val expectedSha = velocityProxySha256
    val target = velocityProxyJar.get().asFile
    outputs.file(target)
    doLast {
        if (!target.exists()) {
            target.parentFile.mkdirs()
            logger.lifecycle("Downloading Velocity proxy jar from $url")
            uri(url).toURL().openStream().use { input -> target.outputStream().use { input.copyTo(it) } }
        }
        val actual = MessageDigest.getInstance("SHA-256")
            .digest(target.readBytes())
            .joinToString("") { byte -> "%02x".format(byte) }
        check(actual == expectedSha) {
            "Checksum mismatch for the Velocity proxy jar: expected $expectedSha but got $actual"
        }
    }
}

dependencies {
    // Provided by the proxy at runtime -- never shaded.
    compileOnly("com.velocitypowered:velocity-api:${property("velocityApiVersion")}")
    annotationProcessor("com.velocitypowered:velocity-api:${property("velocityApiVersion")}")
    compileOnly(files(velocityProxyJar) { builtBy(downloadVelocityProxy) })

    // Bundled inside the final jar so a single file is all an admin has to drop in.
    implementation("org.spongepowered:configurate-yaml:4.1.2")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("com.velocitypowered:velocity-api:${property("velocityApiVersion")}")
    testImplementation(files(velocityProxyJar) { builtBy(downloadVelocityProxy) })
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
    relocate("org.spongepowered.configurate", "dev.adriel.proxypulse.libs.configurate")
    relocate("org.yaml.snakeyaml", "dev.adriel.proxypulse.libs.snakeyaml")
    relocate("io.leangen.geantyref", "dev.adriel.proxypulse.libs.geantyref")
    mergeServiceFiles()
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
