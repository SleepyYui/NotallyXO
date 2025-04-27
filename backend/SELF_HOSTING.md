# Self-Hosting the NotallyXO Backend Server

This guide provides instructions for setting up and running your own NotallyXO backend server for cloud synchronization.

## System Requirements

- JDK 11 or higher
- Gradle 7.0 or higher
- PostgreSQL 12 or higher
- 1GB RAM minimum (2GB recommended)
- 1GB disk space minimum

## Setup Instructions

### 1. Clone the Repository

```bash
git clone https://github.com/YourUsername/NotallyX.git
cd NotallyX
```

### 2. Configure the Database

1. Install PostgreSQL if you haven't already
2. Create a new database and user for NotallyXO:

```sql
CREATE DATABASE notallyxo;
CREATE USER notallyxo_user WITH ENCRYPTED PASSWORD 'your_secure_password';
GRANT ALL PRIVILEGES ON DATABASE notallyxo TO notallyxo_user;
```

### 3. Configure the Application

Create a file named `application.conf` in the `backend/src/main/resources` directory with the following content:

```hocon
ktor {
    deployment {
        port = 8080
        port = ${?PORT}
    }
    application {
        modules = [ com.sleepyyui.notallyxo.backend.ApplicationKt.module ]
    }
}

database {
    driverClassName = "org.postgresql.Driver"
    jdbcURL = "jdbc:postgresql://localhost:5432/notallyxo"
    user = "notallyxo_user"
    password = "your_secure_password"
}

jwt {
    issuer = "https://your.domain.com"
    audience = "https://your.domain.com/audience"
    realm = "NotallyXO App"
    secret = "your_jwt_secret_key"  # Generate a secure random key
    validity = 2592000000  # 30 days in milliseconds
}
```

Replace the placeholder values with your own configuration. It's especially important to use a strong random secret for the JWT token signing.

### 4. Build the Application

```bash
cd backend
./gradlew build
```

### 5. Run the Server

#### Development Mode

```bash
./gradlew run
```

#### Production Mode

1. Build a standalone JAR:

```bash
./gradlew shadowJar
```

2. Run the JAR:

```bash
java -jar build/libs/notallyxo-backend-all.jar
```

### 6. Configure as a System Service (Linux)

1. Create a service file at `/etc/systemd/system/notallyxo.service`:

```ini
[Unit]
Description=NotallyXO Backend Server
After=network.target postgresql.service

[Service]
User=notallyxo
WorkingDirectory=/opt/notallyxo
ExecStart=/usr/bin/java -jar /opt/notallyxo/notallyxo-backend-all.jar
Restart=on-failure

[Install]
WantedBy=multi-user.target
```

2. Copy the JAR file to the service directory:

```bash
sudo mkdir -p /opt/notallyxo
sudo cp build/libs/notallyxo-backend-all.jar /opt/notallyxo/
```

3. Start the service:

```bash
sudo systemctl enable notallyxo
sudo systemctl start notallyxo
```

## Connecting the Android App

In the NotallyXO Android app:

1. Go to Settings > Cloud Sync
2. Enter your server address and port (e.g., `your.domain.com:8080`)
3. Generate an authentication token and enter it in the app
4. Set your master password for encryption
5. Enable sync and auto sync as desired

## Security Considerations

1. **TLS/SSL**: For production deployments, configure a secure HTTPS connection using a reverse proxy like Nginx or Apache with Let's Encrypt certificates
2. **Firewall**: Configure your firewall to only expose the required ports
3. **Regular Updates**: Keep your server and dependencies up to date
4. **Backups**: Regularly backup your PostgreSQL database
5. **Rate Limiting**: Consider implementing rate limiting at the reverse proxy level to prevent abuse

## API Endpoints

The server exposes the following main API endpoints:

- **Authentication**: `/api/v1/auth/token` - Validate authentication tokens
- **Users**: `/api/v1/users/*` - User management endpoints
- **Notes**: `/api/v1/notes/*` - Note CRUD operations
- **Sync**: `/api/v1/sync/*` - Synchronization endpoints
- **WebSocket**: `/ws/updates` - Real-time updates via WebSocket

## Troubleshooting

### Database Connection Issues

If the server cannot connect to the database, verify:
- PostgreSQL is running
- Database credentials are correct in `application.conf`
- Network connectivity between server and database if they're on different hosts

### Authentication Problems

If users cannot authenticate:
- Check the JWT configuration in `application.conf`
- Ensure the client is sending the token correctly in the `Authorization` header

### WebSocket Connection Issues

If real-time updates aren't working:
- Ensure your reverse proxy (if any) supports WebSocket connections
- Check that the JWT token is valid and included in the WebSocket connection request

## Advanced Configuration

### CORS Settings

By default, CORS is configured to allow requests from any origin. For production, you should restrict this to your specific domains.

### Connection Pooling

For high-traffic deployments, you may want to configure the database connection pool parameters in the application.conf file:

```hocon
database {
    ...
    maximumPoolSize = 10
    connectionTimeout = 30000
    idleTimeout = 600000
}
```

### Logging Configuration

Logging can be configured by creating or modifying a `logback.xml` file in the `src/main/resources` directory.