package org.htwk.pacing.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class HeartRateParserTest {

    @Test
    fun parseHeartRateRecords_validCsv_returnsRecords() {
        val lines = listOf(
            "139292,1,2025-07-09,00:00:00,57,136.5",
            "139293,2,2025-07-09,00:00:10,63,136.5"
        )

        val records = parseHeartRateRecords(lines)

        assertEquals(2, records.size)
        assertEquals(57, records[0].samples[0].beatsPerMinute)
        assertEquals(63, records[1].samples[0].beatsPerMinute)
    }

    @Test
    fun parseHeartRateRecords_invalidLines_areIgnored() {
        val lines = listOf(
            "139292,1,2025-07-09,00:00:00,57,136.5",
            "kaputte,zeile",
            "139294,3,2025-07-09,00:00:20,notANumber,136.5"
        )

        val records = parseHeartRateRecords(lines)

        assertEquals(1, records.size)
        assertEquals(57, records[0].samples[0].beatsPerMinute)
    }
}
