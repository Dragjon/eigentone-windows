package io.github.dragjon.eigentone.platform

/** Android's SystemClock, for the JVM: milliseconds that only ever go forward, unlike the wall clock. */
object SystemClock {
    fun uptimeMillis(): Long = System.nanoTime() / 1_000_000
    fun elapsedRealtime(): Long = System.nanoTime() / 1_000_000
}
