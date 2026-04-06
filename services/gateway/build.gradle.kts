plugins {
    id("asoview.spring-boot-conventions")
}

dependencies {
    implementation(platform(libs.spring.cloud.bom))
    implementation("org.springframework.cloud:spring-cloud-starter-gateway")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation(libs.firebase.admin)
    // Required by Spring Cloud Gateway's gRPC filter auto-configuration
    implementation(platform(libs.grpc.bom))
    runtimeOnly("io.grpc:grpc-netty")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
}
