package com.shmbles.raccoon

import io.ktor.server.application.*
import io.ktor.server.netty.*

/**
 * Punto de entrada principal de la aplicación Ktor.
 * Esta función inicia el servidor Netty.
 */
fun main(args: Array<String>) {
    EngineMain.main(args)
}

/**
 * Módulo principal de la aplicación.
 * Esta función es llamada por el motor Ktor para configurar las características de la aplicación,
 * incluyendo la serialización y el enrutamiento.
 */
fun Application.module() {
    configureSerialization()
    configureRouting()
}
