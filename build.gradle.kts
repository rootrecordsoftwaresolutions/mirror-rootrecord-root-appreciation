plugins {
    java
}

version = "1.7.13"

dependencies {
    compileOnly(project(":plugins:root-core"))
    compileOnly(project(":plugins:root-economy"))
}

tasks.named<Jar>("jar") {
    duplicatesStrategy = org.gradle.api.file.DuplicatesStrategy.EXCLUDE
}
