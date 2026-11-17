package io.github.yulbax.erkin.data

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import java.io.File
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppDatabaseVersionTest {
    private val dir = File(System.getProperty("java.io.tmpdir"), "erkin-db-version-${System.nanoTime()}").apply { mkdirs() }
    private val dbFile = File(dir, AppDatabase.FILE_NAME)

    @After
    fun cleanup() {
        dir.deleteRecursively()
    }

    @Test
    fun aMissingDatabaseIsNotNewer() {
        assertFalse(AppDatabase.isNewerThanThisBuild(dbFile))
        assertFalse("the check must not create the database", dbFile.exists())
    }

    @Test
    fun onlyADatabaseFromANewerBuildIsNewer() {
        seed(AppDatabase.VERSION)
        assertFalse(AppDatabase.isNewerThanThisBuild(dbFile))
        seed(AppDatabase.VERSION + 1)
        assertTrue(AppDatabase.isNewerThanThisBuild(dbFile))
    }

    private fun seed(version: Int) {
        val connection = BundledSQLiteDriver().open(dbFile.absolutePath)
        try {
            connection.execSQL("PRAGMA user_version = $version")
        } finally {
            connection.close()
        }
    }
}
