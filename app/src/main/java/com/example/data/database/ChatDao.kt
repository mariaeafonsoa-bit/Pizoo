package com.example.data.database

import androidx.room.*
import com.example.data.model.Chat
import com.example.data.model.GroupMember
import com.example.data.model.Message
import com.example.data.model.User
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {

    // --- Users ---
    @Query("SELECT * FROM users")
    fun getAllUsersFlow(): Flow<List<User>>

    @Query("SELECT * FROM users WHERE id = :userId")
    suspend fun getUserById(userId: String): User?

    @Query("SELECT * FROM users WHERE id != 'me'")
    fun getContactsFlow(): Flow<List<User>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsers(users: List<User>)

    @Update
    suspend fun updateUser(user: User)

    @Query("UPDATE users SET isBlocked = :blocked WHERE id = :userId")
    suspend fun setUserBlocked(userId: String, blocked: Boolean)

    @Query("UPDATE users SET hasViewedStatus = :viewed WHERE id = :userId")
    suspend fun setStatusViewed(userId: String, viewed: Boolean)


    // --- Chats ---
    @Query("SELECT * FROM chats ORDER BY id DESC")
    fun getAllChatsFlow(): Flow<List<Chat>>

    @Query("SELECT * FROM chats WHERE id = :chatId")
    suspend fun getChatById(chatId: String): Chat?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChat(chat: Chat)

    @Query("UPDATE chats SET disappearingTimerSeconds = :timerSecs WHERE id = :chatId")
    suspend fun updateChatDisappearingTimer(chatId: String, timerSecs: Int)

    @Query("UPDATE chats SET isLocked = :isLocked, lockType = :lockType, lockPassword = :lockPassword WHERE id = :chatId")
    suspend fun updateChatLock(chatId: String, isLocked: Boolean, lockType: String?, lockPassword: String?)

    @Query("DELETE FROM chats WHERE id = :chatId")
    suspend fun deleteChatById(chatId: String)


    // --- Messages ---
    @Query("SELECT * FROM messages WHERE chatId = :chatId AND isDeletedLocally = 0 ORDER BY timestamp ASC")
    fun getMessagesForChatFlow(chatId: String): Flow<List<Message>>

    @Query("SELECT * FROM messages WHERE isDeletedLocally = 0")
    fun getAllMessagesFlow(): Flow<List<Message>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: Message)

    @Query("UPDATE messages SET isViewed = 1, encryptedPayload = '[Purged View-Once Media]', localMediaUri = null WHERE id = :messageId")
    suspend fun purgeViewOnceMedia(messageId: String)

    @Query("DELETE FROM messages WHERE id = :messageId")
    suspend fun deleteMessageById(messageId: String)

    @Query("DELETE FROM messages WHERE chatId = :chatId")
    suspend fun clearChatMessages(chatId: String)

    // Used to automatically clean up disappearing messages
    @Query("DELETE FROM messages WHERE expiringAt IS NOT NULL AND expiringAt <= :currentEpochMilli")
    suspend fun purgeExpiredMessages(currentEpochMilli: Long)

    @Query("SELECT * FROM messages WHERE id = :messageId")
    suspend fun getMessageById(messageId: String): Message?


    // --- Group Members ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroupMember(member: GroupMember)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroupMembers(members: List<GroupMember>)

    @Query("SELECT userId FROM group_members WHERE chatId = :chatId")
    suspend fun getGroupMemberIds(chatId: String): List<String>

    @Query("SELECT * FROM group_members WHERE chatId = :chatId")
    fun getGroupMembersFlow(chatId: String): Flow<List<GroupMember>>

    @Query("DELETE FROM group_members WHERE chatId = :chatId AND userId = :userId")
    suspend fun removeMemberFromGroup(chatId: String, userId: String)

    @Query("UPDATE group_members SET isAdmin = :isAdmin WHERE chatId = :chatId AND userId = :userId")
    suspend fun updateMemberAdminStatus(chatId: String, userId: String, isAdmin: Boolean)
}
