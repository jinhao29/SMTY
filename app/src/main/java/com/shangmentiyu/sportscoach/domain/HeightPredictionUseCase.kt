package com.shangmentiyu.sportscoach.domain

import com.shangmentiyu.sportscoach.data.model.Student

/**
 * 身高预测用例：协调 [HeightPredictionProcessor] / [HeightRatingProcessor] 与学员数据。
 *
 * 职责：
 * - 从 Student 实体提取参数并调用预测处理器与评级处理器
 * - 判断数据是否充足可进行预测
 * - 合并遗传预测结果与当前身高评级，返回 [HeightPredictionResult] 供 UI 层使用
 */
class HeightPredictionUseCase(
    private val processor: HeightPredictionProcessor = HeightPredictionProcessor(),
    private val ratingProcessor: HeightRatingProcessor = HeightRatingProcessor()
) {

    /**
     * 根据学员数据执行身高预测（含当前身高评级）。
     *
     * @param student 学员实体（需包含父母身高、当前身高等字段）
     * @return 预测结果；父母身高未填时返回 null
     */
    fun execute(student: Student): HeightPredictionResult? {
        val prediction = processor.predict(
            gender = student.gender,
            age = student.age,
            fatherHeight = student.fatherHeight,
            motherHeight = student.motherHeight,
            avgSleepHours = student.avgSleepHours,
            nutritionScore = student.nutritionScore,
            sportsMinsPerWeek = student.sportsMinsPerWeek
        ) ?: return null

        // 评级依赖当前身高，若学员未填身高则评级为 null
        val ratingOutcome = ratingProcessor.evaluate(
            gender = student.gender,
            age = student.age,
            currentHeight = student.heightCm.toDouble(),
            avgSleepHours = student.avgSleepHours,
            nutritionScore = student.nutritionScore,
            sportsMinsPerWeek = student.sportsMinsPerWeek
        )

        return prediction.copy(
            rating = ratingOutcome?.rating,
            ratingStandard = ratingOutcome?.standard,
            ratingAdvice = ratingOutcome?.advice
        )
    }

    /**
     * 根据独立参数执行身高预测（用于 UI 实时预览，无需先写库）。
     *
     * @param currentHeight   当前身高（cm），用于评级；为 0 时不评级
     */
    fun execute(
        gender: String,
        age: Int,
        fatherHeight: Double,
        motherHeight: Double,
        avgSleepHours: Double,
        nutritionScore: Int,
        sportsMinsPerWeek: Int,
        currentHeight: Double = 0.0
    ): HeightPredictionResult? {
        val prediction = processor.predict(
            gender, age, fatherHeight, motherHeight,
            avgSleepHours, nutritionScore, sportsMinsPerWeek
        ) ?: return null

        val ratingOutcome = ratingProcessor.evaluate(
            gender = gender,
            age = age,
            currentHeight = currentHeight,
            avgSleepHours = avgSleepHours,
            nutritionScore = nutritionScore,
            sportsMinsPerWeek = sportsMinsPerWeek
        )

        return prediction.copy(
            rating = ratingOutcome?.rating,
            ratingStandard = ratingOutcome?.standard,
            ratingAdvice = ratingOutcome?.advice
        )
    }
}
