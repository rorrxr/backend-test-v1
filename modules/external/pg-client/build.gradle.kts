plugins {
    kotlin("jvm")
}
tasks.jar {
    enabled = true
}

tasks.bootJar {
    enabled = false
}

dependencies {
    implementation(projects.modules.application)
    implementation(projects.modules.domain)
    implementation(libs.spring.boot.starter.web)
    implementation(kotlin("stdlib-jdk8"))
}
repositories {
    mavenCentral()
}
kotlin {
    jvmToolchain(8)
}