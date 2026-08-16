// ============================================================================
// :data 模块构建配置（Phase 3 启用）
//
// 职责：数据层 / Room 数据库 / Repository 层
// - 包含：com.shangmentiyu.sportscoach.data.*
//   - db/      Room Database、DAO（StudentDao / LessonDao / ScheduleDao 等）
//   - model/   Entity / Converters / 数据模型
//   - repo/    Repository（StudentRepository / LessonRepository / ScheduleRepository 等）
// - 依赖：:core（复用纯逻辑层，如 JsonSafe、Standards、Scorer 等）
// - 不包含：UI（Compose）、Android Framework 入口（Activity/Application）
// - 不依赖：:app，确保可被未来其它前端（如 WearOS / Desktop Compose）复用
//
// 命名约束：包名保留 com.shangmentiyu.sportscoach.data，与原 app 模块兼容，
//           迁移后 app 模块的 import 路径无需修改。
// ============================================================================
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.shangmentiyu.sportscoach.data"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    // 单元测试配置：与 app 模块一致，供 Robolectric 等 JVM 测试框架使用
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

// Room schema 导出（P2 修复）：exportSchema=true 时生成 JSON 到 data/schemas/，纳入版本控制
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // 依赖 :core，复用纯逻辑层（Standards / Scorer / JsonSafe 等）
    implementation(project(":core"))

    // 依赖统一走 Version Catalog（gradle/libs.versions.toml），去除硬编码版本
    implementation(libs.androidx.core.ktx)
    // Paging：DAO 的 PagingSource 类型依赖（Android 专用 paging-runtime，传递依赖 paging-common）
    implementation(libs.androidx.paging.runtime)

    // Room 数据库
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.paging)
    ksp(libs.androidx.room.compiler)

    // DataStore：设置项持久化
    implementation(libs.androidx.datastore.preferences)

    // 协程
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // 安全加密（签到照片加密存储，稳定版 1.1.0）
    implementation(libs.androidx.security.crypto)

    // JSON 解析（Repository 解析 _meta 工作表 JSON）
    implementation(libs.gson)

    // Apache POI（Excel 导入导出）
    implementation(libs.apache.poi)
    implementation(libs.apache.poi.ooxml)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // Robolectric：JVM 上运行需要 Android Context 的测试（Room 内存库 / 备份恢复）
    testImplementation(libs.robolectric)
    // Truth：流式断言库（与既有测试风格保持一致）
    testImplementation(libs.truth)
}
