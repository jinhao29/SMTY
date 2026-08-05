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
}

dependencies {
    // 依赖 :core，复用纯逻辑层（Standards / Scorer / JsonSafe 等）
    implementation(project(":core"))

    // AndroidX 核心
    implementation("androidx.core:core-ktx:1.15.0")

    // Compose runtime：实体类 @Stable 注解依赖（与 app 模块 compose 1.7.6 一致）
    implementation("androidx.compose.runtime:runtime:1.7.6")

    // Paging：DAO 的 PagingSource 类型依赖（与 app 模块 paging 3.3.5 一致）
    implementation("androidx.paging:paging-common:3.3.5")

    // Room 数据库（与 app 模块保持版本一致，避免歧义）
    implementation("androidx.room:room-runtime:2.7.1")
    implementation("androidx.room:room-ktx:2.7.1")
    // Room Paging：DAO 的 @Query PagingSource 返回类型需要（与 app 模块一致）
    implementation("androidx.room:room-paging:2.7.1")
    ksp("androidx.room:room-compiler:2.7.1")

    // DataStore：设置项持久化
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // 协程
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // 安全加密（签到照片加密存储）
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // JSON 解析（Repository 解析 _meta 工作表 JSON）
    implementation("com.google.code.gson:gson:2.11.0")

    // Apache POI（Excel 导入导出，ExcelSync 依赖；与 app 模块 poi 5.3.0 一致）
    implementation("org.apache.poi:poi:5.3.0")
    implementation("org.apache.poi:poi-ooxml:5.3.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}
