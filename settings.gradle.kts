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
// 模块化结构（v22 引入，分阶段拆分）：
// - :core  纯逻辑层：算法/计算/分析（无 Android 依赖，便于单元测试）
// - :data  数据层：Room DAO/Entity/Repository（依赖 :core，可独立替换为远程源）
// - :app   应用层：UI / 入口 / Android Framework 集成（依赖 :core + :data）
//
// 迁移阶段（参考回复中的「模块化迁移步骤清单」）：
// 1. Phase 1（已完成）：core/build.gradle.kts + data/build.gradle.kts 就位，
//    app 模块原代码不动，确保现有构建正常。
// 2. Phase 2（待执行）：取消 include(":core") 注释，迁移 com.shangmentiyu.sportscoach.core 包
// 3. Phase 3（待执行）：取消 include(":data") 注释，迁移 com.shangmentiyu.sportscoach.data 包
// 4. Phase 4（待执行）：app/build.gradle.kts 添加 implementation(project(":core")) / (":data")
// ============================================================================
include(":app")
// 模块化预备：以下模块在 Phase 2/3 启用，先注释保留入口，避免破坏现有构建
// include(":core")
// include(":data")
