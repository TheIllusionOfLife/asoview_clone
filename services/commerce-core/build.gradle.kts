plugins {
    id("asoview.spring-boot-conventions")
}

// Override Spring Boot managed Testcontainers version for Docker Desktop 29.x compatibility.
// TC 1.21.4 fixes hardcoded docker-java API version 1.32 -> 1.44.
extra["testcontainers.version"] = "1.21.4"

// scanner-app-api scope: check-in and ticket validation endpoints live here.
// Extract to a separate service if operational needs require it.

dependencies {
    implementation(project(":libraries:java-common"))
    implementation(project(":libraries:proto-contracts"))

    // Web
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // Security
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation(libs.firebase.admin)

    // Cloud SQL / JPA
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    runtimeOnly(libs.postgresql)
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation(libs.flyway.database.postgresql)

    // Cloud Spanner
    implementation(platform(libs.spring.cloud.gcp.bom))
    implementation(libs.spring.cloud.gcp.starter.data.spanner)

    // Redis
    implementation("org.springframework.boot:spring-boot-starter-data-redis")

    // Spring Retry (for @Retryable on transactional event listeners)
    implementation(libs.spring.retry)
    implementation("org.springframework:spring-aspects")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation(platform(libs.testcontainers.bom))
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.gcloud)

}
