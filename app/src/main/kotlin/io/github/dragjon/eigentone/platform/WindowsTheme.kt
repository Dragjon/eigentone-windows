package io.github.dragjon.eigentone.platform

/** What Windows itself is set to, so Eigentone matches it by default. */
object WindowsTheme {
    /**
     * The accent colour chosen in Windows' personalisation settings, as 0xAARRGGBB, or null if it can't be read.
     * Windows stores it as ABGR.
     */
    val accent: Long? by lazy {
        runCatching {
            val p = ProcessBuilder("reg", "query", """HKCU\Software\Microsoft\Windows\DWM""", "/v", "AccentColor")
                .redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().readText()
            p.waitFor()
            val abgr = Regex("0x([0-9a-fA-F]{8})").find(out)!!.groupValues[1].toLong(16)
            val r = abgr and 0xFF
            val g = (abgr shr 8) and 0xFF
            val b = (abgr shr 16) and 0xFF
            0xFF000000L or (r shl 16) or (g shl 8) or b
        }.getOrNull()
    }
}
