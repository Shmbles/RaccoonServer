package com.shmbles.raccoon.server

import com.shmbles.raccoon.engine.GameEngine
import com.shmbles.raccoon.model.GameConfig
import com.shmbles.raccoon.model.GameStatus
import com.shmbles.raccoon.network.ClientAction
import com.shmbles.raccoon.network.ServerEvent
import com.shmbles.raccoon.server.GameManager
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Representa una única sala de juego, gestionando sus jugadores, estado y lógica de juego.
 *
 * @property roomCode El código único para esta sala de juego.
 * @param config La configuración inicial del juego.
 */
class GameRoom(
    val roomCode: String,
    private var config: GameConfig
) {
    private var engine: GameEngine? = null
    private val AppJson = GameManager.AppJson

    private val players = ConcurrentHashMap<String, String>() // Map<PlayerID, Nickname>
    private val sessions = ConcurrentHashMap<WebSocketServerSession, String>() // Map<Session, PlayerID>
    private val playerCounter = AtomicInteger(0)

    private var hostSession: WebSocketServerSession? = null
    private val mutex = Mutex()

    /**
     * Comprueba si la sala no tiene jugadores activos.
     * @return `true` si la sala está vacía, `false` en caso contrario.
     */
    fun isEmpty(): Boolean = sessions.isEmpty()

    /**
     * Comprueba si una sesión específica está en esta sala.
     * @param session La sesión WebSocket a comprobar.
     * @return `true` si la sesión está en la sala, `false` en caso contrario.
     */
    fun hasPlayer(session: WebSocketServerSession): Boolean = sessions.containsKey(session)

    /**
     * Añade un nuevo jugador a la sala.
     * @param session La sesión WebSocket del nuevo jugador.
     * @param nickname El apodo del nuevo jugador.
     * @param isHost `true` si este jugador es el anfitrión de la sala.
     */
    suspend fun addPlayer(session: WebSocketServerSession, nickname: String, isHost: Boolean) {
        mutex.withLock {
            if (engine != null && engine!!.gameState.value.status == GameStatus.PLAYING) {
                session.send(Frame.Text(AppJson.encodeToString(ServerEvent.serializer(), ServerEvent.ErrorMessage("La partida ya ha comenzado."))))
                return
            }
            if (players.size >= config.playerCount && !sessions.containsKey(session)) {
                session.send(Frame.Text(AppJson.encodeToString(ServerEvent.serializer(), ServerEvent.ErrorMessage("La sala está llena."))))
                return
            }
            if (isHost) {
                hostSession = session
            }

            val playerId = sessions.getOrPut(session) { "p${playerCounter.incrementAndGet()}" }
            players[playerId] = nickname

            println("Player $playerId ('$nickname') joined room $roomCode")

            val welcomeEvent = ServerEvent.YouAre(playerId)
            session.send(Frame.Text(AppJson.encodeToString(ServerEvent.serializer(), welcomeEvent)))

            if (isHost) {
                val createdEvent = ServerEvent.RoomCreated(roomCode)
                session.send(Frame.Text(AppJson.encodeToString(ServerEvent.serializer(), createdEvent)))
            }

            broadcastLobbyUpdate()
        }
    }

    /**
     * Punto de entrada público para manejar la desconexión de un jugador. Adquiere un bloqueo.
     * Es llamado por GameManager cuando se pierde una conexión WebSocket.
     */
    suspend fun handleDisconnection(session: WebSocketServerSession) {
        mutex.withLock {
            removePlayerFromRoom(session)
        }
    }

    /**
     * Lógica principal para eliminar a un jugador. NO adquiere un bloqueo, asumiendo que el llamador lo tiene.
     */
    private suspend fun removePlayerFromRoom(session: WebSocketServerSession) {
        val playerId = sessions.remove(session)
        if (playerId != null) {
            val nickname = players.remove(playerId)
            println("Player $playerId ('$nickname') left room $roomCode")

            if (session == hostSession) {
                println("HOST has left room $roomCode. Terminating room for all players.")
                val hostLeftMessage = ServerEvent.ErrorMessage("El Host ha abandonado la partida. El juego ha terminado.")
                val eventJson = AppJson.encodeToString(ServerEvent.serializer(), hostLeftMessage)

                sessions.keys.forEach { remainingSession ->
                    try {
                        remainingSession.send(Frame.Text(eventJson))
                        remainingSession.close(CloseReason(CloseReason.Codes.NORMAL, "Host disconnected"))
                    } catch (e: Exception) {
                        println("Failed to notify session ${remainingSession.hashCode()}: ${e.message}")
                    }
                }
                players.clear()
                sessions.clear()
                hostSession = null
                engine = null
            } else {
                val currentEngine = engine
                if (currentEngine != null && currentEngine.gameState.value.status == GameStatus.PLAYING) {
                    println("Player $playerId ('$nickname') disconnected from an active game.")
                    currentEngine.removePlayer(playerId)
                    broadcastGameState()
                } else {
                    println("Player $playerId ('$nickname') left the lobby.")
                    broadcastLobbyUpdate()
                }
            }
        }
    }

    /**
     * Maneja una acción enviada por un jugador en la sala.
     * @param session La sesión WebSocket del jugador que envió la acción.
     * @param action La acción enviada por el jugador.
     */
    suspend fun handleAction(session: WebSocketServerSession, action: ClientAction) {
        mutex.withLock {
            val playerId = sessions[session] ?: return@withLock
            val currentEngine = engine

            when (action) {
                is ClientAction.LeaveRoom -> {
                    removePlayerFromRoom(session)
                }
                is ClientAction.StartGame -> {
                    if (session != hostSession) { return@withLock }
                    if (players.size != config.playerCount) {
                        val errorMessage = "Se necesitan ${config.playerCount} jugadores, pero hay ${players.size}."
                        session.send(Frame.Text(AppJson.encodeToString(ServerEvent.serializer(), ServerEvent.ErrorMessage(errorMessage))))
                        return@withLock
                    }
                    this.engine = GameEngine(config, players.toList().sortedBy { it.first })
                    println("Room $roomCode is starting the game with ${config.playerCount} players and ${config.winScore} points!")
                    broadcast(ServerEvent.GameStarted)
                    broadcastGameState()
                }
                is ClientAction.ReturnToLobby -> {
                    if (session != hostSession) { return@withLock }
                    println("Room $roomCode returning to lobby...")
                    this.engine = null
                    broadcast(ServerEvent.NavigateToLobby)
                    broadcastLobbyUpdate()
                }
                is ClientAction.UpdateConfig -> {
                    if (session != hostSession) { return@withLock }
                    val newConfig = action.newConfig
                    val validPlayerRange = 2..6
                    val validScoreRange = 3..20

                    if (newConfig.playerCount !in validPlayerRange || newConfig.winScore !in validScoreRange) {
                        val errorEvent = ServerEvent.ErrorMessage("Configuración inválida. Jugadores: ${validPlayerRange}, Puntos: ${validScoreRange}.")
                        session.send(Frame.Text(AppJson.encodeToString(ServerEvent.serializer(), errorEvent)))
                        return@withLock
                    }

                    if (newConfig.playerCount < players.size) {
                        val errorEvent = ServerEvent.ErrorMessage("No se puede reducir el número de jugadores por debajo de la cantidad actual.")
                        session.send(Frame.Text(AppJson.encodeToString(ServerEvent.serializer(), errorEvent)))
                        broadcastLobbyUpdate()
                        return@withLock
                    }

                    this.config = newConfig
                    println("Room $roomCode updated configuration to: $newConfig")
                    broadcastLobbyUpdate()
                }
                else -> {
                    if (currentEngine == null || currentEngine.gameState.value.status != GameStatus.PLAYING || currentEngine.gameState.value.currentPlayerId != playerId) {
                        return@withLock
                    }
                    when (action) {
                        is ClientAction.PlayFood -> currentEngine.onPlayFood(playerId, action.color)
                        is ClientAction.SkipPlayFood -> currentEngine.onSkipPlayFood()
                        is ClientAction.UseBear -> currentEngine.onUseBear(playerId, action.bearCardId, action.targetPlayerId, action.targetColor)
                        is ClientAction.SkipBear -> currentEngine.onSkipBear(playerId)
                        is ClientAction.DrawAndScore -> currentEngine.onDrawAndScore()
                        else -> {}
                    }
                    broadcastGameState()
                }
            }
        }
    }

    private suspend fun broadcastGameState() {
        val currentEngine = engine ?: return
        val newEvent = ServerEvent.FullGameState(currentEngine.gameState.value)
        broadcast(newEvent)
    }

    private suspend fun broadcastLobbyUpdate() {
        val nicknames = players.values.toList()
        val lobbyUpdateEvent = ServerEvent.LobbyUpdate(nicknames, this.config)
        broadcast(lobbyUpdateEvent)
    }

    private suspend fun broadcast(event: ServerEvent) {
        val eventJson = AppJson.encodeToString(ServerEvent.serializer(), event)
        sessions.keys.forEach { session ->
            try {
                session.send(Frame.Text(eventJson))
            } catch (e: Exception) {
                println("Failed to send to session ${session.hashCode()}: ${e.message}")
            }
        }
    }
}
