package com.shmbles.raccoon.server

import com.shmbles.raccoon.model.Card
import com.shmbles.raccoon.network.ClientAction
import com.shmbles.raccoon.network.ServerEvent
import com.shmbles.raccoon.server.helpers.RoomCodeGenerator
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import java.util.concurrent.ConcurrentHashMap

/**
 * Objeto singleton que gestiona todas las salas de juego y las conexiones de los jugadores.
 * Actúa como el controlador principal que enruta las acciones a la sala de juego correcta.
 */
object GameManager {

    private val rooms = ConcurrentHashMap<String, GameRoom>()

    /**
     * Instancia de [Json] configurada para la serialización polimórfica de acciones y eventos.
     * Es compartida en toda la aplicación del servidor.
     */
    val AppJson = Json {
        classDiscriminator = "type"
        ignoreUnknownKeys = true
        serializersModule = SerializersModule {
            polymorphic(ServerEvent::class) {
                subclass(ServerEvent.FullGameState::class)
                subclass(ServerEvent.ErrorMessage::class)
                subclass(ServerEvent.YouAre::class)
                subclass(ServerEvent.RoomCreated::class)
                subclass(ServerEvent.LobbyUpdate::class)
                subclass(ServerEvent.GameStarted::class)
                subclass(ServerEvent.NavigateToLobby::class)
                subclass(ServerEvent.ConfigUpdate::class)
            }
            polymorphic(ClientAction::class) {
                subclass(ClientAction.PlayFood::class)
                subclass(ClientAction.SkipPlayFood::class)
                subclass(ClientAction.UseBear::class)
                subclass(ClientAction.SkipBear::class)
                subclass(ClientAction.DrawAndScore::class)
                subclass(ClientAction.CreateRoom::class)
                subclass(ClientAction.JoinRoom::class)
                subclass(ClientAction.LeaveRoom::class)
                subclass(ClientAction.StartGame::class)
                subclass(ClientAction.ReturnToLobby::class)
                subclass(ClientAction.UpdateConfig::class)
            }
            polymorphic(Card::class) {
                subclass(Card.FoodCard::class)
                subclass(Card.BearCard::class)
            }
        }
    }

    /**
     * Registra una nueva conexión de cliente.
     */
    suspend fun onPlayerConnected(session: WebSocketServerSession) {
        println("New client connected: ${session.hashCode()}")
    }

    /**
     * Maneja la desconexión de un cliente, notificando a la sala correspondiente para que lo elimine.
     * Si la sala queda vacía, se elimina.
     */
    suspend fun onPlayerDisconnected(session: WebSocketServerSession) {
        val room = findRoomBySession(session)
        room?.handleDisconnection(session)

        if (room != null && room.isEmpty()) {
            rooms.remove(room.roomCode)
            println("Room ${room.roomCode} is empty and has been removed.")
        }
    }

    /**
     * Maneja acciones a nivel de sesión, como crear o unirse a una sala.
     * Estas acciones no requieren que un jugador ya esté en una sala.
     * @param session La sesión del jugador que realiza la acción.
     * @param action La acción de sesión a procesar.
     */
    suspend fun handleSessionAction(session: WebSocketServerSession, action: ClientAction) {
        when (action) {
            is ClientAction.CreateRoom -> {
                if (action.nickname.isBlank()) {
                    val errorEvent = ServerEvent.ErrorMessage("El nickname no puede estar vacío.")
                    session.send(Frame.Text(AppJson.encodeToString(ServerEvent.serializer(), errorEvent)))
                    return
                }

                var newCode: String
                do {
                    newCode = RoomCodeGenerator.generate()
                } while (rooms.containsKey(newCode))

                val newRoom = GameRoom(newCode, action.config)
                rooms[newCode] = newRoom

                println("Player with nickname '${action.nickname}' created room $newCode")
                newRoom.addPlayer(session, action.nickname, isHost = true)
            }

            is ClientAction.JoinRoom -> {
                if (action.nickname.isBlank()) {
                    val errorEvent = ServerEvent.ErrorMessage("El nickname no puede estar vacío.")
                    session.send(Frame.Text(AppJson.encodeToString(ServerEvent.serializer(), errorEvent)))
                    return
                }

                val room = rooms[action.roomCode.uppercase()]
                if (room == null) {
                    val errorEvent = ServerEvent.ErrorMessage("Sala no encontrada: ${action.roomCode}")
                    session.send(Frame.Text(AppJson.encodeToString(ServerEvent.serializer(), errorEvent)))
                } else {
                    println("Player with nickname '${action.nickname}' attempting to join room ${action.roomCode}")
                    room.addPlayer(session, action.nickname, isHost = false)
                }
            }

            else -> {
                println("Error: Received non-session action in handleSessionAction.")
                session.close(CloseReason(CloseReason.Codes.PROTOCOL_ERROR, "Invalid session action."))
            }
        }
    }

    /**
     * Maneja acciones de juego que requieren que el jugador esté en una sala.
     * @param session La sesión del jugador que realiza la acción.
     * @param action La acción de juego a procesar.
     */
    suspend fun handleGameAction(session: WebSocketServerSession, action: ClientAction) {
        val room = findRoomBySession(session)
        if (room == null) {
            println("Error: Received game action from a player who is not in a room.")
            val errorEvent = ServerEvent.ErrorMessage("No estás actualmente en una sala.")
            session.send(Frame.Text(AppJson.encodeToString(ServerEvent.serializer(), errorEvent)))
            return
        }
        room.handleAction(session, action)
    }

    /**
     * Encuentra la sala a la que pertenece una sesión de jugador.
     * @param session La sesión a buscar.
     * @return La [GameRoom] correspondiente, o null si no se encuentra.
     */
    private fun findRoomBySession(session: WebSocketServerSession): GameRoom? {
        return rooms.values.find { it.hasPlayer(session) }
    }
}
