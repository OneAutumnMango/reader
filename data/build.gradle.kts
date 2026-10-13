plugins {
    id("reader.android-library")
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "dev.reader.data"
}

dependencies {
    implementation(projects.core)
    implementation(projects.parser)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.datastore.core.okio)
    implementation(libs.okio)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.androidx.documentfile)
    ksp(libs.room.compiler)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okio.fakefilesystem)
    testImplementation(libs.turbine)
    testImplementation(libs.room.testing)
}

kotlin {
    compilerOptions {
        optIn.add("kotlinx.coroutines.ExperimentalCoroutinesApi")
    }
}
