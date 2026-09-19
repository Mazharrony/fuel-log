import com.android.build.api.artifact.SingleArtifact

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

// Configured through the Room Gradle plugin rather than a KSP compiler argument: this
// project path contains spaces, and passing schemaLocation through compiler-option encoding
// mangles it silently.
room {
    schemaDirectory("$projectDir/schemas")
}

android {
    namespace = "com.fuelexpenselog.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.fuelexpenselog.app"
        minSdk = 26          // java.time is native from 26, so no core library desugaring
        targetSdk = 36       // Play requires 36 for new apps
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(17)
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        warningsAsErrors = false
        abortOnError = true
        // The version catalog pins deliberately - every version is proven against this
        // machine's toolchain. "A newer version is available" is noise here, and acting on
        // it without re-locking would defeat the dependency lockfile.
        disable += setOf("AndroidGradlePluginVersion", "GradleDependency")
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    packaging {
        resources.excludes += setOf(
            "META-INF/AL2.0",
            "META-INF/LGPL2.1",
            "META-INF/LICENSE*",
            "META-INF/NOTICE*",
        )
    }
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:csv"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.junit)
    testImplementation(libs.androidx.test.core.ktx)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.room.testing)
}

// ---------------------------------------------------------------------------------------
// The permission ratchet.
//
// This is a compliance control, not engineering hygiene. The Play listing will declare
// "no data collected" and an inaccurate data-safety declaration is a removable offence.
// The threat being defended against is a transitive dependency quietly merging INTERNET
// into the manifest, which no one would notice by reading source.
//
// Checked against the MERGED manifest, because that is the only artefact that reflects
// what every library contributed.
// ---------------------------------------------------------------------------------------
val allowedPermissions = setOf(
    "android.permission.POST_NOTIFICATIONS",
    "android.permission.RECEIVE_BOOT_COMPLETED",
)

abstract class VerifyPermissions : DefaultTask() {
    @get:org.gradle.api.tasks.InputFile
    abstract val mergedManifest: org.gradle.api.file.RegularFileProperty

    @get:Input
    abstract val allowed: SetProperty<String>

    @TaskAction
    fun check() {
        val text = mergedManifest.get().asFile.readText()

        val declared = Regex("""<uses-permission(?:-sdk-23)?\s[^>]*android:name="([^"]+)"""")
            .findAll(text)
            .map { it.groupValues[1] }
            .toSet()

        val unexpected = declared - allowed.get()
        if (unexpected.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("Permission ratchet FAILED.")
                    appendLine("Unexpected permission(s) in the merged manifest:")
                    unexpected.sorted().forEach { appendLine("    $it") }
                    appendLine()
                    appendLine("This app ships with no INTERNET permission. If a new dependency")
                    appendLine("added this, remove the dependency or add an explicit")
                    appendLine("tools:node=\"remove\" entry - do not widen the allowlist without")
                    appendLine("a deliberate product decision.")
                    appendLine("Allowed: ${allowed.get().sorted()}")
                },
            )
        }

        // tools:node="remove" fails SILENTLY when mistyped, so assert the removal actually
        // happened rather than trusting that the directive is present.
        if ("android.permission.INTERNET" in declared) {
            throw GradleException("INTERNET permission is present in the merged manifest.")
        }

        logger.lifecycle("Permission ratchet OK - declared: ${declared.sorted()}")
    }
}

androidComponents.onVariants { variant ->
    val verify = tasks.register<VerifyPermissions>(
        "verify${variant.name.replaceFirstChar(Char::uppercase)}Permissions",
    ) {
        group = "verification"
        description = "Fails the build if the merged manifest declares an unexpected permission."
        mergedManifest.set(variant.artifacts.get(SingleArtifact.MERGED_MANIFEST))
        allowed.set(allowedPermissions)
    }
    tasks.named("check").configure { dependsOn(verify) }
}
