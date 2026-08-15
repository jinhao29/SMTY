package com.shangmentiyu.sportscoach.ui.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import com.shangmentiyu.sportscoach.ui.theme.appOutline

/**
 * 长期排课（重复设置）开关：勾选后每周自动生成对应时间的课记录。
 *
 * 状态与回调由父级（ScheduleEditDialog）提升传入。
 */
@Composable
fun ScheduleRepeatSection(
    isLongTerm: Boolean,
    onLongTermChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "长期排课",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                "勾选后每周自动生成对应时间的课记录",
                style = MaterialTheme.typography.bodySmall,
                color = appOutline()
            )
        }
        Switch(
            checked = isLongTerm,
            onCheckedChange = onLongTermChange
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF5F7FA)
@Composable
private fun ScheduleRepeatSectionPreview() {
    ScheduleRepeatSection(
        isLongTerm = false,
        onLongTermChange = {}
    )
}
