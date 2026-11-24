rootProject.name = "gosu-mutation-intellij"

plugins {
    // Downloads a JDK 21 when none is installed.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

// The plugin bundles the command line tool built by the main project.
includeBuild("..") {
    // Fixed name, so the build works whatever the folder of the clone is called.
    name = "gosu-mutation-main"
}
