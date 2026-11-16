package io.github.dragjon.eigentone.platform

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Android's AtomicFile, for the JVM: a save is written beside the file and then moved over it in one step,
 * so a crash mid-save leaves the last good copy rather than half of a new one.
 */
class AtomicFile(val baseFile: File) {
    private val newFile = File(baseFile.path + ".new")

    fun startWrite(): FileOutputStream {
        baseFile.parentFile?.mkdirs()
        return FileOutputStream(newFile)
    }

    fun finishWrite(stream: FileOutputStream) {
        stream.fd.sync()
        stream.close()
        Files.move(newFile.toPath(), baseFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    fun failWrite(stream: FileOutputStream) {
        runCatching { stream.close() }
        newFile.delete()
    }

    fun readFully(): ByteArray = baseFile.readBytes()
}
