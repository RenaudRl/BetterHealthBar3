plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "BetterHealthBar"

include(
    "api",
    "dist",

    "scheduler:standard",
    "scheduler:folia",

    "bedrock:geyser",
    "bedrock:floodgate",

    "modelengine:legacy",
    "modelengine:current",


    "nms:v1_21_R7",
)