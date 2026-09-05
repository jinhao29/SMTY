package com.shangmentiyu.sportscoach.ui.coach

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.shangmentiyu.sportscoach.data.model.Coach
import com.shangmentiyu.sportscoach.data.model.CoachRole
import com.shangmentiyu.sportscoach.ui.theme.AppTextField
import com.shangmentiyu.sportscoach.ui.theme.GlassAlertDialog
import com.shangmentiyu.sportscoach.ui.theme.Spacing
import com.shangmentiyu.sportscoach.ui.theme.StyledDropdown

/**
 * 教练新增/编辑弹窗：档案 + 角色 + 上级 + 薪资参数。
 *
 * 薪资字段按角色显隐：兼职只填课时费；全职填底薪+课时费；
 * 合伙人另填分成比例。上级下拉只列出层级更高的在职教练。
 */
@Composable
fun CoachEditDialog(
    initial: Coach?,
    candidates: List<Coach>,
    onDismiss: () -> Unit,
    onSave: (Coach) -> Unit
) {
    val isNew = initial == null
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var phone by remember { mutableStateOf(initial?.phone ?: "") }
    var role by remember { mutableStateOf(initial?.role ?: CoachRole.PARTTIME) }
    var superiorId by remember { mutableStateOf(initial?.superiorId ?: "") }
    var hireDate by remember { mutableStateOf(initial?.hireDate ?: CoachManageViewModel.todayString()) }
    var specialty by remember { mutableStateOf(initial?.specialty ?: "") }
    var certificates by remember { mutableStateOf(initial?.certificates ?: "") }
    var baseSalary by remember { mutableStateOf(initial?.baseSalary?.toString() ?: "") }
    var lessonRate by remember { mutableStateOf(initial?.lessonRate?.toString() ?: "") }
    var commissionRate by remember { mutableStateOf(initial?.commissionRate?.toString() ?: "") }
    var dailyLimit by remember { mutableStateOf((initial?.dailyLimit ?: 8).toString()) }
    var note by remember { mutableStateOf(initial?.note ?: "") }

    // 上级候选：在职 + 层级高于当前所选角色
    val superiorCandidates = remember(role, candidates) {
        candidates.filter { it.status == "在职" && CoachRole.rank(it.role) > CoachRole.rank(role) }
    }

    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = if (isNew) "新增教练" else "编辑教练",
        confirmButton = {
            Button(onClick = {
                onSave(
                    Coach(
                        name = name.trim(),
                        phone = phone.trim(),
                        specialty = specialty.trim(),
                        hireDate = hireDate,
                        status = initial?.status ?: "在职",
                        role = role,
                        superiorId = superiorId.ifBlank { null },
                        certificates = certificates.trim(),
                        baseSalary = baseSalary.toDoubleOrNull() ?: 0.0,
                        lessonRate = lessonRate.toDoubleOrNull() ?: 0.0,
                        commissionRate = commissionRate.toDoubleOrNull() ?: 0.0,
                        dailyLimit = dailyLimit.toIntOrNull() ?: 8,
                        note = note.trim(),
                        createdAt = initial?.createdAt ?: System.currentTimeMillis()
                    )
                )
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            AppTextField(
                value = name, onValueChange = { name = it },
                label = { Text("姓名 *") },
                singleLine = true,
                enabled = isNew  // 主键不可改，避免关联数据失联
            )
            AppTextField(
                value = phone, onValueChange = { phone = it },
                label = { Text("手机号") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
            )
            StyledDropdown(
                selected = role,
                options = CoachRole.ALL,
                optionLabel = { CoachRole.label(it) },
                optionIcon = { Icons.Outlined.Person },
                onSelected = {
                    role = it
                    // 切换角色后原上级可能不再合法，清空重选
                    superiorId = ""
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = "选择角色"
            )
            if (superiorCandidates.isNotEmpty()) {
                StyledDropdown(
                    selected = superiorCandidates.firstOrNull { it.name == superiorId },
                    options = superiorCandidates,
                    optionLabel = { "${it.name}（${CoachRole.label(it.role)}）" },
                    optionIcon = { Icons.Outlined.Person },
                    onSelected = { superiorId = it.name },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = "所属上级（可选）"
                )
            }
            AppTextField(
                value = hireDate, onValueChange = { hireDate = it },
                label = { Text("入职日期 (yyyy-MM-dd)") }, singleLine = true
            )
            AppTextField(
                value = specialty, onValueChange = { specialty = it },
                label = { Text("擅长项目（逗号分隔，如：田径,跳绳）") }, singleLine = true
            )
            AppTextField(
                value = certificates, onValueChange = { certificates = it },
                label = { Text("资质证书（逗号分隔）") }, singleLine = true
            )
            if (role != CoachRole.PARTTIME) {
                AppTextField(
                    value = baseSalary, onValueChange = { baseSalary = it },
                    label = { Text("底薪（元/月）") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
            }
            AppTextField(
                value = lessonRate, onValueChange = { lessonRate = it },
                label = { Text("课时费（元/节）") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
            if (role == CoachRole.PARTNER_L1 || role == CoachRole.PARTNER_L2) {
                AppTextField(
                    value = commissionRate, onValueChange = { commissionRate = it },
                    label = {
                        Text(
                            if (role == CoachRole.PARTNER_L1) "净利润分红比例（%）"
                            else "团队课时费提成比例（%）"
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
            }
            AppTextField(
                value = dailyLimit, onValueChange = { dailyLimit = it },
                label = { Text("单日排课上限（节，负荷预警）") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            AppTextField(
                value = note, onValueChange = { note = it },
                label = { Text("备注") }
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "* 姓名保存后不可修改",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
