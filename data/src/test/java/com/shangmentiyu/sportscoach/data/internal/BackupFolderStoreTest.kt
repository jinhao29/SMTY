package com.shangmentiyu.sportscoach.data.internal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 固定备份文件夹纯函数自检：文件名识别 + 最新备份选取。
 * 守护"恢复自动取最新"的选文件逻辑——前缀/扩展名匹配规则改坏时在此报错。
 */
class BackupFolderStoreTest {

    @Test
    fun `isBackupFileName recognizes manual and auto prefixes`() {
        assertTrue(BackupFolderStore.isBackupFileName("smty_backup_20260910_123507.zip"))
        assertTrue(BackupFolderStore.isBackupFileName("AutoBackup_20260910_120000.zip"))
        assertFalse(BackupFolderStore.isBackupFileName("restore_purge_zuqixin.zip"))
        assertFalse(BackupFolderStore.isBackupFileName("smty_backup_20260910_123507"))
        assertFalse(BackupFolderStore.isBackupFileName(null))
    }

    @Test
    fun `pickLatest returns newest backup across prefixes`() {
        val candidates = listOf(
            "AutoBackup_20260908_100000.zip" to 1000L,
            "smty_backup_20260910_123507.zip" to 3000L,
            "AutoBackup_20260909_100000.zip" to 2000L
        )
        assertEquals("smty_backup_20260910_123507.zip", BackupFolderStore.pickLatest(candidates)?.first)
    }

    @Test
    fun `pickLatest filters non-backup files and returns null when empty`() {
        assertNull(BackupFolderStore.pickLatest(listOf("notes.zip" to 9999L, "readme.txt" to 8888L)))
        assertNull(BackupFolderStore.pickLatest(emptyList()))
    }
}
