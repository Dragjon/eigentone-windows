package io.github.dragjon.eigentone.platform

import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.net.URI

/** What Android does through intents and system services, done the Windows way. */
object Actions {
    fun copy(text: String) {
        runCatching { Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null) }
    }

    fun paste(): String? = runCatching {
        Toolkit.getDefaultToolkit().systemClipboard.getData(java.awt.datatransfer.DataFlavor.stringFlavor) as? String
    }.getOrNull()

    /**
     * Android's share sheet has no Windows equivalent for plain text, so the text is saved as a file in the
     * user's Documents\Eigentone folder and shown in File Explorer, ready to attach or send.
     */
    fun share(subject: String, text: String): File? = runCatching {
        val dir = File(System.getProperty("user.home"), "Documents/Eigentone").apply { mkdirs() }
        val safe = subject.replace(Regex("""[^\w \-]"""), "").trim().ifEmpty { "Eigentone" }
        val file = File(dir, "$safe.txt")
        file.writeText(text)
        ProcessBuilder("explorer.exe", "/select,", file.absolutePath).start()
        file
    }.getOrNull()

    fun openUrl(url: String) {
        // Web links go to the browser; Windows' own links, such as ms-settings:sound, go through Explorer.
        if (url.startsWith("http")) runCatching { Desktop.getDesktop().browse(URI(url)) }
        else runCatching { ProcessBuilder("explorer.exe", url).start() }
    }
}
