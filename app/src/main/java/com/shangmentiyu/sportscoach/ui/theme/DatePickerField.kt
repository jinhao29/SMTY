package com.shangmentiyu.sportscoach.ui.theme

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 日期选择器共享组件。
 *
 * 提供两种风格：
 * - [IosDatePickerRow]：iOS 分组表单风格（Label + 值 + 日历图标），用于 AddStudentScreen
 * - [OutlinedDatePickerField]：Material OutlinedTextField 风格（只读，点击触发选择），用于 OperationScreen 弹窗
 *
 * 内部统一使用 Material3 [DatePickerDialog]，点击触发选择，确认后回调 YYYY-MM-DD 字符串。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IosDatePickerRow(
    label: String,
    dateStr: String,
    onDateChange: (String) -> Unit,
    placeholder: String = "选择日期",
    showDivider: Boolean = true
) {
    var showPicker by remember { mutableStateOf(false) }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showPicker = true }
                .padding(horizontal = Spacing.md, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.width(72.dp)
            )
            Text(
                if (dateStr.isBlank()) placeholder else dateStr,
                style = MaterialTheme.typography.bodyLarge,
                color = if (dateStr.isBlank())
                    appOnSurface().copy(alpha = 0.3f)
                else
                    appOnSurface().copy(alpha = 0.6f),
                modifier = Modifier.weight(1f)
            )
            Icon(
                Icons.Outlined.CalendarMonth,
                contentDescription = "选择日期",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 100.dp),
                thickness = 0.5.dp,
                color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.06f)
            )
        }
    }

    if (showPicker) {
        val initialMillis = parseDateToMillis(dateStr)
        val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.selectedDateMillis?.let { onDateChange(formatMillisToDate(it)) }
                        showPicker = false
                    }
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("取消") }
            }
        ) {
            DatePicker(state = state)
        }
    }
}

/**
 * OutlinedTextField 风格的日期选择器。
 *
 * 只读输入框 + 日历图标，点击触发 DatePickerDialog。
 * 用于 OperationScreen 的弹窗表单。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutlinedDatePickerField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String = "选择日期",
    modifier: Modifier = Modifier
) {
    var showPicker by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = value,
        onValueChange = { },
        readOnly = true,
        label = { Text(label) },
        singleLine = true,
        modifier = modifier
            .fillMaxWidth()
            .clickable { showPicker = true },
        trailingIcon = {
            Icon(
                Icons.Outlined.CalendarMonth,
                contentDescription = "选择日期",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { showPicker = true }
            )
        }
    )

    if (showPicker) {
        val initialMillis = parseDateToMillis(value)
        val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.selectedDateMillis?.let { onValueChange(formatMillisToDate(it)) }
                        showPicker = false
                    }
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("取消") }
            }
        ) {
            DatePicker(state = state)
        }
    }
}

/** yyyy-MM-dd 字符串 → 毫秒时间戳（解析失败返回 null，由 DatePicker 使用今日默认值）
 *  线程安全：基于 [LocalDate.parse] + [DateTimeFormatter]，替代 [java.text.SimpleDateFormat] */
private fun parseDateToMillis(dateStr: String): Long? {
    if (dateStr.isBlank()) return null
    return try {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.getDefault())
        LocalDate.parse(dateStr, formatter)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    } catch (e: Exception) {
        null
    }
}

/** 毫秒时间戳 → yyyy-MM-dd 字符串（DatePicker 返回的是 UTC 当天零点毫秒）
 *  线程安全：基于 [Instant.ofEpochMilli] + [ZoneId] 系统时区转换 */
private fun formatMillisToDate(millis: Long): String {
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.getDefault())
    return Instant.ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .format(formatter)
}

// =================== 时间选择器 ===================

/**
 * OutlinedTextField 风格的时间选择器。
 *
 * 只读输入框 + 时钟图标，点击触发 TimePicker 弹窗。
 * 时间格式为 HH:mm（24 小时制）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutlinedTimePickerField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String = "选择时间",
    modifier: Modifier = Modifier
) {
    var showPicker by remember { mutableStateOf(false) }

    val (initHour, initMin) = parseTimeToHM(value)

    OutlinedTextField(
        value = value,
        onValueChange = { },
        readOnly = true,
        label = { Text(label) },
        singleLine = true,
        modifier = modifier
            .fillMaxWidth()
            .clickable { showPicker = true },
        trailingIcon = {
            Icon(
                Icons.Outlined.Schedule,
                contentDescription = "选择时间",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { showPicker = true }
            )
        }
    )

    if (showPicker) {
        val state = rememberTimePickerState(
            initialHour = initHour,
            initialMinute = initMin,
            is24Hour = true
        )
        GlassAlertDialog(
            onDismissRequest = { showPicker = false },
            title = "选择时间",
            confirmButton = {
                TextButton(
                    onClick = {
                        onValueChange(
                            "${state.hour.toString().padStart(2, '0')}:" +
                            "${state.minute.toString().padStart(2, '0')}"
                        )
                        showPicker = false
                    }
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("取消") }
            },
            content = { TimePicker(state = state) }
        )
    }
}

/** HH:mm 字符串 → Pair(时, 分)，解析失败返回 (10, 0) */
private fun parseTimeToHM(timeStr: String): Pair<Int, Int> {
    if (timeStr.isBlank()) return 10 to 0
    return try {
        val parts = timeStr.split(":")
        parts[0].toInt() to parts[1].toInt()
    } catch (e: Exception) {
        10 to 0
    }
}
