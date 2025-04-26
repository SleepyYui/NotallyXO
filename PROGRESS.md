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

### 3. Encryption Implementation ⌛ (In Progress)

- [ ] Extend existing encryption utilities for cloud sync
  - [ ] Leverage existing AES encryption infrastructure
  - [ ] Implement master password-based key derivation using PBKDF2
  - [ ] Create content encryption/decryption service
- [ ] Implement public/private key cryptography for shared notes
  - [ ] Generate user keypairs
  - [ ] Encrypt shared notes with symmetric key
  - [ ] Encrypt symmetric key with each participant's public key

### 4. Settings UI ⏱️ (Not Started)

- [ ] Create server configuration screen
  - [ ] Server address (IP/Domain)
  - [ ] Port number
  - [ ] Authentication token input
  - [ ] Master password for encryption
  - [ ] Auto-sync preferences
- [ ] Add sync status indicators
- [ ] Create sharing UI
  - [ ] Generate and display one-time sharing hashes
  - [ ] Input field for receiving shared notes
  - [ ] Manage shared access (change permissions, revoke access)

### 5. API Client & Networking ⏱️ (Not Started)

- [ ] Design and implement REST API client
  - [ ] Authentication mechanism
  - [ ] Note synchronization endpoints
  - [ ] User management for sharing
- [ ] Implement offline queue system
  - [ ] Store pending changes locally
  - [ ] Implement background sync when connectivity is restored
- [ ] Add conflict resolution mechanism

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

Currently focusing on extending the existing encryption utilities to support cloud synchronization. This involves:

1. Creating a password-based key derivation function using PBKDF2
2. Implementing note content encryption/decryption for cloud storage
3. Setting up secure key management for note sharing