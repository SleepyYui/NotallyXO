package com.sleepyyui.notallyxo.backend.plugins

import io.ktor.server.application.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.request.*
import mu.KotlinLogging
import org.slf4j.event.Level

private val logger = KotlinLogging.logger {}

/**
 * Configure application monitoring and logging.
 */
fun Application.configureMonitoring() {
    logger.info { "Configuring monitoring" }
    
    install(CallLogging) {
        level = Level.INFO
        filter { call -> call.request.path().startsWith("/api") }
        format { call ->
            val status = call.response.status()
            val httpMethod = call.request.httpMethod.value
            val path = call.request.path()
            val userAgent = call.request.headers["User-Agent"] ?: "unknown"
            val duration = call.getDuration()
            
            "$status: $httpMethod $path - $userAgent ($duration ms)"
        }
    }
}

/**
 * Calculate the request duration in milliseconds.
 */
private fun ApplicationCall.getDuration(): Long {
    val startTime = attributes.getOrNull(CallLogging.RequestStartTimeMark) ?: return 0L
    return startTime.timePassed()?.inWholeMilliseconds ?: 0L
}
