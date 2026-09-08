package com.shangmentiyu.sportscoach.ui.settings

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shangmentiyu.sportscoach.data.internal.ModeManager
import com.shangmentiyu.sportscoach.ui.home.IosCard
import com.shangmentiyu.sportscoach.ui.theme.Spacing
import com.shangmentiyu.sportscoach.ui.theme.appOnSurface
import com.shangmentiyu.sportscoach.ui.theme.appOnSurfaceVariant
import com.shangmentiyu.sportscoach.ui.theme.appPrimary
import kotlinx.coroutines.launch

/**
 * 工作模式切换（v23.12 多租户·物理隔离）。
 *
 * 上门体育 / 俱乐部 各自独立数据库（sports_coach_db / sports_coach_club_db），
 * 切换后重启 App 生效（已创建的 ViewModel 持有旧库 DAO，热切不安全）。
 */
@Composable
fun ModeSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val current by ModeManager.mode.collectAsStateWithLifecycle()
    var pending by remember { mutableStateOf<String?>(null) }

    Column {
        Text(
            text = "工作模式",
            modifier = Modifier.padding(horizontal = Spacing.screenH),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = appOnSurface()
        )
        Spacer(Modifier.height(8.dp))
        IosCard {
            ModeOption(
                title = "上门体育",
                desc = "现有学员 / 课时 / 财务数据（sports_coach_db）",
                selected = (pending ?: current) == ModeManager.MODE_COACHING,
                onClick = {
                    if (current != ModeManager.MODE_COACHING) {
                        pending = ModeManager.MODE_COACHING
                        scope.launch {
                            ModeManager.setMode(context, ModeManager.MODE_COACHING)
                            restartApp(context)
                        }
                    }
                }
            )
            ModeOption(
                title = "俱乐部",
                desc = "EVOLVE 独立数据空间，与上门体育完全隔离（club_db）",
                selected = (pending ?: current) == ModeManager.MODE_CLUB,
                onClick = {
                    if (current != ModeManager.MODE_CLUB) {
                        pending = ModeManager.MODE_CLUB
                        scope.launch {
                            ModeManager.setMode(context, ModeManager.MODE_CLUB)
                            restartApp(context)
                        }
                    }
                }
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "切换模式后数据完全独立；同步时只会连接对应模式的数据。",
            modifier = Modifier.padding(horizontal = Spacing.screenH),
            style = MaterialTheme.typography.bodySmall,
            color = appOnSurfaceVariant()
        )
    }
}

@Composable
private fun ModeOption(title: String, desc: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, color = appOnSurface())
            Spacer(Modifier.height(2.dp))
            Text(desc, fontSize = 12.sp, color = appOnSurfaceVariant())
        }
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = appPrimary())
        )
    }
}

/** 切换模式后重启应用（重新创建入口 Activity，Application 重新初始化挂新库） */
private fun restartApp(context: android.content.Context) {
    val pm = context.packageManager
    val intent = pm.getLaunchIntentForPackage(context.packageName)
    intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    context.startActivity(intent)
    android.os.Process.killProcess(android.os.Process.myPid())
}

