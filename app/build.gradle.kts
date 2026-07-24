import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// 读取 local.properties（包含 GITHUB_TOKEN 等本地配置，已在 .gitignore 中）
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.shangmentiyu.sportscoach"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.shangmentiyu.sportscoach"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // 注入 GitHub Token 到 BuildConfig，用于 App 运行时访问私有仓库的 Release 信息
        // Token 从 local.properties 读取，不会进入 Git 仓库
        buildConfigField(
            "String",
            "GITHUB_TOKEN",
            "\"${localProps.getProperty("GITHUB_TOKEN", "")}\""
        )
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
            // 个人使用：release 构建使用 debug 签名，确保 GitHub Actions 可直接生成可安装 APK
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        // 启用 BuildConfig 生成，使 BuildConfig.GITHUB_TOKEN 可用
        buildConfig = true
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
        }
    }
}

// Room 2.6.1 与 KSP2（KSP 2.3.2）存在 "unexpected jvm signature V" 兼容性问题，
// 升级到 Room 2.7.1（原生支持 KSP2）已解决，无需额外 ksp 参数。

dependencies {
    // Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)

    // AndroidX 核心
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    // Compose
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.5")

    // Room 数据库（2.7.1 原生支持 KSP2，解决 Kotlin 2.2.10 + KSP 2.3.2 兼容性问题）
    implementation("androidx.room:room-runtime:2.7.1")
    implementation("androidx.room:room-ktx:2.7.1")
    ksp("androidx.room:room-compiler:2.7.1")

    // 数据存储
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Apache POI（Excel 导入导出，与桌面端互通）
    implementation("org.apache.poi:poi:5.3.0")
    implementation("org.apache.poi:poi-ooxml:5.3.0")

    // 文件选择器
    implementation("androidx.documentfile:documentfile:1.0.1")

    // 图片加载（签到照片缩略图）
    implementation("io.coil-kt:coil-compose:2.7.0")

    // 安全加密存储（签到照片加密，符合 PIPL 对生物特征的加密存储要求）
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // ===== 自动更新检测功能依赖 =====
    // OkHttp：网络请求（访问 GitHub API 下载 APK）
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // Gson：解析 GitHub Release JSON
    implementation("com.google.code.gson:gson:2.11.0")
    // WorkManager：定期后台检查更新
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    // 调试工具
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
