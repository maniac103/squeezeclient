import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/*
 * This file is part of Squeeze Client, an Android client for the LMS music server.
 * Copyright (c) 2024 Danny Baumann
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "de.maniac103.squeezeclient.wearapi"
    compileSdk = 36

    defaultConfig {
        minSdk = 26

        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
            freeCompilerArgs = listOf(
                "-Xstring-concat=inline"
            )
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    api(libs.google.play.services.wearable)
    api(libs.kotlinx.coroutines.play.services)
}

configurations.all {
    // Pulled in outdated version via
    // com.google.android.gms:play-services-wearable
    // -> com.google.android.gms:play-services-base:18.0.1
    // -> com.google.android.gms:play-services-basement:18.0.0
    // -> androidx.fragment:fragment:1.0.0
    // Gradle handles that properly, but Android Studio gets confused and uses the old version,
    // so exclude that library here since we don't need it ourselves anyway
    exclude(group = "androidx.fragment")
}
