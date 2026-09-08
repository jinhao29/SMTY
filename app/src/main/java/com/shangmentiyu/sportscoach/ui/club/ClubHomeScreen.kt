package com.shangmentiyu.sportscoach.ui.club

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.SupervisorAccount
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shangmentiyu.sportscoach.data.model.Schedule
import com.shangmentiyu.sportscoach.ui.home.HomeViewModel
import com.shangmentiyu.sportscoach.ui.home.IosCard
import com.shangmentiyu.sportscoach.ui.operation.OperationViewModel
import com.shangmentiyu.sportscoach.ui.theme.appOnSurface
import com.shangmentiyu.sportscoach.ui.theme.appOnSurfaceVariant
import com.shangmentiyu.sportscoach.ui.theme.appPrimary
import com.shangmentiyu.sportscoach.ui.theme.appSurface
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * EVOLVE 俱乐部 · 首页仪表盘。
 *
 * 复用 [HomeViewModel] / [OperationViewModel]：两者全部经
 * AppDatabase.getDatabase() 取数，俱乐部模式下自动落在
 * sports_coach_club_db（ModeManager 选库），与上门体育物理隔离。
 *
 * 内容：EVOLVE 品牌头 → 今日概览（排课/已签到/待签到/学员数）→
 * 续费提醒横幅 → 今日课程列表（按时间排序，点击进入排课页）→ 底部快捷入口。
 * 数据全部来自 Room Flow（响应式），无下拉刷新必要——数据变化即刷新。
 */
@Composable
fun ClubHomeScreen(
    vm: HomeViewModel,
    opVm: OperationViewModel,
    onCheckIn: () -> Unit,
    onSchedule: () -> Unit,
    onStudents: () -> Unit,
    onCoaches: () -> Unit,
    onLessons: () -> Unit
) {
    val students by vm.students.collectAsStateWithLifecycle()
    val todayLessons by vm.todayLessons.collectAsStateWithLifecycle()
    val renewalAlerts by vm.renewalAlerts.collectAsStateWithLifecycle()
    val schedules by opVm.schedules.collectAsStateWithLifecycle()

    val today = remember { LocalDate.now() }
    val todayStr = remember(today) {
        today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.getDefault()))
    }
    // Schedule.dayOfWeek：1=周一 ... 7=周日，与 LocalDate.dayOfWeek.value 一致
    val todayDayOfWeek = remember(today) { today.dayOfWeek.value }

    // 今日有效排课：启用 + 生效期内 + 周几匹配，按开始时间排序
    val todaySchedules = remember(schedules, todayStr, todayDayOfWeek) {
        schedules.filter { s ->
            s.isActive &&
                s.dayOfWeek == todayDayOfWeek &&
                s.startDate <= todayStr &&
                (s.endDate.isBlank() || s.endDate >= todayStr)
        }.sortedBy { it.startTime }
    }

    // 每条排课的签到状态：从今日课时记录反查（学员名匹配；小班课成员各自有排课条目）
    val lessonByStudent = remember(todayLessons) {
        todayLessons.associateBy { it.studentName }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp, end = 16.dp, top = 8.dp, bottom = 160.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // === 品牌头 ===
        item(key = "header") { ClubBrandHeader(today) }

        // === 今日概览 ===
        item(key = "overview") {
            IosCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatItem("今日排课", "${todaySchedules.size}")
                    StatItem("已签到", "${todayLessons.size}")
                    StatItem(
                        "待签到",
                        "${(todaySchedules.size - todayLessons.size).coerceAtLeast(0)}"
                    )
                    StatItem("学员总数", "${students.size}")
                }
            }
        }

        // === 续费/余额提醒（有提醒才显示，点击进课时管理） ===
        if (renewalAlerts.isNotEmpty()) {
            item(key = "renewal") {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onLessons),
                    shape = RoundedCornerShape(14.dp),
                    color = appPrimary().copy(alpha = 0.10f)
                ) {
                    Text(
                        text = "⚡ ${renewalAlerts.size} 位学员课时余额不足或即将过期，点击处理",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = appPrimary(),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
            }
        }

        // === 今日课程 ===
        item(key = "today_title") {
            Text(
                text = "今日课程",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = appOnSurface(),
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        if (todaySchedules.isEmpty()) {
            item(key = "today_empty") {
                IosCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("今天没有排课", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = appOnSurface())
                        Spacer(Modifier.height(4.dp))
                        Text("去「排课」给学员安排训练吧", fontSize = 12.sp, color = appOnSurfaceVariant())
                    }
                }
            }
        } else {
            items(todaySchedules.size, key = { todaySchedules[it].id }) { i ->
                TodayCourseCard(
                    schedule = todaySchedules[i],
                    lessonStatus = lessonByStudent[todaySchedules[i].studentName]?.status,
                    onClick = onSchedule
                )
            }
        }

        // === 快捷入口 ===
        item(key = "quick_title") {
            Text(
                text = "快捷入口",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = appOnSurface(),
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        item(key = "quick_grid") {
            IosCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 18.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    QuickEntry(Icons.Outlined.TaskAlt, "上课签到", onCheckIn)
                    QuickEntry(Icons.Outlined.CalendarMonth, "排课", onSchedule)
                    QuickEntry(Icons.Outlined.Group, "学员管理", onStudents)
                    QuickEntry(Icons.Outlined.SupervisorAccount, "教练管理", onCoaches)
                }
            }
        }
    }
}

/** EVOLVE 品牌头：黑白极简字标 + 日期行 */
@Composable
private fun ClubBrandHeader(today: LocalDate) {
    val weekday = when (today.dayOfWeek.value) {
        1 -> "一"; 2 -> "二"; 3 -> "三"; 4 -> "四"; 5 -> "五"; 6 -> "六"; else -> "日"
    }
    Column(modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)) {
        Text(
            text = "EVOLVE",
            fontSize = 30.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 6.sp,
            color = appOnSurface()
        )
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(6.dp)
                    .background(appPrimary(), CircleShape)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "进化体育 · ${today.monthValue}月${today.dayOfMonth}日 星期$weekday",
                fontSize = 13.sp,
                color = appOnSurfaceVariant(),
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = appOnSurface()
        )
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = 11.sp, color = appOnSurfaceVariant())
    }
}

/** 今日课程卡片：时间轴圆点 + 课程信息 + 签到状态徽章 */
@Composable
private fun TodayCourseCard(
    schedule: Schedule,
    lessonStatus: String?,
    onClick: () -> Unit
) {
    IosCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 时间列
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(64.dp)
            ) {
                Text(
                    text = schedule.startTime,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = appOnSurface()
                )
                Text(
                    text = schedule.endTime(),
                    fontSize = 11.sp,
                    color = appOnSurfaceVariant()
                )
            }
            Spacer(Modifier.width(14.dp))
            // 分隔竖线
            Box(
                Modifier
                    .size(width = 3.dp, height = 36.dp)
                    .background(
                        brush = Brush.verticalGradient(
                            listOf(appPrimary(), appPrimary().copy(alpha = 0.15f))
                        ),
                        shape = RoundedCornerShape(2.dp)
                    )
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = schedule.studentName,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = appOnSurface()
                )
                Spacer(Modifier.height(2.dp))
                val coachText = if (schedule.coachName.isNotBlank()) " · ${schedule.coachName}" else ""
                val locText = if (schedule.location.isNotBlank()) " · ${schedule.location}" else ""
                Text(
                    text = "${schedule.lessonType}$coachText$locText",
                    fontSize = 12.sp,
                    color = appOnSurfaceVariant(),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
            // 签到状态徽章
            val (badgeText, badgeColor) = when (lessonStatus) {
                "已签退" -> "已签退" to Color(0xFF10B981)
                "已签到" -> "已签到" to appPrimary()
                else -> "待上课" to Color(0xFF9B9B9B)
            }
            Surface(shape = RoundedCornerShape(6.dp), color = badgeColor.copy(alpha = 0.12f)) {
                Text(
                    text = badgeText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = badgeColor,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}

@Composable
private fun QuickEntry(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(appPrimary().copy(alpha = 0.10f), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = appPrimary(), modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, fontSize = 11.sp, color = appOnSurface())
    }
}
