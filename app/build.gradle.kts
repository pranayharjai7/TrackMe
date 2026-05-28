import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
}

android {
    namespace = "com.trackme"
    compileSdk = 36

    // Secure property loading helper
    val props = Properties()
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { props.load(it) }
    }

    fun getSecuredProperty(key: String, envKey: String): String {
        return System.getenv(envKey) ?: props.getProperty(key) ?: ""
    }

    signingConfigs {
        create("release") {
            val keystorePath = getSecuredProperty("RELEASE_STORE_FILE", "RELEASE_STORE_FILE")
            if (keystorePath.isNotEmpty()) {
                storeFile = file(keystorePath)
                storePassword = getSecuredProperty("RELEASE_STORE_PASSWORD", "RELEASE_STORE_PASSWORD")
                keyAlias = getSecuredProperty("RELEASE_KEY_ALIAS", "RELEASE_KEY_ALIAS")
                keyPassword = getSecuredProperty("RELEASE_KEY_PASSWORD", "RELEASE_KEY_PASSWORD")
            }
        }
    }

    defaultConfig {
        applicationId = "com.trackme"
        minSdk = 26
        targetSdk = 35

        val buildVersionCode = (System.getenv("BUILD_NUMBER") ?: props.getProperty("VERSION_CODE") ?: "1").toInt()
        val buildVersionName = System.getenv("VERSION_NAME") ?: props.getProperty("VERSION_NAME") ?: "1.0.0"
        versionCode = buildVersionCode
        versionName = buildVersionName

        buildConfigField("String", "SUPABASE_URL",          "\"${getSecuredProperty("SUPABASE_URL", "SUPABASE_URL")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY",     "\"${getSecuredProperty("SUPABASE_ANON_KEY", "SUPABASE_ANON_KEY")}\"")
        buildConfigField("String", "EXERCISE_DB_API_KEY",   "\"${getSecuredProperty("EXERCISE_DB_API_KEY", "EXERCISE_DB_API_KEY")}\"")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID",  "\"${getSecuredProperty("GOOGLE_WEB_CLIENT_ID", "GOOGLE_WEB_CLIENT_ID")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val keystorePath = getSecuredProperty("RELEASE_STORE_FILE", "RELEASE_STORE_FILE")
            if (keystorePath.isNotEmpty() && file(keystorePath).exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    implementation("com.google.firebase:firebase-analytics:23.2.0")
    implementation("com.google.firebase:firebase-crashlytics:20.0.6")
    implementation("com.google.firebase:firebase-messaging:24.1.0")
    wearApp(project(":wear"))
    implementation(project(":wear-bridge"))

    val composeBom = platform("androidx.compose:compose-bom:2024.10.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-process:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.4")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.58")
    ksp("com.google.dagger:hilt-android-compiler:2.58")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    implementation("androidx.hilt:hilt-work:1.2.0")
    ksp("androidx.hilt:hilt-compiler:1.2.0")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Supabase
    implementation(platform("io.github.jan-tennert.supabase:bom:3.0.0"))
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.github.jan-tennert.supabase:auth-kt")
    implementation("io.ktor:ktor-client-android:3.0.3")
    implementation("io.ktor:ktor-client-core:3.0.3")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Health Connect
    implementation("androidx.health.connect:connect-client:1.1.0-rc01")

    // Coil (images + GIFs)
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-gif:2.7.0")

    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // Wear OS Data Layer
    implementation("com.google.android.gms:play-services-wearable:20.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Charts (Vico)
    implementation("com.patrykandpatrick.vico:compose-m3:1.15.0")

    // Security
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Credential Manager (Google Sign-In)
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    // Drag-to-reorder
    implementation("sh.calvin.reorderable:reorderable:2.4.3")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("io.mockk:mockk:1.13.13")
    testImplementation("io.ktor:ktor-client-mock:3.0.3")
    testImplementation("app.cash.turbine:turbine:1.2.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
