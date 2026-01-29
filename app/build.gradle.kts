plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
    alias(libs.plugins.kotlin.serialization)
    id("org.sonarqube")
    jacoco
}

android {
    namespace = "org.htwk.pacing"

    defaultConfig {
        applicationId = "org.htwk.pacing"
        minSdk = 28
        targetSdk = 36
        compileSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.extensions.configure(JacocoTaskExtension::class) {
                    isIncludeNoLocationClasses = true
                    excludes = listOf("jdk.internal.*")
                }
            }
        }
    }

    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("ANDROID_KEYSTORE")
            val storePass = System.getenv("KEYSTORE_PASSWORD")
            val alias = System.getenv("KEY_ALIAS")
            val keyPass = System.getenv("KEY_PASSWORD")

            if (keystorePath != null && storePass != null && alias != null && keyPass != null) {
                println("Found keystore. Configuring Signing....")
                storeFile = file(keystorePath)
                storePassword = storePass
                keyAlias = alias
                keyPassword = keyPass
            } else {
                println("No keystore found.")
            }
        }
    }

    buildTypes {
        debug {
            enableUnitTestCoverage = true
            enableAndroidTestCoverage = true
        }
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"
            )
            if (signingConfigs.getByName("release").storeFile?.exists() == true) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    room {
        schemaDirectory("$projectDir/schemas")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.graphics.shapes)
    implementation(libs.google.guava)
    implementation("androidx.constraintlayout:constraintlayout-compose:1.1.1")
    implementation("androidx.paging:paging-runtime:3.2.0")
    implementation("androidx.paging:paging-compose:1.0.0")

    // Koin (dependency injection)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    // Room (database)
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.room.ktx)
    testImplementation(libs.androidx.room.testing)

    // Datetime
    implementation(libs.kotlinx.datetime)

    // Health Connect
    implementation(libs.androidx.health.connect)

    // Http Client
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.client.logging)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    // CSV Parsing
    implementation(libs.kotlin.csv.jvm)

    // Splash Screen
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.material)

    // Multik for Matrix Math
    implementation(libs.multik.kotlin)
    testImplementation(libs.kotlinx.serialization.json)
}

val exclusions = listOf(
    "**/R.class",
    "**/R$*.class",
    "**/BuildConfig.*",
    "**/Manifest*.*",
    "**/*Test*.*",
    "android/**/*.*"
)

fun ConfigurableFileTree.excludeDefaults(): ConfigurableFileTree {
    exclude(exclusions)
    return this
}

val coverageSourceDirs = listOf("src/main/java", "src/main/kotlin")
tasks.register<JacocoReport>("jacocoDebugUnitTestReport") {
    dependsOn("testDebugUnitTest")

    group = "Reporting"
    description = "Generates JaCoCo report for Debug Unit Tests"

    reports {
        xml.required.set(true)
        xml.outputLocation.set(
            file("${layout.buildDirectory.get()}/reports/jacoco/jacocoDebugUnitTestReport/jacocoDebugUnitTestReport.xml")
        )
        html.required.set(true)
    }

    classDirectories.setFrom(
        files(
            fileTree("${layout.buildDirectory.get()}/intermediates/javac/debug").excludeDefaults(),
            fileTree("${layout.buildDirectory.get()}/tmp/kotlin-classes/debug").excludeDefaults()
        )
    )

    sourceDirectories.setFrom(files(coverageSourceDirs))
    executionData.setFrom(
        file("${layout.buildDirectory.get()}/outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec")
    )
}

tasks.register<JacocoReport>("jacocoDebugAndroidTestReport") {
    dependsOn("connectedDebugAndroidTest")

    group = "Reporting"
    description = "Generates JaCoCo report for Debug Instrumentation Tests"

    reports {
        xml.required.set(true)
        xml.outputLocation.set(
            file("${layout.buildDirectory.get()}/reports/jacoco/jacocoDebugAndroidTestReport/jacocoDebugAndroidTestReport.xml")
        )
        html.required.set(true)
    }

    classDirectories.setFrom(
        files(
            fileTree("${layout.buildDirectory.get()}/intermediates/javac/debug").excludeDefaults(),
            fileTree("${layout.buildDirectory.get()}/tmp/kotlin-classes/debug").excludeDefaults()
        )
    )

    sourceDirectories.setFrom(files(coverageSourceDirs))

    executionData.setFrom(
        project.fileTree(
            layout.buildDirectory.dir("outputs/code_coverage/debugAndroidTest/connected")
                .get().asFile
        ) {
            include("**/*.ec")
        })
}

