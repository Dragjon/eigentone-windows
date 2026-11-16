package io.github.dragjon.eigentone.engine

import org.junit.Assert.assertEquals
import org.junit.Test

/** Windows' volume in dB becomes the "below full" that quiet listening works from. */
class VolumeTest {
    @Test
    fun aVolumeBelowTheTopOfItsRangeIsThatFarBelowFull() {
        assertEquals(17.8, AudioService.volumeAttenuation(-17.8, 0.0), 1e-9)
    }

    @Test
    fun anOutputWhoseRangeGoesAboveZeroIsMeasuredFromItsOwnTop() {
        // Some USB outputs top out above 0 dB, so 0 dB there is already turned down.
        assertEquals(6.0, AudioService.volumeAttenuation(0.0, 6.0), 1e-9)
    }

    @Test
    fun fullVolumeIsNoneBelowFull() {
        assertEquals(0.0, AudioService.volumeAttenuation(0.0, 0.0), 1e-9)
        assertEquals(0.0, AudioService.volumeAttenuation(0.5, 0.0), 1e-9)
    }
}
