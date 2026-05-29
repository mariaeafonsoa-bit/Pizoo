package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class User(
    @PrimaryKey val id: String, // String ID e.g., "me", "alice", "bob", "charlie"
    val displayName: String,
    val profilePic: String?, // name of mock drawable/avatar asset
    val publicKey: String, // Base64 RSA Public Key
    val privateKey: String?, // Only populated for "me" device owner. Null for contacts.
    val statusText: String,
    val statusTimestamp: Long = System.currentTimeMillis(),
    val statusMediaUri: String? = null,
    val statusMediaType: String? = null, // "PHOTO" or "VIDEO"
    val hasViewedStatus: Boolean = false,
    val isBlocked: Boolean = false
)

@Entity(tableName = "chats")
data class Chat(
    @PrimaryKey val id: String, // Unique chatId e.g., "dm_alice", "group_alpha"
    val isGroup: Boolean,
    val name: String,
    val adminId: String?, // Administrator ID (e.g., "me" or "alice" inside groups)
    val disappearingTimerSeconds: Int = 0, // 0 = disabled, else duration in seconds
    val isViewOnceMediaEnabled: Boolean = false,
    val createdBy: String = "system",
    val createdAt: Long = System.currentTimeMillis(),
    val isLocked: Boolean = false,
    val lockType: String? = null, // "PASSWORD", "FINGERPRINT", "FACE_LOCK"
    val lockPassword: String? = null // Holds password if lockType is PASSWORD or sim status
)

@Entity(tableName = "messages")
data class Message(
    @PrimaryKey val id: String, // UUID String
    val chatId: String,
    val senderId: String,
    val senderName: String,
    val encryptedPayload: String, // AES encrypted text/media info
    val iv: String,              // AES Initialization Vector (base64)
    val encryptedAESKey: String, // AES Key encrypted via recipient's RSA Public Key (base64)
    val isEncrypted: Boolean = true,
    val isViewOnce: Boolean = false,
    val isViewed: Boolean = false, // True once a "View Once" photo/video is viewed and permanently deleted from local cache
    val mediaType: String = "TEXT", // "TEXT", "PHOTO", "VIDEO", "AUDIO" (voice mail)
    val localMediaUri: String? = null,
    val durationSeconds: Int = 0,   // For voice mail
    val timestamp: Long = System.currentTimeMillis(),
    val expiringAt: Long? = null,   // null = never, or epoch seconds when to purge from Room
    val isDeletedLocally: Boolean = false
)

@Entity(tableName = "group_members", primaryKeys = ["chatId", "userId"])
data class GroupMember(
    val chatId: String,
    val userId: String,
    val isAdmin: Boolean = false
)
