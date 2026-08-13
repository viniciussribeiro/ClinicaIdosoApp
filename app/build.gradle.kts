plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "br.com.carinhosos"
    compileSdk = 36

    defaultConfig {
        applicationId = "br.com.carinhosos"
        minSdk = 24
        targetSdk = 36
        versionCode = 6
        versionName = "2.0.0"

        // Configuração pública do app Firebase Web já existente no projeto.
        // O app usa um FirebaseApp nomeado e não exige alterações no back-end.
        buildConfigField("String", "FIREBASE_API_KEY", "\"AIzaSyCnsSsA36Xs7lRhFBCCN45Odu3FYsLnK0s\"")
        buildConfigField("String", "FIREBASE_APP_ID", "\"1:1006538486156:web:1a5ae87388994d50fba6a9\"")
        buildConfigField("String", "FIREBASE_PROJECT_ID", "\"extensao-unip\"")
        buildConfigField("String", "FIREBASE_STORAGE_BUCKET", "\"extensao-unip.firebasestorage.app\"")
        buildConfigField("String", "FIREBASE_SENDER_ID", "\"1006538486156\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation(platform("com.google.firebase:firebase-bom:34.17.0"))
    implementation("androidx.activity:activity-compose:1.12.4")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    // 3.3.0 é compatível com o compilador Kotlin 2.2 usado pelo projeto.
    implementation("io.coil-kt.coil3:coil-compose:3.3.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.3.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
