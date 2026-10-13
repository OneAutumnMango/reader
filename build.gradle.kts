plugins {
    alias(libs.plugins.kover)
}

dependencies {
    kover(projects.core)
    kover(projects.parser)
    kover(projects.data)
}
