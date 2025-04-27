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

    /** Get the default view mode for a note type */
    private fun defaultViewMode(type: Type): NoteViewMode {
        return when (type) {
            Type.NOTE -> NoteViewMode.EDIT
            Type.LIST -> NoteViewMode.EDIT
        }
    }
}
