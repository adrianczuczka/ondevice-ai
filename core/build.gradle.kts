import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.mavenPublish)
}

group = "com.adrianczuczka.ondeviceai"
version = "0.1.0"

kotlin {
    explicitApi()
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    android {
        namespace = "com.adrianczuczka.ondeviceai.core"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }

        withHostTest {}
    }

    iosArm64()
    iosSimulatorArm64()
    iosX64()

    listOf(iosArm64(), iosSimulatorArm64(), iosX64()).forEach { target ->
        target.binaries.framework {
            baseName = "OnDeviceAiCore"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        androidMain.dependencies {
            implementation(libs.androidx.startup)
            implementation(libs.mlkit.genai.prompt)
        }
    }
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
    coordinates("com.adrianczuczka.ondeviceai", "core", version.toString())

    pom {
        name.set("ondevice-ai")
        description.set(
            "One Kotlin Multiplatform API over the system on-device AI models – " +
                "Gemini Nano (ML Kit / AICore) on Android and Apple Foundation Models on iOS."
        )
        url.set("https://github.com/adrianczuczka/ondevice-ai")
        inceptionYear.set("2026")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        developers {
            developer {
                id.set("adrianczuczka")
                name.set("Adrian Czuczka")
                url.set("https://adrianczuczka.com")
            }
        }
        scm {
            url.set("https://github.com/adrianczuczka/ondevice-ai")
            connection.set("scm:git:git://github.com/adrianczuczka/ondevice-ai.git")
            developerConnection.set("scm:git:ssh://git@github.com/adrianczuczka/ondevice-ai.git")
        }
    }
}
