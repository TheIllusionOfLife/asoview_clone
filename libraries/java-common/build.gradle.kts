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
    // compileOnly: JsonAccessDeniedHandler / JsonAuthenticationEntryPoint
    // reference spring-security types, but only services that actually
    // enable Spring Security (i.e. register a SecurityFilterChain) need
    // the runtime classes. Those services pull spring-boot-starter-security
    // transitively, so the classes are available at runtime where they
    // matter; compile-time visibility is enough here.
    compileOnly("org.springframework.security:spring-security-web")
    compileOnly("org.springframework.security:spring-security-core")
    compileOnly("jakarta.servlet:jakarta.servlet-api")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
