plugins {
    id("java-library")
    id("maven-publish")
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly(libs.adventure.api)
    compileOnly(libs.adventure.minimessage)
    compileOnly(libs.adventure.serializer.legacy)
    compileOnly(libs.adventure.serializer.plain)
    compileOnly(libs.adventure.logger)

    testImplementation(libs.adventure.api)
    testImplementation(libs.adventure.minimessage)
    testImplementation(libs.adventure.serializer.legacy)
    testImplementation(libs.adventure.serializer.plain)
    testImplementation(libs.adventure.logger)
    testImplementation(libs.junit)
    testRuntimeOnly(libs.junit.launcher)
}

group = "top.vulpine"
version = "0.3.0"
description = "Commons"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    withSourcesJar()
}

tasks {
    test {
        useJUnitPlatform()
    }
}

publishing {
    repositories {
        maven {
            name = "vulpine"
            url = uri("https://repo.vulpine.top/repository/maven-open/")
            credentials(PasswordCredentials::class)
        }
    }

    publications {
        create<MavenPublication>("maven") {
            artifactId = "commons"
            from(components["java"])
        }
    }
}
