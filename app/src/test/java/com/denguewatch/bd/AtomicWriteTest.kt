package com.denguewatch.bd

import com.denguewatch.bd.data.writeTextAtomically
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * The stores' save path: the reader's whole health record is one file, so a
 * save must replace it completely or leave it as it was.
 */
class AtomicWriteTest {

    @Test
    fun `replaces the contents and leaves no temporary file behind`() {
        val directory = Files.createTempDirectory("atomic").toFile()
        try {
            val file = File(directory, "vitals.json")
            file.writeText("[{\"old\":true}]")

            file.writeTextAtomically("[]")

            assertEquals("[]", file.readText())
            assertFalse(File(directory, "vitals.json.tmp").exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `creates the file when there was none`() {
        val directory = Files.createTempDirectory("atomic").toFile()
        try {
            val file = File(directory, "fluids.json")
            file.writeTextAtomically("[1,2,3]")
            assertEquals("[1,2,3]", file.readText())
        } finally {
            directory.deleteRecursively()
        }
    }
}
