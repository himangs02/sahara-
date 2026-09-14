package com.yourteam.sahara.api

import org.junit.Assert.assertEquals
import org.junit.Test

class Iso8601Test {

    @Test fun `round trips millis through the formatted string`() {
        val millis = 1_757_836_800_000L
        val formatted = Iso8601.fromMillis(millis)
        assertEquals(millis, Iso8601.toMillis(formatted))
    }

    @Test fun `parses the second-precision variant FastAPI can also emit`() {
        val millis = Iso8601.toMillis("2026-09-14T12:00:00Z")
        assertEquals("2026-09-14T12:00:00.000Z", Iso8601.fromMillis(millis))
    }

    @Test fun `an unparseable string never throws, falls back instead`() {
        val result = Iso8601.toMillis("not a date")
        assert(result > 0)
    }
}
