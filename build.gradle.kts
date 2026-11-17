plugins {
    alias(libs.plugins.android.application) apply false
alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlinter) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
}

tasks.register<Exec>("e2eInstall") {
    dependsOn(":app:assembleDebug")
    notCompatibleWithConfigurationCache("E2E tasks interact with a device at runtime")
    commandLine("bash", "e2e/scripts/install.sh")
}

tasks.register<Exec>("e2e") {
    dependsOn("e2eInstall")
    notCompatibleWithConfigurationCache("E2E tasks interact with a device at runtime")
    commandLine("bash", "e2e/scripts/run.sh", providers.gradleProperty("flow").getOrElse("all"))
}
