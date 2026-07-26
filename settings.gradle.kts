pluginManagement {
    repositories {
        // 阿里云镜像（国内加速）
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/central") }
        // 腾讯云镜像
        maven { url = uri("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/") }
        // 官方源（兜底）
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
        // 阿里云镜像（国内加速）
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/central") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        // 腾讯云镜像
        maven { url = uri("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/") }
        // 官方源（兜底）
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
