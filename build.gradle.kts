import org.gradle.api.tasks.bundling.Jar
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.9.10"
    id("org.jetbrains.intellij") version "1.16.1"
}

group = "com.example"
version = "0.4.0"

repositories {
    mavenCentral()
}

intellij {
    version.set(property("intellij.version") as String)
    type.set(property("intellij.type") as String)
    plugins.set(listOf("java"))
}

dependencies {
    implementation(kotlin("stdlib"))
}

tasks {
    withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
    }

    jar {
        archiveBaseName.set("indentation-checker")
        manifest {
            attributes(
                "Implementation-Title" to "Indentation Checker",
                "Implementation-Version" to version,
            )
        }
    }

    val pluginJar by tasks.registering(Jar::class) {
        archiveBaseName.set("indentation-checker-plugin")
        from(sourceSets.main.get().output)
        manifest {
            attributes(
                "Implementation-Title" to "Indentation Checker Plugin",
                "Implementation-Version" to version,
            )
        }
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }

    val packageJar by registering {
        dependsOn(pluginJar)
        group = "Build"
        description = "Builds the Jar package for the JetBrains plugin."
    }

    build {
        dependsOn(pluginJar)
    }

    patchPluginXml {
        changeNotes.set("Auto-checks indentation for all languages and highlights inconsistent leading whitespace.")
    }
}
