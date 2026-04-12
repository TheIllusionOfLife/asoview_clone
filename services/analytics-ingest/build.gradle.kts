plugins {
    id("asoview.spring-boot-conventions")
}

dependencies {
    implementation(project(":libraries:java-common"))
    implementation(project(":libraries:proto-contracts"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // Cloud Pub/Sub (subscribe to domain event topics)
    implementation(platform(libs.spring.cloud.gcp.bom))
    implementation(libs.spring.cloud.gcp.starter.pubsub)

    // Spring Integration (required for Pub/Sub inbound channel adapters)
    implementation("org.springframework.integration:spring-integration-core")

    // BigQuery (event ingestion)
    implementation("com.google.cloud:google-cloud-bigquery")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
