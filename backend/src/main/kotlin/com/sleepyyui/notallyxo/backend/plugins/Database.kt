package com.sleepyyui.notallyxo.backend.plugins

import com.sleepyyui.notallyxo.backend.config.DatabaseConfig
import com.sleepyyui.notallyxo.backend.models.database.*
import kotlinx.coroutines.Dispatchers
import mu.KotlinLogging
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction

private val logger = KotlinLogging.logger {}

/**
 * Initialize and configure the database connection.
 */
fun initializeDatabase(config: DatabaseConfig) {
    logger.info { "Initializing database connection to ${config.jdbcUrl}" }
    
    // Create database connection
    val database = Database.connect(
        url = config.jdbcUrl,
        driver = config.driverClassName,
        user = config.username,
        password = config.password
    )
    
    // Create or update schema
    transaction(database) {
        logger.info { "Creating or updating database schema" }
        SchemaUtils.create(
            Users,
            Notes,
            SharedAccesses,
            SharingTokens
        )
    }
    
    logger.info { "Database initialization completed successfully" }
}

/**
 * Execute a database query in a coroutine-friendly way.
 */
suspend fun <T> dbQuery(block: () -> T): T =
    newSuspendedTransaction(Dispatchers.IO) {
        block()
    }
