import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.jetbrains.kotlin.jvm") version "2.4.21"
    id("org.jetbrains.intellij.platform") version "2.19.0"
}

group = "chopper"
version = "0.1.0"

repositories {
    mavenCentral()

    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        // ponytail: builds against the locally installed PhpStorm; switch to phpstorm("2026.1") for CI.
        local(providers.gradleProperty("phpstormPath"))
        bundledPlugin("com.jetbrains.php")
        testFramework(TestFrameworkType.Platform)
    }

    testImplementation("junit:junit:4.13.2")
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            // 2021.3 introduced the platform list split/join API Chopper extends.
            sinceBuild = "213"
            untilBuild = provider { null }
        }
    }

    // Chopper has no settings page to index, and the task blocks auto-reload while runIde is open.
    buildSearchableOptions = false

    pluginVerification {
        ides {
            create(IntelliJPlatformType.PhpStorm, "2021.3.3")
            local(providers.gradleProperty("phpstormPath"))
        }
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    // Compiles with PhpStorm's bundled JBR 25; bytecode targets Java 17, which IDEs before 2024.2 run on.
    jvmToolchain(25)
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions.jvmTarget = JvmTarget.JVM_17
}
