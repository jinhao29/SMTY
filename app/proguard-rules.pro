# ============================================================
# 体育教学 App ProGuard 规则
# ============================================================

# ---------- Apache POI（Excel 导入导出） ----------
# v34 优化：原 -keep class org.apache.poi.** { *; } 太宽，让 POI 整个库不能裁剪
# 新策略：仅 keep POI 公共 API（用户使用的 Workbook/Sheet/Row/Cell 等接口与实体类）
#   - xmlbeans 必须完整保留（POI 反射加载 schema 类，混淆会崩）
#   - POI 内部实现类可以裁剪，体积可减 30%+
-keep public class org.apache.poi.ss.usermodel.** { *; }
-keep public class org.apache.poi.ss.SpreadsheetVersion { *; }
-keep public class org.apache.poi.ss.util.** { *; }
-keep public class org.apache.poi.hssf.usermodel.** { *; }
-keep public class org.apache.poi.xssf.usermodel.** { *; }
-keep public class org.apache.poi.xssf.eventusermodel.** { *; }
-keep public class org.apache.poi.ooxml.** { *; }
-keep public class org.apache.poi.ooxml.util.** { *; }
-keep public class org.apache.poi.util.** { *; }
-keep public class org.apache.poi.poifs.** { *; }
-keep public class org.apache.poi.hpsf.** { *; }
-keep public class org.apache.poi.EncryptedDocumentException { *; }
# xmlbeans：POI 通过反射加载 schema 类，必须完整保留
-keep class org.apache.xmlbeans.** { *; }
-keep class org.openxmlformats.** { *; }
-keep class org.etsi.** { *; }
-keep class org.w3.** { *; }
-dontwarn org.apache.poi.**
-dontwarn org.apache.xmlbeans.**
-dontwarn org.openxmlformats.**
# POI 传递依赖中可能缺失的注解类（仅编译期使用，运行时不需要）
-dontwarn aQute.bnd.annotation.**
-dontwarn org.apache.logging.log4j.**
-dontwarn javax.annotation.**
-dontwarn org.antlr.**
-dontwarn org.objenesis.**
# Android 不含 AWT（POI 图表库引用 java.awt.Shape 等）
-dontwarn java.awt.**
# POI 数字签名相关依赖（App 不使用数字签名功能，运行时不需要）
-dontwarn org.etsi.**
-dontwarn com.graphbuilder.**
# === v34 修复 R8 "Missing class" 编译错误 ===
# 背景：R8 默认对缺失类直接报错停止编译，导致 Release 包无法生成
# 缺失类来源：POI 数字签名功能引用的 org.w3.x2000.x09.xmldsig.* 类
# 这些类是 POI 可选依赖，App 仅使用 POI 的 Excel 读写功能，不使用数字签名
# 解决：通过 -dontwarn 告诉 R8 忽略缺失类，而不是报错停止
# 同时通过 -keep 防止这些类被引用的其他类被裁剪
-dontwarn org.w3.x2000.x09.xmldsig.**
-dontwarn org.w3.x2000.x09.xmldsig.base.**
-dontwarn org.w3.x2000.x09.xmldsig.elements.**
-dontwarn org.w3.x2000.x09.xmldsig.manifest.**
-dontwarn org.w3.x2000.x09.xmldsig.signatureproperties.**
-dontwarn org.w3.x2000.x09.xmldsig.signedinfo.**
-dontwarn org.w3.x2000.x09.xmldsig.keyinfo.**
-dontwarn org.w3.x2000.x09.xmldsig.object.**
-dontwarn org.w3.x2000.x09.xmldsig.signature.**
-dontwarn org.w3.x2000.x09.xmldsig.reference.**
-dontwarn org.w3.x2000.x09.xmldsig.transforms.**
-dontwarn org.w3.x2000.x09.xmldsig.digestmethod.**
-dontwarn org.w3.x2000.x09.xmldsig.signaturevalue.**
-dontwarn org.w3.x2000.x09.xmldsig.keyinfo.**
-dontwarn org.w3.x2000.x09.xmldsig.keyvalue.**
-dontwarn org.w3.x2000.x09.xmldsig.retrievalmethod.**
-dontwarn org.w3.x2000.x09.xmldsig.x509data.**
-dontwarn org.w3.x2000.x09.xmldsig.pgpdata.**
-dontwarn org.w3.x2000.x09.xmldsig.spkidata.**
-dontwarn org.w3.x2000.x09.xmldsig.keyname.**
-dontwarn org.w3.x2000.x09.xmldsig.mgmtdata.**
# 兜底：所有 org.w3.** 缺失类全部忽略
-dontwarn org.w3.**

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

