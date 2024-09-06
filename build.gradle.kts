// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    dependencies {
        classpath ("com.android.tools.build:gradle:8.6.0")
        classpath ("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.22")

        // NOTE: Do not place your application dependencies here; they belong
        // in the individual module build.gradle files
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
        maven { setUrl("https://jitpack.io") }
    }
}

tasks.register("clean",Delete::class.java) {
    delete(rootProject.getLayout().buildDirectory)
}
