plugins {
    alias(libs.plugins.android.application) apply false
alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlinter) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
}

val maestroBin = "${System.getProperty("user.home")}/.maestro/bin/maestro"
val appPackage = "com.zangetsu101.pass"
val apkPath = "app/build/outputs/apk/debug/app-debug.apk"
val testKeyLocal = "/tmp/test-key.asc"

fun Task.cmd(vararg args: String) {
    val process = ProcessBuilder(*args).redirectErrorStream(true).start()
    process.inputStream.bufferedReader().forEachLine { logger.lifecycle(it) }
    val exit = process.waitFor()
    if (exit != 0) throw GradleException("Command failed (exit $exit): ${args.joinToString(" ")}")
}

tasks.register("maestroInstall") {
    dependsOn(":app:assembleDebug")
    notCompatibleWithConfigurationCache("Maestro tasks interact with a device at runtime")
    doLast {
        cmd("adb", "install", "-r", apkPath)
        if (!file(testKeyLocal).exists()) {
            // TODO: switch to curl/wget once pass-test-store is public
            logger.lifecycle("Test key not found — cloning pass-test-store via gh...")
            if (!file("/tmp/pass-test-store").exists()) {
                cmd("gh", "repo", "clone", "Zangetsu101/pass-test-store", "/tmp/pass-test-store")
            }
            file("/tmp/pass-test-store/test-key.asc").copyTo(file(testKeyLocal))
        }
        cmd("adb", "push", testKeyLocal, "/sdcard/Download/test-key.asc")
    }
}

// ./gradlew maestro                        — runs flow_all.yaml
// ./gradlew maestro -Pflow=flow_onboarding — runs a specific flow
tasks.register("maestro") {
    dependsOn("maestroInstall")
    notCompatibleWithConfigurationCache("Maestro tasks interact with a device at runtime")
    doLast {
        if (!file(maestroBin).exists()) {
            throw GradleException("Maestro not found at $maestroBin — install: curl -Ls \"https://get.maestro.mobile.dev\" | bash")
        }
        val flow = (findProperty("flow") as String? ?: "flow_all")
            .let { if (it.endsWith(".yaml")) it else "$it.yaml" }
        cmd(maestroBin, "test", "maestro/$flow")
    }
}
