package com.shangmentiyu.sportscoach.update

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class UpdateCheckerTest {

    @Test
    fun extractRemoteVersionCode_standardTag_returnsRunNumber() {
        assertThat(UpdateChecker.extractRemoteVersionCode("v0.33")).isEqualTo(33)
        assertThat(UpdateChecker.extractRemoteVersionCode("v0.1")).isEqualTo(1)
        assertThat(UpdateChecker.extractRemoteVersionCode("v0.10")).isEqualTo(10)
        assertThat(UpdateChecker.extractRemoteVersionCode("v0.99")).isEqualTo(99)
        assertThat(UpdateChecker.extractRemoteVersionCode("v0.100")).isEqualTo(100)
    }

    @Test
    fun extractRemoteVersionCode_legacyTag_returnsRunNumber() {
        assertThat(UpdateChecker.extractRemoteVersionCode("v33")).isEqualTo(33)
    }

    @Test
    fun extractRemoteVersionCode_unparseable_returnsNull() {
        assertThat(UpdateChecker.extractRemoteVersionCode("v0.33-beta")).isNull()
        assertThat(UpdateChecker.extractRemoteVersionCode("v1.0.5")).isNull()
        assertThat(UpdateChecker.extractRemoteVersionCode("0.33")).isNull()
        assertThat(UpdateChecker.extractRemoteVersionCode("")).isNull()
    }

    @Test
    fun extractRemoteVersionCode_crossesFloatBoundary() {
        assertThat(UpdateChecker.extractRemoteVersionCode("v0.10")!! > 9).isTrue()
        assertThat(UpdateChecker.extractRemoteVersionCode("v0.100")!! > 99).isTrue()
    }
}
