package com.shangmentiyu.sportscoach.ui.schedule

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.shangmentiyu.sportscoach.ui.theme.PrimaryButton
import com.shangmentiyu.sportscoach.ui.theme.Spacing

/**
 * 底部固定保存按钮栏（iOS 风格 Bottom Bar）。
 *
 * 校验与提交逻辑由父级（ScheduleEditDialog）提升传入，本组件只负责渲染按钮。
 */
@Composable
fun ScheduleSubmitButton(
    onSave: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = Spacing.screenH)
            .padding(top = Spacing.sm, bottom = 48.dp)
    ) {
        PrimaryButton(
            text = "保存课程",
            onClick = onSave,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF5F7FA)
@Composable
private fun ScheduleSubmitButtonPreview() {
    ScheduleSubmitButton(onSave = {})
}
