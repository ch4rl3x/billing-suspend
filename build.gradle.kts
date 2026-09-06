plugins {
    // AGP: the conventions compile against it but leave the version to this build.
    alias(conventions.plugins.android.library) apply false

    alias(conventions.plugins.convention.publishing.repository)
    alias(conventions.plugins.convention.android.library) apply false
    alias(conventions.plugins.convention.publishing) apply false
}

allprojects {
    group = "de.charlex.billing"
}
