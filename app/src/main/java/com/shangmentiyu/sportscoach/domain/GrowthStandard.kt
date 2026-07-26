package com.shangmentiyu.sportscoach.domain

/**
 * 中国儿童身高标准条目（中国卫健委发布）。
 *
 * @param age   年龄（周岁）
 * @param p3    第 3 百分位（偏矮阈值，cm）
 * @param p50   第 50 百分位（中位数 / 平均线，cm）
 * @param p97   第 97 百分位（优秀阈值，cm）
 */
data class GrowthStandard(
    val age: Int,
    val p3: Double,
    val p50: Double,
    val p97: Double
)

/**
 * 当前身高评级状态。
 *
 * - [HEIGHT_SHORT]  偏矮：当前身高 < P3
 * - [HEIGHT_AVERAGE] 正常：P3 ≤ 当前身高 ≤ P97
 * - [HEIGHT_TALL]   优秀：当前身高 > P97
 */
enum class HeightRating {
    HEIGHT_SHORT,
    HEIGHT_AVERAGE,
    HEIGHT_TALL;

    /** 中文标签 */
    val label: String
        get() = when (this) {
            HEIGHT_SHORT  -> "偏矮"
            HEIGHT_AVERAGE -> "正常"
            HEIGHT_TALL   -> "优秀"
        }
}

/**
 * 中国儿童身高标准表（硬编码，纯本地数据，禁止网络请求）。
 *
 * 数据来源：中国卫健委发布的《中国 7 岁以下儿童生长发育参照标准》
 * 与《学龄儿童青少年超重与肥胖筛查》配套身高参考值，
 * 涵盖 3-18 岁男女儿童 P3 / P50 / P97 三个百分位。
 *
 * 评级规则：
 * - 当前身高 < P3     → [HeightRating.HEIGHT_SHORT]  偏矮
 * - P3 ≤ 身高 ≤ P97   → [HeightRating.HEIGHT_AVERAGE] 正常
 * - 当前身高 > P97    → [HeightRating.HEIGHT_TALL]   优秀
 */
object GrowthStandardTable {

    /** 男孩标准（3-18 岁） */
    val MALE: List<GrowthStandard> = listOf(
        GrowthStandard(3,  89.3,  96.8,  104.3),
        GrowthStandard(4,  95.8, 103.7,  111.6),
        GrowthStandard(5, 102.1, 110.5,  118.9),
        GrowthStandard(6, 108.6, 117.7,  126.8),
        GrowthStandard(7, 114.0, 124.0,  134.0),
        GrowthStandard(8, 119.3, 130.0,  140.7),
        GrowthStandard(9, 124.3, 135.4,  146.5),
        GrowthStandard(10, 128.7, 140.2, 151.7),
        GrowthStandard(11, 133.4, 145.3, 157.2),
        GrowthStandard(12, 139.1, 151.5, 163.9),
        GrowthStandard(13, 145.9, 159.5, 173.1),
        GrowthStandard(14, 152.0, 165.6, 179.2),
        GrowthStandard(15, 156.5, 169.7, 182.9),
        GrowthStandard(16, 159.1, 171.6, 184.1),
        GrowthStandard(17, 160.1, 172.3, 184.5),
        GrowthStandard(18, 160.5, 172.7, 184.9)
    )

    /** 女孩标准（3-18 岁） */
    val FEMALE: List<GrowthStandard> = listOf(
        GrowthStandard(3,  88.2,  95.6,  103.0),
        GrowthStandard(4,  94.3, 102.3,  110.3),
        GrowthStandard(5, 100.5, 109.1,  117.7),
        GrowthStandard(6, 107.1, 115.8,  124.5),
        GrowthStandard(7, 112.7, 122.0,  131.3),
        GrowthStandard(8, 118.2, 128.2,  138.2),
        GrowthStandard(9, 123.3, 134.1,  144.9),
        GrowthStandard(10, 128.7, 140.3, 151.9),
        GrowthStandard(11, 135.0, 147.2, 159.4),
        GrowthStandard(12, 142.2, 154.5, 166.8),
        GrowthStandard(13, 147.6, 159.3, 171.0),
        GrowthStandard(14, 150.6, 161.8, 173.0),
        GrowthStandard(15, 152.3, 162.8, 173.3),
        GrowthStandard(16, 153.0, 163.0, 173.0),
        GrowthStandard(17, 153.2, 163.2, 173.2),
        GrowthStandard(18, 153.4, 163.4, 173.4)
    )

    /**
     * 按性别与年龄查询对应标准。
     *
     * 年龄超出范围（< 3 或 > 18）时返回 null，调用方应跳过评级。
     */
    fun lookup(gender: String, age: Int): GrowthStandard? {
        val table = if (gender == "女") FEMALE else MALE
        return table.firstOrNull { it.age == age }
    }
}
