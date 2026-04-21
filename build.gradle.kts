import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("org.ow2.asm:asm:9.9.1")
        classpath("org.ow2.asm:asm-commons:9.6")
    }
}

plugins {
    java
    // Mantenemos tu versión experimental de Kotlin
    kotlin("jvm") version "2.3.20"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("maven-publish")
}

group = "liric.mistaken"
version = "3.0.6"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://maven.enginehub.org/repo/")
    maven("https://repo.triumphteam.dev/repository/maven-releases/")
    maven("https://repo.codemc.io/repository/maven-public/")
    maven("https://jitpack.io")
    maven("https://repo.helpch.at/releases")
    maven("https://repo.infernalsuite.com/repository/maven-snapshots/")

    flatDir {
        dirs("libs")
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    implementation(kotlin("stdlib"))

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("com.github.retrooper:packetevents-spigot:2.12.0")
    implementation("dev.triumphteam:triumph-gui:3.1.13")
    implementation("org.slf4j:slf4j-simple:2.0.17")
    implementation("com.infernalsuite.asp:file-loader:4.0.0-SNAPSHOT")
    compileOnly("com.infernalsuite.asp:api:4.0.0-SNAPSHOT")
    compileOnly(files("libs/GSit-3.3.3.jar"))
    compileOnly("net.luckperms:api:5.5")
    compileOnly("me.clip:placeholderapi:2.12.2")

    compileOnly("net.kyori:adventure-text-minimessage:4.26.1")
    compileOnly("org.jetbrains:annotations:26.1.0")
}

tasks {
    shadowJar {
        archiveClassifier.set("")
        mergeServiceFiles()
        isZip64 = true
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE

        relocate("com.github.retrooper.packetevents", "liric.mistaken.libs.packetevents")
        relocate("dev.triumphteam.gui", "liric.mistaken.libs.gui")
        relocate("kotlin", "liric.mistaken.libs.kotlin")
        relocate("kotlinx", "liric.mistaken.libs.kotlinx")
    }

    withType<JavaCompile> {
        options.encoding = "UTF-8"
    }

    // 🔥 LA CORRECCIÓN CLAVE: Migración de kotlinOptions a compilerOptions
    withType<KotlinCompile>().configureEach {
        compilerOptions {
            // Se usa el enum JvmTarget en lugar de Strings
            jvmTarget.set(JvmTarget.JVM_21)

            // Opcional: Activar optimizaciones de K2
            freeCompilerArgs.add("-Xjsr305=strict")
        }
    }

    processResources {
        val props = mapOf("version" to version)
        inputs.properties(props)
        filteringCharset = "UTF-8"
        filesMatching("paper-plugin.yml") {
            expand(props)
        }
    }

    build {
        // Asegura que siempre se genere el ShadowJar al compilar
        dependsOn(shadowJar)
    }
}
