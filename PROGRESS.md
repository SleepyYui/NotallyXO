# Cloud Synchronization Implementation Plan

This document tracks the progress of implementing cloud synchronization in NotallyXO, similar to Google Keep's functionality but with end-to-end encryption and custom server support.

## Overview

The cloud synchronization feature will allow users to:

1. Sync notes with a custom server (specified by IP/Domain and port)
2. Authenticate with a token
3. Encrypt data both in transit and at rest
4. Work offline and sync when connectivity is restored
5. Share notes securely with other users using one-time hash tokens

## Implementation Stages

### 1. Data Model Enhancements ✅ (Completed)

- [x] Create `SyncStatus` enum for tracking synchronization state
- [x] Create sharing-related data structures:
  - [x] `ShareAccessLevel` enum (READ_ONLY, READ_WRITE)
  - [x] `SharedAccess` class for users with access to notes
  - [x] `SharingToken` class for one-time sharing tokens
- [x] Update `BaseNote` class with sync and sharing fields:
  - [x] Added sync fields: `syncId`, `syncStatus`, `lastSyncedTimestamp`
  - [x] Added sharing fields: `isShared`, `sharedAccesses`, `sharingTokens`, `ownerUserId`
- [x] Implement sharing-related utility methods:
  - [x] `createSharingToken()`: Generate one-time hash for sharing
  - [x] `addUserWithToken()`: Add a user using a token
  - [x] `updateUserAccessLevel()`: Change a user's permissions
  - [x] `removeUserAccess()`: Remove a user's access
  - [x] `revokeToken()`: Invalidate an unused token

### 2. Database Migration ✅ (Completed)

- [x] Create Room migration to add sync and sharing fields to the database schema
- [x] Update TypeConverters for new complex data types
- [x] Implement DAO methods for sync and sharing operations

### 3. Encryption Implementation ✅ (Completed)

- [x] Extend existing encryption utilities for cloud sync
  - [x] Leverage existing AES encryption infrastructure
  - [x] Implement master password-based key derivation using PBKDF2
  - [x] Create content encryption/decryption service
- [x] Implement public/private key cryptography for shared notes
  - [x] Generate user keypairs
  - [x] Encrypt shared notes with symmetric key
  - [x] Encrypt symmetric key with each participant's public key

### 4. Settings UI ✅ (Completed)

- [x] Create server configuration screen
  - [x] Server address (IP/Domain)
  - [x] Port number
  - [x] Authentication token input
  - [x] Master password for encryption
  - [x] Auto-sync preferences
- [x] Add sync status indicators
- [ ] Create sharing UI (Pending for future implementation)
  - [ ] Generate and display one-time sharing hashes
  - [ ] Input field for receiving shared notes
  - [ ] Manage shared access (change permissions, revoke access)

### 5. API Client & Networking ✅ (Completed)

- [x] Design and implement REST API client
  - [x] Authentication mechanism
  - [x] Note synchronization endpoints
  - [x] User management for sharing
- [x] Implement offline queue system
  - [x] Store pending changes locally
  - [x] Track sync status of notes
- [x] Add basic conflict resolution mechanism
- [x] Add background sync capabilities with WorkManager
- [x] Fix manual sync functionality in Settings UI

### 6. Backend Implementation ✅ (Completed - Core Features)

- [x] Create backend server structure in the backend/ folder
  - [x] Set up Ktor server with proper dependencies
  - [x] Configure content negotiation, security, CORS, and error handling
  - [x] Define database models for notes, users, and sharing
  - [x] Define API models to match client expectations
  - [x] Create route handlers for authentication, users, notes, sync, and sharing
  - [x] Create repository classes for database operations
  - [x] Implement domain models (Note, User, SharedAccess, SharingToken)
  - [x] Set up JWT-based authentication
  - [x] Set up database connections and initialization
  - [x] Implement main Application.kt entry point
  - [x] Implement WebSocket/push notifications for real-time updates
    - [x] Configure WebSocket plugin (`plugins/Sockets.kt`)
    - [x] Implement connection management and broadcasting logic
    - [x] Integrate broadcasting into `NoteRoutes.kt` for note updates and deletions
    - [x] Update `NoteRoutes.kt` to handle shared access checks and include shared access details in responses
- [ ] Write basic documentation for self-hosting the backend

### 7. Conflict Resolution ✅ (Completed)

- [x] Implement conflict detection
- [x] Create UI for conflict resolution
  - [x] Show differences between local and server versions
  - [x] Allow choosing which version to keep or merge
- [x] Implement automatic resolution strategies (server wins by default)
- [x] Add advanced manual resolution options:
  - [x] Keep local version
  - [x] Keep server version
  - [x] Launch edit activity for manual merge

### 8. Testing ⏱️ (Not Started)

- [ ] Unit tests for sync logic (Client & Backend)
- [ ] Integration tests with mock server
- [ ] End-to-end tests with real server
- [ ] Offline capability testing
- [ ] WebSocket communication testing

### 9. Documentation ⏱️ (Not Started)

- [ ] User documentation for cloud features
- [ ] API documentation for server
- [ ] Self-hosting guide for backend

## Current Focus

We have completed the core implementation of the backend server, including:

1. ✅ All database models (Notes, Users, SharedAccesses, SharingTokens)
2. ✅ Domain models for business logic
3. ✅ API models matching client expectations
4. ✅ All required API routes (Authentication, Users, Notes, Sync, Sharing)
5. ✅ Repository classes for database access
6. ✅ JWT-based authentication
7. ✅ Database initialization and connection handling
8. ✅ Robust error handling with StatusPages
9. ✅ CORS support
10. ✅ Main Ktor application configuration
11. ✅ WebSocket implementation for real-time updates:
    - ✅ Setup and connection management
    - ✅ Broadcasting note updates (`NOTE_UPDATED`) and deletions (`NOTE_DELETED`)
    - ✅ Integration with `NoteRoutes` to trigger broadcasts
    - ✅ Enhanced `NoteRoutes` for shared access checks and response details

**Next steps:**

1. **Implement WebSocket client logic in the Android app:**
   - Connect to the backend WebSocket endpoint.
   - Handle incoming messages (`NOTE_UPDATED`, `NOTE_DELETED`).
   - Update the local database and UI in real-time based on received messages.
   - Ensure proper reconnection logic.
2. Write basic documentation for self-hosting the backend.
3. Begin writing unit and integration tests for both client and backend sync logic.
4. Add missing translations for UI elements related to sync and sharing.