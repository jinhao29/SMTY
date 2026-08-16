// ============================================================================
// 仓库源策略：统一使用官方源（CI + 本地一致）
// ============================================================================
// 设计说明：
// - 早期版本把国内镜像（阿里云/腾讯云）放在官方源前面，本地开发快，
//   但 CI（GitHub Actions ubuntu-latest，在海外）访问国内镜像会超时或被限速，
//   导致 plugin artifact 解析失败（Gradle 错误归类为 "Plugin not found"）。
// - 现统一改为只使用官方源：
//   * CI（海外）：官方源访问快，构建稳定
//   * 本地（国内）：首次构建较慢（国内访问 Google/Maven Central 慢），
//     但 Gradle 下载后会缓存到 ~/.gradle/caches，后续构建不慢
// - 不再使用 if (isCIEnv) 动态判断：
//   * 代码更简洁，避免环境变量未设置时的边界 case
//   * CI 与本地行为一致，便于复现问题
// - 若本地首次构建确实太慢，可临时在 ~/.gradle/init.gradle.kts 中配置全局镜像，
//   不要污染项目 settings.gradle.kts
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "SportsCoach"

// ============================================================================
// 模块化结构（v22 引入，v46 迁移完成）：
// - :core  纯逻辑层：算法/计算/分析（无 Android 依赖，便于单元测试）
// - :data  数据层：Room DAO/Entity/Repository（依赖 :core，可独立替换为远程源）
// - :app   应用层：UI / 入口 / Android Framework 集成（依赖 :core + :data）
// ============================================================================
include(":app")
include(":core")
include(":data")
