package com.shangmentiyu.sportscoach.update

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class UpdateCheckerTest {

    // === SemVer 编码公式：MAJOR*10000 + MINOR*100 + PATCH（2026-09-09 起） ===
    @Test
    fun extractRemoteVersionCode_semverTag_encodesFormula() {
        assertThat(UpdateChecker.extractRemoteVersionCode("v1.0.0")).isEqualTo(10000)
        assertThat(UpdateChecker.extractRemoteVersionCode("v1.2.3")).isEqualTo(10203)
        assertThat(UpdateChecker.extractRemoteVersionCode("v2.0.0")).isEqualTo(20000)
        assertThat(UpdateChecker.extractRemoteVersionCode("v1.0.1")).isEqualTo(10001)
        assertThat(UpdateChecker.extractRemoteVersionCode("v1.1.0")).isEqualTo(10100)
    }

    // === 兼容旧 run_number 时代 tag：v0.N → N*100，保证老设备（versionCode=N）能收到升级提示 ===
    @Test
    fun extractRemoteVersionCode_legacyRunNumberTag_mapsAboveOldVersionCode() {
        assertThat(UpdateChecker.extractRemoteVersionCode("v0.62")).isEqualTo(6200)
        assertThat(UpdateChecker.extractRemoteVersionCode("v0.10")).isEqualTo(1000)
        // 老 run_number 版本码（62、9）必须小于对应映射值，升级提示才成立
        assertThat(UpdateChecker.extractRemoteVersionCode("v0.62")!! > 62).isTrue()
        assertThat(UpdateChecker.extractRemoteVersionCode("v0.10")!! > 9).isTrue()
        // 新版 v1.0.0 必须大于所有旧 v0.N 映射，升级链不断
        assertThat(UpdateChecker.extractRemoteVersionCode("v1.0.0")!!)
            .isGreaterThan(UpdateChecker.extractRemoteVersionCode("v0.62")!!)
    }

    @Test
    fun extractRemoteVersionCode_unparseable_returnsNull() {
        // 无 minor 段：新公式会算成 MAJOR*10000 造成误判，必须拒绝
        assertThat(UpdateChecker.extractRemoteVersionCode("v33")).isNull()
        assertThat(UpdateChecker.extractRemoteVersionCode("v0.33-beta")).isNull()
        assertThat(UpdateChecker.extractRemoteVersionCode("0.33")).isNull()
        assertThat(UpdateChecker.extractRemoteVersionCode("")).isNull()
        assertThat(UpdateChecker.extractRemoteVersionCode("v1.0.0.0")).isNull()
        // 段值越界（>99）会破坏编码唯一性，同样拒绝
        assertThat(UpdateChecker.extractRemoteVersionCode("v1.100.0")).isNull()
        assertThat(UpdateChecker.extractRemoteVersionCode("v1.0.100")).isNull()
    }
}
