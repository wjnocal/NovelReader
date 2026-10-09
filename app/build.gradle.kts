plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.wjnocal.novelreader"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.wjnocal.novelreader"
        minSdk = 24
        targetSdk = 36
        versionCode = 3300
        versionName = "3.300"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.coordinatorlayout)
    implementation(libs.constraintlayout)
    implementation(libs.drawerlayout)
    implementation(libs.recyclerview)
    implementation(libs.material)
    implementation(libs.room.runtime)
    annotationProcessor(libs.room.compiler)
    implementation(libs.jsoup)
    implementation(libs.okhttp)
    testImplementation(libs.junit)
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.json:json:20240303")
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}

tasks.withType<Test>().configureEach {
    systemProperty("novelreader.liveProbe", providers.gradleProperty("liveProbe").orElse("false").get())
    systemProperty("novelreader.probeSources", providers.gradleProperty("probeSources").orElse("shuhaige,biquge365,ranwen8,laoyaoxs").get())
}
