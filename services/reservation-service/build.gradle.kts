plugins {
    id("asoview.spring-boot-conventions")
}

dependencies {
    implementation(project(":libraries:java-common"))
    implementation(project(":libraries:proto-contracts"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // Security
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation(libs.firebase.admin)

    // Cloud Spanner
    implementation(platform(libs.spring.cloud.gcp.bom))
    implementation(libs.spring.cloud.gcp.starter.data.spanner)

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation(platform(libs.testcontainers.bom))
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.gcloud)
}
