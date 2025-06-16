plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("org.sonarqube")
    jacoco
}

android {
    namespace = "org.htwk.pacing"
    compileSdk = 35

    defaultConfig {
        applicationId = "org.htwk.pacing"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"
            )
        }
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
        }
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
    implementation(libs.kotlinx.datetime)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}


tasks.register<JacocoReport>("jacocoUnitTestReport") {
    group = "Reporting"
    description = "Generates JaCoCo code coverage report for debug unit tests."

    //dependsOn("testDebugUnitTest")

    executionData.from(
        fileTree(layout.buildDirectory) {
            include("jacoco/testDebugUnitTestCoverage.exec")
            include("jacoco/testDebugUnitTest.exec")
        }
    )

    classDirectories.from(
        fileTree("${layout.buildDirectory}/tmp/kotlin-classes/debug")
    )

    sourceDirectories.from(
        files(
            "$projectDir/src/main/java",
            "$projectDir/src/main/kotlin"
        )
    )

    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.register<JacocoReport>("jacocoAndroidTestReport") {
    group = "Reporting"
    description = "Generates JaCoCo code coverage report for debug instrumented tests."

    //dependsOn("createDebugCoverageReport")

    executionData.from(
        fileTree(layout.buildDirectory) {
            include("outputs/coverage/debugAndroidTest/connected/**/*.ec")
        }
    )

    sourceDirectories.from(
        files(
            "$projectDir/src/main/java",
            "$projectDir/src/main/kotlin"
        )
    )

    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.register<JacocoReport>("jacocoMergedReport") {
    group = "Reporting"
    description =
        "Generates a merged JaCoCo code coverage report for debug unit and instrumented tests."

    //dependsOn("testDebugUnitTest", "createDebugCoverageReport")

    executionData.from(
        fileTree(layout.buildDirectory) {
            include(
                "jacoco/testDebugUnitTest.exec",
                "outputs/coverage/debugAndroidTest/connected/**/*.ec"
            )
        }
    )

    sourceDirectories.from(
        files(
            "$projectDir/src/main/java",
            "$projectDir/src/main/kotlin"
        )
    )

    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}