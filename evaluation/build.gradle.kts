plugins {
    application
    kotlin("jvm")
}

application {
    mainClass.set("org.htwk.pacing.evaluation.MainKt")
}

dependencies {
    implementation(project(":app"))
}
