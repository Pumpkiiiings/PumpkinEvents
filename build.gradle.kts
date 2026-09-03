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
    kotlin("jvm") version "2.3.20"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("maven-publish")
}

group = "pumpkin.eventos"
version = "3.3.9"

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
    maven("https://repo.md-5.net/content/groups/public/")

    maven("https://maven.maxhenkel.de/repository/public")
    flatDir {
        dirs("libs")
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    compileOnly("de.maxhenkel.voicechat:voicechat-api:2.5.0")
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
    compileOnly("com.github.Lodestones:Chain-API:1.0.2")

    compileOnly("net.kyori:adventure-text-minimessage:4.26.1")
    compileOnly("org.jetbrains:annotations:26.1.0")
    compileOnly(files("libs/LibsDisguises.jar"))
    // Contrato opcional para mundos desechables basados en esquemáticos.
    // El JAR no se empaqueta: ArenaAPI debe instalarse como plugin separado.
    compileOnly(files("libs/ArenaAPI.jar"))

    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks {
    shadowJar {
        archiveClassifier.set("")
        mergeServiceFiles()
        isZip64 = true
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE

        relocate("com.github.retrooper.packetevents", "pumpkin.eventos.libs.packetevents")
        relocate("dev.triumphteam.gui", "pumpkin.eventos.libs.gui")
        relocate("kotlin", "pumpkin.eventos.libs.kotlin")
        relocate("kotlinx", "pumpkin.eventos.libs.kotlinx")
    }

    withType<JavaCompile> {
        options.encoding = "UTF-8"
    }

    withType<KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)

            // Opcional: Activar optimizaciones de K2
            freeCompilerArgs.add("-Xjsr305=strict")
        }
    }

    processResources {
        val props = mapOf("version" to project.version.toString())
        inputs.properties(props)
        filteringCharset = "UTF-8"
        filesMatching(listOf("paper-plugin.yml", "plugin.yml")) {
            expand(props)
        }
    }

    build {
        dependsOn(shadowJar)
    }

    test {
        useJUnitPlatform()
    }
}
