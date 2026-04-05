plugins {
    id("asoview.java-library-conventions")
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    compileOnly("jakarta.persistence:jakarta.persistence-api")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
