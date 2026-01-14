plugins {
    // Apply the Kotlin JVM plugin to add support for Kotlin.
    id("org.jetbrains.kotlin.jvm")

    // Apply the application plugin to build a runnable JVM application.
    id("application")
}

dependencies {
    implementation(project(":predictor"))
}

application {
    //main class that will be executed when the task is run.
    mainClass.set("org.htwk.pacing.evaluator.MainKt")
}

