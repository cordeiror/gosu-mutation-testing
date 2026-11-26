import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import java.util.Properties

plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.20.1"
}

group = "io.github.gosumutation"
// Same version as the command line tool: projectVersion in the main build's gradle.properties.
version = Properties().apply {
    layout.projectDirectory.file("../gradle.properties").asFile.inputStream().use { load(it) }
}.getProperty("projectVersion")

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    intellijPlatform {
        intellijIdea("2025.3.4")
        testFramework(TestFrameworkType.Platform)
    }
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    // IDE tests (BasePlatformTestCase) are JUnit 4 tests, run through the Vintage engine.
    testImplementation("junit:junit:4.13.2")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine")
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "253"
            untilBuild = "253.*"
        }
    }
    pluginVerification {
        ides {
            create("IU", "2025.3.4")
        }
    }
}

// The command line tool (gosu-mutation) is bundled in <plugin>/cli/lib, so nothing else must be installed.
val cliInstall = gradle.includedBuild("gosu-mutation-main").task(":mutation-cli:installDist")
val cliLib = layout.projectDirectory.dir("../mutation-cli/build/install/gosu-mutation/lib")

tasks {
    processResources {
        // The stubbing guide is embedded in the AI request the plugin builds.
        from("../docs/stubbing-guide.md") {
            into("io/gosumutation/intellij")
        }
    }

    prepareSandbox {
        dependsOn(cliInstall)
        from(cliLib) {
            into(pluginName.map { "$it/cli/lib" })
        }
    }

    test {
        useJUnitPlatform()
        dependsOn(cliInstall)
        // Lets the tests run the real bundled command line tool.
        systemProperty("gosu.mutation.cli.lib", cliLib.asFile.absolutePath)
        systemProperty("gosu.mutation.sample", layout.projectDirectory.dir("../sample-gosu-project").asFile.absolutePath)
    }
}
