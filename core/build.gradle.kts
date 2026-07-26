// ============================================================================
// :core 模块构建配置（Phase 2 启用）
//
// 职责：纯逻辑层 / 算法层 / 计算层
// - 包含：com.shangmentiyu.sportscoach.core.*
//   - AbilityAnalyzer / DataAnalyzer / ReportGenerator / TrainingPlanGenerator
//   - BmiProcessor / Scorer / Standards / JsonSafe / TemplateData
//   - HeightPredictionProcessor / TdeeProcessor 等领域处理器
// - 不包含：Android Framework 依赖（Context、Room、Compose 等）
// - 不依赖：:app 或 :data，确保可被任意上层模块复用
//
// 命名约束：包名保留 com.shangmentiyu.sportscoach.core，与原 app 模块兼容，
//           迁移后 app 模块的 import 路径无需修改。
// ============================================================================
plugins {
    id("org.jetbrains.kotlin.jvm")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // 仅依赖 Kotlin 标准库与测试库
    // 如需 JSON 解析，使用 Kotlin 原生或显式引入 kotlinx-serialization（避免引入 org.json 的 Android 依赖）
    // 如需引入 org.json，需将本模块改为 com.android.library 并配置 SDK
    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")

    // JSON 兜底解析：原 core 包使用 org.json.JSONObject，迁移时需替换为：
    // 方案 A：保留 org.json（需把本模块改为 Android Library）
    // 方案 B：改用 kotlinx-serialization-json（推荐，纯 JVM）
    // 方案 C：改用 Gson（已有依赖）
    // 当前模板采用方案 A 占位，迁移时按实际情况选择

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}

// 启用单元测试报告
tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}
