plugins {
    id("asoview.java-library-conventions")
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    // api: every consumer of the shared GlobalExceptionHandler references
    // jakarta.validation.ConstraintViolationException at bean-introspection
    // time (Spring walks all @ExceptionHandler methods when creating the
    // advice bean), so the class MUST be on the runtime classpath of every
    // service that pulls java-common. compileOnly is not enough; a service
    // without it starts up with NoClassDefFoundError before any controller
    // runs. The validation-api jar is a few KB of interfaces — no harm.
    api("jakarta.validation:jakarta.validation-api")
    compileOnly("jakarta.persistence:jakarta.persistence-api")
    compileOnly("org.springframework.data:spring-data-commons")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
