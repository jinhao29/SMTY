package com.shangmentiyu.sportscoach.core

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File
import java.nio.file.Files

class PhotoDirMigrationTest {

    private fun tempFilesDir(): File = Files.createTempDirectory("photo_migration_test").toFile()

    @Test
    fun `旧目录文件迁移至SignPhotos并删除旧目录`() {
        val filesDir = tempFilesDir()
        val legacy = File(filesDir, "sign_photos").apply { mkdirs() }
        File(legacy, "photo_a.enc.jpg").writeText("a")
        File(legacy, "photo_b.enc.jpg").writeText("b")

        BackupManager.migrateLegacySignPhotosDir(filesDir)

        assertThat(File(filesDir, "SignPhotos/photo_a.enc.jpg").readText()).isEqualTo("a")
        assertThat(File(filesDir, "SignPhotos/photo_b.enc.jpg").readText()).isEqualTo("b")
        assertThat(legacy.exists()).isFalse()
    }

    @Test
    fun `无旧目录时零副作用`() {
        val filesDir = tempFilesDir()

        BackupManager.migrateLegacySignPhotosDir(filesDir)

        assertThat(File(filesDir, "SignPhotos").exists()).isFalse()
    }

    @Test
    fun `目标已有同名文件时不覆盖且删除旧目录`() {
        val filesDir = tempFilesDir()
        val legacy = File(filesDir, "sign_photos").apply { mkdirs() }
        File(legacy, "photo_a.enc.jpg").writeText("old")
        File(filesDir, "SignPhotos/").mkdirs()
        File(filesDir, "SignPhotos/photo_a.enc.jpg").writeText("new")

        BackupManager.migrateLegacySignPhotosDir(filesDir)

        assertThat(File(filesDir, "SignPhotos/photo_a.enc.jpg").readText()).isEqualTo("new")
        assertThat(legacy.exists()).isFalse()
    }
}