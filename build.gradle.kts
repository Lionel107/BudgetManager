import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "1.9.23"
    kotlin("plugin.serialization") version "1.9.23"
    id("org.jetbrains.compose") version "1.6.1"
}

group = "com.budgetmanager"
version = "2.0.0"

repositories {
    google()
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

dependencies {
    // Compose Desktop
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)

    // Exposed ORM + SQLite
    implementation("org.jetbrains.exposed:exposed-core:0.46.0")
    implementation("org.jetbrains.exposed:exposed-dao:0.46.0")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.46.0")
    implementation("org.jetbrains.exposed:exposed-java-time:0.46.0")
    implementation("org.xerial:sqlite-jdbc:3.44.1.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.0")

    // Koin DI
    implementation("io.insert-koin:koin-core:3.5.3")

    // Supabase (backend synchro : Postgrest + Auth) — Phase 2
    // NB : version 2.x (Kotlin 1.9.23) → module auth = "gotrue-kt" (renommé "auth-kt" en 3.0.0/Kotlin 2.0)
    implementation(platform("io.github.jan-tennert.supabase:bom:2.5.4"))
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.github.jan-tennert.supabase:gotrue-kt")
    // Moteur HTTP Ktor requis par supabase-kt
    implementation("io.ktor:ktor-client-cio:2.3.12")
    // Sérialisation JSON (DTO <-> API REST Supabase)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // Logging
    implementation("org.slf4j:slf4j-simple:2.0.9")
}

compose.desktop {
    application {
        mainClass = "com.budgetmanager.MainKt"

        jvmArgs += listOf(
            "-Dfile.encoding=UTF-8"
        )

        nativeDistributions {
            targetFormats(TargetFormat.Exe, TargetFormat.Msi)
            packageName = "BudgetManager"
            packageVersion = "2.0.0"
            description = "Application de gestion budgetaire"
            vendor = "BudgetManager"

            // Include ALL required JVM modules for SQLite/Exposed/Prefs
            modules(
                "java.base",
                "java.desktop",
                "java.logging",
                "java.naming",
                "java.sql",
                "java.management",
                "java.xml",
                "java.prefs",
                "java.datatransfer",
                "java.instrument",
                "jdk.unsupported",
                "jdk.crypto.ec"
            )

            windows {
                menuGroup = "BudgetManager"
                dirChooser = true
                perUserInstall = true
                upgradeUuid = "b3f4c1a2-7e89-4d56-a123-456789abcdef"
                // iconFile.set(project.file("src/main/resources/icon.ico"))
            }
        }

        buildTypes.release {
            proguard {
                isEnabled.set(false)
            }
        }
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}
