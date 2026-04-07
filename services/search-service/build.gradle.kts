plugins {
    id("asoview.spring-boot-conventions")
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation(libs.opensearch.rest.high.level.client)

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(platform(libs.testcontainers.bom))
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.opensearch.testcontainers)
}
