package com.denguewatch.bd.data

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Replaces this file's contents with [text], all at once or not at all.
 *
 * `writeText` truncates the file before it writes, so a process killed in
 * between — the reader swiping the app away as they save, the system reclaiming
 * memory — leaves a file holding half an array. The store then fails to parse
 * it, starts empty, and the next save writes that emptiness back: every reading
 * the reader ever kept, gone. iOS writes these files with `.atomic` for the same
 * reason.
 *
 * Written beside the file, flushed to disk, then renamed over it. A rename
 * within one directory is atomic, so the file is always either the old record
 * or the new one.
 */
internal fun File.writeTextAtomically(text: String) {
    val temporary = File(parentFile, "$name.tmp")
    FileOutputStream(temporary).use { stream ->
        stream.write(text.toByteArray(Charsets.UTF_8))
        stream.fd.sync()
    }
    Files.move(
        temporary.toPath(),
        toPath(),
        StandardCopyOption.REPLACE_EXISTING,
        StandardCopyOption.ATOMIC_MOVE,
    )
}
