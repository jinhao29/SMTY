package com.shangmentiyu.sportscoach.ui

/**
 * 路由常量集中声明。
 *
 * 底部 Tab 路由：[HOME]、[SCORE]、[SETTINGS]。
 * 其余均为二级页面路由，通过 NavController.navigate 跳转。
 */
object Routes {
    // === 底部 Tab ===
    const val HOME = "home"
    const val SCORE = "score"
    const val SETTINGS = "settings"

    // === 二级页面 ===
    const val LESSON = "lesson/{lessonId}"
    const val SUMMARY = "summary/{lessonId}"
    const val ADD_STUDENT = "add_student"
    const val EDIT_STUDENT = "edit_student/{studentName}"
    const val GROWTH = "growth/{studentName}"
    const val TRAINING_PLAN = "training_plan/{studentName}"
    const val HEIGHT_PREDICTION = "height_prediction/{studentName}"
    const val DIET_MANAGE = "diet_manage/{studentName}"

    // === 训练规划类（设置详情页二级入口） ===
    const val STAGE_SUMMARY = "stage_summary"
    const val TRAINING_CYCLE = "training_cycle"
    const val BODY_METRIC = "body_metric"
    const val COACH_REPORT = "coach_report"

    // === 工具类 ===
    const val BMI_CALCULATOR = "bmi_calculator"

    // === 话术管理（设置页二级入口） ===
    const val SCRIPT_LIST = "script_list"
    const val SCRIPT_DETAIL = "script_detail/{scriptId}"

    fun scriptDetail(scriptId: String?) = if (scriptId.isNullOrBlank()) "script_detail/new" else "script_detail/$scriptId"

    // === 运营/排课（保留为二级页面，供主页课前准备 Tab 调用） ===
    const val OPERATION = "operation"
    const val LESSON_CHECKIN = "lesson_checkin"
    const val SCHEDULE = "schedule"

    // === 成绩录入（带 lessonId 关联） ===
    const val SCORING_WITH_LESSON = "scoring/{lessonId}"

    fun lesson(id: String) = "lesson/$id"
    fun summary(id: String) = "summary/$id"
    fun scoringWithLesson(id: String) = "scoring/$id"
    fun growth(studentName: String) = "growth/$studentName"
    fun trainingPlan(studentName: String) = "training_plan/$studentName"
    fun heightPrediction(studentName: String) = "height_prediction/$studentName"
    fun dietManage(studentName: String) = "diet_manage/$studentName"
    fun editStudent(studentName: String) = "edit_student/$studentName"
}
