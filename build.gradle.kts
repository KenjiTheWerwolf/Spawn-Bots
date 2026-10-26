plugins {
    java
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.23"
}

group = "dev.spawnbots"
version = "2.2.0"
description = "Controllable fake players for Paper 26.3"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

dependencies {
    paperweight.paperDevBundle("26.3.build.222-stable")
}

tasks.compileJava {
    options.encoding = "UTF-8"
    options.release.set(25)
}

tasks.processResources {
    filteringCharset = "UTF-8"
}

tasks.jar {
    archiveBaseName.set("SpawnBots-26.3")
}
