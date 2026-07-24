package com.shangmentiyu.sportscoach.ui.parent

import android.content.Context
import android.content.Intent
import com.shangmentiyu.sportscoach.core.JsonSafe
import com.shangmentiyu.sportscoach.data.model.ParentReport

/**
 * 报告分享助手：生成纯文本/H5 摘要并调用系统分享。
 *
 * 安全性：报告 JSON 解析走 [JsonSafe] 兜底，
 * 即使数据库中残留了不完整的报告内容，分享也不会崩溃，而是降级输出基础信息。
 */
object ReportShareHelper {

    /**
     * 将报告转为可读的纯文本摘要（用于系统分享 Intent）。
     */
    fun toTextSummary(report: ParentReport): String {
        val sb = StringBuilder()
        sb.appendLine("===== ${report.reportType} =====")
        sb.appendLine("学员：${report.studentName}")
        // 报告内容解析失败时降级输出，不崩溃
        val obj = JsonSafe.parseObject(report.content)
        if (obj == null) {
            sb.appendLine("（报告内容已损坏，无法解析详情）")
            sb.appendLine()
            sb.appendLine("—— 体育教学助手")
            return sb.toString()
        }
        sb.appendLine("周期：${obj.optString("period")}")
        sb.appendLine()

        // 统计
        val stats = obj.optJSONObject("stats")
        if (stats != null) {
            sb.appendLine("【训练统计】")
            sb.appendLine("课时数：${stats.optInt("lessonCount")}")
            sb.appendLine("总时长：${stats.optInt("totalMinutes")} 分钟")
            sb.appendLine("平均表现：${stats.optString("avgPerformance")} 分")
            sb.appendLine()
        }

        // 里程碑
        val milestones = obj.optJSONArray("milestones")
        if (milestones != null && milestones.length() > 0) {
            sb.appendLine("【里程碑】")
            for (i in 0 until milestones.length()) {
                // 单条里程碑解析失败时跳过
                val m = milestones.optJSONObject(i) ?: continue
                sb.appendLine("· ${m.optString("title")}：${m.optString("desc")}（${m.optString("date")}）")
            }
            sb.appendLine()
        }

        // 成绩变化
        val changes = obj.optJSONArray("scoreChanges")
        if (changes != null && changes.length() > 0) {
            sb.appendLine("【成绩变化】")
            for (i in 0 until changes.length()) {
                // 单条变化解析失败时跳过
                val c = changes.optJSONObject(i) ?: continue
                sb.appendLine("· ${c.optString("project")}: ${c.optDouble("before", 0.0).toInt()} → ${c.optDouble("after", 0.0).toInt()} (${c.optString("trend")})")
            }
            sb.appendLine()
        }

        // 教练寄语
        sb.appendLine("【教练寄语】")
        sb.appendLine(obj.optString("coachComment"))
        sb.appendLine()

        // 建议
        sb.appendLine("【下阶段建议】")
        sb.appendLine(obj.optString("suggestion"))

        sb.appendLine()
        sb.appendLine("—— 体育教学助手")
        return sb.toString()
    }

    /**
     * 调用系统分享。
     */
    fun shareText(context: Context, title: String, text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, title).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}
