package com.sleepyyui.notallyxo.backend.plugins

import com.google.gson.GsonBuilder
import io.ktor.serialization.gson.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*

/**
 * Configure content negotiation using Gson for JSON serialization.
 */
fun Application.configureContentNegotiation() {
    install(ContentNegotiation) {
        gson {
            // Configure Gson for proper date serialization and pretty printing
            setPrettyPrinting()
            setLenient()
            serializeNulls()
            setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
        }
    }
}
