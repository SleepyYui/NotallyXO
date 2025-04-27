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

### 8. WebSocket Client Implementation ✅ (Completed)

- [x] Implement WebSocket client in the Android app
  - [x] Create connection management and status tracking
  - [x] Add support for reconnection attempts
  - [x] Handle incoming messages (NOTE_UPDATED, NOTE_DELETED)
  - [x] Update the local database based on received messages
  - [x] Add connection listener interface for status updates
- [x] Create sync status indicator for the main UI
  - [x] Design and implement custom view component
  - [x] Show WebSocket connection status (connected, disconnected, error)
  - [x] Show sync activity status (syncing, idle, failed)
  - [x] Add visual indicators with appropriate colors
  - [x] Add tap-to reconnect functionality

### 9. Testing ⏱️ (Not Started)

- [ ] Unit tests for sync logic (Client & Backend)
- [ ] Integration tests with mock server
- [ ] End-to-end tests with real server
- [ ] Offline capability testing
- [ ] WebSocket communication testing

### 10. Documentation ⏱️ (Not Started)

- [ ] User documentation for cloud features
- [ ] API documentation for server
- [ ] Self-hosting guide for backend

## Current Focus

We have completed the implementation of the WebSocket client in the Android app, including:

1. ✅ Connection management and status tracking
2. ✅ Support for reconnection attempts
3. ✅ Handling of incoming messages (NOTE_UPDATED, NOTE_DELETED) 
4. ✅ Local database updates based on received messages
5. ✅ Connection listener interface for status updates
6. ✅ Sync status indicator in the main UI:
   - Shows WebSocket connection status with appropriate colors
   - Shows sync activity status
   - Provides tap-to-reconnect functionality
   - Integrated into the toolbar for visibility without taking up much space

**Next steps:**

1. Write basic documentation for self-hosting the backend.
2. Begin writing unit and integration tests for both client and backend sync logic.
3. Add missing translations for UI elements related to sync and sharing.