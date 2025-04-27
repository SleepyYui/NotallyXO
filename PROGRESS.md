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

### 5. API Client & Networking 🔄 (In Progress)

- [x] Design and implement REST API client
  - [x] Authentication mechanism
  - [x] Note synchronization endpoints
  - [x] User management for sharing
- [x] Implement offline queue system
  - [x] Store pending changes locally
  - [x] Track sync status of notes
- [x] Add basic conflict resolution mechanism
- [ ] Add background sync capabilities with WorkManager

### 6. Backend Implementation ⏱️ (Not Started)

- [ ] Create backend server in the backend/ folder
  - [ ] User authentication and authorization
  - [ ] Secure note storage (encrypted)
  - [ ] API endpoints for sync operations
  - [ ] Sharing functionality
- [ ] Implement WebSocket/push notifications for real-time updates

### 7. Conflict Resolution ⏱️ (Not Started)

- [ ] Implement conflict detection
- [ ] Create UI for conflict resolution
  - [ ] Show differences between local and server versions
  - [ ] Allow choosing which version to keep or merge
- [ ] Implement automatic resolution strategies (optional)

### 8. Testing ⏱️ (Not Started)

- [ ] Unit tests for sync logic
- [ ] Integration tests with mock server
- [ ] End-to-end tests with real server
- [ ] Offline capability testing

### 9. Documentation ⏱️ (Not Started)

- [ ] User documentation for cloud features
- [ ] API documentation for server
- [ ] Self-hosting guide for backend

## Current Focus

We've made significant progress on the cloud synchronization feature:

1. ✅ Completed the Data Model enhancements with sharing and sync status fields
2. ✅ Completed the Database Migration to support the new fields
3. ✅ Completed the Encryption Implementation using PBKDF2 and AES/RSA encryption
4. ✅ Completed the main Settings UI for enabling/disabling sync, configuring server, and encryption
5. 🔄 API Client & Networking implementation largely complete:
   - ✅ Created CloudApiClient interface with all necessary endpoints
   - ✅ Implemented CloudSyncService with robust error handling
   - ✅ Added NoteMapper for converting between BaseNote and API DTOs
   - ✅ Implemented core note synchronization logic with proper encryption/decryption
   - ✅ Added support for handling conflicts (currently server wins)
   - ❌ Still need to implement background sync with WorkManager

Next steps:
1. Add background synchronization using WorkManager
2. Implement more advanced conflict resolution UI
3. Begin backend server implementation
4. Add unit tests for the sync logic