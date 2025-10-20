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
    implementation(projects.modules.domain)
    // Only need Spring annotations (@Service) for this module
    implementation("org.springframework:spring-context")
    implementation(kotlin("stdlib-jdk8"))
}
repositories {
    mavenCentral()
}
kotlin {
    jvmToolchain(8)
}