package com.shangmentiyu.sportscoach.ui.coach

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import com.shangmentiyu.sportscoach.ui.theme.AppTopBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shangmentiyu.sportscoach.data.model.Coach
import com.shangmentiyu.sportscoach.data.model.CoachRole
import com.shangmentiyu.sportscoach.ui.theme.AppTextField
import com.shangmentiyu.sportscoach.ui.theme.Spacing
import com.shangmentiyu.sportscoach.ui.theme.appOnPrimary
import com.shangmentiyu.sportscoach.ui.theme.appOnSurfaceVariant
import com.shangmentiyu.sportscoach.ui.theme.appOnWarningContainer
import com.shangmentiyu.sportscoach.ui.theme.appPrimary
import com.shangmentiyu.sportscoach.ui.theme.appWarningContainer
import com.shangmentiyu.sportscoach.ui.theme.glassTopAppBarColors

/**
 * 教练管理主页：档案 / 学员排课 / 排班·工作量 / 团队 / 薪资 五 Tab。
 *
 * @param onBack 返回回调；null=作为底部导航主页面（隐藏返回箭头）
 */
@OptIn(ExperimentalMaterial3Api::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@Composable
fun CoachManageScreen(
    viewModel: CoachManageViewModel,
    onBack: (() -> Unit)? = null
) {
    val toast by viewModel.toast.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(toast) {
        toast?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.clearToast()
        }
    }

    // 默认落在"排课"Tab（教练管理核心工作流：选教练 → 看周课表 → 点方块排课）
    var tabIndex by remember { mutableStateOf(1) }
    val tabs = listOf("档案", "排课", "工作量", "团队", "薪资")

    Scaffold(
        topBar = {
            AppTopBar(
                title = { Text("教练管理", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    }
                },
                colors = glassTopAppBarColors(),
                shareLabel = "教练管理",
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // === TabRow 美化（v51）：
            // 1. containerColor = Transparent 去掉 M3 1.3 surfaceContainer 浅灰底，让顶部与背景一致
            // 2. divider = {} 去掉默认顶部 HorizontalDivider（顶栏已用 surface 衬底分隔，无需重复线条）
            // 3. 未选中 Tab 显式用 onSurfaceVariant（灰）替换默认 primary，避免"5 个全橙"过度强调
            // 4. 字号 13sp / Medium 字重，确保 5 个 tab 平分时"工作量"不被 truncate
            TabRow(
                selectedTabIndex = tabIndex,
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onBackground,
                divider = {}
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = tabIndex == index,
                        onClick = { tabIndex = index },
                        selectedContentColor = MaterialTheme.colorScheme.primary,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        text = {
                            Text(
                                title,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    )
                }
            }
            when (tabIndex) {
                0 -> CoachRosterTab(viewModel)
                1 -> CoachLessonGridTab(viewModel)
                2 -> CoachScheduleTab(viewModel)
                3 -> CoachTeamTab(viewModel)
                else -> CoachPayrollTab(viewModel)
            }
        }
    }
}

/** Tab 1：教练档案（筛选 + 列表 + 新增/编辑/启停/删除） */
@Composable
private fun CoachRosterTab(viewModel: CoachManageViewModel) {
    val coaches by viewModel.filteredCoaches.collectAsStateWithLifecycle()
    val allCoaches by viewModel.allCoaches.collectAsStateWithLifecycle()
    val roleFilter by viewModel.roleFilter.collectAsStateWithLifecycle()
    val statusFilter by viewModel.statusFilter.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()

    var editing by remember { mutableStateOf<Coach?>(null) }
    var showEdit by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Coach?>(null) }
    var bindingCoach by remember { mutableStateOf<Coach?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = Spacing.screenH, vertical = Spacing.md
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppTextField(
                            value = query,
                            onValueChange = viewModel::setQuery,
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("搜索姓名 / 专长 / 手机号") },
                            singleLine = true
                        )
                        Button(onClick = { editing = null; showEdit = true }) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("新增教练")
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        FilterDropdown(
                            options = listOf("") + CoachRole.ALL,
                            selected = roleFilter,
                            label = { if (it.isBlank()) "全部角色" else CoachRole.label(it) },
                            onSelect = viewModel::setRoleFilter,
                            modifier = Modifier.weight(1f)
                        )
                        FilterDropdown(
                            options = listOf("", "在职", "休假", "离职"),
                            selected = statusFilter,
                            label = { if (it.isBlank()) "全部状态" else it },
                            onSelect = viewModel::setStatusFilter,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            items(coaches, key = { it.name }) { coach ->
                CoachCard(
                    coach = coach,
                    onEdit = { editing = coach; showEdit = true },
                    onStatusChange = { viewModel.setStatus(coach, it) },
                    onDelete = { deleting = coach },
                    onBind = { bindingCoach = coach }
                )
            }
            if (coaches.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 64.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("暂无教练", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "点击上方「新增教练」添加第一位教练",
                            style = MaterialTheme.typography.bodySmall,
                            color = appOnSurfaceVariant()
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(160.dp)) }
    }

    if (showEdit) {
        CoachEditDialog(
            initial = editing,
            candidates = allCoaches,
            onDismiss = { showEdit = false },
            onSave = { coach ->
                viewModel.saveCoach(coach) { ok -> if (ok) showEdit = false }
            }
        )
    }
    deleting?.let { coach ->
        com.shangmentiyu.sportscoach.ui.theme.GlassAlertDialog(
            onDismissRequest = { deleting = null },
            title = "删除教练",
            confirmButton = {
                androidx.compose.material3.Button(onClick = {
                    viewModel.deleteCoach(coach)
                    deleting = null
                }) { Text("删除") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { deleting = null }) { Text("取消") }
            }
        ) {
            Text("确定删除「${coach.name}」？该操作不可恢复，历史课时记录将保留教练姓名。")
        }
    }
    bindingCoach?.let { coach ->
        BindStudentsDialog(
            coach = coach,
            viewModel = viewModel,
            onDismiss = { bindingCoach = null }
        )
    }
}

/**
 * 绑定学员弹窗（v34）：上半区已绑定名单（可解绑），
 * 下半区未绑定学员下拉选择后一键绑定。绑定关系用于"排课"Tab 的学员候选。
 */
@Composable
private fun BindStudentsDialog(
    coach: Coach,
    viewModel: CoachManageViewModel,
    onDismiss: () -> Unit
) {
    val allStudents by viewModel.students.collectAsStateWithLifecycle()
    val allBindings by viewModel.allBindings.collectAsStateWithLifecycle()

    val boundNames = remember(allBindings, coach.name) {
        allBindings.filter { it.coachName == coach.name }.map { it.studentName }.toSet()
    }
    val bound = allStudents.filter { it.name in boundNames }
    val unbound = allStudents.filter { it.name !in boundNames }
    var pick by remember { mutableStateOf<com.shangmentiyu.sportscoach.data.model.Student?>(null) }

    com.shangmentiyu.sportscoach.ui.theme.GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = "绑定学员 · ${coach.name}",
        confirmButton = {
            androidx.compose.material3.Button(onClick = onDismiss) { Text("完成") }
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(
                "已绑定 ${bound.size} 位学员",
                style = MaterialTheme.typography.labelMedium,
                color = appOnSurfaceVariant()
            )
            if (bound.isEmpty()) {
                Text(
                    "暂未绑定学员，从下方选择后绑定",
                    style = MaterialTheme.typography.bodySmall,
                    color = appOnSurfaceVariant()
                )
            } else {
                bound.forEach { student ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            student.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        androidx.compose.material3.TextButton(onClick = {
                            viewModel.unbindStudent(coach.name, student.name)
                        }) {
                            Text("解绑", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            Spacer(Modifier.height(Spacing.xs))
            if (unbound.isNotEmpty()) {
                com.shangmentiyu.sportscoach.ui.theme.StyledDropdown(
                    selected = pick,
                    options = unbound,
                    optionLabel = { it.name },
                    optionIcon = { Icons.Outlined.Person },
                    onSelected = { pick = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = "选择学员绑定"
                )
                androidx.compose.material3.TextButton(
                    onClick = {
                        pick?.let {
                            viewModel.bindStudent(coach.name, it)
                            pick = null
                        }
                    },
                    enabled = pick != null
                ) { Text("绑定该学员", color = appPrimary()) }
            } else if (bound.isNotEmpty()) {
                Text(
                    "全部学员已绑定",
                    style = MaterialTheme.typography.bodySmall,
                    color = appOnSurfaceVariant()
                )
            }
        }
    }
}

/** 教练卡片：渐变头像 + 角色/状态标签 + 专长 + 薪资概要 + 操作 */
@Composable
private fun CoachCard(
    coach: Coach,
    onEdit: () -> Unit,
    onStatusChange: (String) -> Unit,
    onDelete: () -> Unit,
    onBind: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .padding(Spacing.cardPadding)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(appPrimary(), appPrimary().copy(alpha = 0.75f))
                        ),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    coach.name.take(1),
                    color = appOnPrimary(),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.width(Spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(coach.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(Spacing.sm))
                    RoleTag(coach.role)
                    if (coach.status != "在职") {
                        Spacer(Modifier.width(4.dp))
                        StatusTag(coach.status)
                    }
                }
                val sub = listOfNotNull(
                    coach.phone.ifBlank { null },
                    coach.specialty.ifBlank { null }
                ).joinToString(" · ")
                if (sub.isNotBlank()) {
                    Text(sub, style = MaterialTheme.typography.bodySmall, color = appOnSurfaceVariant())
                }
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Outlined.Edit, contentDescription = "编辑", tint = appOnSurfaceVariant())
            }
        }
        Spacer(Modifier.height(Spacing.sm))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            InfoCell("课时费", if (coach.lessonRate > 0) "${coach.lessonRate}元/节" else "未设置")
            if (coach.role != CoachRole.PARTTIME) {
                InfoCell("底薪", if (coach.baseSalary > 0) "${coach.baseSalary}元" else "未设置")
            }
            if (coach.role == CoachRole.PARTNER_L1 || coach.role == CoachRole.PARTNER_L2) {
                InfoCell("分成", "${coach.commissionRate}%")
            }
            InfoCell("日上限", "${coach.dailyLimit}节")
        }
        Spacer(Modifier.height(Spacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            androidx.compose.material3.TextButton(onClick = onBind) {
                Icon(
                    Icons.Outlined.Groups,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = appPrimary()
                )
                Spacer(Modifier.width(4.dp))
                Text("学员", color = appPrimary())
            }
            var statusMenu by remember { mutableStateOf(false) }
            Box {
                androidx.compose.material3.TextButton(onClick = { statusMenu = true }) {
                    Icon(
                        Icons.Outlined.PowerSettingsNew,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = appPrimary()
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(coach.status, color = appPrimary())
                    Icon(
                        Icons.Outlined.KeyboardArrowDown,
                        contentDescription = "切换状态",
                        modifier = Modifier.size(14.dp),
                        tint = appPrimary()
                    )
                }
                DropdownMenu(expanded = statusMenu, onDismissRequest = { statusMenu = false }) {
                    listOf("在职", "休假", "离职").forEach { status ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (status == coach.status) "$status（当前）" else "设为$status",
                                    color = if (status == coach.status) appOnSurfaceVariant() else appPrimary()
                                )
                            },
                            onClick = {
                                statusMenu = false
                                onStatusChange(status)
                            }
                        )
                    }
                }
            }
            androidx.compose.material3.TextButton(onClick = onDelete) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.width(4.dp))
                Text("删除", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/** 角色标签（浅橙胶囊） */
@Composable
private fun RoleTag(role: String) {
    Tag(text = CoachRole.label(role), container = appPrimary().copy(alpha = 0.12f), content = appPrimary())
}

/** 状态标签：休假=琥珀警示色，离职=灰 */
@Composable
private fun StatusTag(status: String) {
    if (status == "休假") {
        Tag(text = status, container = appWarningContainer(), content = appOnWarningContainer())
    } else {
        Tag(
            text = status,
            container = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f),
            content = appOnSurfaceVariant()
        )
    }
}

@Composable
private fun Tag(text: String, container: androidx.compose.ui.graphics.Color, content: androidx.compose.ui.graphics.Color) {
    Box(
        modifier = Modifier
            .background(container, RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(text, color = content, fontSize = 11.sp)
    }
}

@Composable
private fun InfoCell(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = appOnSurfaceVariant())
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

/** 选项胶囊组（横向滚动，选中珊瑚橙实底，未选中灰）——仅排班对话框周几选择器仍在使用 */
@Composable
internal fun FilterChipGroup(
    options: List<String>,
    selected: String,
    label: (String) -> String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Box(
                modifier = Modifier
                    .background(
                        if (isSelected) appPrimary()
                        else MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(50)
                    )
                    .clickable { onSelect(option) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label(option),
                    color = if (isSelected) appOnPrimary() else appOnSurfaceVariant(),
                    fontSize = 12.sp
                )
            }
        }
    }
}

/** 筛选下拉（ExposedDropdownMenuBox，选中项珊瑚橙高亮，触摸区最小 48dp） */@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FilterDropdown(
    options: List<String>,
    selected: String,
    label: (String) -> String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .heightIn(min = 48.dp)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                .border(
                    1.dp,
                    if (expanded) appPrimary() else MaterialTheme.colorScheme.outlineVariant,
                    RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label(selected),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            Icon(
                Icons.Outlined.KeyboardArrowDown,
                contentDescription = null,
                tint = appOnSurfaceVariant(),
                modifier = Modifier.rotate(if (expanded) 180f else 0f)
            )
        }
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(12.dp),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            options.forEach { option ->
                val isSelected = option == selected
                DropdownMenuItem(
                    text = {
                        Text(
                            label(option),
                            fontSize = 14.sp,
                            color = if (isSelected) appPrimary() else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    }
                )
            }
        }
    }
}
