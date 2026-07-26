// 顶层构建文件
//
// === v25 优化6：插件版本统一通过 gradle/libs.versions.toml 管理 ===
// 与 app/build.gradle.kts 共用同一份 Version Catalog，避免版本号散落导致冲突
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.plugin.compose) apply false
    alias(libs.plugins.ksp) apply false
}
