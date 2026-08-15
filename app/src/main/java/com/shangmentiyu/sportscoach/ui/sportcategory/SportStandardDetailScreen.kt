package com.shangmentiyu.sportscoach.ui.sportcategory

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Grade
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Rule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shangmentiyu.sportscoach.ui.theme.ShadowTokens
import com.shangmentiyu.sportscoach.ui.theme.Spacing
import com.shangmentiyu.sportscoach.ui.theme.appBackground
import com.shangmentiyu.sportscoach.ui.theme.appOnSurface
import com.shangmentiyu.sportscoach.ui.theme.appOnSurfaceVariant
import com.shangmentiyu.sportscoach.ui.theme.appPrimary
import com.shangmentiyu.sportscoach.ui.theme.appSurface

@Composable
fun SportStandardDetailScreen(
    sportId: String,
    onBack: () -> Unit
) {
    val detail = remember(sportId) {
        val d = mockSportDetail(sportId)
        android.util.Log.d("SportDetail", "当前加载的项目ID: $sportId, 加载到的文本长度: ${d.requirement.length}")
        d
    }

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
                    .statusBarsPadding()
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
                    text = detail.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = appOnSurface()
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = Spacing.screenH,
                    end = Spacing.screenH,
                    top = Spacing.sm,
                    bottom = 160.dp
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                item {
                    DetailCard(
                        icon = Icons.Outlined.AccountCircle,
                        title = "考试要求",
                        content = detail.requirement
                    )
                }
                item {
                    DetailCard(
                        icon = Icons.Outlined.Rule,
                        title = "评判规则",
                        content = detail.judgingRule
                    )
                }
                item {
                    DetailCard(
                        icon = Icons.Outlined.Place,
                        title = "场地器材",
                        content = detail.venueEquipment
                    )
                }
                item {
                    ScoreStandardCard(rows = detail.scoreRows)
                }
            }
        }
    }
}

@Composable
private fun DetailCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    content: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = appSurface()),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.cardPadding)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = appPrimary(),
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = appOnSurface()
                )
            }
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = content,
                fontSize = 14.sp,
                lineHeight = 24.sp,
                color = appOnSurfaceVariant()
            )
        }
    }
}

@Composable
private fun ScoreStandardCard(rows: List<ScoreRow>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = appSurface()),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.cardPadding)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Grade,
                    contentDescription = null,
                    tint = appPrimary(),
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = "评分标准",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = appOnSurface()
                )
            }

            // === v32：评分标准默认全部可见，移除手动展开/收起逻辑 ===
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.md)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "分值",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = appOnSurfaceVariant(),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "男生",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = appOnSurfaceVariant(),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "女生",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = appOnSurfaceVariant(),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(Spacing.xs))
                rows.forEach { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = Spacing.sm),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = row.score,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = appOnSurface(),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = row.male,
                            fontSize = 14.sp,
                            color = appOnSurface(),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = row.female,
                            fontSize = 14.sp,
                            color = appOnSurface(),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

private data class ScoreRow(
    val score: String,
    val male: String,
    val female: String
)

private data class SportDetail(
    val name: String,
    val requirement: String,
    val judgingRule: String,
    val venueEquipment: String,
    val scoreRows: List<ScoreRow>
)

private fun mockSportDetail(sportId: String): SportDetail {
    val nameMap = mapOf(
        "long_run" to "长跑（1000米/800米）",
        "sprint_100m" to "100米跑",
        "jump_rope_4min" to "4分钟跳绳",
        "football" to "足球运球绕杆",
        "basketball" to "篮球运球上篮",
        "volleyball" to "排球垫球",
        "swim_50m" to "50米游泳",
        "swim_200m" to "200米游泳",
        "pull_up" to "引体向上",
        "sit_up" to "仰卧起坐",
        "standing_long_jump" to "立定跳远",
        "table_tennis" to "乒乓球对墙推挡",
        "badminton" to "羽毛球发球/高远球",
        "tennis" to "网球正手击球",
        "shuttlecock_kick" to "踢毽子",
        "double_frog_jump" to "二级蛙跳"
    )
    val name = nameMap[sportId] ?: "体育中考项目"

    val requirement = when (sportId) {
        "long_run" -> "考生在起跑线后采用站立式起跑，听到发令枪声后开始跑进。" +
            "全程独立完成，不得借助外力。到达终点时以躯干抵达终点线后沿垂直面为准。"
        "sprint_100m" -> "考生在起跑线后采用蹲踞式或站立式起跑，听到发令枪声后开始跑进。" +
            "全程独立完成，不得借助外力。到达终点时以躯干抵达终点线后沿垂直面为准。"
        "jump_rope_4min" -> "考生在指定区域内双手摇绳，双脚或单脚跳跃，绳过脚底为一次。" +
            "限时 4 分钟，累计跳跃次数为最终成绩。"
        "football" -> "考生从起点出发，依次运球绕过若干标志杆后到达终点。" +
            "球触及标志杆或偏离路线均按规则扣分。全程用时越短分数越高。"
        "basketball" -> "考生从起点出发，运球绕过标志杆后完成上篮，抢篮板后运球返回。" +
            "未命中需补篮直至命中。全程用时越短分数越高。"
        "volleyball" -> "考生在指定区域内正面双手垫球，垫球高度不低于规定高度。" +
            "限时内累计有效垫球次数为最终成绩。"
        "swim_50m" -> "考生在水中出发，可采用任何泳姿完成 50 米距离。" +
            "转身时必须身体某部位触及池壁，到达终点以躯干触壁为准。"
        "swim_200m" -> "考生在水中出发，可采用任何泳姿完成 200 米距离。" +
            "每次转身必须身体某部位触及池壁，到达终点以躯干触壁为准。"
        "pull_up" -> "考生双手正握单杠，身体悬垂静止开始，引体至下颌过杠为一次，" +
            "下降至两臂伸直为完成一次。不限时间，累计完成次数。"
        "sit_up" -> "考生仰卧于垫上，双手交叉置于脑后，起坐时双肘触及双膝为一次。" +
            "限时 1 分钟，累计完成次数。"
        "standing_long_jump" -> "考生双脚站立于起跳线后，原地双脚同时起跳，双脚同时落地。" +
            "以起跳线至最近着地点的垂直距离为成绩。"
        "table_tennis" -> "考生在指定区域内对墙推挡乒乓球，记录 1 分钟内有效推挡次数。" +
            "球落地或未触及墙面不计次数。"
        "badminton" -> "考生在指定区域内完成发球或高远球动作，按落点准确性评分。" +
            "每人若干次机会，取最好成绩。"
        "tennis" -> "考生在指定区域内完成正手击球动作，按击球落点准确性评分。" +
            "每人若干次机会，取最好成绩。"
        "shuttlecock_kick" -> "考生在指定区域内用脚内侧或脚面踢毽子，限时 1 分钟。" +
            "累计有效踢毽次数为最终成绩。"
        "double_frog_jump" -> "考生双脚站立于起跳线后，连续完成两次蛙跳。" +
            "以起跳线至第二次落地最近着地点的垂直距离为成绩。"
        else -> "考生按规定动作完成项目，每项动作需达到技术规范要求。" +
            "动作不到位或未达标准按扣分规则执行。"
    }

    val judgingRule = when (sportId) {
        "long_run" -> "1. 抢跑一次取消资格。\n" +
            "2. 跑道内阻挡他人成绩无效。\n" +
            "3. 以电子计时为准，精确到 0.1 秒。\n" +
            "4. 每人一次测试机会。"
        "sprint_100m" -> "1. 抢跑一次取消资格。\n" +
            "2. 跑道内阻挡他人成绩无效。\n" +
            "3. 以电子计时为准，精确到 0.01 秒。\n" +
            "4. 每人一次测试机会。"
        "jump_rope_4min" -> "1. 每人一次测试机会。\n" +
            "2. 绊绳后可继续，次数累计。\n" +
            "3. 双摇只计一次。\n" +
            "4. 以 4 分钟内累计次数计分。"
        "football" -> "1. 每人两次测试机会，取最好成绩。\n" +
            "2. 碰倒标志杆每次扣 0.5 秒。\n" +
            "3. 球出界需从出界处重新开始。\n" +
            "4. 以用时最短者分数最高。"
        "basketball" -> "1. 每人两次测试机会，取最好成绩。\n" +
            "2. 碰倒标志杆每次扣 0.5 秒。\n" +
            "3. 上篮未命中需补篮，不另计时。\n" +
            "4. 以用时最短者分数最高。"
        "volleyball" -> "1. 每人两次测试机会，取最好成绩。\n" +
            "2. 垫球高度不达标不计次数。\n" +
            "3. 球落地或出界测试结束。\n" +
            "4. 以累计有效垫球次数计分。"
        "swim_50m" -> "1. 每人一次测试机会。\n" +
            "2. 出发犯规取消当次成绩。\n" +
            "3. 转身未触壁加 2 秒。\n" +
            "4. 以用时最短者分数最高。"
        "swim_200m" -> "1. 每人一次测试机会。\n" +
            "2. 出发犯规取消当次成绩。\n" +
            "3. 转身未触壁每次加 2 秒。\n" +
            "4. 以用时最短者分数最高。"
        "pull_up" -> "1. 每人一次测试机会。\n" +
            "2. 下颌未过杠不计次数。\n" +
            "3. 借助身体摆动不计次数。\n" +
            "4. 以累计完成次数计分。"
        "sit_up" -> "1. 每人一次测试机会。\n" +
            "2. 双肘未触膝不计次数。\n" +
            "3. 臀部离垫不计次数。\n" +
            "4. 以 1 分钟内累计次数计分。"
        "standing_long_jump" -> "1. 每人两次测试机会，取最好成绩。\n" +
            "2. 起跳时脚尖过线判犯规。\n" +
            "3. 落地后向后倒退取最近着地点。\n" +
            "4. 以跳跃距离最远者分数最高。"
        "table_tennis" -> "1. 每人一次测试机会。\n" +
            "2. 球落地或未触及墙面不计次数。\n" +
            "3. 推挡动作不规范不计次数。\n" +
            "4. 以 1 分钟内累计有效次数计分。"
        "badminton" -> "1. 每人两次测试机会，取最好成绩。\n" +
            "2. 发球踩线判犯规。\n" +
            "3. 球落点以第一次落地为准。\n" +
            "4. 以落点准确性分数最高。"
        "tennis" -> "1. 每人两次测试机会，取最好成绩。\n" +
            "2. 击球踩线判犯规。\n" +
            "3. 球落点以第一次落地为准。\n" +
            "4. 以落点准确性分数最高。"
        "shuttlecock_kick" -> "1. 每人一次测试机会。\n" +
            "2. 毽子落地测试结束。\n" +
            "3. 手触毽不计次数。\n" +
            "4. 以 1 分钟内累计有效次数计分。"
        "double_frog_jump" -> "1. 每人两次测试机会，取最好成绩。\n" +
            "2. 起跳时脚尖过线判犯规。\n" +
            "3. 两次跳跃间不得停顿过久。\n" +
            "4. 以总跳跃距离最远者分数最高。"
        else -> "1. 每人两次测试机会，取最好成绩。\n" +
            "2. 动作未达标准每次扣 1 分。\n" +
            "3. 超时未完成按已完成数量计分。\n" +
            "4. 以完成数量/用时最优者分数最高。"
    }

    val venueEquipment = when (sportId) {
        "long_run" -> "场地：标准 400 米田径场，跑道宽 1.22 米。\n" +
            "器材：发令枪、电子计时系统、终点摄影设备。"
        "sprint_100m" -> "场地：标准 100 米直道，跑道宽 1.22 米。\n" +
            "器材：发令枪、电子计时系统、终点摄影设备。"
        "jump_rope_4min" -> "场地：平整硬地，面积不小于 2 米 × 2 米。\n" +
            "器材：标准跳绳（可自备）、电子计数器、秒表。"
        "football" -> "场地：平整硬地或人造草坪，标志杆间距 2 米。\n" +
            "器材：标准 5 号足球、标志杆（高度 1.5 米）、秒表。"
        "basketball" -> "场地：标准篮球场半场，标志杆 3-4 根。\n" +
            "器材：标准 7 号篮球、标志杆、秒表、标准篮架。"
        "volleyball" -> "场地：平整室内场地，划定 3 米 × 3 米区域。\n" +
            "器材：标准排球、高度标志线、秒表。"
        "swim_50m" -> "场地：标准 50 米游泳池，水深不低于 1.35 米。\n" +
            "器材：电子触壁板、出发台、秒表。"
        "swim_200m" -> "场地：标准 50 米游泳池，水深不低于 1.35 米。\n" +
            "器材：电子触壁板、出发台、秒表。"
        "pull_up" -> "场地：室内或室外单杠场地。\n" +
            "器材：标准单杠（高度可调）、防滑粉。"
        "sit_up" -> "场地：平整室内场地。\n" +
            "器材：标准体操垫、秒表。"
        "standing_long_jump" -> "场地：平整沙坑或硬地，起跳线至落地区不少于 3 米。\n" +
            "器材：卷尺、起跳线标志。"
        "table_tennis" -> "场地：平整室内墙面，墙面高度不低于 2 米。\n" +
            "器材：标准乒乓球、球拍（可自备）、秒表。"
        "badminton" -> "场地：标准羽毛球场地，发球区标线清晰。\n" +
            "器材：标准羽毛球、球拍（可自备）、落点标志。"
        "tennis" -> "场地：标准网球场或平整硬地，落点区域标线清晰。\n" +
            "器材：标准网球、球拍（可自备）、落点标志。"
        "shuttlecock_kick" -> "场地：平整室内硬地，面积不小于 2 米 × 2 米。\n" +
            "器材：标准毽子（可自备）、秒表。"
        "double_frog_jump" -> "场地：平整沙坑或硬地，起跳线至落地区不少于 5 米。\n" +
            "器材：卷尺、起跳线标志。"
        else -> "场地：平整室内场地，配备标准器材。\n" +
            "器材：单杠/垫子/秒表/卷尺。"
    }

    val scoreRows = when (sportId) {
        "long_run" -> listOf(
            ScoreRow("10分", "3'40\"以内", "3'25\"以内"),
            ScoreRow("8分", "3'40\"-3'55\"", "3'25\"-3'40\""),
            ScoreRow("6分", "3'55\"-4'10\"", "3'40\"-3'55\""),
            ScoreRow("4分", "4'10\"-4'25\"", "3'55\"-4'10\""),
            ScoreRow("2分", "4'25\"以外", "4'10\"以外")
        )
        "sprint_100m" -> listOf(
            ScoreRow("10分", "13.5\"以内", "15.5\"以内"),
            ScoreRow("8分", "13.5\"-14.5\"", "15.5\"-16.5\""),
            ScoreRow("6分", "14.5\"-15.5\"", "16.5\"-17.5\""),
            ScoreRow("4分", "15.5\"-16.5\"", "17.5\"-18.5\""),
            ScoreRow("2分", "16.5\"以外", "18.5\"以外")
        )
        "jump_rope_4min" -> listOf(
            ScoreRow("10分", "180次以上", "170次以上"),
            ScoreRow("8分", "160-179次", "150-169次"),
            ScoreRow("6分", "140-159次", "130-149次"),
            ScoreRow("4分", "120-139次", "110-129次"),
            ScoreRow("2分", "120次以下", "110次以下")
        )
        "football" -> listOf(
            ScoreRow("10分", "9.0\"以内", "11.0\"以内"),
            ScoreRow("8分", "9.0\"-11.0\"", "11.0\"-13.0\""),
            ScoreRow("6分", "11.0\"-13.0\"", "13.0\"-15.0\""),
            ScoreRow("4分", "13.0\"-15.0\"", "15.0\"-17.0\""),
            ScoreRow("2分", "15.0\"以外", "17.0\"以外")
        )
        "basketball" -> listOf(
            ScoreRow("10分", "12.0\"以内", "14.0\"以内"),
            ScoreRow("8分", "12.0\"-14.0\"", "14.0\"-16.0\""),
            ScoreRow("6分", "14.0\"-16.0\"", "16.0\"-18.0\""),
            ScoreRow("4分", "16.0\"-18.0\"", "18.0\"-20.0\""),
            ScoreRow("2分", "18.0\"以外", "20.0\"以外")
        )
        "volleyball" -> listOf(
            ScoreRow("10分", "30次以上", "25次以上"),
            ScoreRow("8分", "25-29次", "21-24次"),
            ScoreRow("6分", "20-24次", "17-20次"),
            ScoreRow("4分", "15-19次", "13-16次"),
            ScoreRow("2分", "15次以下", "13次以下")
        )
        "swim_50m" -> listOf(
            ScoreRow("10分", "35\"以内", "40\"以内"),
            ScoreRow("8分", "35\"-45\"", "40\"-50\""),
            ScoreRow("6分", "45\"-55\"", "50\"-60\""),
            ScoreRow("4分", "55\"-65\"", "60\"-70\""),
            ScoreRow("2分", "65\"以外", "70\"以外")
        )
        "swim_200m" -> listOf(
            ScoreRow("10分", "2'30\"以内", "2'50\"以内"),
            ScoreRow("8分", "2'30\"-2'50\"", "2'50\"-3'10\""),
            ScoreRow("6分", "2'50\"-3'10\"", "3'10\"-3'30\""),
            ScoreRow("4分", "3'10\"-3'30\"", "3'30\"-3'50\""),
            ScoreRow("2分", "3'30\"以外", "3'50\"以外")
        )
        "pull_up" -> listOf(
            ScoreRow("10分", "15次以上", "—"),
            ScoreRow("8分", "12-14次", "—"),
            ScoreRow("6分", "9-11次", "—"),
            ScoreRow("4分", "6-8次", "—"),
            ScoreRow("2分", "5次以下", "—")
        )
        "sit_up" -> listOf(
            ScoreRow("10分", "50次以上", "45次以上"),
            ScoreRow("8分", "44-49次", "40-44次"),
            ScoreRow("6分", "38-43次", "35-39次"),
            ScoreRow("4分", "32-37次", "30-34次"),
            ScoreRow("2分", "32次以下", "30次以下")
        )
        "standing_long_jump" -> listOf(
            ScoreRow("10分", "2.30m以上", "1.95m以上"),
            ScoreRow("8分", "2.20-2.29m", "1.85-1.94m"),
            ScoreRow("6分", "2.10-2.19m", "1.75-1.84m"),
            ScoreRow("4分", "2.00-2.09m", "1.65-1.74m"),
            ScoreRow("2分", "2.00m以下", "1.65m以下")
        )
        "table_tennis" -> listOf(
            ScoreRow("10分", "80次以上", "70次以上"),
            ScoreRow("8分", "70-79次", "60-69次"),
            ScoreRow("6分", "60-69次", "50-59次"),
            ScoreRow("4分", "50-59次", "40-49次"),
            ScoreRow("2分", "50次以下", "40次以下")
        )
        "badminton" -> listOf(
            ScoreRow("10分", "落点精准", "落点精准"),
            ScoreRow("8分", "落点良好", "落点良好"),
            ScoreRow("6分", "落点一般", "落点一般"),
            ScoreRow("4分", "落点偏差", "落点偏差"),
            ScoreRow("2分", "落点失误", "落点失误")
        )
        "tennis" -> listOf(
            ScoreRow("10分", "落点精准", "落点精准"),
            ScoreRow("8分", "落点良好", "落点良好"),
            ScoreRow("6分", "落点一般", "落点一般"),
            ScoreRow("4分", "落点偏差", "落点偏差"),
            ScoreRow("2分", "落点失误", "落点失误")
        )
        "shuttlecock_kick" -> listOf(
            ScoreRow("10分", "80次以上", "90次以上"),
            ScoreRow("8分", "65-79次", "75-89次"),
            ScoreRow("6分", "50-64次", "60-74次"),
            ScoreRow("4分", "35-49次", "45-59次"),
            ScoreRow("2分", "35次以下", "45次以下")
        )
        "double_frog_jump" -> listOf(
            ScoreRow("10分", "4.50m以上", "4.00m以上"),
            ScoreRow("8分", "4.20-4.49m", "3.70-3.99m"),
            ScoreRow("6分", "3.90-4.19m", "3.40-3.69m"),
            ScoreRow("4分", "3.60-3.89m", "3.10-3.39m"),
            ScoreRow("2分", "3.60m以下", "3.10m以下")
        )
        else -> listOf(
            ScoreRow("10分", "15次以上", "40次以上"),
            ScoreRow("8分", "12-14次", "35-39次"),
            ScoreRow("6分", "9-11次", "30-34次"),
            ScoreRow("4分", "6-8次", "25-29次"),
            ScoreRow("2分", "5次以下", "24次以下")
        )
    }

    return SportDetail(name, requirement, judgingRule, venueEquipment, scoreRows)
}
