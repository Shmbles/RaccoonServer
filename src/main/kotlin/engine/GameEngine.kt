package com.shmbles.raccoon.engine

import com.shmbles.raccoon.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * El cerebro del juego, responsable de gestionar el estado y aplicar las reglas.
 * Esta clase es completamente independiente de la red y la UI.
 *
 * @param gameConfig La configuración para la partida (p. ej., puntos para ganar).
 * @param playersInfo Una lista de pares con los IDs y apodos de los jugadores.
 */
class GameEngine(
    private val gameConfig: GameConfig,
    private val playersInfo: List<Pair<String, String>>
) {

    private val _gameState: MutableStateFlow<GameState>
    /**
     * El estado actual del juego, expuesto como un [StateFlow] de solo lectura.
     */
    val gameState: StateFlow<GameState>

    init {
        _gameState = MutableStateFlow(createInitialGameState())
        gameState = _gameState.asStateFlow()
    }

    /**
     * Elimina a un jugador de la partida en curso.
     * Si el jugador eliminado era el jugador actual, avanza el turno al siguiente.
     * Si quedan menos de dos jugadores, la partida termina.
     * @param playerId El ID del jugador a eliminar.
     */
    fun removePlayer(playerId: String) {
        val currentState = _gameState.value
        val playerToRemove = currentState.players.find { it.id == playerId } ?: return

        println("GAME ENGINE: Removing player $playerId ('${playerToRemove.name}')")

        val remainingPlayers = currentState.players.filterNot { it.id == playerId }

        if (remainingPlayers.size < 2) {
            println("GAME OVER: Not enough players to continue.")
            _gameState.value = currentState.copy(
                players = remainingPlayers,
                status = GameStatus.GAME_OVER,
                winnerId = remainingPlayers.firstOrNull()?.id
            )
            return
        }

        var nextPlayerId = currentState.currentPlayerId
        if (currentState.currentPlayerId == playerId) {
            val oldPlayerIndex = currentState.players.indexOf(playerToRemove)
            val nextPlayerIndex = oldPlayerIndex % remainingPlayers.size
            nextPlayerId = remainingPlayers[nextPlayerIndex].id
        }

        var newState = currentState.copy(players = remainingPlayers, currentPlayerId = nextPlayerId)
        CardColor.entries.forEach { color ->
            newState = recalculateRaccoonHolder(newState, color)
        }

        _gameState.value = newState.copy(turnPhase = TurnPhase.PLAY_FOOD)
    }


    /**
     * Procesa la acción de un jugador de jugar todas sus cartas de comida de un color específico.
     * Valida que sea el turno y la fase correctos.
     * @param playerId El ID del jugador que realiza la acción.
     * @param color El color de las cartas de comida a jugar.
     */
    fun onPlayFood(playerId: String, color: CardColor) {
        val currentState = _gameState.value
        if (playerId != currentState.currentPlayerId || currentState.turnPhase != TurnPhase.PLAY_FOOD) return

        val player = currentState.players.find { it.id == playerId } ?: return

        val cardsToPlay = player.hand.filterIsInstance<Card.FoodCard>().filter { it.color == color }
        if (cardsToPlay.isEmpty()) return

        val newHand = player.hand.filterNot { it in cardsToPlay }
        val newPlayedFood = player.playedFood[color].orEmpty() + cardsToPlay

        val updatedPlayer = player.copy(
            hand = newHand,
            playedFood = player.playedFood + (color to newPlayedFood)
        )

        val updatedPlayers = currentState.players.map { if (it.id == playerId) updatedPlayer else it }
        var newState = currentState.copy(players = updatedPlayers)

        newState = recalculateRaccoonHolder(newState, color)

        val nextPhase = getNextPhaseAfterFood(updatedPlayer)
        _gameState.value = newState.copy(turnPhase = nextPhase)
    }

    /**
     * Procesa la acción de un jugador de saltar la fase de "jugar comida".
     */
    fun onSkipPlayFood() {
        val currentState = _gameState.value
        if (currentState.turnPhase != TurnPhase.PLAY_FOOD) return
        val player = currentState.players.find { it.id == currentState.currentPlayerId } ?: return
        val nextPhase = getNextPhaseAfterFood(player)
        _gameState.value = currentState.copy(turnPhase = nextPhase)
    }

    /**
     * Determina la siguiente fase del turno después de que un jugador ha jugado o saltado la fase de comida.
     */
    private fun getNextPhaseAfterFood(player: Player): TurnPhase {
        return if (player.hand.any { it is Card.BearCard }) {
            TurnPhase.USE_BEAR
        } else {
            TurnPhase.DRAW_AND_SCORE
        }
    }

    /**
     * Procesa la acción de un jugador de usar una carta de oso contra otro jugador.
     * Valida el turno, la fase, y que el jugador posea la carta y el objetivo sea válido.
     * @param playerId El ID del jugador que usa la carta.
     * @param bearCardId El ID de la carta de oso a usar.
     * @param targetPlayerId El ID del jugador objetivo.
     * @param targetColor El color de la pila de comida objetivo.
     */
    fun onUseBear(
        playerId: String,
        bearCardId: Int,
        targetPlayerId: String,
        targetColor: CardColor
    ) {
        var currentState = _gameState.value
        if (playerId != currentState.currentPlayerId || currentState.turnPhase != TurnPhase.USE_BEAR) return

        val player = currentState.players.find { it.id == playerId } ?: return

        val bearCardInHand = player.hand.find { it.id == bearCardId && it is Card.BearCard }
        if (bearCardInHand == null) {
            println("SECURITY: Player $playerId tried to use a bear card they don't have (ID: $bearCardId).")
            return
        }

        val targetPlayer = currentState.players.find { it.id == targetPlayerId } ?: run {
            println("SECURITY: Player $playerId targeted a non-existent player $targetPlayerId.")
            return
        }

        val targetPile = targetPlayer.playedFood[targetColor].orEmpty()
        if (targetPile.isEmpty()) return

        val cardToEat = targetPile.maxByOrNull { it.value } ?: return

        val newHand = player.hand - bearCardInHand
        val updatedPlayer = player.copy(hand = newHand)

        val newTargetPile = targetPile.minus(cardToEat)
        val updatedTargetPlayer = targetPlayer.copy(
            playedFood = targetPlayer.playedFood + (targetColor to newTargetPile)
        )

        val updatedPlayers = currentState.players.map {
            when (it.id) {
                playerId -> updatedPlayer
                targetPlayerId -> updatedTargetPlayer
                else -> it
            }
        }
        var newState = currentState.copy(players = updatedPlayers)
        newState = recalculateRaccoonHolder(newState, targetColor)
        _gameState.value = newState.copy(turnPhase = TurnPhase.DRAW_AND_SCORE)
    }

    /**
     * Procesa la acción de un jugador de saltar la fase de "usar oso".
     * @param playerId El ID del jugador que salta la fase.
     */
    fun onSkipBear(playerId: String) {
        val currentState = _gameState.value
        if (playerId != currentState.currentPlayerId || currentState.turnPhase != TurnPhase.USE_BEAR) return
        _gameState.value = currentState.copy(turnPhase = TurnPhase.DRAW_AND_SCORE)
    }

    /**
     * Procesa la fase final del turno: robar, puntuar y pasar al siguiente jugador.
     * Valida que sea la fase correcta.
     */
    fun onDrawAndScore() {
        val currentState = _gameState.value
        val playerId = currentState.currentPlayerId
        if (playerId != currentState.currentPlayerId || currentState.turnPhase != TurnPhase.DRAW_AND_SCORE) return

        val player = currentState.players.find { it.id == playerId }!!

        // 1. Calcular cuántas cartas robar.
        val cardsToDrawCount = (5 - player.hand.size).coerceAtLeast(0)

        // 2. Comprobar si el mazo se agota (condición de fin de juego).
        if (cardsToDrawCount > 0 && currentState.drawDeck.size < cardsToDrawCount) {
            println("GAME OVER: Draw deck is empty and player needs to draw.")
            val maxScore = currentState.players.maxOfOrNull { it.scorePile.size } ?: 0
            val winners = currentState.players.filter { it.scorePile.size == maxScore }
            val winnerId = if (winners.size == 1) winners.first().id else null
            _gameState.value = currentState.copy(status = GameStatus.GAME_OVER, winnerId = winnerId)
            return
        }

        // 3. Si no necesita robar, simplemente pasa el turno.
        if (cardsToDrawCount == 0) {
            val currentPlayerIndex = currentState.players.indexOfFirst { it.id == playerId }
            val nextPlayerIndex = (currentPlayerIndex + 1) % currentState.players.size
            val nextPlayerId = currentState.players[nextPlayerIndex].id
            _gameState.value = currentState.copy(currentPlayerId = nextPlayerId, turnPhase = TurnPhase.PLAY_FOOD)
            return
        }

        // 4. Robar cartas.
        val currentDeck = currentState.drawDeck
        val cardsDrawn = currentDeck.take(cardsToDrawCount)
        val remainingDeck = currentDeck.drop(cardsToDrawCount)

        // 5. Separar cartas que puntúan de las que van a la mano.
        val playerRaccoons = currentState.raccoonHolders.filter { it.value == playerId }.keys
        val (scoringCards, handCards) = cardsDrawn.partition { card ->
            card is Card.FoodCard && card.color in playerRaccoons
        }

        // 6. Actualizar mano y pila de puntuación del jugador.
        val finalHand = player.hand + handCards
        val finalScorePile = player.scorePile + scoringCards.filterIsInstance<Card.FoodCard>()
        val updatedPlayer = player.copy(hand = finalHand, scorePile = finalScorePile)
        val updatedPlayers = currentState.players.map { if (it.id == playerId) updatedPlayer else it }

        // 7. Comprobar si el jugador ha ganado.
        if (finalScorePile.size >= gameConfig.winScore) {
            _gameState.value = currentState.copy(
                players = updatedPlayers,
                drawDeck = remainingDeck,
                status = GameStatus.GAME_OVER,
                winnerId = playerId
            )
            return
        }

        // 8. Pasar al siguiente jugador.
        val currentPlayerIndex = updatedPlayers.indexOfFirst { it.id == playerId }
        val nextPlayerIndex = (currentPlayerIndex + 1) % updatedPlayers.size
        val nextPlayerId = updatedPlayers[nextPlayerIndex].id

        _gameState.value = currentState.copy(
            players = updatedPlayers,
            drawDeck = remainingDeck,
            currentPlayerId = nextPlayerId,
            turnPhase = TurnPhase.PLAY_FOOD
        )
    }

    /**
     * Recalcula quién posee el mapache de un color específico basado en la puntuación más alta.
     * Si hay un empate, nadie posee el mapache.
     * * IMPORTANTE: Incluye la lógica donde el dueño anterior pierde sus cartas si el dueño cambia.
     */
    private fun recalculateRaccoonHolder(state: GameState, color: CardColor): GameState {
        // 1. Calcular la puntuación de CADA jugador para este color
        val scores = state.players.associate { player ->
            player.id to (player.playedFood[color]?.sumOf { it.value } ?: 0)
        }

        // 2. Encontrar la puntuación máxima actual
        val maxScore = scores.values.maxOrNull() ?: 0

        // 3. Determinar quién debe tener el mapache
        val newHolderId = if (maxScore > 0) {
            // Encontrar todos los jugadores que tienen la puntuación máxima
            val playersWithMaxScore = scores.filter { it.value == maxScore }.keys

            if (playersWithMaxScore.size == 1) {
                // Si solo hay UNO con el máximo, él es el dueño
                playersWithMaxScore.first()
            } else {
                // Si hay empate en la cima, nadie tiene el mapache (se devuelve al centro)
                null
            }
        } else {
            // Si nadie tiene puntos en este color, el mapache está en el centro
            null
        }

        // 4. Lógica de transición de dueño (Pérdida de cartas del perdedor)
        var finalPlayers = state.players
        val previousHolderId = state.raccoonHolders[color]

        if (previousHolderId != null && previousHolderId != newHolderId) {
            finalPlayers = state.players.map { player ->
                if (player.id == previousHolderId) {
                    // El dueño anterior pierde sus cartas de comida de ese color al perder el mapache.
                    player.copy(playedFood = player.playedFood + (color to emptyList()))
                } else {
                    player
                }
            }
        }

        return state.copy(
            players = finalPlayers,
            raccoonHolders = state.raccoonHolders + (color to newHolderId)
        )
    }

    /**
     * Crea el estado inicial del juego, incluyendo el mazo y las manos de los jugadores.
     */
    private fun createInitialGameState(): GameState {
        var cardId = 0
        val deck = mutableListOf<Card>()

        CardColor.entries.forEach { color ->
            repeat(10) { deck.add(Card.FoodCard(id = cardId++, color = color, value = 1)) }
            repeat(5) { deck.add(Card.FoodCard(id = cardId++, color = color, value = 2)) }
            repeat(4) { deck.add(Card.FoodCard(id = cardId++, color = color, value = 3)) }
        }
        repeat(13) { deck.add(Card.BearCard(id = cardId++)) }

        val shuffledDeck = deck.shuffled().toMutableList()

        val players = playersInfo.map { (playerId, nickname) ->
            val hand = shuffledDeck.take(5)
            shuffledDeck.removeAll(hand)
            Player(
                id = playerId,
                name = nickname,
                hand = hand,
                playedFood = CardColor.entries.associateWith { emptyList() },
                scorePile = emptyList()
            )
        }

        return GameState(
            players = players,
            drawDeck = shuffledDeck,
            raccoonHolders = CardColor.entries.associateWith { null },
            currentPlayerId = players.firstOrNull()?.id ?: "",
            turnPhase = TurnPhase.PLAY_FOOD
        )
    }
}
