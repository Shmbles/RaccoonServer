package com.shmbles.raccoon

import com.shmbles.raccoon.server.GameManager
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*

/**
 * Configura la negociación de contenido para la aplicación Ktor, utilizando la instancia JSON compartida.
 * Esto permite la serialización y deserialización automática de objetos Kotlin a/desde JSON.
 */
fun Application.configureSerialization() {
    install(ContentNegotiation) {
        json(GameManager.AppJson)
    }
}
