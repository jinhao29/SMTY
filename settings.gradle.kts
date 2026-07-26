// ============================================================================
// 仓库源策略：CI 与本地动态切换
// ============================================================================
// 背景：
// - 之前 settings.gradle.kts 把国内镜像（阿里云/腾讯云）放在官方源前面，
//   本地构建（国内）能从国内镜像加速下载，但 CI（GitHub Actions ubuntu-latest，在海外）
//   访问国内镜像会超时或被限速，导致 plugin artifact 解析失败（错误信息被
//   Gradle 笼统归类为 "Plugin was not found"，实际是网络问题）。
// - 修复：根据 CI 环境变量动态切换仓库顺序：
//   * CI 环境（GITHUB_ACTIONS=true 或 CI=true）：官方源优先，国内镜像兜底
//   * 本地开发：国内镜像优先，官方源兜底
// - GitHub Actions 自动设置 CI=true 和 GITHUB_ACTIONS=true，无需在 workflow 里手动注入
val isCIEnv: Boolean = System.getenv("CI")?.equals("true", ignoreCase = true) == true
    || System.getenv("GITHUB_ACTIONS")?.equals("true", ignoreCase = true) == true

pluginManagement {
    repositories {
        if (isCIEnv) {
            // CI 环境（海外 runner）：官方源优先，避免访问国内镜像超时
            // 实测：阿里云镜像对海外 IP 限速严重，单次 POM 请求可能耗时 30s+，
            // 触发 Gradle plugin 解析超时，错误归类为 "Plugin not found"
            google()
            mavenCentral()
            gradlePluginPortal()
            // 国内镜像保留作为兜底（CI 几乎不会用到）
            maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
            maven { url = uri("https://maven.aliyun.com/repository/google") }
            maven { url = uri("https://maven.aliyun.com/repository/central") }
            maven { url = uri("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/") }
        } else {
            // 本地开发（国内）：国内镜像优先，下载速度快
            maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
            maven { url = uri("https://maven.aliyun.com/repository/google") }
            maven { url = uri("https://maven.aliyun.com/repository/central") }
            maven { url = uri("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/") }
            // 官方源兜底（国内镜像同步延迟时使用）
            google()
            mavenCentral()
            gradlePluginPortal()
        }
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        if (isCIEnv) {
            // CI 环境：官方源优先
            google()
            mavenCentral()
            maven { url = uri("https://maven.aliyun.com/repository/google") }
            maven { url = uri("https://maven.aliyun.com/repository/central") }
            maven { url = uri("https://maven.aliyun.com/repository/public") }
            maven { url = uri("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/") }
        } else {
            // 本地开发：国内镜像优先
            maven { url = uri("https://maven.aliyun.com/repository/google") }
            maven { url = uri("https://maven.aliyun.com/repository/central") }
            maven { url = uri("https://maven.aliyun.com/repository/public") }
            maven { url = uri("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/") }
            google()
            mavenCentral()
        }
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
