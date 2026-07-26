package com.shangmentiyu.sportscoach.util

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 语音播报器：封装 Android TextToSpeech，用于成绩播报。
 *
 * 职责：
 *  - 初始化中文 TTS 引擎
 *  - 提供成绩/等级播报接口
 *  - 支持开关控制，避免误触
 *  - 重复播报时打断上一次，避免排队堆积
 *
 * 使用方式：
 *  val announcer = VoiceAnnouncer(context)
 *  announcer.isEnabled = true
 *  announcer.announceScore("50米跑", "8.5", 92.0, "优秀")
 *  // 页面销毁时调用 release()
 */
class VoiceAnnouncer(context: Context) {

    /** 是否启用语音播报，关闭后所有播报请求被忽略 */
    @Volatile
    var isEnabled: Boolean = true

    private val ready = AtomicBoolean(false)
    private lateinit var tts: TextToSpeech

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts.setLanguage(Locale.CHINESE)
                if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                    ready.set(true)
                } else {
                    Log.w(TAG, "中文 TTS 不可用，result=$result")
                }
            } else {
                Log.w(TAG, "TTS 初始化失败 status=$status")
            }
        }
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {}
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {}
        })
    }

    /**
     * 播报成绩：例 "50米跑 8.5秒，92.0分，优秀"
     * @param projectName 项目名
     * @param value 原始成绩文本（已含单位）
     * @param score 得分（0-100）
     * @param grade 等级文字（优秀/良好/及格/不及格）
     */
    fun announceScore(projectName: String, value: String, score: Double, grade: String) {
        if (!isEnabled || !ready.get()) return
        val text = buildString {
            append(projectName)
            if (value.isNotBlank()) append(" $value")
            append(String.format("，%.1f分", score))
            if (grade.isNotBlank()) append("，$grade")
        }
        speak(text)
    }

    /** 播报任意文本 */
    fun speak(text: String) {
        if (!isEnabled || !ready.get() || text.isBlank()) return
        // 打断之前的播报，避免堆积
        tts.stop()
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
    }

    /** 释放资源，应在 Activity/Composable 销毁时调用 */
    fun release() {
        tts.stop()
        tts.shutdown()
        ready.set(false)
    }

    companion object {
        private const val TAG = "VoiceAnnouncer"
        private const val UTTERANCE_ID = "score_announce"

        // === v31 优化3：应用级单例，由 SportsCoachApp 初始化 ===
        // - 避免每次进入学员列表都重新初始化 TTS 引擎
        // - 全局开关由 SettingsRepository.voiceModeEnabled 持久化驱动
        // - sign() 签到时直接 announce()，无需持有 Context
        @Volatile
        private var instance: VoiceAnnouncer? = null

        /** 在 Application.onCreate 中调用，初始化全局 TTS 引擎。重复调用幂等。 */
        fun init(context: Context) {
            if (instance != null) return
            synchronized(this) {
                if (instance != null) return
                instance = VoiceAnnouncer(context.applicationContext)
            }
        }

        /** 切换语音播报总开关（不影响 TTS 引擎生命周期） */
        fun setEnabled(enabled: Boolean) {
            instance?.isEnabled = enabled
        }

        /**
         * 播报任意文本（受 [isEnabled] 控制）。
         *
         * - 调用前必须已通过 [init] 初始化；未初始化时静默忽略
         * - 主要调用方：HomeViewModel.sign() 在签到成功后播报学员信息
         */
        fun announce(text: String) {
            instance?.speak(text)
        }

        /** 应用退出时释放资源（一般由系统自动处理，无需手动调用） */
        fun releaseGlobal() {
            instance?.release()
            instance = null
        }
    }
}
