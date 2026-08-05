package com.shangmentiyu.sportscoach.ui.script

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shangmentiyu.sportscoach.data.repo.ScriptRepository
import org.koin.androidx.compose.koinViewModel
import com.shangmentiyu.sportscoach.ui.theme.LightPrimary
import com.shangmentiyu.sportscoach.ui.theme.Spacing
import com.shangmentiyu.sportscoach.ui.theme.appBackground
import com.shangmentiyu.sportscoach.ui.theme.appDividerColor
import com.shangmentiyu.sportscoach.ui.theme.appOnSurface
import com.shangmentiyu.sportscoach.ui.theme.appOnSurfaceVariant
import com.shangmentiyu.sportscoach.ui.theme.appPrimary

/**
 * 话术列表页（UI 层）。
 *
 * 展示所有已保存的话术项目，点击进入编辑页，长按删除。
 * 右下角 FAB 新建话术。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScriptListScreen(
    onBack: () -> Unit = {},
    onOpen: (String) -> Unit = {},
    onAdd: () -> Unit = {}
) {
    val vm: ScriptViewModel = koinViewModel()
    val context = LocalContext.current
    val scripts by vm.scripts.collectAsStateWithLifecycle()
    val toast by vm.toast.collectAsStateWithLifecycle()

    var pendingDelete by remember { mutableStateOf<ScriptRepository.ScriptItem?>(null) }

    LaunchedEffect(toast) {
        toast?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            vm.consumeToast()
        }
    }

    Scaffold(
        containerColor = appBackground(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "话术管理",
                        fontWeight = FontWeight.Bold,
                        color = appOnSurface()
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "返回",
                            tint = appOnSurface()
                        )
                    }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = appBackground()
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAdd,
                containerColor = appPrimary(),
                contentColor = Color.White
            ) {
                Icon(Icons.Outlined.Add, contentDescription = "新建话术")
            }
        }
    ) { padding ->
        if (scripts.isEmpty()) {
            EmptyScriptState(modifier = Modifier.fillMaxSize().padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = Spacing.screenH,
                    vertical = Spacing.sm
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                items(scripts, key = { it.id }) { item ->
                    ScriptItemCard(
                        item = item,
                        onClick = { onOpen(item.id) },
                        onLongClick = { pendingDelete = item }
                    )
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除话术？") },
            text = { Text("将删除「${target.name}」，此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(target.id)
                    pendingDelete = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            }
        )
    }
}

/**
 * 话术列表项卡片：纯白底 + 24dp 圆角 + 柔和阴影。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ScriptItemCard(
    item: ScriptRepository.ScriptItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(20.dp),
                ambientColor = Color.Black.copy(alpha = 0.04f),
                spotColor = Color.Black.copy(alpha = 0.06f)
            )
            .background(Color.White, RoundedCornerShape(20.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(Spacing.md)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(LightPrimary.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Article,
                    contentDescription = null,
                    tint = appPrimary(),
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.size(Spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = appOnSurface(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    item.content.ifBlank { "（空话术）" },
                    style = MaterialTheme.typography.labelSmall,
                    color = appOnSurfaceVariant(),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "更新于 ${item.updatedAt}",
                    style = MaterialTheme.typography.labelSmall,
                    color = appOnSurfaceVariant().copy(alpha = 0.6f)
                )
            }
        }
    }
}

/**
 * 空状态：引导用户新建话术。
 */
@Composable
private fun EmptyScriptState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(LightPrimary.copy(alpha = 0.12f), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.Article,
                contentDescription = null,
                tint = appPrimary(),
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(Modifier.height(Spacing.md))
        Text(
            "暂无话术",
            style = MaterialTheme.typography.titleMedium,
            color = appOnSurface()
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            "点击右下角 + 新建第一条话术",
            style = MaterialTheme.typography.labelMedium,
            color = appOnSurfaceVariant()
        )
    }
}
