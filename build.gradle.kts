plugins {
    id("java-library")
    id("maven-publish")
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly(libs.paper)

    testImplementation(libs.paper)
    testImplementation(libs.junit)
    testRuntimeOnly(libs.junit.launcher)
}

group = "top.vulpine"
version = "0.2.0"
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
