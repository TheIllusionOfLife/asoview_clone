plugins {
    java
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-parameters"))
}

dependencies {
    "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showCauses = true
        showStackTraces = true
    }
}
