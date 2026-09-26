package com.example

import com.example.data.formatSignedTaka
import com.example.data.formatTaka
import org.junit.Assert.assertEquals
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun takaFormatting_isDeterministic() {
        assertEquals("৳25,000", formatTaka(25000.0))
        assertEquals("+৳1,500", formatSignedTaka(1500.0))
        assertEquals("-৳2,000", formatSignedTaka(-2000.0))
    }
}
