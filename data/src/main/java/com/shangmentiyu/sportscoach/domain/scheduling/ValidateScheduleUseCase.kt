package com.shangmentiyu.sportscoach.domain.scheduling

import com.shangmentiyu.sportscoach.data.model.LessonPackage

interface ScheduleValidationSource {
    suspend fun getActivePackagesByStudent(studentName: String): List<LessonPackage>
    suspend fun countUnconsumedLessonsFrom(studentName: String, fromDate: String): Int
    suspend fun countLongTermPendingFrom(studentName: String, fromDate: String): Int
    suspend fun earliestPurchaseDateOf(studentName: String): String?
}

class ValidateScheduleUseCase(
    private val source: ScheduleValidationSource
) {

    suspend fun isDateValid(studentName: String, dateStr: String): Boolean {
        val purchaseDate = source.earliestPurchaseDateOf(studentName) ?: return true
        return dateStr >= purchaseDate
    }

    suspend fun hasRemainingCapacity(studentName: String, dateStr: String): Boolean {
        val totalRemaining = EffectiveRemainingCalculator.calculate(
            source.getActivePackagesByStudent(studentName), dateStr)
        if (totalRemaining <= 0) return false
        val pending = source.countUnconsumedLessonsFrom(studentName, dateStr)
        return pending < totalRemaining
    }

    suspend fun futureAvailableLessons(studentName: String, fromDate: String): Int {
        val totalRemaining = EffectiveRemainingCalculator.calculate(
            source.getActivePackagesByStudent(studentName), fromDate)
        val pending = source.countLongTermPendingFrom(studentName, fromDate)
        return (totalRemaining - pending).coerceAtLeast(0)
    }
}
