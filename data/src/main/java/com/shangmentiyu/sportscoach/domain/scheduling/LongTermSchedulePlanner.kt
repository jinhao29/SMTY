package com.shangmentiyu.sportscoach.domain.scheduling

import android.util.Log
import com.shangmentiyu.sportscoach.data.model.Schedule
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

data class PlannedLongTermLesson(
    val schedule: Schedule,
    val date: String
)

object LongTermSchedulePlanner {

    const val DEFAULT_WINDOW_DAYS = 28

    private val formatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US)

    fun plan(
        studentSchedules: List<Schedule>,
        weekStart: String,
        today: String,
        availableQuota: Int,
        alreadyBookedDates: Set<String>,
        windowDays: Int = DEFAULT_WINDOW_DAYS,
        firstPurchaseDate: String? = null,
        expireDate: String? = null,
        pendingSlots: Int = 0,
        studentName: String = ""
    ): List<PlannedLongTermLesson> {
        val regularSchedules = studentSchedules.filter { !it.isTrial }
        if (regularSchedules.isEmpty()) return emptyList()

        val userStart = try {
            LocalDate.parse(weekStart, formatter)
        } catch (_: Exception) {
            return emptyList()
        }

        val purchaseLocal = firstPurchaseDate?.takeIf { it.isNotBlank() }
            ?.let { runCatching { LocalDate.parse(it, formatter) }.getOrNull() }
        val expireLocal = expireDate?.takeIf { it.isNotBlank() }
            ?.let { runCatching { LocalDate.parse(it, formatter) }.getOrNull() }
        val todayLocal = today.takeIf { it.isNotBlank() }
            ?.let { runCatching { LocalDate.parse(it, formatter) }.getOrNull() }

        // 规则0：起始日期自动对齐 max(用户选择, 首次购买)
        val effectiveStart = if (purchaseLocal != null) maxOf(userStart, purchaseLocal) else userStart

        // 规则3：初始剩余额度 = 总课时包剩余 - 已签退 - 未签退占位
        var remainingSlots = (availableQuota - pendingSlots).coerceAtLeast(0)

        // 规则4：用户模板的周几集合
        val templateDays = regularSchedules.map { it.dayOfWeek }.toSet()

        val result = mutableListOf<PlannedLongTermLesson>()

        Log.d("ScheduleGen", "=== 开始为学员 $studentName 生成排课 ===")
        Log.d("ScheduleGen", "首次购买日期: $firstPurchaseDate")
        Log.d("ScheduleGen", "最晚到期日期: $expireDate")
        Log.d("ScheduleGen", "初始剩余额度: $remainingSlots")
        Log.d("ScheduleGen", "用户选择起始: $weekStart, 实际起始(effectiveStart): $effectiveStart")

        for (offset in 0 until windowDays) {
            val currentDate = effectiveStart.plusDays(offset.toLong())
            // 早于今天的日期不生成（历史数据不回溯）——修复 v49 重构丢失的 today 过滤，
            // 否则会把 weekStart（本周一）到今天之间的过去日期也排成占位课时。
            if (todayLocal != null && currentDate < todayLocal) continue
            val dateStr = currentDate.format(formatter)
            val dayOfWeek = currentDate.dayOfWeek.value

            Log.d("ScheduleGen", "检查日期: $dateStr, 当前剩余额度: $remainingSlots")

            // 规则1：购买日期前跳过（effectiveStart 已对齐，通常不触发）
            if (purchaseLocal != null && currentDate < purchaseLocal) {
                Log.d("ScheduleGen", "跳过：早于购买日期")
                continue
            }

            // 规则2：超过到期日期后才停止（到期日当天仍可排，与购买日「当天含」语义一致）
            if (expireLocal != null && currentDate > expireLocal) {
                Log.d("ScheduleGen", "停止：超过到期日期")
                break
            }

            // 规则3：额度耗尽停止
            if (remainingSlots <= 0) {
                Log.d("ScheduleGen", "停止：额度已用完")
                break
            }

            // 规则4：周几过滤
            if (dayOfWeek !in templateDays) {
                Log.d("ScheduleGen", "跳过：周$dayOfWeek 不在模板中")
                continue
            }

            if (dateStr in alreadyBookedDates) {
                Log.d("ScheduleGen", "跳过：当天已有排课")
                continue
            }

            val sched = regularSchedules.filter { it.dayOfWeek == dayOfWeek }
                .minByOrNull { it.startTime } ?: continue

            if (sched.startDate.isNotBlank() && dateStr < sched.startDate) {
                Log.d("ScheduleGen", "跳过：早于模板生效日 ${sched.startDate}")
                continue
            }
            if (sched.endDate.isNotBlank() && dateStr > sched.endDate) {
                Log.d("ScheduleGen", "跳过：晚于模板到期日 ${sched.endDate}")
                continue
            }

            // 规则5：四要素全部满足，生成当天课程并扣减额度
            result += PlannedLongTermLesson(schedule = sched, date = dateStr)
            Log.d("ScheduleGen", "成功生成排课: $dateStr, 剩余额度减为: ${remainingSlots - 1}")
            remainingSlots--
        }
        return result
    }
}
