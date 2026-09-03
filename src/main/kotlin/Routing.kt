package com.shmbles.raccoon

import com.shmbles.raccoon.network.ClientAction
import com.shmbles.raccoon.server.GameManager
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.serialization.decodeFromString

/**
 * Configura el enrutamiento principal y el manejo de WebSockets para la aplicación.
 *
 * Establece un único endpoint WebSocket en "/game" que actúa como el punto de entrada para toda
 * la comunicación en tiempo real del juego. Las acciones del cliente se decodifican y se
 * enrutan al [GameManager] apropiado según su tipo.
 */
fun Application.configureRouting() {

    install(WebSockets)

    routing {
        webSocket("/game") {
            // Notifica al GameManager sobre la nueva conexión.
            GameManager.onPlayerConnected(this)

            try {
                // Bucle principal para escuchar las acciones entrantes del cliente.
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        val actionJson = frame.readText()
                        val action = GameManager.AppJson.decodeFromString<ClientAction>(actionJson)

                        // Enruta la acción al manejador correcto basado en su tipo.
                        // Esto permite que un cliente se una a una sala incluso si ya estaba en una (p. ej., después de una desconexión).
                        when (action) {
                            is ClientAction.CreateRoom, is ClientAction.JoinRoom -> {
                                GameManager.handleSessionAction(this, action)
                            }
                            else -> {
                                GameManager.handleGameAction(this, action)
                            }
                        }
                    }
                }
            } catch (e: ClosedReceiveChannelException) {
                // Esto es normal, ocurre cuando un cliente se desconecta de forma limpia.
                println("Connection closed cleanly by the client: ${closeReason.await()}")
            } catch (e: Exception) {
                println("Error in WebSocket connection: ${e.message}")
                e.printStackTrace()
            } finally {
                // Asegura que el jugador sea desconectado del GameManager sin importar cómo se cierre la conexión.
                GameManager.onPlayerDisconnected(this)
            }
        }
    }
}
