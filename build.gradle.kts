import de.undercouch.gradle.tasks.download.Download
import java.io.BufferedOutputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

plugins {
    id("org.jetbrains.kotlin.jvm") version "2.0.21"
    id("com.gradleup.shadow") version "8.3.5" // Import shadow API.
    id("de.undercouch.download") version "5.6.0"
    id("maven-publish")
    id("eclipse")
}

group = "nl.skbotnl.chatog"
version = "2.1.6"

val apiVersion = "1.19"

publishing {
    publications {
        create<MavenPublication>("mavenPublication") {
            groupId = "nl.skbotnl.chatog"
            artifactId = "Chat-OG"
            version = version
        }
    }
}

tasks.named<ProcessResources>("processResources") {
    val props = mapOf(
        "version" to version,
        "apiVersion" to apiVersion,
    )
    filesMatching("plugin.yml") {
        expand(props)
    }
}

repositories {
    mavenCentral()
    maven {
        url = uri("https://repo.purpurmc.org/snapshots")
    }
    maven {
        url = uri("https://jitpack.io")
    }
    maven {
        url = uri("https://repo.extendedclip.com/content/repositories/placeholderapi/")
    }
    maven {
        url = uri("https://repo.essentialsx.net/releases/")
    }
}

dependencies {
    compileOnly("org.purpurmc.purpur:purpur-api:1.19.4-R0.1-SNAPSHOT")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")
    compileOnly("me.clip:placeholderapi:2.11.6")
    compileOnly("net.essentialsx:EssentialsX:2.20.1")
    compileOnly(files("libs/AnnouncerPlus-1.3.6.jar"))
    implementation("net.dv8tion:JDA:5.2.2") {
        exclude(module = "opus-java")
    }
    implementation(project(":libs:Utilities-OG"))
    implementation("club.minnced:jda-ktx:0.12.0")
    implementation("club.minnced:discord-webhooks:0.8.4")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("io.github.crackthecodeabhi:kreds:0.9.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
}

tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

val downloadPython by tasks.registering(Download::class) {
    src("https://github.com/python/cpython/archive/refs/heads/3.11.zip")
    dest(layout.buildDirectory.file("python.zip"))
}

val unzipPython by tasks.registering(Copy::class) {
    dependsOn(downloadPython)
    from(zipTree(downloadPython.get().dest)) {
        include("cpython*/**")
        eachFile {
            relativePath = RelativePath(true, *relativePath.segments.drop(1).toTypedArray())
        }
        includeEmptyDirs = false
    }
    into(layout.buildDirectory.dir("python"))
}

val configurePython by tasks.registering(Exec::class) {
    dependsOn(unzipPython)
    doFirst {
        Files.createDirectories(file("$rootDir/build/python/build").toPath())
    }
    workingDir("$rootDir/build/python")
    commandLine("./configure")
    args("--enable-optimizations")
}

val makePython by tasks.registering(Exec::class) {
    dependsOn(configurePython)
    workingDir("$rootDir/build/python")
    commandLine("sh")
    args("-c", "make -j$(nproc)")
}

val installPython by tasks.registering(Exec::class) {
    dependsOn(makePython)
    workingDir("$rootDir/build/python")
    commandLine("make")
    environment("DESTDIR", "$rootDir/build/python/install")
    args("install")
}

val pythonZipFile = file("$rootDir/build/python/python.zip")

val createPythonZip by tasks.registering {
    group = "Chat-OG"
    dependsOn(installPython)
    outputs.file(pythonZipFile)
    doLast {
        val inputDirectory = file("$rootDir/build/python/install/usr/local/")
        if (!pythonZipFile.exists() && inputDirectory.exists()) {
            println("Creating python.zip from $inputDirectory")
            pythonZipFile.parentFile.mkdirs()
            if (!pythonZipFile.exists()) {
                pythonZipFile.createNewFile()
            }
            ZipOutputStream(BufferedOutputStream(FileOutputStream(pythonZipFile))).use { zos ->
                inputDirectory.walkTopDown().forEach { file ->
                    val zipFileName = file.absolutePath
                        .removePrefix(inputDirectory.absolutePath)
                        .removePrefix("/")
                    val entry = ZipEntry(zipFileName + if (file.isDirectory) "/" else "")
                    zos.putNextEntry(entry)
                    if (file.isFile) {
                        file.inputStream().use { fis -> fis.copyTo(zos) }
                    }
                }
            }
        }
    }
}

val buildPython by tasks.registering {
    group = "Chat-OG"
    dependsOn(createPythonZip)
}

tasks.shadowJar {
    dependsOn(buildPython)
    minimize()
    archiveClassifier.set("")
    from(pythonZipFile) {
        into("/")
    }
}

tasks.named("clean").configure {
    onlyIf {
        System.getenv("SELF_MAVEN_LOCAL_REPO") == null
    }
}

tasks.build {
    dependsOn(buildPython)
    dependsOn("shadowJar")
}

tasks.jar.configure {
    archiveClassifier.set("part")
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-parameters")
    options.encoding = "UTF-8"
    options.forkOptions.executable = File(options.forkOptions.javaHome, "bin/javac").path
}

kotlin {
    jvmToolchain(17)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
        vendor = JvmVendorSpec.GRAAL_VM
    }
}
