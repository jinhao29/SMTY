package com.shangmentiyu.sportscoach.update

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * [UpdateIntegrity.signaturesMatch] 纯逻辑单元测试（P2-3 修复配套）。
 *
 * 锁定不变量（fail-closed）：
 * - 签名指纹集合一致（顺序无关）→ 放行
 * - 指纹不一致 / 任一侧为空 → 一律拒绝
 *
 * 运行方式：./gradlew :app:testDebugUnitTest --tests "com.shangmentiyu.sportscoach.update.UpdateIntegrityTest"
 */
class UpdateIntegrityTest {

    private val fpA = "308202.." // 占位示意，实际为 Signature.toCharsString() 长串
    private val fpB = "308203.."

    @Test
    fun `指纹一致_顺序无关_放行`() {
        assertThat(
            UpdateIntegrity.signaturesMatch(listOf(fpA, fpB), listOf(fpB, fpA))
        ).isTrue()
    }

    @Test
    fun `指纹完全一致_放行`() {
        assertThat(
            UpdateIntegrity.signaturesMatch(listOf(fpA), listOf(fpA))
        ).isTrue()
    }

    @Test
    fun `指纹不一致_拒绝`() {
        assertThat(
            UpdateIntegrity.signaturesMatch(listOf(fpA), listOf(fpB))
        ).isFalse()
    }

    @Test
    fun `下载侧多出未知指纹_拒绝`() {
        assertThat(
            UpdateIntegrity.signaturesMatch(listOf(fpA), listOf(fpA, fpB))
        ).isFalse()
    }

    @Test
    fun `任一侧为空_拒绝_fail_closed`() {
        assertThat(UpdateIntegrity.signaturesMatch(emptyList(), listOf(fpA))).isFalse()
        assertThat(UpdateIntegrity.signaturesMatch(listOf(fpA), emptyList())).isFalse()
        assertThat(UpdateIntegrity.signaturesMatch(emptyList(), emptyList())).isFalse()
    }
}
