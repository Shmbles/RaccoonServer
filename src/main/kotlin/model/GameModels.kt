package com.shmbles.raccoon.model

import kotlinx.serialization.Serializable

/**
 * Represents the colors of the food cards in the game.
 */
enum class CardColor {
    YELLOW, PINK, GREEN, BLUE, ORANGE
}

/**
 * Represents a card in the game.
 */
@Serializable
sealed class Card {
    abstract val id: Int

    /**
     * Represents a food card with a specific color and value.
     * @property id The unique ID of the card.
     * @property color The color of the card.
     * @property value The value of the card.
     */
    @Serializable
    data class FoodCard(
        override val id: Int,
        val color: CardColor,
        val value: Int
    ) : Card()

    /**
     * Represents a bear card.
     * @property id The unique ID of the card.
     */
    @Serializable
    data class BearCard(
        override val id: Int
    ) : Card()
}

/**
 * Represents a player in the game.
 * @property id The unique ID of the player.
 * @property name The name of the player.
 * @property hand The cards in the player's hand.
 * @property playedFood The food cards the player has played, organized by color.
 * @property scorePile The cards in the player's score pile.
 */
@Serializable
data class Player(
    val id: String,
    val name: String,
    val hand: List<Card>,
    val playedFood: Map<CardColor, List<Card.FoodCard>>,
    val scorePile: List<Card.FoodCard>
)

/**
 * Represents the entire state of the game at a given moment.
 * @property players The list of players in the game.
 * @property drawDeck The list of cards in the draw deck.
 * @property raccoonHolders A map indicating which player holds the raccoon for each color.
 * @property currentPlayerId The ID of the player whose turn it is.
 * @property turnPhase The current phase of the turn.
 * @property status The current status of the game (e.g., playing, game over).
 * @property winnerId The ID of the winning player, if the game is over.
 */
@Serializable
data class GameState(
    val players: List<Player>,
    val drawDeck: List<Card>,
    val raccoonHolders: Map<CardColor, String?>,
    val currentPlayerId: String,
    val turnPhase: TurnPhase,
    val status: GameStatus = GameStatus.PLAYING,
    val winnerId: String? = null
)

/**
 * Represents the status of the game.
 */
enum class GameStatus {
    PLAYING,
    GAME_OVER
}

/**
 * Represents the different phases of a player's turn.
 */
enum class TurnPhase {
    PLAY_FOOD,
    USE_BEAR,
    DRAW_AND_SCORE
}

/**
 * Represents the configuration of the game.
 * @property playerCount The number of players in the game.
 * @property winScore The number of points required to win the game.
 */
@Serializable
data class GameConfig(
    val playerCount: Int = 2,
    val winScore: Int = 8
)