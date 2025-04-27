package com.sleepyyui.notallyxo.backend.plugins

import com.sleepyyui.notallyxo.backend.models.database.Notes
import com.sleepyyui.notallyxo.backend.models.database.SharedAccesses
import com.sleepyyui.notallyxo.backend.models.database.SharingTokens
import com.sleepyyui.notallyxo.backend.models.database.Users
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.*
import kotlinx.coroutines.Dispatchers
import mu.KotlinLogging
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction

private val logger = KotlinLogging.logger {}

/**
 * Configure database connection and schema.
 */
fun Application.configureDatabases() {
    logger.info { "Configuring database connection" }
    
    // Get database config from environment or use defaults
    val databaseUrl = System.getenv("DATABASE_URL") ?: "jdbc:h2:file:./notallyxo-db;DB_CLOSE_DELAY=-1"
    val databaseDriver = System.getenv("DATABASE_DRIVER") ?: "org.h2.Driver"
    val databaseUser = System.getenv("DATABASE_USER") ?: ""
    val databasePassword = System.getenv("DATABASE_PASSWORD") ?: ""
    
    // Configure connection pool
    val config = HikariConfig().apply {
        driverClassName = databaseDriver
        jdbcUrl = databaseUrl
        username = databaseUser
        password = databasePassword
        maximumPoolSize = 10
        isAutoCommit = false
        transactionIsolation = "TRANSACTION_REPEATABLE_READ"
        validate()
    }
    
    // Create database connection
    val dataSource = HikariDataSource(config)
    val database = Database.connect(dataSource)
    
    // Initialize database schema
    transaction(database) {
        SchemaUtils.create(Users)
        SchemaUtils.create(Notes)
        SchemaUtils.create(SharedAccesses)
        SchemaUtils.create(SharingTokens)
        
        logger.info { "Database schema created" }
    }
}

/**
 * Helper function to execute database operations in a suspended context.
 */
suspend fun <T> dbQuery(block: suspend () -> T): T =
    newSuspendedTransaction(Dispatchers.IO) { block() }
