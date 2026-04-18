plugins {
    id("asoview.spring-boot-conventions")
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // BigQuery (popularity score sync from analytics mart) + Vertex AI Search (Discovery Engine).
    // Order matters: spring-cloud-gcp-bom is applied LAST so its newer google-cloud-bigquery
    // version wins over the libraries-bom snapshot. libraries-bom still provides the
    // google-cloud-discoveryengine version (not present in spring-cloud-gcp-bom).
    implementation(platform(libs.google.cloud.libraries.bom))
    implementation(platform(libs.spring.cloud.gcp.bom))
    implementation("com.google.cloud:google-cloud-bigquery")
    implementation(libs.google.cloud.discoveryengine)
    // Auto-reindex path: search-service subscribes to product-index-events so
    // commerce-core seed updates flow through without a manual runbook.
    implementation(libs.spring.cloud.gcp.starter.pubsub)
    // Spring 7 RestClient still uses fasterxml jackson 2 converters even though
    // Boot 4 ships tools.jackson 3 by default; pull jackson 2 explicitly so the
    // default HTTP message converters don't NoClassDefFoundError.
    implementation("com.fasterxml.jackson.core:jackson-databind:2.21.2")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
}
