package com.shangmentiyu.sportscoach.ui.club

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shangmentiyu.sportscoach.data.internal.ModeManager
import com.shangmentiyu.sportscoach.ui.home.IosCard
import com.shangmentiyu.sportscoach.ui.theme.appOnSurface
import com.shangmentiyu.sportscoach.ui.theme.appOnSurfaceVariant
import com.shangmentiyu.sportscoach.ui.theme.appPrimary

/**
 * EVOLVE 俱乐部 · 设置。
 *
 * 俱乐部信息（只读展示）+ 数据管理入口 + 切换板块 + 版本信息。
 * 备份/恢复/双端同步等完整能力在「完整设置」页（与上门体育共用一套实现，
 * 全部作用于当前模式的独立数据库），这里做俱乐部语境的入口聚合。
 */
@Composable
fun ClubSettingsScreen(
    onOpenFullSettings: () -> Unit,
    onOpenLessons: () -> Unit,
    onSwitchModule: () -> Unit
) {
    val context = LocalContext.current
    val versionName = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "—"
        } catch (_: Exception) {
            "—"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 160.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        Text("设置", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = appOnSurface())

        // === 俱乐部信息 ===
        SectionLabel("俱乐部信息")
        IosCard {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "EVOLVE 进化体育",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = appOnSurface()
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "青少年体适能 · 会员制俱乐部",
                            fontSize = 12.sp,
                            color = appOnSurfaceVariant()
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = appPrimary().copy(alpha = 0.10f)
                    ) {
                        Text(
                            text = "当前数据空间",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = appPrimary(),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "数据空间：${ModeManager.displayName()}（${ModeManager.activeDbName}）" +
                        "· 独立数据库，与上门体育物理隔离",
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = appOnSurfaceVariant()
                )
            }
        }

        // === 数据管理 ===
        SectionLabel("数据管理")
        IosCard {
            SettingsRow(
                title = "完整设置（备份 / 恢复 / 双端同步）",
                subtitle = "与电脑端同步、备份与恢复数据",
                onClick = onOpenFullSettings
            )
            SettingsRow(
                title = "课时与课程包",
                subtitle = "课时统计 / 课时包管理 / 年级分组导出",
                onClick = onOpenLessons
            )
        }

        // === 模式 ===
        SectionLabel("模式")
        IosCard {
            SettingsRow(
                title = "切换板块",
                subtitle = "返回启动页，切换上门体育 / 俱乐部数据空间",
                onClick = onSwitchModule
            )
        }

        // === 关于 ===
        SectionLabel("关于")
        IosCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("版本", fontSize = 14.sp, color = appOnSurface())
                Text(versionName, fontSize = 13.sp, color = appOnSurfaceVariant())
            }
        }

        Text(
            text = "SPORTSCOACH · 让每一节课都有据可依",
            fontSize = 11.sp,
            color = appOnSurfaceVariant(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = appOnSurfaceVariant(),
        letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
    )
}

@Composable
private fun SettingsRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = appOnSurface())
            Spacer(Modifier.height(2.dp))
            Text(subtitle, fontSize = 11.sp, color = appOnSurfaceVariant())
        }
        Text("›", fontSize = 20.sp, color = appOnSurfaceVariant())
    }
}
