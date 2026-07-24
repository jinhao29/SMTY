# ============================================================
# 体育教学 App ProGuard 规则
# ============================================================

# ---------- Apache POI（Excel 导入导出，依赖大量反射） ----------
-keep class org.apache.poi.** { *; }
-keep class org.apache.xmlbeans.** { *; }
-keep class org.openxmlformats.** { *; }
-keep class org.etsi.** { *; }
-keep class org.w3.** { *; }
-dontwarn org.apache.poi.**
-dontwarn org.apache.xmlbeans.**
-dontwarn org.openxmlformats.**

# ---------- Room 数据库（实体与 DAO 实现由 KSP 生成，需保留） ----------
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep class com.shangmentiyu.sportscoach.data.model.** { *; }
-keep class com.shangmentiyu.sportscoach.data.db.** { *; }

# ---------- Kotlin 协程与元数据 ----------
-keepclassmembernames class kotlinx.** { volatile <fields>; }
-keep class kotlin.Metadata { *; }
-dontwarn kotlinx.coroutines.**

# ---------- Compose（运行时需保留 Composable 元数据） ----------
-dontwarn androidx.compose.**
-keep class androidx.compose.runtime.** { *; }

# ---------- Coil 图片加载 ----------
-dontwarn coil.**

# ---------- DataStore ----------
-keep class androidx.datastore.** { *; }
-dontwarn androidx.datastore.**

# ---------- 项目自身实体与枚举（避免 R8 裁剪数据类构造器） ----------
-keep class com.shangmentiyu.sportscoach.data.model.** { *; }
-keep class com.shangmentiyu.sportscoach.core.** { *; }

# ---------- 保留注解与签名（反射调用需要） ----------
-keepattributes Signature
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeInvisibleAnnotations
-keepattributes EnclosingMethod
-keepattributes InnerClasses

# ---------- Gson（JSON 解析依赖反射，需保留模型类字段） ----------
-keep class com.google.gson.** { *; }
-keep class com.shangmentiyu.sportscoach.update.UpdateModelsKt { *; }
-keep class com.shangmentiyu.sportscoach.update.GitHubRelease { *; }
-keep class com.shangmentiyu.sportscoach.update.GitHubAsset { *; }
-keep class com.shangmentiyu.sportscoach.update.UpdateResult { *; }
-keep class com.shangmentiyu.sportscoach.update.UpdateResult$* { *; }
-keep @com.google.gson.annotations.SerializedName class * { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ---------- OkHttp（网络请求） ----------
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# ---------- WorkManager（后台任务） ----------
-keep class androidx.work.** { *; }
-dontwarn androidx.work.**

# ---------- BuildConfig（自动更新 Token 注入） ----------
-keep class com.shangmentiyu.sportscoach.BuildConfig { *; }

