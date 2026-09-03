package com.shmbles.raccoon.engine

import com.shmbles.raccoon.model.*
import kotlin.test.*

class GameEngineTest {

    private val player1 = "p1" to "Player One"
    private val player2 = "p2" to "Player Two"
    private val player3 = "p3" to "Player Three"
    private val defaultConfig = GameConfig(playerCount = 2, winScore = 10)

    @Test
    fun `initial game state should deal 5 cards to each player and start in PLAY_FOOD phase`() {
        val engine = GameEngine(defaultConfig, listOf(player1, player2))
        val state = engine.gameState.value

        assertEquals(GameStatus.PLAYING, state.status)
        assertEquals(2, state.players.size)
        assertEquals(TurnPhase.PLAY_FOOD, state.turnPhase)
        assertNotNull(state.currentPlayerId)
        assertTrue(state.players.any { it.id == state.currentPlayerId })

        for (player in state.players) {
            assertEquals(5, player.hand.size, "${player.name} should have 5 cards")
            assertTrue(player.scorePile.isEmpty(), "${player.name} score pile should be empty")
            assertTrue(player.playedFood.values.all { it.isEmpty() }, "${player.name} played food should have no cards")
        }

        // 108 total cards (19 food cards * 5 colors + 13 bear cards) - 10 dealt = 98 remaining
        assertEquals(98, state.drawDeck.size)
    }

    @Test
    fun `playing food cards should move all cards of selected color from hand to playedFood`() {
        val engine = GameEngine(defaultConfig, listOf(player1, player2))
        val initial = engine.gameState.value
        val currentPlayer = initial.players.find { it.id == initial.currentPlayerId }!!

        val foodCard = currentPlayer.hand.filterIsInstance<Card.FoodCard>().firstOrNull()
        if (foodCard != null) {
            val color = foodCard.color
            val cardsOfColorInHand = currentPlayer.hand.filterIsInstance<Card.FoodCard>().filter { it.color == color }

            engine.onPlayFood(currentPlayer.id, color)
            val updated = engine.gameState.value
            val updatedPlayer = updated.players.find { it.id == currentPlayer.id }!!

            // Cards should no longer be in hand
            assertTrue(updatedPlayer.hand.filterIsInstance<Card.FoodCard>().none { it.color == color })
            // Cards should be in playedFood
            assertEquals(cardsOfColorInHand.size, updatedPlayer.playedFood[color]?.size)
            // Raccoon holder should be updated
            assertEquals(currentPlayer.id, updated.raccoonHolders[color])
        }
    }

    @Test
    fun `skipping play food should transition phase without altering cards`() {
        val engine = GameEngine(defaultConfig, listOf(player1, player2))
        val initial = engine.gameState.value
        val currentPlayer = initial.players.find { it.id == initial.currentPlayerId }!!
        val handBefore = currentPlayer.hand

        engine.onSkipPlayFood()
        val updated = engine.gameState.value
        val updatedPlayer = updated.players.find { it.id == initial.currentPlayerId }!!

        assertEquals(handBefore, updatedPlayer.hand)
        val expectedPhase = if (updatedPlayer.hand.any { it is Card.BearCard }) {
            TurnPhase.USE_BEAR
        } else {
            TurnPhase.DRAW_AND_SCORE
        }
        assertEquals(expectedPhase, updated.turnPhase)
    }

    @Test
    fun `action out of turn should be ignored`() {
        val engine = GameEngine(defaultConfig, listOf(player1, player2))
        val initial = engine.gameState.value
        val nonCurrentPlayerId = initial.players.first { it.id != initial.currentPlayerId }.id

        engine.onPlayFood(nonCurrentPlayerId, CardColor.YELLOW)
        val stateAfter = engine.gameState.value

        assertEquals(initial.currentPlayerId, stateAfter.currentPlayerId)
        assertEquals(initial.turnPhase, stateAfter.turnPhase)
    }

    @Test
    fun `skip bear phase transitions to DRAW_AND_SCORE`() {
        val engine = GameEngine(defaultConfig, listOf(player1, player2))
        val initial = engine.gameState.value
        val currentId = initial.currentPlayerId

        // Call onSkipPlayFood
        engine.onSkipPlayFood()

        val midState = engine.gameState.value
        if (midState.turnPhase == TurnPhase.USE_BEAR) {
            engine.onSkipBear(currentId)
            assertEquals(TurnPhase.DRAW_AND_SCORE, engine.gameState.value.turnPhase)
        }
    }

    @Test
    fun `draw and score should refill hand and pass turn to next player`() {
        val engine = GameEngine(defaultConfig, listOf(player1, player2))
        val initial = engine.gameState.value
        val firstPlayerId = initial.currentPlayerId

        // Advance to DRAW_AND_SCORE
        engine.onSkipPlayFood()
        val afterFood = engine.gameState.value
        if (afterFood.turnPhase == TurnPhase.USE_BEAR) {
            engine.onSkipBear(firstPlayerId)
        }

        assertEquals(TurnPhase.DRAW_AND_SCORE, engine.gameState.value.turnPhase)

        engine.onDrawAndScore()
        val afterTurn = engine.gameState.value

        // Next player should now have the turn in PLAY_FOOD phase
        assertNotEquals(firstPlayerId, afterTurn.currentPlayerId)
        assertEquals(TurnPhase.PLAY_FOOD, afterTurn.turnPhase)
    }

    @Test
    fun `removing player when 2 players remain ends game and declares winner`() {
        val engine = GameEngine(defaultConfig, listOf(player1, player2))
        val initial = engine.gameState.value

        engine.removePlayer(player1.first)
        val finalState = engine.gameState.value

        assertEquals(GameStatus.GAME_OVER, finalState.status)
        assertEquals(player2.first, finalState.winnerId)
        assertEquals(1, finalState.players.size)
    }

    @Test
    fun `removing player when 3 players remain continues the game`() {
        val engine = GameEngine(GameConfig(playerCount = 3, winScore = 10), listOf(player1, player2, player3))
        engine.removePlayer(player1.first)
        val finalState = engine.gameState.value

        assertEquals(GameStatus.PLAYING, finalState.status)
        assertEquals(2, finalState.players.size)
    }
}
