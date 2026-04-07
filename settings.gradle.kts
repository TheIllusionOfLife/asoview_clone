pluginManagement {
    includeBuild("build-logic")
}

rootProject.name = "asoview-clone"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

// Shared libraries
include("libraries:java-common")
include("libraries:proto-contracts")

// Backend services
include("services:gateway")
include("services:commerce-core")
include("services:ticketing-service")
include("services:reservation-service")
include("services:ads-service")
include("services:analytics-ingest")
include("services:search-service")
