package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.model.Chat
import com.example.data.model.GroupMember
import com.example.data.model.Message
import com.example.data.model.User
import com.example.data.repository.ChatRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

sealed interface UIState {
    object Idle : UIState
    object Loading : UIState
    object Success : UIState
}

data class ActiveCall(
    val id: String = UUID.randomUUID().toString(),
    val chatId: String,
    val contactName: String,
    val contactProfilePic: String?,
    val isVideo: Boolean,
    val status: CallStatus, // DIALING, RINGING, CONNECTED, DISCONNECTED
    val isMuted: Boolean = false,
    val isCameraOn: Boolean = true,
    val durationSeconds: Int = 0,
    val encryptionVerified: Boolean = false
)

enum class CallStatus {
    DIALING, RINGING, CONNECTED, DISCONNECTED, INCOMING
}

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    val repository = ChatRepository(db.chatDao())

    // --- UI state holders ---
    val allChats: StateFlow<List<Chat>> = repository.allChats
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val contacts: StateFlow<List<User>> = repository.contacts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedChatId = MutableStateFlow<String?>(null)
    val selectedChatId: StateFlow<String?> = _selectedChatId.asStateFlow()

    private val _selectedChat = MutableStateFlow<Chat?>(null)
    val selectedChat: StateFlow<Chat?> = _selectedChat.asStateFlow()

    // Observable flow of messages depending on the active chatId chosen
    val messagesForSelectedChat: StateFlow<List<Message>> = _selectedChatId
        .flatMapLatest { id ->
            if (id != null) repository.getMessagesForChat(id) else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Group members for the chosen chat
    val groupMembersOfSelectedChat: StateFlow<List<GroupMember>> = _selectedChatId
        .flatMapLatest { id ->
            if (id != null && id.startsWith("group_")) repository.getGroupMembers(id) else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Active states ---
    val meUser = flow {
        emitAll(flow {
            while(true) {
                val u = repository.getUser("me")
                if (u != null) {
                    emit(u)
                    break
                }
                delay(200)
            }
        })
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // Secure Call states
    private val _activeCall = MutableStateFlow<ActiveCall?>(null)
    val activeCall: StateFlow<ActiveCall?> = _activeCall.asStateFlow()

    // Dynamic Screenshot Protection signals
    private val _screenshotProtectionEnabled = MutableStateFlow(true) // Protected by default!
    val screenshotProtectionEnabled: StateFlow<Boolean> = _screenshotProtectionEnabled.asStateFlow()

    // Active View Once media being viewed
    private val _activeViewOnceMessage = MutableStateFlow<Message?>(null)
    val activeViewOnceMessage: StateFlow<Message?> = _activeViewOnceMessage.asStateFlow()

    // Active status stories being viewed
    private val _activeStatusUser = MutableStateFlow<User?>(null)
    val activeStatusUser: StateFlow<User?> = _activeStatusUser.asStateFlow()

    // Recording voice notes states
    private val _isRecordingVoiceNote = MutableStateFlow(false)
    val isRecordingVoiceNote: StateFlow<Boolean> = _isRecordingVoiceNote.asStateFlow()
    private val _voiceNoteDuration = MutableStateFlow(0)
    val voiceNoteDuration: StateFlow<Int> = _voiceNoteDuration.asStateFlow()
    private var voiceNoteJob: Job? = null

    // Group creation state
    private val _groupSelectedMembers = MutableStateFlow<Set<String>>(emptySet())
    val groupSelectedMembers: StateFlow<Set<String>> = _groupSelectedMembers.asStateFlow()

    init {
        // Bootstrap base encryption profiles in background
        viewModelScope.launch {
            repository.bootstrapDatabase()
            startDisappearingSweeper()
        }
    }

    // SWEAPER LOOP: checks and deletes expired disappearing messages in real time!
    private fun startDisappearingSweeper() {
        viewModelScope.launch {
            while (true) {
                repository.cleanExpiredMessages()
                delay(1000) // sweep once every second
            }
        }
    }

    // Toggle selected chat contact
    fun selectChat(chatId: String?) {
        _selectedChatId.value = chatId
        viewModelScope.launch {
            _selectedChat.value = if (chatId != null) db.chatDao().getChatById(chatId) else null
        }
    }

    private val _unlockedChatIds = MutableStateFlow<Set<String>>(emptySet())
    val unlockedChatIds: StateFlow<Set<String>> = _unlockedChatIds.asStateFlow()

    fun lockChat(chatId: String, lockType: String, password: String?) {
        viewModelScope.launch {
            repository.updateChatLock(chatId, true, lockType, password)
            _unlockedChatIds.value = _unlockedChatIds.value - chatId
            _selectedChat.value = db.chatDao().getChatById(chatId)
        }
    }

    fun unlockChatSession(chatId: String) {
        _unlockedChatIds.value = _unlockedChatIds.value + chatId
    }

    fun permanentlyUnlockChat(chatId: String) {
        viewModelScope.launch {
            repository.updateChatLock(chatId, false, null, null)
            _unlockedChatIds.value = _unlockedChatIds.value - chatId
            _selectedChat.value = db.chatDao().getChatById(chatId)
        }
    }

    fun toggleScreenshotProtection(enabled: Boolean) {
        _screenshotProtectionEnabled.value = enabled
    }

    // Chat Actions
    fun sendTextMessage(text: String, isViewOnce: Boolean = false) {
        val chatId = _selectedChatId.value ?: return
        viewModelScope.launch {
            repository.sendMessage(
                chatId = chatId,
                senderId = "me",
                senderName = "Maria (You)",
                plainText = text,
                isViewOnce = isViewOnce
            )
        }
    }

    fun sendMediaMessage(mediaType: String, isViewOnce: Boolean = false) {
        val chatId = _selectedChatId.value ?: return
        viewModelScope.launch {
            val label = if (isViewOnce) "🔒 View Once $mediaType File" else "$mediaType Attachment"
            val uri = if (mediaType == "PHOTO") "mock_media_photo" else "mock_media_video"
            repository.sendMessage(
                chatId = chatId,
                senderId = "me",
                senderName = "Maria (You)",
                plainText = label,
                mediaType = mediaType,
                localMediaUri = uri,
                isViewOnce = isViewOnce
            )

            // Auto reply simulate
            delay(2500)
            repository.simulateMediaReply(chatId, mediaType, isViewOnce)
        }
    }

    fun startVoiceNoteRecording() {
        _isRecordingVoiceNote.value = true
        _voiceNoteDuration.value = 0
        voiceNoteJob = viewModelScope.launch {
            while (_isRecordingVoiceNote.value) {
                delay(1000)
                _voiceNoteDuration.value += 1
            }
        }
    }

    fun cancelVoiceNoteRecording() {
        _isRecordingVoiceNote.value = false
        voiceNoteJob?.cancel()
        _voiceNoteDuration.value = 0
    }

    fun stopAndSendVoiceNote() {
        _isRecordingVoiceNote.value = false
        voiceNoteJob?.cancel()
        val duration = _voiceNoteDuration.value
        if (duration <= 0) return

        val chatId = _selectedChatId.value ?: return
        viewModelScope.launch {
            repository.sendMessage(
                chatId = chatId,
                senderId = "me",
                senderName = "Maria (You)",
                plainText = "🎤 Secure Voice Mail (${duration}s)",
                mediaType = "AUDIO",
                localMediaUri = "mock_media_audio",
                durationSeconds = duration
            )
        }
        _voiceNoteDuration.value = 0
    }

    // View Once Controls
    fun openViewOnceMedia(message: Message) {
        _activeViewOnceMessage.value = message
    }

    fun closeViewOnceMediaAndPurge() {
        val msg = _activeViewOnceMessage.value ?: return
        _activeViewOnceMessage.value = null
        viewModelScope.launch {
            repository.viewOnceMediaOpened(msg.id)
        }
    }

    // Status controls
    fun openStatus(user: User) {
        _activeStatusUser.value = user
    }

    fun closeStatus() {
        val statusUser = _activeStatusUser.value ?: return
        _activeStatusUser.value = null
        viewModelScope.launch {
            db.chatDao().setStatusViewed(statusUser.id, true)
        }
    }

    fun createMyStatusPatch(statusText: String) = viewModelScope.launch {
        repository.postStatusUpdate(statusText)
    }

    // Group Member Admin Operations
    fun toggleMemberSelected(userId: String) {
        val current = _groupSelectedMembers.value
        _groupSelectedMembers.value = if (current.contains(userId)) {
            current - userId
        } else {
            current + userId
        }
    }

    fun clearGroupSelectedMembers() {
        _groupSelectedMembers.value = emptySet()
    }

    fun createGroupConversation(name: String, disappearingTimerSec: Int) {
        viewModelScope.launch {
            val membersList = _groupSelectedMembers.value.toList()
            val createdId = repository.createGroupChat(name, membersList, disappearingTimerSec)
            clearGroupSelectedMembers()
            selectChat(createdId)
        }
    }

    fun adjustGroupDisappearingTimer(chatId: String, seconds: Int) {
        viewModelScope.launch {
            repository.setDisappearingTimer(chatId, seconds)
            // Re-fetch selected chat detail
            _selectedChat.value = db.chatDao().getChatById(chatId)
        }
    }

    fun addMemberToSelectedGroup(userId: String) {
        val chatId = _selectedChatId.value ?: return
        viewModelScope.launch {
            repository.addMemberToGroup(chatId, userId)
        }
    }

    fun removeMemberFromSelectedGroup(userId: String) {
        val chatId = _selectedChatId.value ?: return
        viewModelScope.launch {
            repository.removeMemberFromGroup(chatId, userId)
        }
    }

    fun toggleMemberAdminPrivileges(userId: String, isAdmin: Boolean) {
        val chatId = _selectedChatId.value ?: return
        viewModelScope.launch {
            repository.promoteMemberToAdmin(chatId, userId, isAdmin)
        }
    }

    // --- CALL SIMULATOR (Secure calling) ---
    fun startSecuredCall(chatId: String, isVideo: Boolean) {
        viewModelScope.launch {
            val chat = db.chatDao().getChatById(chatId) ?: return@launch
            var cName = chat.name
            var avatar: String? = null

            if (!chat.isGroup) {
                val contactId = chatId.removePrefix("dm_")
                val contact = db.chatDao().getUserById(contactId)
                if (contact != null) {
                    cName = contact.displayName
                    avatar = contact.profilePic
                }
            } else {
                avatar = "avatar_group"
            }

            // Create outbound dialing state
            val call = ActiveCall(
                chatId = chatId,
                contactName = cName,
                contactProfilePic = avatar,
                isVideo = isVideo,
                status = CallStatus.DIALING
            )
            _activeCall.value = call

            // Step 1: Simulate Dialing -> Ringing after 1.5 seconds
            delay(1500)
            if (_activeCall.value?.id == call.id) {
                _activeCall.value = _activeCall.value?.copy(status = CallStatus.RINGING)
            }

            // Step 2: Establish Secure connected handshake after 2.5 seconds
            delay(2500)
            if (_activeCall.value?.id == call.id) {
                _activeCall.value = _activeCall.value?.copy(
                    status = CallStatus.CONNECTED,
                    encryptionVerified = true
                )
                startCallTimer(call.id)
            }
        }
    }

    // Trigger an incoming secured calling simulation from bots so users can audit answering E2EE keys!
    fun simulateIncomingCall(botId: String, isVideo: Boolean) {
        viewModelScope.launch {
            val contact = db.chatDao().getUserById(botId) ?: return@launch
            val chatId = "dm_$botId"
            
            val incomingCall = ActiveCall(
                chatId = chatId,
                contactName = contact.displayName,
                contactProfilePic = contact.profilePic,
                isVideo = isVideo,
                status = CallStatus.INCOMING
            )
            _activeCall.value = incomingCall
        }
    }

    fun acceptIncomingCall() {
        val currentCall = _activeCall.value ?: return
        viewModelScope.launch {
            _activeCall.value = currentCall.copy(
                status = CallStatus.CONNECTED,
                encryptionVerified = true
            )
            startCallTimer(currentCall.id)
        }
    }

    fun endOrDeclineCall() {
        val currentCall = _activeCall.value ?: return
        _activeCall.value = currentCall.copy(status = CallStatus.DISCONNECTED)
        viewModelScope.launch {
            delay(1000)
            _activeCall.value = null
        }
    }

    fun toggleCallMute() {
        val currentCall = _activeCall.value ?: return
        _activeCall.value = currentCall.copy(isMuted = !currentCall.isMuted)
    }

    fun toggleCallCamera() {
        val currentCall = _activeCall.value ?: return
        _activeCall.value = currentCall.copy(isCameraOn = !currentCall.isCameraOn)
    }

    private var callTimerJob: Job? = null
    private fun startCallTimer(callId: String) {
        callTimerJob?.cancel()
        callTimerJob = viewModelScope.launch {
            while (_activeCall.value?.id == callId && _activeCall.value?.status == CallStatus.CONNECTED) {
                delay(1000)
                _activeCall.value = _activeCall.value?.let {
                    it.copy(durationSeconds = it.durationSeconds + 1)
                }
            }
        }
    }

    // Check if user is an Administrator of the active selected chat
    fun currentUserIsAdminOfSelectedChat(): Boolean {
        val chat = _selectedChat.value ?: return false
        if (!chat.isGroup) return true // In DM, both can control settings
        return chat.adminId == "me"
    }

}
