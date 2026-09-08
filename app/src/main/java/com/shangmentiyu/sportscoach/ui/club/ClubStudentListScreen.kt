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
import androidx.compose.material.icons.outlined.PersonAddAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shangmentiyu.sportscoach.data.model.Lesson
import com.shangmentiyu.sportscoach.data.model.Student
import com.shangmentiyu.sportscoach.ui.home.HomeViewModel
import com.shangmentiyu.sportscoach.ui.home.IosCard
import com.shangmentiyu.sportscoach.ui.operation.OperationViewModel
import com.shangmentiyu.sportscoach.ui.theme.appOnSurface
import com.shangmentiyu.sportscoach.ui.theme.appOnSurfaceVariant
import com.shangmentiyu.sportscoach.ui.theme.appPrimary
import com.shangmentiyu.sportscoach.ui.theme.appSurface

/**
 * EVOLVE 俱乐部 · 学员管理。
 *
 * 数据源复用 [HomeViewModel] / [OperationViewModel]（俱乐部库自动隔离）：
 * - 搜索：姓名 / 手机号 / 课程包名称（课程包名命中即显示对应学员）
 * - 筛选：按学段（初中 / 高中，由 grade 编码推导，展示层纯计算；俱乐部学员均为初中及以上）
 * - 卡片：姓名 / 年级组 / 剩余课时 / 课时包到期日，点击弹出学员详情
 * - 详情：基本信息 + 课时概览 + 最近课时记录 + 编辑/成长报告入口
 *
 * 说明：数据模型暂无「停课/毕业」独立状态字段（Student 仅有软删除 isActive），
 * 状态筛选待二期字段落地后接入。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClubStudentListScreen(
    vm: HomeViewModel,
    opVm: OperationViewModel,
    onAddStudent: () -> Unit,
    onEditStudent: (String) -> Unit,
    onGrowth: (String) -> Unit
) {
    val students by vm.students.collectAsStateWithLifecycle()
    val remainingMap by vm.remainingMap.collectAsStateWithLifecycle()
    val expireDateMap by vm.expireDateMap.collectAsStateWithLifecycle()
    val allLessons by vm.allLessons.collectAsStateWithLifecycle()
    val packages by opVm.packages.collectAsStateWithLifecycle()

    var query by remember { mutableStateOf("") }
    var uGroup by remember { mutableStateOf("全部") }
    var detailStudent by remember { mutableStateOf<Student?>(null) }

    // 活跃学员（排除软删除）
    val activeStudents = remember(students) { students.filter { it.isActive } }

    // 课程包名 → 学员名映射（搜索"课程"用）
    val courseToStudents = remember(packages) {
        packages.associate { it.studentName to it.name }
    }

    val filtered = remember(activeStudents, query, uGroup, courseToStudents) {
        val key = query.trim().lowercase(java.util.Locale.getDefault())
        activeStudents.filter { s ->
            val hit = key.isBlank() ||
                s.name.lowercase(java.util.Locale.getDefault()).contains(key) ||
                s.phone.contains(key) ||
                courseToStudents[s.name]?.lowercase(java.util.Locale.getDefault())?.contains(key) == true
            hit && (uGroup == "全部" || uGroupOf(s.grade) == uGroup)
        }.sortedWith(compareBy(comparator = java.text.Collator.getInstance(java.util.Locale.CHINESE)) { it.name })
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // === 顶部：标题 + 新增 ===
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("学员管理", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = appOnSurface())
                Text(
                    "共 ${activeStudents.size} 名学员",
                    fontSize = 12.sp,
                    color = appOnSurfaceVariant()
                )
            }
            IconButton(onClick = onAddStudent) {
                Icon(
                    Icons.Outlined.PersonAddAlt,
                    contentDescription = "新增学员",
                    tint = appPrimary()
                )
            }
        }

        // === 搜索框 ===
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("搜索姓名 / 手机号 / 课程", fontSize = 14.sp) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = appPrimary(),
                unfocusedBorderColor = appOnSurfaceVariant().copy(alpha = 0.25f)
            )
        )

        // === 年级组筛选 ===
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("全部", "初中", "高中").forEach { g ->
                FilterChip(
                    selected = uGroup == g,
                    onClick = { uGroup = g },
                    label = { Text(g, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = appPrimary().copy(alpha = 0.12f),
                        selectedLabelColor = appPrimary()
                    )
                )
            }
        }

        // === 学员列表 ===
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp, end = 16.dp, top = 12.dp, bottom = 160.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(filtered.size, key = { filtered[it].name }) { i ->
                val s = filtered[i]
                ClubStudentCard(
                    student = s,
                    remaining = remainingMap[s.name],
                    expireDate = expireDateMap[s.name],
                    onClick = { detailStudent = s }
                )
            }
            if (filtered.isEmpty()) {
                item(key = "empty") {
                    IosCard {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                if (query.isBlank()) "还没有学员，点右上角 + 新增"
                                else "没有匹配「$query」的学员",
                                fontSize = 14.sp,
                                color = appOnSurfaceVariant()
                            )
                        }
                    }
                }
            }
        }
    }

    // === 学员详情底部弹层 ===
    detailStudent?.let { s ->
        ModalBottomSheet(
            onDismissRequest = { detailStudent = null },
            containerColor = appSurface()
        ) {
            StudentDetailSheet(
                student = s,
                remaining = remainingMap[s.name],
                expireDate = expireDateMap[s.name],
                lessons = allLessons.filter { it.studentName == s.name }
                    .sortedByDescending { it.date + it.time }
                    .take(5),
                onEdit = {
                    detailStudent = null
                    onEditStudent(s.name)
                },
                onGrowth = {
                    detailStudent = null
                    onGrowth(s.name)
                }
            )
        }
    }
}

/**
 * 年级编码 → 俱乐部年级组标签（初中/高中，展示层纯推导）。
 * 俱乐部学员均为初中及以上：初一~初三/中考归"初中"，高一~高三归"高中"；
 * 学龄前/小学编码（0-6）与未填均为异常数据，归入"初中"兜底。
 */
internal fun uGroupOf(grade: String): String {
    val num = grade.trim().toIntOrNull() ?: return "初中"
    return if (num in 10..12) "高中" else "初中"
}

/** 年级编码 → 显示名（俱乐部只出现初中及以上；异常编码显示"年级未填"） */
internal fun gradeLabel(grade: String): String {
    return when (grade.trim().toIntOrNull()) {
        7 -> "初一"; 8 -> "初二"; 9 -> "初三"
        10 -> "高一"; 11 -> "高二"; 12 -> "高三"; 13 -> "中考"
        else -> "年级未填"
    }
}

@Composable
private fun ClubStudentCard(
    student: Student,
    remaining: Int?,
    expireDate: String?,
    onClick: () -> Unit
) {
    IosCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 头像（姓名首字）
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(appPrimary().copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = student.name.take(1),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = appPrimary()
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = student.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = appOnSurface()
                    )
                    Spacer(Modifier.width(6.dp))
                    Surface(shape = RoundedCornerShape(5.dp), color = appPrimary().copy(alpha = 0.10f)) {
                        Text(
                            text = "${uGroupOf(student.grade)} · ${gradeLabel(student.grade)}",
                            fontSize = 10.sp,
                            color = appPrimary(),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    text = if (student.phone.isNotBlank()) student.phone else "未留电话",
                    fontSize = 12.sp,
                    color = appOnSurfaceVariant(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            // 右侧：剩余课时 + 到期日
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = when {
                        remaining == null || remaining < 0 -> "未购课"
                        else -> "剩余 ${remaining} 课时"
                    },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (remaining != null && remaining in 1..3) appPrimary() else appOnSurface()
                )
                if (!expireDate.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "$expireDate 到期",
                        fontSize = 11.sp,
                        color = appOnSurfaceVariant()
                    )
                }
            }
        }
    }
}

/** 学员详情底部弹层：基本信息 + 课时概览 + 最近记录 + 操作入口 */
@Composable
private fun StudentDetailSheet(
    student: Student,
    remaining: Int?,
    expireDate: String?,
    lessons: List<Lesson>,
    onEdit: () -> Unit,
    onGrowth: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp)
    ) {
        // 姓名 + 年级组
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = student.name,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = appOnSurface()
            )
            Spacer(Modifier.width(8.dp))
            Surface(shape = RoundedCornerShape(6.dp), color = appPrimary().copy(alpha = 0.10f)) {
                Text(
                    text = "${uGroupOf(student.grade)} · ${gradeLabel(student.grade)}",
                    fontSize = 12.sp,
                    color = appPrimary(),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
        Spacer(Modifier.height(14.dp))

        // 基本信息
        InfoRow("性别", student.gender)
        InfoRow("年龄", if (student.age > 0) "${student.age} 岁" else "未填")
        InfoRow("学校", student.school.ifBlank { "未填" })
        InfoRow("电话", student.phone.ifBlank { "未填" })
        if (student.heightCm > 0) InfoRow("身高体重", "${student.heightCm}cm / ${student.weightKg.toInt()}kg")
        InfoRow("剩余课时", when {
            remaining == null || remaining < 0 -> "未购课"
            else -> "$remaining 课时" + (expireDate?.let { " · $it 到期" } ?: "")
        })
        Spacer(Modifier.height(12.dp))

        // 最近课时记录
        Text("最近课时", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = appOnSurface())
        Spacer(Modifier.height(6.dp))
        if (lessons.isEmpty()) {
            Text("暂无课时记录", fontSize = 13.sp, color = appOnSurfaceVariant())
        } else {
            lessons.forEach { l ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${l.date} ${l.time}",
                        fontSize = 13.sp,
                        color = appOnSurface(),
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = l.coach.ifBlank { "—" },
                        fontSize = 12.sp,
                        color = appOnSurfaceVariant(),
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Surface(shape = RoundedCornerShape(5.dp), color = appOnSurfaceVariant().copy(alpha = 0.10f)) {
                        Text(
                            text = l.status,
                            fontSize = 10.sp,
                            color = appOnSurfaceVariant(),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(18.dp))

        // 操作按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TextButton(
                onClick = onEdit,
                modifier = Modifier.weight(1f)
            ) { Text("编辑资料", color = appPrimary()) }
            TextButton(
                onClick = onGrowth,
                modifier = Modifier.weight(1f)
            ) { Text("成长报告", color = appPrimary()) }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(label, fontSize = 13.sp, color = appOnSurfaceVariant(), modifier = Modifier.width(72.dp))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = appOnSurface())
    }
}
