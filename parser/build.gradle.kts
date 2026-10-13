plugins {
    id("reader.android-library")
}

android {
    namespace = "dev.reader.parser"
}

dependencies {
    implementation(projects.core)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.commonmark)
    implementation(libs.jsoup)
    implementation(libs.pdfbox.android)
    implementation(libs.junrar)

    testImplementation(libs.kotlinx.coroutines.test)
}
