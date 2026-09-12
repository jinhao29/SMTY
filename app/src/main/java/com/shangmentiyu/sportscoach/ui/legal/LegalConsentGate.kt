package com.shangmentiyu.sportscoach.ui.legal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shangmentiyu.sportscoach.data.repo.SettingsRepository
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

/**
 * 合规同意门禁（v1.0.6 新增）。
 *
 * 未同意当前版本《用户协议》《隐私政策》时全屏拦截，不渲染 [content]。
 * 应用根节点（MainActivity）包在最外层，保证任何业务界面都无法绕过。
 *
 * 放行条件：[SettingsRepository.legalAcceptedVersion] 等于 [Legal.VERSION]。
 * 政策修订后提升 [Legal.VERSION] 即自动触发全量重新确认。
 */
@Composable
fun LegalConsentGate(content: @Composable () -> Unit) {
    val repository = remember { GlobalContext.get().get<SettingsRepository>() }
    val scope = rememberCoroutineScope()

    // null = 尚未从 DataStore 读到值：此时不渲染任何内容，
    // 避免「已同意用户」在首帧被闪一次弹窗。
    var accepted by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(repository) {
        repository.legalAcceptedVersion.collect { accepted = it }
    }

    when (accepted) {
        null -> Unit
        Legal.VERSION -> content()
        else -> LegalConsentScreen(
            onAccept = {
                // 只写 DataStore，由上方 flow 驱动放行 —— 写入失败则停在弹窗，
                // 不会出现「没存上却已进入应用」的假同意状态。
                scope.launch { repository.setLegalAcceptedVersion(Legal.VERSION) }
            }
        )
    }
}

/** 合规同意页：概要 + 全文入口 + 同意/不同意。 */
@Composable
private fun LegalConsentScreen(onAccept: () -> Unit) {
    val context = LocalContext.current
    var viewing by remember { mutableStateOf<String?>(null) }
    var showDeclineConfirm by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 24.dp, vertical = 20.dp)
        ) {
            Text(
                text = "用户协议与隐私政策",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = CONSENT_SUMMARY,
                fontSize = 14.sp,
                lineHeight = 23.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            )
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TextButton(onClick = { viewing = Legal.AGREEMENT_ASSET }) {
                    Text("《用户协议》", fontSize = 14.sp)
                }
                TextButton(onClick = { viewing = Legal.PRIVACY_ASSET }) {
                    Text("《隐私政策》", fontSize = 14.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { showDeclineConfirm = true },
                    modifier = Modifier.weight(1f)
                ) { Text("暂不同意") }
                Button(
                    onClick = onAccept,
                    modifier = Modifier.weight(1f)
                ) { Text("同意并继续") }
            }
        }
    }

    viewing?.let { assetPath ->
        LegalFullTextDialog(
            assetPath = assetPath,
            title = if (assetPath == Legal.PRIVACY_ASSET) "隐私政策" else "用户协议",
            onClose = { viewing = null }
        )
    }

    if (showDeclineConfirm) {
        AlertDialog(
            onDismissRequest = { showDeclineConfirm = false },
            title = { Text("无法继续使用") },
            text = {
                Text(
                    "本应用需要处理学员信息（含不满十四周岁未成年人的个人信息）才能提供" +
                        "档案、课时与体测管理功能。不同意将无法使用，应用会退出。",
                    fontSize = 14.sp, lineHeight = 21.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeclineConfirm = false
                    (context as? android.app.Activity)?.finish()
                }) { Text("退出应用") }
            },
            dismissButton = {
                TextButton(onClick = { showDeclineConfirm = false }) { Text("返回") }
            }
        )
    }
}

/** 全文查看弹窗（滚动显示 assets 内文本）。设置页「关于」区块亦复用此组件。 */
@Composable
internal fun LegalFullTextDialog(assetPath: String, title: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val body = remember(assetPath) { Legal.read(context, assetPath) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold) },
        text = {
            Text(
                text = body,
                fontSize = 13.sp,
                lineHeight = 21.sp,
                modifier = Modifier.verticalScroll(rememberScrollState())
            )
        },
        confirmButton = {
            TextButton(onClick = onClose) { Text("关闭") }
        }
    )
}

private const val CONSENT_SUMMARY = """欢迎使用「上门体育教学管理工具」。

为提供学员档案、课时与体测管理服务，我们需要处理以下信息：
· 学员信息：姓名、性别、年龄、年级、学校、联系电话；
· 身体与体测：身高、体重、BMI、体测成绩、训练与签到记录；
· 签到照片：签到环节拍摄的学员照片；
· 教练信息：姓名、联系电话、所属机构。

其中部分信息属于不满十四周岁未成年人的个人信息。请在取得监护人明确同意后录入，并避免在备注等自由文本中填写与教学管理无关的内容。

我们的做法：
· 数据仅存储在您的设备本地，使用加密数据库（SQLCipher）保存，不上传至运营方服务器；
· 备份文件支持口令加密，口令由您自行保管，遗失后无法找回；
· 签到照片在本机加密存储，跨设备迁移时以备份口令重新加密；
· 与机构自有电脑的同步仅在同一局域网内进行，并需通过同步口令校验；
· 不会向任何第三方出售、出租或提供学员信息，也不会将其用于广告营销。

您可以随时在应用内查阅、更正、导出或删除学员信息。
完整条款请点击下方链接阅读。"""
