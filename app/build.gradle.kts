import java.io.File
import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.plugin.compose)
    alias(libs.plugins.ksp)
}

// === 版本号自动化（CI 注入环境变量 / 本地 fallback） ===
// 策略：
// 1. CI 环境（GitHub Actions）：workflow 通过 GITHUB_ENV 注入 VERSION_CODE 和 VERSION_NAME
//    - VERSION_CODE = github.run_number（严格递增，每次 push +1）
//    - VERSION_NAME = 1.0.<github.run_number>（与 Release tag 严格一致）
// 2. 本地环境（Android Studio 直接打包）：环境变量不存在时使用默认值
//    - versionCode = 1
//    - versionName = "1.0.0-local"
// 设计说明：
// - github.run_number 在仓库维度严格递增，可保证每次构建 versionCode 唯一递增
// - 本地默认值保证 AS 直接打包不报错，发布版本号由 CI 严格控制

// === Release 签名配置（CI 通过 Secrets 注入 / 本地可选 keystore.properties） ===
// 设计要点：
// - CI 环境：GitHub Actions 解码 KEYSTORE_BASE64 Secret 写入 keystore.jks + keystore.properties
//   build.gradle.kts 检测到 keystore.properties 自动切换 release 签名
// - 本地环境：开发者可手动放置 keystore.properties + keystore.jks 使用 release 签名
// - Fallback：未配置 keystore.properties 时使用 debug 签名，保证 AS 直接打包不报错
//   ⚠️ 注意：debug 签名的 APK 无法覆盖安装到已安装 release 签名的设备
//           GitHub Release 必须配置 KEYSTORE_BASE64 Secret 以使用 release 签名
val signingPropsFile: File = rootProject.file("keystore.properties")
val hasSigningProps: Boolean = signingPropsFile.exists()
val signingProps: Properties = Properties().apply {
    if (hasSigningProps) {
        load(FileInputStream(signingPropsFile))
    }
}

android {
    namespace = "com.shangmentiyu.sportscoach"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.shangmentiyu.sportscoach"
        minSdk = 26
        targetSdk = 35

        // 让版本号自动读取环境变量，并设置默认本地开发版本
        // CI 环境通过 workflow 注入 VERSION_CODE / VERSION_NAME，本地 fallback 到默认值
        versionCode = (System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1)
        versionName = (System.getenv("VERSION_NAME") ?: "1.0.0-local")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        // 仓库已改为 public，自动更新无需任何 Token
    }

    signingConfigs {
        // 仅在 keystore.properties 存在时创建 release 签名配置
        // 未配置时 fallback 到 debug 签名（Android Studio 直接打包不报错）
        if (hasSigningProps) {
            create("release") {
                storeFile = file(signingProps.getProperty("storeFile"))
                storePassword = signingProps.getProperty("storePassword")
                keyAlias = signingProps.getProperty("keyAlias")
                keyPassword = signingProps.getProperty("keyPassword")
                // 启用全签名方案，兼容 Android 5.0+ 至最新版本
                enableV1Signing = true   // JAR signing（Android 7.0 以下兼容）
                enableV2Signing = true   // APK Signature Scheme v2（Android 7.0+）
                enableV3Signing = true   // APK Signature Scheme v3（Android 9+，支持密钥轮换）
            }
        }
    }

    buildTypes {
        release {
            // 开启代码混淆与资源压缩：
            // - 移除未使用代码，APK 体积可减 30-50%
            // - 增加反编译难度，保护业务逻辑与学员数据处理代码
            // - proguard-rules.pro 已配置 POI/Room/Compose/DataStore 等 keep 规则
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // CI 配置了 keystore.properties 时使用正式 release 签名
            // 否则 fallback 到 debug 签名（仅本地开发用，不可发布到 GitHub Release）
            // ⚠️ UpdateChecker 警告：发布到 GitHub 的 APK 必须使用 release 签名，
            //    否则覆盖安装时会报"解析包错误 / 应用未安装"
            signingConfig = if (hasSigningProps) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Kotlin 编译器选项（AGP 8.x 支持 kotlinOptions DSL）
    kotlinOptions {
        jvmTarget = "17"
    }

    // === 单元测试配置（v22 引入） ===
    // 启用单元测试返回 Robolectric 等需要 Android 资源的测试框架
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    buildFeatures {
        // Compose 已通过 kotlin.plugin.compose 插件启用，此处显式声明保持清晰
        compose = true
        // AGP 8.x 默认 buildConfig=false，需显式开启以生成 BuildConfig.VERSION_NAME 等字段
        buildConfig = true
        // 注：viewBinding / dataBinding 未使用（纯 Compose 项目），保持默认 false
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/LICENSE"
            excludes += "META-INF/LICENSE.txt"
            excludes += "META-INF/license.txt"
            excludes += "META-INF/NOTICE"
            excludes += "META-INF/NOTICE.txt"
            excludes += "META-INF/notice.txt"
            // v22 新增：测试框架（Truth / AutoValue / Robolectric）依赖的元数据文件
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/io.netty.versions.properties"
            excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
    }
}

// Room 2.6.1 与 KSP2（KSP 2.3.2）存在 "unexpected jvm signature V" 兼容性问题，
// 升级到 Room 2.7.1（原生支持 KSP2）已解决，无需额外 ksp 参数。

dependencies {
    // === v25 优化6：依赖版本统一通过 gradle/libs.versions.toml 管理 ===
    // 升级依赖时只需修改 libs.versions.toml 一处，避免版本号散落导致冲突

    // Compose BOM（统一管理 Compose 各库版本）
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)

    // AndroidX 核心
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    // lifecycle-process：提供 ProcessLifecycleOwner，用于监听应用前后台生命周期
    // 启动优化时用它把 WorkManager 初始化延迟到应用前台，避免冷启动阻塞首帧
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    // Room 数据库（2.7.1 原生支持 KSP2，解决 Kotlin 2.2.10 + KSP 2.3.2 兼容性问题）
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    // room-paging：Room 与 Paging 3 集成，自动处理分页查询
    implementation(libs.androidx.room.paging)
    ksp(libs.androidx.room.compiler)

    // Paging 3：分页加载历史课时列表，避免一次性加载 5000+ 条记录导致内存峰值与卡顿
    implementation(libs.androidx.paging.runtime.ktx)
    implementation(libs.androidx.paging.compose)

    // 数据存储
    implementation(libs.androidx.datastore.preferences)

    // Apache POI（Excel 导入导出，与桌面端互通）
    implementation(libs.apache.poi)
    implementation(libs.apache.poi.ooxml)

    // 文件选择器
    implementation(libs.androidx.documentfile)

    // 图片加载（签到照片缩略图）
    implementation(libs.coil.compose)
    // 全屏图片查看器：使用纯 Compose 原生手势 API 实现，无需第三方依赖
    // 支持：双指缩放、单指/双指拖拽平移、双击还原 1:1、回弹边界

    // 安全加密存储（签到照片加密，符合 PIPL 对生物特征的加密存储要求）
    implementation(libs.androidx.security.crypto)

    // ===== 自动更新检测功能依赖 =====
    // OkHttp：网络请求（访问 GitHub API 下载 APK）
    implementation(libs.okhttp)
    // Gson：解析 GitHub Release JSON
    implementation(libs.gson)
    // WorkManager：定期后台检查更新
    implementation(libs.androidx.work.runtime.ktx)

    // 调试工具
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // ===== 单元测试基建（v22 引入） =====
    // JUnit 4：标准单元测试框架，用于纯逻辑模块（如 HeightPredictionProcessor）的算法验证
    testImplementation(libs.junit)
    // kotlinx-coroutines-test：测试协程挂起函数（ViewModel / Repository）
    testImplementation(libs.kotlinx.coroutines.test)
    // Robolectric：在 JVM 上运行需要 Android Context 的单元测试（无需真机）
    testImplementation(libs.robolectric)
    // AndroidX Core Testing： LiveData / Room 等组件的测试支持
    testImplementation(libs.androidx.core.testing)
    // Truth：Google 推荐的流式断言库，使测试断言更易读（可选）
    testImplementation(libs.truth)
}
