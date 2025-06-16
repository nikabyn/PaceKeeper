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

val coverageSourceDirs = listOf("src/main/java", "src/main/kotlin")

val fileFilter = listOf(
    "**/R.class",
    "**/R$*.class",
    "**/BuildConfig.*",
    "**/Manifest*.*",
    "**/*Test*.*",
    "android/**/*.*"
)

tasks.register<JacocoReport>("jacocoUnitTestReport") {
    dependsOn("testDebugUnitTest")

    group = "verification"
    description = "Generates JaCoCo coverage report for unit tests."

    val javaClasses =
        fileTree("${project.layout.buildDirectory}/intermediates/javac/debug").excludeFilter()
    val kotlinClasses =
        fileTree("${project.layout.buildDirectory}/tmp/kotlin-classes/debug").excludeFilter()

    classDirectories.setFrom(files(javaClasses, kotlinClasses))
    sourceDirectories.setFrom(files(coverageSourceDirs))
    executionData.setFrom(file("${project.layout.buildDirectory}/jacoco/testDebugUnitTest.exec"))

    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}


tasks.register<JacocoReport>("jacocoAndroidTestReport") {
    dependsOn("connectedDebugAndroidTest")

    group = "verification"
    description = "Generates JaCoCo coverage report for Android instrumentation tests."

    val javaClasses =
        fileTree("${project.layout.buildDirectory}/intermediates/javac/debug").excludeFilter()
    val kotlinClasses =
        fileTree("${project.layout.buildDirectory}/tmp/kotlin-classes/debug").excludeFilter()

    classDirectories.setFrom(files(javaClasses, kotlinClasses))
    sourceDirectories.setFrom(files(coverageSourceDirs))
    executionData.setFrom(fileTree("${project.layout.buildDirectory}/outputs/code-coverage/connected") {
        include("**/*.ec")
    })

    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}


tasks.register<JacocoReport>("jacocoMergedReport") {
    dependsOn("testDebugUnitTest", "connectedDebugAndroidTest")

    group = "verification"
    description = "Generates merged JaCoCo report from unit and instrumentation tests."

    val javaClasses =
        fileTree("${project.layout.buildDirectory}/intermediates/javac/debug").excludeFilter()
    val kotlinClasses =
        fileTree("${project.layout.buildDirectory}/tmp/kotlin-classes/debug").excludeFilter()

    classDirectories.setFrom(files(javaClasses, kotlinClasses))
    sourceDirectories.setFrom(files(coverageSourceDirs))
    executionData.setFrom(
        files(
            "${project.layout.buildDirectory}/jacoco/testDebugUnitTest.exec",
            fileTree("${project.layout.buildDirectory}/outputs/code-coverage/connected") {
                include("**/*.ec")
            }
        )
    )

    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

private fun ConfigurableFileTree.excludeFilter() {
    exclude(fileFilter)
}
