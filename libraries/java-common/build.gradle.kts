plugins {
    id("asoview.java-library-conventions")
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    compileOnly("jakarta.persistence:jakarta.persistence-api")
    compileOnly("org.springframework.data:spring-data-commons")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
