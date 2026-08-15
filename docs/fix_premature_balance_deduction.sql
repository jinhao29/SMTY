-- ============================================================
-- 一次性修复脚本：修复自动排课阶段错误扣减的课时包余额
-- ============================================================
--
-- 背景：
--   旧版自动排课在排课阶段即扣减课时包余额（增加 usedLessons + 设置 Lesson.packageId），
--   导致排课与消课未分离。签退时检测到 packageId 非空会跳过扣费，
--   但未签退的排课记录已错误扣减了余额。
--
-- 修复逻辑：
--   1. 回退未签退课时对应的课时包 usedLessons（减去错误扣减数，不低于 0）
--   2. 恢复课时包状态（若 usedLessons 回退后 < totalLessons 且原状态为"已用完"，恢复为"活跃"）
--   3. 清除未签退课时的 packageId（恢复为待消课状态，签退时统一扣费）
--
-- 已签退的课时记录不受影响（packageId 保留作为扣费归属记录，usedLessons 计数正确）。
--
-- 使用方法：
--   方式1：在 Android Device Monitor / sqlite3 中直接执行本脚本
--   方式2：在 App 设置页"数据库修复与检查"中触发（调用了等价的 Kotlin 方法 fixPrematureBalanceDeduction）
--
-- 建议执行前先备份数据库。
-- ============================================================

-- Step 1: 回退课时包 usedLessons（按 packageId 分组统计未签退课时数，从 usedLessons 中扣除）
UPDATE lesson_packages
SET usedLessons = MAX(0, usedLessons - (
    SELECT COUNT(*) FROM lessons
    WHERE lessons.packageId = lesson_packages.id
    AND lessons.status != '已签退'
)),
status = CASE
    WHEN MAX(0, usedLessons - (
        SELECT COUNT(*) FROM lessons
        WHERE lessons.packageId = lesson_packages.id
        AND lessons.status != '已签退'
    )) >= totalLessons THEN '已用完'
    WHEN status = '已用完' THEN '活跃'
    ELSE status
END
WHERE id IN (
    SELECT DISTINCT packageId FROM lessons
    WHERE packageId != '' AND packageId IS NOT NULL
    AND status != '已签退'
);

-- Step 2: 清除未签退课时的 packageId（恢复为待消课状态）
UPDATE lessons
SET packageId = ''
WHERE packageId != '' AND packageId IS NOT NULL
AND status != '已签退';

-- 验证：检查是否还有未签退但 packageId 非空的课时记录（预期结果为 0）
-- SELECT COUNT(*) FROM lessons WHERE packageId != '' AND packageId IS NOT NULL AND status != '已签退';
