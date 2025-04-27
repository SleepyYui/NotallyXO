package com.sleepyyui.notallyxo.backend.plugins

import io.ktor.serialization.gson.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Configure serialization for the application.
 */
fun Application.configureSerialization() {
    logger.info { "Configuring serialization" }
    
    install(ContentNegotiation) {
        gson {
            // Configure Gson serializer
            setPrettyPrinting()
            serializeNulls()
            disableHtmlEscaping()
        }
    }
}
