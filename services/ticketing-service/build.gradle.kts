plugins {
    id("asoview.spring-boot-conventions")
}

// scanner-app-api scope: check-in and ticket validation endpoints live here.
// Extract to a separate service if operational needs require it.

dependencies {
    implementation(project(":libraries:java-common"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
