package com.shangmentiyu.sportscoach.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

/**
 * 设计系统：无边框浅灰输入框（全局文本字段统一规格）。
 *
 * === 视觉规格 ===
 * - 背景：浅灰 #F0F0F0（暗色模式自动切换为 #3A3A3C）
 * - 圆角：12dp 大圆角
 * - 边框：完全无边框（透明），替代 Material 默认 1dp 实色描边
 * - 光标/错误态：珊瑚橙主色，保证可用性
 *
 * === 用法 ===
 * 所有 `OutlinedTextField` / `ExposedDropdownMenuBox` 内的文本字段统一调用：
 * ```
 * OutlinedTextField(
 *     ...
 *     shape = AppTextFieldShape,
 *     colors = appTextFieldColors(),
 * )
 * ```
 *
 * @see AppTextFieldShape 统一圆角
 */
@Composable
fun appTextFieldColors(): TextFieldColors {
    // v48：统一读取主题令牌，跟随手动深色模式开关
    val container = appSurfaceVariant()
    val label = appOnSurfaceVariant()
    val accent = appPrimary()
    return OutlinedTextFieldDefaults.colors(
        // 背景浅灰（设计系统指定 #F0F0F0）
        focusedContainerColor = container,
        unfocusedContainerColor = container,
        disabledContainerColor = container.copy(alpha = 0.5f),
        // 不设置任何 Border —— 全部透明
        focusedBorderColor = Color.Transparent,
        unfocusedBorderColor = Color.Transparent,
        disabledBorderColor = Color.Transparent,
        // 错误态用主色描边，保证校验可见性（透明度 0.9）
        errorBorderColor = accent.copy(alpha = 0.9f),
        // 标签文字次级灰
        focusedLabelColor = label,
        unfocusedLabelColor = label,
        disabledLabelColor = label.copy(alpha = 0.5f),
        errorLabelColor = accent,
        // 光标珊瑚橙
        cursorColor = accent,
    )
}

/** 无边框输入框统一圆角（12dp 大圆角）。 */
val AppTextFieldShape: RoundedCornerShape = RoundedCornerShape(12.dp)

/**
 * 全局标准化纯文本输入框：浅灰大圆角胶囊 + 可选左侧图标。
 *
 * 外观与 [appTextFieldColors] / [AppTextFieldShape] 完全一致，
 * 仅收敛调用点参数，交互行为等同 OutlinedTextField。
 */
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    suffix: (@Composable () -> Unit)? = null,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = Int.MAX_VALUE,
    enabled: Boolean = true,
    textStyle: TextStyle = TextStyle.Default,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    readOnly: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        label = label,
        placeholder = placeholder,
        supportingText = supportingText,
        suffix = suffix,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        textStyle = textStyle,
        keyboardOptions = keyboardOptions,
        readOnly = readOnly,
        shape = AppTextFieldShape,
        colors = appTextFieldColors(),
    )
}
