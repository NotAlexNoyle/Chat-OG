/* This is free and unencumbered software released into the public domain */

/* ------------------------------ Plugins ------------------------------ */
plugins {
    id("java") // Import Java plugin.
    id("java-library") // Import Java Library plugin.
    id("com.diffplug.spotless") version "8.1.0" // Import Spotless plugin.
    id("com.gradleup.shadow") version "8.3.9" // Import Shadow plugin.
    eclipse // Import Eclipse plugin.
    kotlin("jvm") version "2.1.21" // Import Kotlin JVM plugin.
}

/* --------------------------- JDK / Kotlin ---------------------------- */
java {
    sourceCompatibility = JavaVersion.VERSION_17 // Compile with JDK 17 compatibility.
    toolchain { // Select Java toolchain.
        languageVersion.set(JavaLanguageVersion.of(17)) // Use JDK 17.
        vendor.set(JvmVendorSpec.GRAAL_VM) // Use GraalVM CE.
    }
}

kotlin { jvmToolchain(17) }

/* ----------------------------- Metadata ------------------------------ */
val commitHash: String =
    providers.exec { commandLine("git", "rev-parse", "--short=10", "HEAD") }.standardOutput.asText.get().trim()

group = "nl.skbotnl.chatog" // Declare bundle identifier.

val apiVersion = "1.19" // Declare API version.

version = "$apiVersion-$commitHash" // Declare plugin version (will be in .jar).

/* ----------------------------- Resources ----------------------------- */
tasks.named<ProcessResources>("processResources") {
    val props = mapOf("version" to version, "apiVersion" to apiVersion)
    inputs.properties(props) // Indicates to rerun if version changes.
    filesMatching("plugin.yml") { expand(props) }
    from("LICENSE") { into("/") } // Bundle licenses into jarfiles.
}

/* ---------------------------- Repos ---------------------------------- */
repositories {
    mavenCentral() // Import the Maven Central Maven Repository.
    gradlePluginPortal() // Import the Gradle Plugin Portal Maven Repository.
    maven { url = uri("https://repo.purpurmc.org/snapshots") } // Import the PurpurMC Maven Repository.
    maven { url = uri("https://jitpack.io") } // Import the Jitpack Maven Repository.
    maven { url = uri("https://repo.essentialsx.net/releases/") } // Import the EssentialsX Maven Repository.
}

/* ---------------------- Java project deps ---------------------------- */
dependencies {
    compileOnly("org.purpurmc.purpur:purpur-api:1.19.4-R0.1-SNAPSHOT") // Declare Purpur API version to be packaged.
    compileOnly(
        files("libs/AnnouncerPlus-1.4.1.jar")
    ) // Import AnnouncerPlus API (binary-compatible with AnnouncerPlus-OG API).
    compileOnlyApi("net.trueog.utilities-og:Utilities-OG") // Import TrueOG Network Utilities-OG Java API (from source).
    compileOnlyApi("de.myzelyam.vanishog:Vanish-OG") // Import TrueOG Network Vanish-OG Java API (from source).
    compileOnly("net.luckperms:api:5.5") // Import LuckPerms API.
    compileOnly(
        "io.github.miniplaceholders:miniplaceholders-api:2.2.3"
    ) // Import MiniPlaceholders API for direct tag access.
    implementation("tools.jackson.core:jackson-databind:3.1.1")
    implementation("tools.jackson.dataformat:jackson-dataformat-yaml:3.1.1")

    implementation("net.dv8tion:JDA:6.1.2")
    implementation("club.minnced:jda-ktx:0.12.0")
    implementation("club.minnced:discord-webhooks:0.8.4")
    implementation("com.google.code.gson:gson:2.13.2")
    implementation("io.lettuce:lettuce-core:7.5.1.RELEASE")
    implementation("com.squareup.okhttp3:okhttp:5.3.2")
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("org.slf4j:slf4j-api:2.0.17") // Bundle a relocated SLF4J API for shaded dependencies.
    implementation("org.slf4j:slf4j-nop:2.0.17") // Provide a relocated no-op SLF4J backend to avoid provider warnings.
}

configurations.all {
    exclude(group = "com.google.crypto.tink", module = "tink")
    exclude(group = "net.java", module = "opus-java")
}

/* ---------------------- Reproducible jars ---------------------------- */
tasks.withType<AbstractArchiveTask>().configureEach { // Ensure reproducible .jars
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

/* ----------------------------- Shadow -------------------------------- */
tasks.shadowJar {
    archiveClassifier.set("") // Use empty string instead of null.
    isEnableRelocation = true
    relocationPrefix = "${project.group}.shadow"
    exclude("io.github.miniplaceholders.*") // Exclude the MiniPlaceholders package from being shadowed.
    exclude("natives/**")
    mergeServiceFiles()
    minimize { exclude(dependency("org.slf4j:slf4j-nop:.*")) }
}

tasks.jar { archiveClassifier.set("part") } // Applies to root jarfile only.

tasks.build { dependsOn(tasks.spotlessApply, tasks.shadowJar) } // Build depends on spotless and shadow.

/* --------------------------- Javac opts ------------------------------- */
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Xlint:deprecation") // Trigger deprecation warning messages.
    options.encoding = "UTF-8" // Use UTF-8 file encoding.
}

/* ----------------------------- Auto Formatting ------------------------ */
spotless {
    kotlin { ktfmt().kotlinlangStyle().configure { it.setMaxWidth(120) } }
    kotlinGradle {
        ktfmt().kotlinlangStyle().configure { it.setMaxWidth(120) } // JetBrains Kotlin formatting.
        target("build.gradle.kts", "settings.gradle.kts") // Gradle files to format.
    }
}

tasks.named("spotlessCheck") {
    dependsOn("spotlessApply") // Run spotless before checking if spotless ran.
}
