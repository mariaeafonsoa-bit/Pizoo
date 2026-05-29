package com.example.data.repository

import android.content.Context
import com.example.data.crypto.CryptoEngine
import com.example.data.crypto.FingerprintData
import com.example.data.database.ChatDao
import com.example.data.model.Chat
import com.example.data.model.GroupMember
import com.example.data.model.Message
import com.example.data.model.User
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class ChatRepository(private val chatDao: ChatDao) {

    val allChats: Flow<List<Chat>> = chatDao.getAllChatsFlow()
    val contacts: Flow<List<User>> = chatDao.getContactsFlow()
    val allMessages: Flow<List<Message>> = chatDao.getAllMessagesFlow()

    fun getMessagesForChat(chatId: String): Flow<List<Message>> {
        return chatDao.getMessagesForChatFlow(chatId)
    }

    fun getGroupMembers(chatId: String): Flow<List<GroupMember>> {
        return chatDao.getGroupMembersFlow(chatId)
    }

    suspend fun getUser(userId: String): User? {
        return chatDao.getUserById(userId)
    }

    // Initialize/Bootstrap app state with default users and key pairs if empty
    suspend fun bootstrapDatabase() {
        val existingMe = chatDao.getUserById("me")
        if (existingMe != null) {
            return // Already bootstrapped
        }

        // 1. Generate E2E keys for "me" (local user)
        val meKeys = CryptoEngine.generateRSAKeyPair()
        val mePublicKey = CryptoEngine.publicKeyToString(meKeys.public)
        val mePrivateKey = CryptoEngine.privateKeyToString(meKeys.private)
        val meUser = User(
            id = "me",
            displayName = "Maria (You)",
            profilePic = "avatar_me",
            publicKey = mePublicKey,
            privateKey = mePrivateKey,
            statusText = "🔒 Active & Encrypted"
        )
        chatDao.insertUser(meUser)

        // 2. Generate E2E keys for contacts (to simulate encryption)
        val contactNames = listOf(
            Triple("alice", "Alice Smith", "Hey, utilizing end-to-end encryption now!"),
            Triple("bob", "Bob Johnson", "Busy encrypting databases..."),
            Triple("charlie", "Charlie Brown", "Offline but secure."),
            Triple("elena", "Elena (Security Expert)", "Always verify safety fingerprints!")
        )

        val insertedUsers = contactNames.map { (id, name, status) ->
            val kp = CryptoEngine.generateRSAKeyPair()
            val pub = CryptoEngine.publicKeyToString(kp.public)
            // Storing their private keys in this sandbox so our message simulator can "sign" OR "encrypt" as them.
            // In a real net app, separate devices have their own, but this lets us create true E2E simulations on-device!
            val priv = CryptoEngine.privateKeyToString(kp.private)
            User(
                id = id,
                displayName = name,
                profilePic = "avatar_$id",
                publicKey = pub,
                privateKey = priv,
                statusText = status,
                statusTimestamp = System.currentTimeMillis() - (1000 * 60 * 30) // 30 mins ago
            )
        }
        chatDao.insertUsers(insertedUsers)

        // Create default Chats
        val defaultChats = listOf(
            Chat(id = "dm_alice", isGroup = false, name = "Alice Smith", adminId = null),
            Chat(id = "dm_bob", isGroup = false, name = "Bob Johnson", adminId = null),
            Chat(
                id = "dm_elena", 
                isGroup = false, 
                name = "Elena (Security Expert)", 
                adminId = null,
                isLocked = true,
                lockType = "PASSWORD",
                lockPassword = "1234"
            ),
            Chat(
                id = "group_crypto", 
                isGroup = true, 
                name = "Secure Shield Team", 
                adminId = "me", // "me" is administrator
                disappearingTimerSeconds = 10, // 10 seconds disappearing messages by default!
                isViewOnceMediaEnabled = true
            )
        )
        for (chat in defaultChats) {
            chatDao.insertChat(chat)
        }

        // Add group membership for "group_crypto"
        val groupMembers = listOf(
            GroupMember(chatId = "group_crypto", userId = "me", isAdmin = true),
            GroupMember(chatId = "group_crypto", userId = "alice", isAdmin = false),
            GroupMember(chatId = "group_crypto", userId = "bob", isAdmin = false),
            GroupMember(chatId = "group_crypto", userId = "elena", isAdmin = false)
        )
        chatDao.insertGroupMembers(groupMembers)

        // Inject initial secured greeting messages
        injectWelcomeMessages(mePublicKey)
    }

    private suspend fun injectWelcomeMessages(mePublicKey: String) {
        // Alice welcomes "me"
        val aliceUser = chatDao.getUserById("alice") ?: return
        val plainTextA = "Hi Maria! Welcome to SecureChat. All conversations here are fully end-to-end encrypted. Tap on our profile to verify our safety fingerprint matches! 🛡️"
        
        // Encrypt message for "me" using Me's public key
        val (ep, iv, ek) = CryptoEngine.encryptHybrid(plainTextA, mePublicKey)
        val msg1 = Message(
            id = UUID.randomUUID().toString(),
            chatId = "dm_alice",
            senderId = "alice",
            senderName = "Alice Smith",
            encryptedPayload = ep,
            iv = iv,
            encryptedAESKey = ek,
            isEncrypted = true,
            timestamp = System.currentTimeMillis() - 1000 * 60 * 5 // 5 mins ago
        )
        chatDao.insertMessage(msg1)

        // Elena expert sends safety notice
        val elenaUser = chatDao.getUserById("elena") ?: return
        val plainTextE = "Hello! I am your cryptographic advisor. Remember: SecureChat blocks all screenshots on profile photos, status views, and view-once media to safeguard your privacy footprint. Try sending a View-Once image inside a chat to witness auto-deletion after open!"
        val (epE, ivE, ekE) = CryptoEngine.encryptHybrid(plainTextE, mePublicKey)
        val msg2 = Message(
            id = UUID.randomUUID().toString(),
            chatId = "dm_elena",
            senderId = "elena",
            senderName = "Elena (Security Expert)",
            encryptedPayload = epE,
            iv = ivE,
            encryptedAESKey = ekE,
            isEncrypted = true,
            timestamp = System.currentTimeMillis() - 1000 * 60 * 2 // 2 mins ago
        )
        chatDao.insertMessage(msg2)
    }

    // Send a message securely
    suspend fun sendMessage(
        chatId: String,
        senderId: String,
        senderName: String,
        plainText: String,
        mediaType: String = "TEXT",
        localMediaUri: String? = null,
        durationSeconds: Int = 0,
        isViewOnce: Boolean = false
    ) {
        val chat = chatDao.getChatById(chatId) ?: return
        val me = chatDao.getUserById("me") ?: return
        val mePublicKey = me.publicKey

        // Generate cryptography payload encrypted for local recipient "me" so we can read our own database
        val encryptionTargetKey = mePublicKey

        val (ep, iv, ek) = CryptoEngine.encryptHybrid(plainText, encryptionTargetKey)

        val now = System.currentTimeMillis()
        val expiringAt = if (chat.disappearingTimerSeconds > 0) {
            now + (chat.disappearingTimerSeconds * 1000)
        } else {
            null
        }

        val message = Message(
            id = UUID.randomUUID().toString(),
            chatId = chatId,
            senderId = senderId,
            senderName = senderName,
            encryptedPayload = ep,
            iv = iv,
            encryptedAESKey = ek,
            isEncrypted = true,
            isViewOnce = isViewOnce,
            isViewed = false,
            mediaType = mediaType,
            localMediaUri = localMediaUri,
            durationSeconds = durationSeconds,
            timestamp = now,
            expiringAt = expiringAt
        )
        chatDao.insertMessage(message)

        // If it's a DM, simulate a smart response from the bot after 2 seconds (with automatic hybrid encryption!)
        if (!chat.isGroup && senderId == "me") {
            simulateResponse(chatId, chat.disappearingTimerSeconds)
        }
    }

    // Simulate cryptographic replies from bots
    private suspend fun simulateResponse(chatId: String, disappearingSec: Int) {
        val botId = chatId.removePrefix("dm_")
        val botUser = chatDao.getUserById(botId) ?: return
        val me = chatDao.getUserById("me") ?: return

        // Text replies based on bot ID
        val replies = when (botId) {
            "alice" -> listOf(
                "That's fantastic! Standard compliance dictates robust zero-knowledge architecture.",
                "Let's test view-once files. Send me a photo and select the '1' view once lock icon!",
                "Our AES-GCM/CBC cryptographic exchange ensures third party snooping is mathematically impossible!"
            )
            "bob" -> listOf(
                "Acknowledged. Key exchange verify successful.",
                "Yes! Did you see you can make video calls? Let's start a dynamic secure call!",
                "Working on building further multi-party security layers in this room!"
            )
            "elena" -> listOf(
                "Safety Check: Ensure you block screenshots for profile pictures under privacy settings.",
                "Disappearing messages can be adjusted. If it is 10 seconds, they dissolve perfectly from memory space after timer completes.",
                "Excellent usage of the hybrid RSA-2048 client protocol."
            )
            else -> listOf("Secured automated acknowledgement signal received.")
        }
        val text = replies.random()

        // Encrypt with 'me' public key so 'me' can read it
        val (ep, iv, ek) = CryptoEngine.encryptHybrid(text, me.publicKey)
        val now = System.currentTimeMillis()
        val expiringAt = if (disappearingSec > 0) {
            now + (disappearingSec * 1000)
        } else {
            null
        }

        val responseMsg = Message(
            id = UUID.randomUUID().toString(),
            chatId = chatId,
            senderId = botId,
            senderName = botUser.displayName,
            encryptedPayload = ep,
            iv = iv,
            encryptedAESKey = ek,
            isEncrypted = true,
            isViewOnce = false,
            mediaType = "TEXT",
            timestamp = now,
            expiringAt = expiringAt
        )
        chatDao.insertMessage(responseMsg)
    }

    // Auto-reply with dummy simulated photo or video
    suspend fun simulateMediaReply(chatId: String, mediaType: String, isViewOnce: Boolean) {
        val botId = chatId.removePrefix("dm_")
        val botUser = chatDao.getUserById(botId) ?: return
        val me = chatDao.getUserById("me") ?: return

        val text = if (isViewOnce) "🔒 View Once $mediaType sent." else "Sent $mediaType."
        val (ep, iv, ek) = CryptoEngine.encryptHybrid(text, me.publicKey)

        val chat = chatDao.getChatById(chatId) ?: return
        val now = System.currentTimeMillis()
        val expiringAt = if (chat.disappearingTimerSeconds > 0) {
            now + (chat.disappearingTimerSeconds * 1000)
        } else {
            null
        }

        // Mock URI based on type
        val mockUri = when (mediaType) {
            "PHOTO" -> "mock_media_photo"
            "VIDEO" -> "mock_media_video"
            else -> null
        }

        val responseMsg = Message(
            id = UUID.randomUUID().toString(),
            chatId = chatId,
            senderId = botId,
            senderName = botUser.displayName,
            encryptedPayload = ep,
            iv = iv,
            encryptedAESKey = ek,
            isEncrypted = true,
            isViewOnce = isViewOnce,
            isViewed = false,
            mediaType = mediaType,
            localMediaUri = mockUri,
            timestamp = now,
            expiringAt = expiringAt
        )
        chatDao.insertMessage(responseMsg)
    }

    // Mark view once media as viewed (Purges it completely!)
    suspend fun viewOnceMediaOpened(messageId: String) {
        chatDao.purgeViewOnceMedia(messageId)
    }

    suspend fun updateChatLock(chatId: String, isLocked: Boolean, lockType: String?, lockPassword: String?) {
        chatDao.updateChatLock(chatId, isLocked, lockType, lockPassword)
    }

    // Set disappearing message timer
    suspend fun setDisappearingTimer(chatId: String, seconds: Int) {
        chatDao.updateChatDisappearingTimer(chatId, seconds)
        // Adjust existing message timers if they do not already have timers, or just notify
        val plainText = if (seconds == 0) {
            "💬 Disappearing messages turned OFF by Administrator."
        } else {
            "⏰ Disappearing messages set to $seconds seconds by Administrator."
        }
        val me = chatDao.getUserById("me") ?: return
        val (ep, iv, ek) = CryptoEngine.encryptHybrid(plainText, me.publicKey)
        
        val notificationMsg = Message(
            id = UUID.randomUUID().toString(),
            chatId = chatId,
            senderId = "system",
            senderName = "SYSTEM",
            encryptedPayload = ep,
            iv = iv,
            encryptedAESKey = ek,
            isEncrypted = false,
            mediaType = "TEXT",
            timestamp = System.currentTimeMillis()
        )
        chatDao.insertMessage(notificationMsg)
    }

    // Create a group conversation
    suspend fun createGroupChat(name: String, members: List<String>, disappearingSeconds: Int = 0): String {
        val groupId = "group_${UUID.randomUUID().toString().take(8)}"
        val chat = Chat(
            id = groupId,
            isGroup = true,
            name = name,
            adminId = "me", // "me" is administrator
            disappearingTimerSeconds = disappearingSeconds,
            isViewOnceMediaEnabled = true
        )
        chatDao.insertChat(chat)

        // Add creator
        chatDao.insertGroupMember(GroupMember(chatId = groupId, userId = "me", isAdmin = true))
        
        // Add other members
        for (memberId in members) {
            chatDao.insertGroupMember(GroupMember(chatId = groupId, userId = memberId, isAdmin = false))
        }

        // Send confirmation message
        val me = chatDao.getUserById("me") ?: return groupId
        val plainText = "🛡️ Group '$name' created with End-To-End Encrypted protocols. Admin privileges granted to 'Maria (You)'."
        val (ep, iv, ek) = CryptoEngine.encryptHybrid(plainText, me.publicKey)
        val infoMsg = Message(
            id = UUID.randomUUID().toString(),
            chatId = groupId,
            senderId = "system",
            senderName = "SYSTEM",
            encryptedPayload = ep,
            iv = iv,
            encryptedAESKey = ek,
            isEncrypted = false,
            mediaType = "TEXT",
            timestamp = System.currentTimeMillis()
        )
        chatDao.insertMessage(infoMsg)

        return groupId
    }

    // Group administrator management methods
    suspend fun addMemberToGroup(chatId: String, userId: String) {
        chatDao.insertGroupMember(GroupMember(chatId = chatId, userId = userId, isAdmin = false))
        val u = chatDao.getUserById(userId) ?: return
        val me = chatDao.getUserById("me") ?: return
        val text = "👤 ${u.displayName} was added to the secure group."
        val (ep, iv, ek) = CryptoEngine.encryptHybrid(text, me.publicKey)
        chatDao.insertMessage(Message(
            id = UUID.randomUUID().toString(),
            chatId = chatId,
            senderId = "system",
            senderName = "SYSTEM",
            encryptedPayload = ep,
            iv = iv,
            encryptedAESKey = ek,
            isEncrypted = false,
            timestamp = System.currentTimeMillis()
        ))
    }

    suspend fun removeMemberFromGroup(chatId: String, userId: String) {
        chatDao.removeMemberFromGroup(chatId, userId)
        val u = chatDao.getUserById(userId) ?: return
        val me = chatDao.getUserById("me") ?: return
        val text = "❌ ${u.displayName} was removed from the secure group."
        val (ep, iv, ek) = CryptoEngine.encryptHybrid(text, me.publicKey)
        chatDao.insertMessage(Message(
            id = UUID.randomUUID().toString(),
            chatId = chatId,
            senderId = "system",
            senderName = "SYSTEM",
            encryptedPayload = ep,
            iv = iv,
            encryptedAESKey = ek,
            isEncrypted = false,
            timestamp = System.currentTimeMillis()
        ))
    }

    suspend fun promoteMemberToAdmin(chatId: String, userId: String, makeAdmin: Boolean) {
        chatDao.updateMemberAdminStatus(chatId, userId, makeAdmin)
        val u = chatDao.getUserById(userId) ?: return
        val me = chatDao.getUserById("me") ?: return
        val statusText = if (makeAdmin) "granted Administrator credentials" else "revoked Administrator credentials"
        val text = "🛡️ ${u.displayName} was $statusText."
        val (ep, iv, ek) = CryptoEngine.encryptHybrid(text, me.publicKey)
        chatDao.insertMessage(Message(
            id = UUID.randomUUID().toString(),
            chatId = chatId,
            senderId = "system",
            senderName = "SYSTEM",
            encryptedPayload = ep,
            iv = iv,
            encryptedAESKey = ek,
            isEncrypted = false,
            timestamp = System.currentTimeMillis()
        ))
    }

    // Clean expired disappearing messages manually/regularly
    suspend fun cleanExpiredMessages() {
        chatDao.purgeExpiredMessages(System.currentTimeMillis())
    }

    // Update status
    suspend fun postStatusUpdate(statusText: String, mediaUri: String? = null, mediaType: String? = null) {
        val me = chatDao.getUserById("me") ?: return
        val updatedMe = me.copy(
            statusText = statusText,
            statusTimestamp = System.currentTimeMillis(),
            statusMediaUri = mediaUri,
            statusMediaType = mediaType,
            hasViewedStatus = false
        )
        chatDao.updateUser(updatedMe)
    }

    // Verify contact key fingerprint
    suspend fun getFingerprint(userId: String): FingerprintData? {
        val me = chatDao.getUserById("me") ?: return null
        val contact = chatDao.getUserById(userId) ?: return null
        return CryptoEngine.generateFingerprint(me.publicKey, contact.publicKey)
    }
}
