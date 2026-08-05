package com.shangmentiyu.sportscoach.ui.script

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import com.shangmentiyu.sportscoach.ui.theme.Spacing
import com.shangmentiyu.sportscoach.ui.theme.appBackground
import com.shangmentiyu.sportscoach.ui.theme.appOnSurface
import com.shangmentiyu.sportscoach.ui.theme.appOnSurfaceVariant
import com.shangmentiyu.sportscoach.ui.theme.appPrimary
import com.shangmentiyu.sportscoach.ui.theme.appTextFieldColors

/**
 * 话术编辑页（UI 层）。
 *
 * 编辑话术项目名称与内容，保存后可一键复制到剪贴板，
 * 方便教练粘贴到家长群或私聊。
 *
 * @param scriptId 话术 ID（null 表示新建）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScriptDetailScreen(
    scriptId: String?,
    onBack: () -> Unit = {}
) {
    val vm: ScriptViewModel = koinViewModel()
    val context = LocalContext.current
    val current by vm.current.collectAsStateWithLifecycle()
    val toast by vm.toast.collectAsStateWithLifecycle()

    // 编辑态：进入页面时从 current 初始化
    var name by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var initialized by remember { mutableStateOf(false) }

    LaunchedEffect(scriptId) {
        vm.loadCurrent(scriptId)
    }
    LaunchedEffect(current) {
        if (!initialized && current != null) {
            name = current!!.name
            content = current!!.content
            initialized = true
        } else if (scriptId.isNullOrBlank() && !initialized) {
            initialized = true
        }
    }
    LaunchedEffect(toast) {
        toast?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            vm.consumeToast()
        }
    }

    val isEdit = !scriptId.isNullOrBlank()

    Scaffold(
        containerColor = appBackground(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isEdit) "编辑话术" else "新建话术",
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
                actions = {
                    IconButton(onClick = {
                        saveScript(
                            vm = vm,
                            isEdit = isEdit,
                            scriptId = scriptId,
                            name = name,
                            content = content,
                            onBack = onBack
                        )
                    }) {
                        Icon(
                            Icons.Outlined.Save,
                            contentDescription = "保存",
                            tint = appPrimary()
                        )
                    }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = appBackground()
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screenH, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            // 项目名称输入
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("项目名称") },
                placeholder = { Text("如：课后反馈-表扬") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = appTextFieldColors()
            )

            // 话术内容输入
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                label = { Text("话术内容") },
                placeholder = { Text("输入要发给家长的话术…") },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp, max = 400.dp),
                colors = appTextFieldColors()
            )

            Spacer(Modifier.height(Spacing.xs))

            // 保存按钮
            Button(
                onClick = {
                    saveScript(
                        vm = vm,
                        isEdit = isEdit,
                        scriptId = scriptId,
                        name = name,
                        content = content,
                        onBack = onBack
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(Icons.Outlined.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(Spacing.xs))
                Text("保存话术")
            }

            // 复制按钮：保存后可复制，也支持直接复制当前输入
            OutlinedButton(
                onClick = {
                    val text = content.trim()
                    if (text.isEmpty()) {
                        Toast.makeText(context, "话术内容为空，无法复制", Toast.LENGTH_SHORT).show()
                    } else {
                        copyToClipboard(context, text)
                        Toast.makeText(context, "已复制到剪贴板，可粘贴给家长", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(Spacing.xs))
                Text("复制话术")
            }
        }
    }
}

/**
 * 保存话术：根据是否为编辑模式调用 add 或 update。
 * 保存成功后返回上一页。
 */
private fun saveScript(
    vm: ScriptViewModel,
    isEdit: Boolean,
    scriptId: String?,
    name: String,
    content: String,
    onBack: () -> Unit
) {
    if (name.isBlank()) {
        vm.consumeToast()
        return
    }
    if (isEdit && scriptId != null) {
        vm.update(scriptId, name, content)
    } else {
        vm.add(name, content)
    }
    onBack()
}

/**
 * 复制文本到系统剪贴板。
 */
private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("话术", text))
}
