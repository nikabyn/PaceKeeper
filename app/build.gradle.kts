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
        html.required.set(true)
    }

    classDirectories.setFrom(
        files(
            fileTree("${layout.buildDirectory.get()}/intermediates/javac/debug").excludeDefaults(),
            fileTree("${layout.buildDirectory.get()}/tmp/kotlin-classes/debug").excludeDefaults()
        )
    )

    sourceDirectories.setFrom(files(coverageSourceDirs))
    executionData.setFrom(file("${layout.buildDirectory.get()}/outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec"))
}

tasks.register<JacocoReport>("jacocoDebugAndroidTestReport") {
    dependsOn("connectedDebugAndroidTest")

    group = "Reporting"
    description = "Generates JaCoCo report for Debug Instrumentation Tests"

    reports {
        xml.required.set(true)
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
        fileTree(
            layout.buildDirectory.dir("outputs/code_coverage/debugAndroidTest/connected")
                .get().asFile
        ) {
            include("**/*.ec")
        }
    )
}


