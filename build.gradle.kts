plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
}

// ---------------------------------------------------------------------------------------
// Dependency locking.
//
// This turns "minimal dependency tree" from an aspiration into a build-enforced fact. The
// threat model for this app is literally "a transitive dependency reintroduces INTERNET",
// and a lockfile means a new transitive arrival FAILS the build until someone regenerates
// and commits it as a reviewable diff. Regenerate deliberately:
//
//     ./gradlew resolveAndLockAll --write-locks
// ---------------------------------------------------------------------------------------
allprojects {
    dependencyLocking {
        lockAllConfigurations()
    }
}

tasks.register("resolveAndLockAll") {
    group = "verification"
    description = "Resolves every lockable configuration so --write-locks can record them."
    notCompatibleWithConfigurationCache("Resolves configurations at execution time")
    doFirst {
        require(gradle.startParameter.isWriteDependencyLocks) {
            "Run with --write-locks: ./gradlew resolveAndLockAll --write-locks"
        }
    }
    doLast {
        allprojects {
            configurations.filter { it.isCanBeResolved }.forEach { config ->
                runCatching { config.resolve() }
            }
        }
    }
}
