package com.shangmentiyu.sportscoach.ui.legal

import android.content.Context

/**
 * 合规文本与版本常量（v1.0.6 新增）。
 *
 * 背景：本应用处理的是不满十四周岁未成年人的个人信息（姓名/电话/体测/照片），
 * 依《个人信息保护法》《未成年人保护法》应"告知—同意"前置。上线前本模块缺失，
 * 属于阻断商业化的合规硬伤。
 *
 * 使用方式：见 [LegalConsentGate] —— 未同意当前 [VERSION] 时全屏拦截，不得进入应用。
 */
object Legal {

    /**
     * 当前生效的《用户协议》《隐私政策》版本号。
     *
     * ⚠️ 修订 assets/legal/ 下任一文本后，**必须同步提升此值**：
     * 应用启动时会比对本地已同意版本，不一致即重新展示弹窗取得同意。
     */
    const val VERSION = "2026-09-12"

    /** 《用户协议》文本资源路径（app/src/main/assets/ 下） */
    const val AGREEMENT_ASSET = "legal/user_agreement.txt"

    /** 《隐私政策》文本资源路径 */
    const val PRIVACY_ASSET = "legal/privacy_policy.txt"

    /**
     * 读取合规文本。
     *
     * 读取失败时返回可读的失败提示而非空串 —— 合规场景下"明明是空白"比报错更危险，
     * 用户会以为同意了一份空协议。
     */
    fun read(context: Context, assetPath: String): String = runCatching {
        context.assets.open(assetPath).bufferedReader(Charsets.UTF_8).use { it.readText() }
    }.getOrElse { "合规文本加载失败（$assetPath）：${it.message}" }
}
