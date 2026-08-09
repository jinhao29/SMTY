package com.shangmentiyu.sportscoach.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 参考图复刻：深色/浅色模式胶囊切换开关。
 *
 * - 黑色 #000000 胶囊轨道（RoundedCornerShape(50)）
 * - 亮蓝 #00A8FF 滑动指示器，Animatable 平滑滑动（0f=左侧 Light，1f=右侧 Dark）
 * - 左半：Sun 图标 + "Light"；右半：Moon 图标 + "Dark"，文字均为纯白 #FFFFFF
 * - checked = 深色模式，双向绑定：点击切换回调 onCheckedChange
 */
@Composable
fun ThemeSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    trackColor: Color = Color(0xFF000000),
    indicatorColor: Color = Color(0xFF00A8FF),
    labelColor: Color = Color(0xFFFFFFFF)
) {
    val fraction = remember { Animatable(if (checked) 1f else 0f) }
    LaunchedEffect(checked) {
        fraction.animateTo(
            targetValue = if (checked) 1f else 0f,
            animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing)
        )
    }

    BoxWithConstraints(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(50))
            .background(trackColor)
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 6.dp, vertical = 6.dp)
    ) {
        val trackWidth = maxWidth - 12.dp
        val thumbWidth = trackWidth / 2f
        Box(
            modifier = Modifier
                .offset(x = (trackWidth - thumbWidth) * fraction.value)
                .width(thumbWidth)
                .fillMaxHeight()
                .clip(RoundedCornerShape(50))
                .background(indicatorColor)
        )

        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SwitchHalf(
                icon = Icons.Outlined.LightMode,
                label = "Light",
                tint = labelColor,
                modifier = Modifier.weight(1f)
            )
            SwitchHalf(
                icon = Icons.Outlined.DarkMode,
                label = "Dark",
                tint = labelColor,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SwitchHalf(
    icon: ImageVector,
    label: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            color = tint,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp
        )
    }
}