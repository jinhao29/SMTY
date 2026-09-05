package com.shangmentiyu.sportscoach.ui.sportcategory

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.Accessibility
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Loop
import androidx.compose.material.icons.outlined.Pool
import androidx.compose.material.icons.outlined.SportsBasketball
import androidx.compose.material.icons.outlined.SportsSoccer
import androidx.compose.material.icons.outlined.SportsVolleyball
import androidx.compose.material.icons.outlined.SportsTennis
import androidx.compose.material.icons.outlined.Waves
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shangmentiyu.sportscoach.ui.theme.Spacing
import com.shangmentiyu.sportscoach.ui.theme.appBackground
import com.shangmentiyu.sportscoach.ui.theme.appOnSurface
import com.shangmentiyu.sportscoach.ui.theme.appPrimary
import com.shangmentiyu.sportscoach.ui.theme.appSurface

@Composable
fun SportCategoryScreen(
    onBack: () -> Unit,
    onItemClick: (String) -> Unit
) {
    Scaffold(
        containerColor = appBackground(),
        contentWindowInsets = WindowInsets(0)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "返回",
                        tint = appOnSurface()
                    )
                }
                Text(
                    text = "体育中考标准分类",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = appOnSurface()
                )
            }
            SportCategoryContent(onItemClick = onItemClick)
        }
    }
}

/**
 * 中考体育分类网格（无标题栏，供整页与"成绩查看"页 Tab 嵌入复用）。
 *
 * @param onItemClick 点击项目回调，参数为项目 ID（跳转标准详情）
 */
@Composable
fun SportCategoryContent(onItemClick: (String) -> Unit) {
    val categories = remember { mockSportCategories() }
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = Spacing.screenH,
            end = Spacing.screenH,
            top = Spacing.sm,
            bottom = 160.dp
        ),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        categories.forEachIndexed { index, category ->
            if (index > 0) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Spacer(Modifier.height(Spacing.xl))
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = appOnSurface(),
                    fontSize = 17.sp,
                    modifier = Modifier.padding(vertical = Spacing.sm)
                )
            }
            items(
                items = category.items,
                key = { it.id }
            ) { item ->
                SportGridItem(
                    item = item,
                    onClick = { onItemClick(item.id) }
                )
            }
        }
    }
}

@Composable
private fun SportGridItem(
    item: SportCategoryItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = appSurface()),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(Spacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.name,
                tint = appPrimary(),
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = item.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = appOnSurface(),
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

private fun mockSportCategories(): List<SportsCategory> = listOf(
    SportsCategory(
        id = "track",
        name = "田径类",
        items = listOf(
            SportCategoryItem(
                id = "long_run",
                name = "长跑",
                icon = Icons.AutoMirrored.Outlined.DirectionsRun,
                description = "耐力项目"
            ),
            SportCategoryItem(
                id = "sprint_100m",
                name = "100米跑",
                icon = Icons.Outlined.Bolt,
                description = "速度项目"
            ),
            SportCategoryItem(
                id = "jump_rope_4min",
                name = "4分钟跳绳",
                icon = Icons.Outlined.Loop,
                description = "协调耐力项目"
            )
        )
    ),
    SportsCategory(
        id = "ball",
        name = "球类",
        items = listOf(
            SportCategoryItem(
                id = "football",
                name = "足球",
                icon = Icons.Outlined.SportsSoccer,
                description = "运球绕杆"
            ),
            SportCategoryItem(
                id = "basketball",
                name = "篮球",
                icon = Icons.Outlined.SportsBasketball,
                description = "运球上篮"
            ),
            SportCategoryItem(
                id = "volleyball",
                name = "排球",
                icon = Icons.Outlined.SportsVolleyball,
                description = "垫球"
            ),
            SportCategoryItem(
                id = "table_tennis",
                name = "乒乓球",
                icon = Icons.Outlined.SportsTennis,
                description = "对墙推挡"
            ),
            SportCategoryItem(
                id = "badminton",
                name = "羽毛球",
                icon = Icons.Outlined.SportsTennis,
                description = "发球/高远球"
            ),
            SportCategoryItem(
                id = "tennis",
                name = "网球",
                icon = Icons.Outlined.SportsTennis,
                description = "正手击球"
            )
        )
    ),
    SportsCategory(
        id = "swimming",
        name = "游泳类",
        items = listOf(
            SportCategoryItem(
                id = "swim_50m",
                name = "50米游泳",
                icon = Icons.Outlined.Pool,
                description = "短距游泳"
            ),
            SportCategoryItem(
                id = "swim_200m",
                name = "200米游泳",
                icon = Icons.Outlined.Waves,
                description = "中长距游泳"
            )
        )
    ),
    SportsCategory(
        id = "technique",
        name = "技巧类",
        items = listOf(
            SportCategoryItem(
                id = "pull_up",
                name = "引体向上",
                icon = Icons.Outlined.FitnessCenter,
                description = "上肢力量项目"
            ),
            SportCategoryItem(
                id = "sit_up",
                name = "仰卧起坐",
                icon = Icons.Outlined.Accessibility,
                description = "核心力量项目"
            ),
            SportCategoryItem(
                id = "standing_long_jump",
                name = "立定跳远",
                icon = Icons.Outlined.ArrowUpward,
                description = "爆发力项目"
            ),
            SportCategoryItem(
                id = "shuttlecock_kick",
                name = "踢毽子",
                icon = Icons.Outlined.Loop,
                description = "协调耐力项目"
            ),
            SportCategoryItem(
                id = "double_frog_jump",
                name = "二级蛙跳",
                icon = Icons.Outlined.ArrowUpward,
                description = "爆发力项目"
            )
        )
    )
)
