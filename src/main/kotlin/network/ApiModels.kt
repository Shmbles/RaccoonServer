package com.shmbles.raccoon.network 

import com.shmbles.raccoon.model.CardColor
import com.shmbles.raccoon.model.GameConfig
import com.shmbles.raccoon.model.GameState
import kotlinx.serialization.Serializable

/**
 * Define todos los mensajes que el Cliente (la App) puede enviar al Servidor.
 */
@Serializable
sealed class ClientAction {

    /**
     * Acción para que un jugador solicite la creación de una nueva sala de juego.
     * @property config La configuración del juego, como el número de jugadores y los puntos para ganar.
     * @property nickname El apodo del jugador que crea la sala.
     */
    @Serializable
    data class CreateRoom(val config: GameConfig, val nickname: String) : ClientAction()

    /**
     * Acción para que un jugador solicite unirse a una sala de juego existente mediante un código.
     * @property roomCode El código de la sala a la que unirse.
     * @property nickname El apodo del jugador que se une a la sala.
     */
    @Serializable
    data class JoinRoom(val roomCode: String, val nickname: String) : ClientAction()

    /**
     * Acción para que un jugador abandone explícitamente la sala actual.
     */
    @Serializable
    data object LeaveRoom : ClientAction()

    /**
     * Acción para que el anfitrión (creador) de la sala inicie el juego.
     */
    @Serializable
    data object StartGame : ClientAction()

    /**
     * Acción para que un jugador juegue todas las cartas de un color específico de su mano.
     * @property color El color de las cartas a jugar.
     */
    @Serializable
    data class PlayFood(val color: CardColor) : ClientAction()

    /**
     * Acción para que un jugador decida no jugar ninguna carta de comida durante su turno.
     */
    @Serializable
    data object SkipPlayFood : ClientAction()

    /**
     * Acción para que un jugador use una carta de Oso contra otro jugador.
     * @property bearCardId El ID de la carta de Oso que se está utilizando.
     * @property targetPlayerId El ID del jugador objetivo.
     * @property targetColor El color de las cartas de comida del tablero del jugador objetivo.
     */
    @Serializable
    data class UseBear(
        val bearCardId: Int,
        val targetPlayerId: String,
        val targetColor: CardColor
    ) : ClientAction()

    /**
     * Acción para que un jugador decida no usar una carta de Oso.
     */
    @Serializable
    data object SkipBear : ClientAction()

    /**
     * Acción para que un jugador finalice su turno, robe nuevas cartas y puntúe.
     */
    @Serializable
    data object DrawAndScore : ClientAction()

    /**
     * Acción para que un jugador vuelva al lobby desde la pantalla de juego.
     */
    @Serializable
    data object ReturnToLobby : ClientAction()

    /**
     * Acción para que el anfitrión actualice la configuración del juego.
     * @property newConfig La nueva configuración del juego.
     */
    @Serializable
    data class UpdateConfig(val newConfig: GameConfig) : ClientAction()
}

/**
 * Define todos los eventos que el Servidor puede enviar al Cliente (la App).
 */
@Serializable
sealed class ServerEvent {
    /**
     * Evento enviado al anfitrión de una sala con el código de la sala recién creada.
     * @property roomCode El código único para la nueva sala de juego.
     */
    @Serializable
    data class RoomCreated(val roomCode: String) : ServerEvent()

    /**
     * Evento enviado a todos los jugadores de una sala para actualizarles sobre la lista actual de jugadores.
     * También se utiliza para confirmar que un jugador se ha unido correctamente a una sala.
     * @property playerNicknames La lista de apodos de los jugadores en el lobby.
     * @property config La configuración actual del juego.
     */
    @Serializable
    data class LobbyUpdate(val playerNicknames: List<String>, val config: GameConfig) : ServerEvent()

    /**
     * Evento enviado a todos los jugadores de una sala para notificarles que el juego ha comenzado.
     * La app cliente utilizará esto para navegar a la pantalla de juego.
     */
    @Serializable
    data object GameStarted : ServerEvent()

    /**
     * El evento más importante: el servidor envía el estado completo y actualizado del juego a todos los jugadores.
     * La app cliente recibe esto y muestra el estado.
     * @property state El estado completo y actual del juego.
     */
    @Serializable
    data class FullGameState(val state: GameState) : ServerEvent()

    /**
     * Evento para notificar a un jugador de un error (ej. "No es tu turno").
     * @property message El mensaje de error a mostrar al jugador.
     */
    @Serializable
    data class ErrorMessage(val message: String) : ServerEvent()

    /**
     * Evento enviado a un jugador para informarle de su ID de jugador único.
     * @property yourPlayerId El identificador único para el jugador.
     */
    @Serializable
    data class YouAre(val yourPlayerId: String) : ServerEvent()

    /**
     * Evento para instruir al cliente a navegar de vuelta a la pantalla del lobby.
     */
    @Serializable
    data object NavigateToLobby : ServerEvent()

    /**
     * Evento para informar a los clientes de una actualización en la configuración del juego.
     * @property newConfig La nueva configuración del juego.
     */
    @Serializable
    data class ConfigUpdate(val newConfig: GameConfig) : ServerEvent()
}
