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
val testKeyAuthLocal = "/tmp/test-key-auth.asc"

fun Task.cmd(vararg args: String) {
    val process = ProcessBuilder(*args).redirectErrorStream(true).start()
    process.inputStream.bufferedReader().forEachLine { logger.lifecycle(it) }
    val exit = process.waitFor()
    if (exit != 0) throw GradleException("Command failed (exit $exit): ${args.joinToString(" ")}")
}

tasks.register("e2eInstall") {
    dependsOn(":app:assembleDebug")
    notCompatibleWithConfigurationCache("e2e tasks interact with a device at runtime")
    doLast {
        cmd("adb", "install", "-r", apkPath)
        val fixtureRevision = "3912051da6e6d4553a990ca63900c3ef2adc06cb"
        for (path in listOf(testKeyLocal, testKeyAuthLocal)) {
            cmd(
                "curl", "--fail", "--location", "--retry", "3",
                "https://raw.githubusercontent.com/Zangetsu101/pass-test-store/$fixtureRevision/${file(path).name}",
                "--output", path,
            )
        }
        cmd("adb", "push", testKeyLocal, "/sdcard/Download/test-key.asc")
        cmd("adb", "push", testKeyAuthLocal, "/sdcard/Download/test-key-auth.asc")
    }
}

// ./gradlew e2e                       — runs flow_all.yaml
// ./gradlew e2e -Pflow=onboarding     — runs a specific flow
tasks.register("e2e") {
    dependsOn("e2eInstall")
    notCompatibleWithConfigurationCache("e2e tasks interact with a device at runtime")
    doLast {
        if (!file(maestroBin).exists()) {
            throw GradleException("Maestro not found at $maestroBin — install: curl -Ls \"https://get.maestro.mobile.dev\" | bash")
        }
        val flow = (findProperty("flow") as String? ?: "flow_all")
            .let { if (it.startsWith("flow_")) it else "flow_$it" }
            .let { if (it.endsWith(".yaml")) it else "$it.yaml" }
        cmd(maestroBin, "test", "e2e/$flow")
    }
}
