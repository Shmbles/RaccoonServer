package com.shmbles.raccoon.server.helpers

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RoomCodeGeneratorTest {

    @Test
    fun `generate should produce a code of length 5`() {
        val code = RoomCodeGenerator.generate()
        assertEquals(5, code.length, "Room code should have exactly 5 characters")
    }

    @Test
    fun `generate should only contain uppercase alphabetic characters`() {
        for (i in 1..50) {
            val code = RoomCodeGenerator.generate()
            assertTrue(code.all { it in 'A'..'Z' }, "Code $code should only contain uppercase letters A-Z")
        }
    }

    @Test
    fun `generate should produce diverse codes`() {
        val generatedCodes = (1..100).map { RoomCodeGenerator.generate() }.toSet()
        assertTrue(generatedCodes.size > 90, "Generated codes should be largely unique")
    }
}
