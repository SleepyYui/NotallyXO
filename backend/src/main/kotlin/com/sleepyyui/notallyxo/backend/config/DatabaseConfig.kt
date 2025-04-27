package com.sleepyyui.notallyxo.backend.config

/**
 * Configuration class for database connection.
 */
data class DatabaseConfig(
    val jdbcUrl: String,
    val driverClassName: String,
    val username: String,
    val password: String
) {
    companion object {
        /**
         * Create a DatabaseConfig from environment variables or use defaults.
         */
        fun fromEnvOrDefault(): DatabaseConfig {
            return DatabaseConfig(
                jdbcUrl = System.getenv("DB_URL") ?: "jdbc:h2:./notallyx;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
                driverClassName = System.getenv("DB_DRIVER") ?: "org.h2.Driver",
                username = System.getenv("DB_USER") ?: "notallyx",
                password = System.getenv("DB_PASSWORD") ?: "password"
            )
        }
        
        /**
         * Create a PostgreSQL DatabaseConfig from environment variables.
         */
        fun fromEnvPostgres(): DatabaseConfig {
            val host = System.getenv("DB_HOST") ?: "localhost"
            val port = System.getenv("DB_PORT") ?: "5432"
            val name = System.getenv("DB_NAME") ?: "notallyx"
            val user = System.getenv("DB_USER") ?: "postgres"
            val password = System.getenv("DB_PASSWORD") ?: "postgres"
            
            return DatabaseConfig(
                jdbcUrl = "jdbc:postgresql://$host:$port/$name",
                driverClassName = "org.postgresql.Driver",
                username = user,
                password = password
            )
        }
    }
}
