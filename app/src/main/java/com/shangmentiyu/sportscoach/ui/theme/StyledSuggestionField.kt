package com.shangmentiyu.sportscoach.ui.theme

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/**
 * StyledDropdown 风格的下拉建议输入框（预设建议 + 自定义输入）。
 *
 * 场景：字段既需快捷选择预设值，又允许自由输入自定义值
 * （StyledDropdown 为纯选择组件，无法表达"可编辑"语义，
 * 故以 wrapper 形式统一视觉：浅灰胶囊输入框 + 预设下拉）。
 *
 * 数据源绑定与选中回调和 [AppTextField] 完全一致（value/onValueChange 受控）。
 *
 * @param value 当前输入值（受控）
 * @param onValueChange 输入/选中预设值时回调
 * @param label 输入框标签
 * @param presets 下拉建议预设列表
 * @param modifier 透传修饰符（如 weight/fillMaxWidth）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StyledSuggestionField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    presets: List<String>,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        AppTextField(
            value = value,
            onValueChange = onValueChange,
            readOnly = false,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryEditable, enabled = true),
            singleLine = true,
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            presets.forEach { preset ->
                DropdownMenuItem(
                    text = { Text(preset) },
                    onClick = {
                        onValueChange(preset)
                        expanded = false
                    }
                )
            }
        }
    }
}
