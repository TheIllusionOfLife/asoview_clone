plugins {
    id("asoview.spring-boot-conventions")
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation(libs.opensearch.rest.high.level.client)
    // Spring 7 RestClient still uses fasterxml jackson 2 converters even though
    // Boot 4 ships tools.jackson 3 by default; pull jackson 2 explicitly so the
    // default HTTP message converters don't NoClassDefFoundError.
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation(platform(libs.testcontainers.bom))
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.opensearch.testcontainers)
}
