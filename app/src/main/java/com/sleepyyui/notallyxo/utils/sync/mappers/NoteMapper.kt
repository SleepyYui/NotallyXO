package com.sleepyyui.notallyxo.utils.sync.mappers

import com.sleepyyui.notallyxo.data.model.BaseNote
import com.sleepyyui.notallyxo.data.model.Folder
import com.sleepyyui.notallyxo.data.model.NoteViewMode
import com.sleepyyui.notallyxo.data.model.ShareAccessLevel
import com.sleepyyui.notallyxo.data.model.SharedAccess
import com.sleepyyui.notallyxo.data.model.SyncStatus
import com.sleepyyui.notallyxo.data.model.Type
import com.sleepyyui.notallyxo.utils.security.CloudEncryptionService
import com.sleepyyui.notallyxo.utils.sync.api.model.NoteDto
import com.sleepyyui.notallyxo.utils.sync.api.model.SharedAccessDto
import java.util.UUID
import javax.crypto.SecretKey
import org.json.JSONObject

/**
 * Mapper class to convert between local [BaseNote] entities and API [NoteDto] objects.
 *
 * This class handles the conversion in both directions, ensuring that the encryption of sensitive
 * data is properly managed during the conversion.
 */
object NoteMapper {

    /**
     * Converts a local [BaseNote] to an API [NoteDto] for sending to the server.
     *
     * This conversion includes encrypting the note content and other sensitive fields.
     *
     * @param note The local note to convert
     * @param encryptionService The service to use for encrypting sensitive content
     * @param secretKey The secret key required for encryption
     * @return A [NoteDto] suitable for API transmission
     */
    fun toNoteDto(
        note: BaseNote,
        encryptionService: CloudEncryptionService,
        secretKey: SecretKey,
    ): NoteDto {
        // Construct the combined content JSON
        val contentJson =
            JSONObject()
                .apply {
                    put("body", note.body)
                    put("items", note.items.toString())
                    put("spans", note.spans.toString())
                }
                .toString()

        // Encrypt the content using the encrypt method
        val encryptedData = encryptionService.encrypt(contentJson.toByteArray(), secretKey)
        val encryptedContent =
            android.util.Base64.encodeToString(encryptedData.data, android.util.Base64.NO_WRAP)
        val iv = android.util.Base64.encodeToString(encryptedData.iv, android.util.Base64.NO_WRAP)

        // Convert local SharedAccess instances to SharedAccessDto for API
        val sharedAccessDtos =
            note.sharedAccesses.map { access ->
                SharedAccessDto(userId = access.userId, accessLevel = access.accessLevel.name)
            }

        // Return the DTO with all fields properly converted
        return NoteDto(
            syncId = note.syncId.ifEmpty { UUID.randomUUID().toString() },
            title = note.title,
            content = encryptedContent,
            encryptionIv = iv,
            lastModifiedTimestamp = note.modifiedTimestamp,
            lastSyncedTimestamp = note.lastSyncedTimestamp,
            type = note.type.name,
            isArchived = note.folder == Folder.ARCHIVED,
            isPinned = note.pinned,
            isShared = note.isShared,
            labels = note.labels,
            ownerUserId = note.ownerUserId.ifEmpty { "" }, // Default to empty if not set
            sharedAccesses = sharedAccessDtos.ifEmpty { null },
        )
    }

    /**
     * Converts an API [NoteDto] to a local [BaseNote] after receiving from the server.
     *
     * This conversion includes decrypting the note content and other sensitive fields.
     *
     * @param dto The API DTO to convert
     * @param encryptionService The service to use for decrypting sensitive content
     * @param secretKey The secret key required for decryption
     * @param existingNote Optional existing note to update (if this is an update)
     * @return A [BaseNote] ready for local storage
     */
    fun toBaseNote(
        dto: NoteDto,
        encryptionService: CloudEncryptionService,
        secretKey: SecretKey,
        existingNote: BaseNote? = null,
    ): BaseNote {
        // If we have an existing note, start with that as base
        val baseNote =
            existingNote?.copy()
                ?: BaseNote(
                    id = 0, // DB will assign ID when inserted
                    type = Type.valueOf(dto.type),
                    folder = if (dto.isArchived) Folder.ARCHIVED else Folder.NOTES,
                    color = BaseNote.COLOR_DEFAULT,
                    title = dto.title,
                    pinned = dto.isPinned,
                    timestamp = dto.lastModifiedTimestamp,
                    modifiedTimestamp = dto.lastModifiedTimestamp,
                    labels = dto.labels ?: emptyList(),
                    body = "",
                    spans = emptyList(),
                    items = emptyList(),
                    images = emptyList(),
                    files = emptyList(),
                    audios = emptyList(),
                    reminders = emptyList(),
                    viewMode = existingNote?.viewMode ?: defaultViewMode(Type.valueOf(dto.type)),
                )

        // Decrypt the content if available
        if (dto.content.isNotEmpty() && dto.encryptionIv != null) {
            try {
                // Create EncryptedData from Base64 strings
                val encryptedData =
                    CloudEncryptionService.EncryptedData(
                        data = android.util.Base64.decode(dto.content, android.util.Base64.NO_WRAP),
                        iv =
                            android.util.Base64.decode(
                                dto.encryptionIv,
                                android.util.Base64.NO_WRAP,
                            ),
                    )

                // Decrypt the content
                val decryptedBytes = encryptionService.decrypt(encryptedData, secretKey)
                val decryptedContent = String(decryptedBytes)

                // Use String constructor to avoid ambiguity
                val contentJson = JSONObject(decryptedContent)

                // Parse the decrypted JSON content
                var updatedNote = baseNote
                if (contentJson.has("body")) {
                    updatedNote = updatedNote.copy(body = contentJson.getString("body"))
                }

                // Additional fields would be parsed from the JSON here
                // This would need more complex logic for spans, items, etc.

                // Return the updated note after parsing content
                return updatedNote.copy(
                    syncId = dto.syncId,
                    syncStatus = SyncStatus.SYNCED,
                    lastSyncedTimestamp = dto.lastSyncedTimestamp,
                    isShared = dto.isShared,
                    sharedAccesses =
                        dto.sharedAccesses?.map { accessDto ->
                            SharedAccess(
                                userId = accessDto.userId,
                                accessLevel = ShareAccessLevel.valueOf(accessDto.accessLevel),
                                grantedTimestamp =
                                    dto.lastModifiedTimestamp, // We don't have exact grant time
                                usedToken = "", // We don't have this info from server
                            )
                        } ?: emptyList(),
                    ownerUserId = dto.ownerUserId,
                )
            } catch (e: Exception) {
                // Log error, but continue with conversion to avoid data loss
                // In production, this would be handled more robustly
            }
        }

        // Convert shared access info (if content decryption failed or wasn't needed)
        val sharedAccesses =
            dto.sharedAccesses?.map { accessDto ->
                SharedAccess(
                    userId = accessDto.userId,
                    accessLevel = ShareAccessLevel.valueOf(accessDto.accessLevel),
                    grantedTimestamp = dto.lastModifiedTimestamp, // We don't have exact grant time
                    usedToken = "", // We don't have this info from server
                )
            } ?: emptyList()

        // Return the BaseNote with updates from DTO (without decrypted content if failed)
        return baseNote.copy(
            syncId = dto.syncId,
            syncStatus = SyncStatus.SYNCED,
            lastSyncedTimestamp = dto.lastSyncedTimestamp,
            isShared = dto.isShared,
            sharedAccesses = sharedAccesses,
            ownerUserId = dto.ownerUserId,
        )
    }

    /**
     * Creates a NoteDto from a WebSocket JSON message.
     *
     * @param jsonObject The JSON object containing note data from WebSocket
     * @return The NoteDto representation of the note
     */
    fun fromWebSocketJson(jsonObject: JSONObject): NoteDto {
        return NoteDto(
            syncId = jsonObject.getString("syncId"),
            title = jsonObject.getString("title"),
            content = jsonObject.getString("content"),
            encryptionIv =
                if (jsonObject.has("encryptionIv")) jsonObject.getString("encryptionIv") else null,
            lastModifiedTimestamp = jsonObject.getLong("lastModifiedTimestamp"),
            lastSyncedTimestamp = jsonObject.getLong("lastSyncedTimestamp"),
            type = jsonObject.getString("type"),
            isArchived = jsonObject.optBoolean("isArchived", false),
            isPinned = jsonObject.optBoolean("isPinned", false),
            isShared = jsonObject.optBoolean("isShared", false),
            labels =
                if (jsonObject.has("labels")) {
                    val labelsArray = jsonObject.getJSONArray("labels")
                    val labelsList = mutableListOf<String>()
                    for (i in 0 until labelsArray.length()) {
                        labelsList.add(labelsArray.getString(i))
                    }
                    labelsList
                } else null,
            ownerUserId = jsonObject.getString("ownerUserId"),
            sharedAccesses =
                if (jsonObject.has("sharedAccesses")) {
                    val accessesArray = jsonObject.getJSONArray("sharedAccesses")
                    val accessesList = mutableListOf<SharedAccessDto>()
                    for (i in 0 until accessesArray.length()) {
                        val accessObj = accessesArray.getJSONObject(i)
                        accessesList.add(
                            SharedAccessDto(
                                userId = accessObj.getString("userId"),
                                accessLevel = accessObj.getString("accessLevel"),
                            )
                        )
                    }
                    accessesList
                } else null,
        )
    }

    /**
     * Creates a BaseNote from a NoteDto received from WebSocket.
     *
     * @param noteDto The NoteDto from WebSocket
     * @param encryptionService The encryption service
     * @param secretKey The decryption key
     * @return A new BaseNote
     */
    fun fromNoteDto(
        noteDto: NoteDto,
        encryptionService: CloudEncryptionService,
        secretKey: SecretKey?,
    ): BaseNote {
        // Create a new BaseNote with basic fields
        val baseNote =
            BaseNote(
                id = 0, // DB will assign ID when inserted
                type = Type.valueOf(noteDto.type),
                folder = if (noteDto.isArchived) Folder.ARCHIVED else Folder.NOTES,
                color = BaseNote.COLOR_DEFAULT,
                title = noteDto.title,
                pinned = noteDto.isPinned,
                timestamp = noteDto.lastModifiedTimestamp,
                modifiedTimestamp = noteDto.lastModifiedTimestamp,
                syncId = noteDto.syncId,
                syncStatus = SyncStatus.SYNCED,
                lastSyncedTimestamp = noteDto.lastSyncedTimestamp,
                isShared = noteDto.isShared,
                ownerUserId = noteDto.ownerUserId,
                labels = noteDto.labels ?: emptyList(),
                body = "",
                spans = emptyList(),
                items = emptyList(),
                images = emptyList(),
                files = emptyList(),
                audios = emptyList(),
                reminders = emptyList(),
                viewMode = defaultViewMode(Type.valueOf(noteDto.type)),
                sharedAccesses =
                    noteDto.sharedAccesses?.map { accessDto ->
                        SharedAccess(
                            userId = accessDto.userId,
                            accessLevel = ShareAccessLevel.valueOf(accessDto.accessLevel),
                            grantedTimestamp = noteDto.lastModifiedTimestamp,
                            usedToken = "",
                        )
                    } ?: emptyList(),
            )

        // Try to decrypt content if available
        if (noteDto.content.isNotEmpty() && noteDto.encryptionIv != null && secretKey != null) {
            try {
                val encryptedData =
                    CloudEncryptionService.EncryptedData(
                        data =
                            android.util.Base64.decode(
                                noteDto.content,
                                android.util.Base64.NO_WRAP,
                            ),
                        iv =
                            android.util.Base64.decode(
                                noteDto.encryptionIv,
                                android.util.Base64.NO_WRAP,
                            ),
                    )

                val decryptedBytes = encryptionService.decrypt(encryptedData, secretKey)
                val decryptedContent = String(decryptedBytes)
                val contentJson = JSONObject(decryptedContent)

                var updatedNote = baseNote
                if (contentJson.has("body")) {
                    updatedNote = updatedNote.copy(body = contentJson.getString("body"))
                }

                // Handle other fields from JSON as needed
                // This is simplified and would need proper parsing for spans, items, etc.

                return updatedNote
            } catch (e: Exception) {
                // Return baseNote without decrypted content if decryption fails
                return baseNote
            }
        }

        return baseNote
    }

    /**
     * Updates an existing local note with data from a DTO received via WebSocket.
     *
     * @param existingNote The existing local note to update
     * @param noteDto The DTO with new data
     * @param encryptionService The encryption service
     * @param secretKey The decryption key
     * @return The updated BaseNote
     */
    fun updateLocalNoteFromDto(
        existingNote: BaseNote,
        noteDto: NoteDto,
        encryptionService: CloudEncryptionService,
        secretKey: SecretKey?,
    ): BaseNote {
        // Start with the existing note
        var updatedNote =
            existingNote.copy(
                title = noteDto.title,
                pinned = noteDto.isPinned,
                folder = if (noteDto.isArchived) Folder.ARCHIVED else Folder.NOTES,
                modifiedTimestamp = noteDto.lastModifiedTimestamp,
                syncStatus = SyncStatus.SYNCED,
                lastSyncedTimestamp = noteDto.lastSyncedTimestamp,
                isShared = noteDto.isShared,
                labels = noteDto.labels ?: existingNote.labels,
                ownerUserId = noteDto.ownerUserId,
                sharedAccesses =
                    noteDto.sharedAccesses?.map { accessDto ->
                        SharedAccess(
                            userId = accessDto.userId,
                            accessLevel = ShareAccessLevel.valueOf(accessDto.accessLevel),
                            grantedTimestamp = noteDto.lastModifiedTimestamp,
                            usedToken = "",
                        )
                    } ?: existingNote.sharedAccesses,
            )

        // Try to decrypt and update content if available
        if (noteDto.content.isNotEmpty() && noteDto.encryptionIv != null && secretKey != null) {
            try {
                val encryptedData =
                    CloudEncryptionService.EncryptedData(
                        data =
                            android.util.Base64.decode(
                                noteDto.content,
                                android.util.Base64.NO_WRAP,
                            ),
                        iv =
                            android.util.Base64.decode(
                                noteDto.encryptionIv,
                                android.util.Base64.NO_WRAP,
                            ),
                    )

                val decryptedBytes = encryptionService.decrypt(encryptedData, secretKey)
                val decryptedContent = String(decryptedBytes)
                val contentJson = JSONObject(decryptedContent)

                if (contentJson.has("body")) {
                    updatedNote = updatedNote.copy(body = contentJson.getString("body"))
                }

                // Handle other content fields from JSON as needed
                // This is simplified and would need proper parsing for spans, items, etc.
            } catch (e: Exception) {
                // Keep existing content if decryption fails
            }
        }

        return updatedNote
    }

    /** Get the default view mode for a note type */
    private fun defaultViewMode(type: Type): NoteViewMode {
        return when (type) {
            Type.NOTE -> NoteViewMode.EDIT
            Type.LIST -> NoteViewMode.EDIT
        }
    }
}
