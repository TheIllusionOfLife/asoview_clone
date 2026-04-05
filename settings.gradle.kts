pluginManagement {
    if (file("build-logic").exists()) {
        includeBuild("build-logic")
    }
}

rootProject.name = "asoview-clone"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

// Shared libraries
include("libraries:java-common")

// Backend services
include("services:gateway")
include("services:commerce-core")
include("services:ticketing-service")
include("services:reservation-service")
include("services:ads-service")
include("services:analytics-ingest")
