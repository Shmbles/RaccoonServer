package com.shmbles.raccoon.server.helpers

/**
 * A utility object for generating random room codes.
 */
object RoomCodeGenerator {
    private const val CODE_LENGTH = 5
    private val CHARS = ('A'..'Z').toList()

    /**
     * Generates a random room code of a fixed length.
     * @return A new, randomly generated room code.
     */
    fun generate(): String {
        return (1..CODE_LENGTH)
            .map { CHARS.random() }
            .joinToString("")
    }
}