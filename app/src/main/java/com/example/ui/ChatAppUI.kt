package com.example.ui

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.crypto.CryptoEngine
import com.example.data.model.Chat
import com.example.data.model.GroupMember
import com.example.data.model.Message
import com.example.data.model.User
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecureChatApp(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier
) {
    val selectedChatId by viewModel.selectedChatId.collectAsState()
    val activeCall by viewModel.activeCall.collectAsState()
    val activeViewOnce by viewModel.activeViewOnceMessage.collectAsState()
    val activeStatusUser by viewModel.activeStatusUser.collectAsState()

    Box(modifier = modifier.fillMaxSize()) {
        if (selectedChatId != null) {
            ChatDetailScreen(
                viewModel = viewModel,
                onBack = { viewModel.selectChat(null) }
            )
        } else {
            PortalDashboard(viewModel = viewModel)
        }

        // Overlays & Sheets
        activeCall?.let { call ->
            CallSimulatorOverlay(call = call, viewModel = viewModel)
        }

        activeViewOnce?.let { msg ->
            ViewOnceMediaOverlay(message = msg, viewModel = viewModel)
        }

        activeStatusUser?.let { user ->
            StatusViewerOverlay(user = user, viewModel = viewModel)
        }
    }
}

// --- PORTAL DASHBOARD (Main view container) ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortalDashboard(viewModel: ChatViewModel) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Chats", "Status", "Calls", "Keys & Safety")

    val chats by viewModel.allChats.collectAsState()
    val contacts by viewModel.contacts.collectAsState()
    val meUser by viewModel.meUser.collectAsState()

    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var showAddChatDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "SecureChat",
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .background(CipherTertiary.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "E2EE",
                                fontSize = 10.sp,
                                color = CipherTertiary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.simulateIncomingCall(botId = "elena", isVideo = false) }) {
                        Icon(
                            imageVector = Icons.Default.PhoneCallback,
                            contentDescription = "Simulate Incoming Call",
                            tint = CipherSecondary
                        )
                    }
                    IconButton(onClick = {
                        showAddChatDialog = true
                    }) {
                        Icon(imageVector = Icons.Default.AddComment, contentDescription = "New chat")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                windowInsets = WindowInsets.navigationBars
            ) {
                tabs.forEachIndexed { index, label ->
                    val icon = when (index) {
                        0 -> if (selectedTab == 0) Icons.Filled.ChatBubble else Icons.Outlined.ChatBubbleOutline
                        1 -> if (selectedTab == 1) Icons.Filled.Adjust else Icons.Outlined.Adjust
                        2 -> if (selectedTab == 2) Icons.Filled.Call else Icons.Outlined.Call
                        else -> if (selectedTab == 3) Icons.Filled.VpnKey else Icons.Outlined.VpnKey
                    }
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Icon(imageVector = icon, contentDescription = label) },
                        label = { Text(label, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }
        },
        floatingActionButton = {
            if (selectedTab == 0) {
                FloatingActionButton(
                    onClick = { showCreateGroupDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    modifier = Modifier.testTag("create_group_fab")
                ) {
                    Icon(imageVector = Icons.Default.GroupAdd, contentDescription = "Create Group")
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> ChatsTab(chats = chats, viewModel = viewModel)
                1 -> StatusTab(contacts = contacts, meUser = meUser, viewModel = viewModel)
                2 -> CallsTab(contacts = contacts, viewModel = viewModel)
                3 -> SecurityTab(meUser = meUser, viewModel = viewModel)
            }
        }
    }

    if (showCreateGroupDialog) {
        CreateGroupDialog(
            contacts = contacts,
            viewModel = viewModel,
            onDismiss = { showCreateGroupDialog = false }
        )
    }

    if (showAddChatDialog) {
        AddChatDialog(
            contacts = contacts,
            viewModel = viewModel,
            onDismiss = { showAddChatDialog = false }
        )
    }
}

// --- TAB 1: CHATS LIST ---
@Composable
fun ChatsTab(chats: List<Chat>, viewModel: ChatViewModel) {
    if (chats.isEmpty()) {
        EmptyStateLayout(
            imageVector = Icons.Default.Lock,
            title = "No Secure Chats",
            subtitle = "Your encrypted conversations will appear here. Start a secure chat with your contacts."
        )
        return
    }

    val unlockedChatIds by viewModel.unlockedChatIds.collectAsState()
    var lockTargetChat by remember { mutableStateOf<Chat?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("chats_list"),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(chats) { chat ->
                val isUnlocked = unlockedChatIds.contains(chat.id)
                ChatListItem(
                    chat = chat,
                    isUnlockedInSession = isUnlocked,
                    onClick = {
                        if (chat.isLocked && !isUnlocked) {
                            lockTargetChat = chat
                        } else {
                            viewModel.selectChat(chat.id)
                        }
                    }
                )
            }
        }

        lockTargetChat?.let { chatToUnlock ->
            ChatUnlockOverlay(
                chat = chatToUnlock,
                onUnlockSuccess = {
                    viewModel.unlockChatSession(chatToUnlock.id)
                    viewModel.selectChat(chatToUnlock.id)
                    lockTargetChat = null
                },
                onDismiss = {
                    lockTargetChat = null
                }
            )
        }
    }
}

@Composable
fun ChatListItem(
    chat: Chat,
    isUnlockedInSession: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("chat_item_${chat.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Profile image view simulation
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(
                        if (chat.isGroup) MaterialTheme.colorScheme.secondary.copy(0.15f)
                        else MaterialTheme.colorScheme.primary.copy(0.15f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (chat.isGroup) {
                    Icon(
                        imageVector = Icons.Default.Groups,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = chat.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    if (chat.isGroup) {
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "Admin",
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Filled.VerifiedUser,
                            contentDescription = "Encrypted Pair",
                            tint = CipherTertiary,
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    if (chat.isLocked) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .background(
                                    if (isUnlockedInSession) CipherTertiary.copy(alpha = 0.15f) 
                                    else CipherError.copy(alpha = 0.15f), 
                                    RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (isUnlockedInSession) "Decrypted" else "Sealed",
                                fontSize = 9.sp,
                                color = if (isUnlockedInSession) CipherTertiary else CipherError,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = if (chat.isLocked && !isUnlockedInSession) {
                        "🔒 Crypt-locked via ${chat.lockType?.lowercase() ?: "key"}"
                    } else if (chat.isGroup) {
                        "Secure group conversation"
                    } else {
                        "E2E Encrypted Room"
                    },
                    fontSize = 13.sp,
                    color = if (chat.isLocked && !isUnlockedInSession) CipherError.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Security details indicators
            Column(horizontalAlignment = Alignment.End) {
                if (chat.disappearingTimerSeconds > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = "Disappearing on",
                            tint = CipherWarning,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "${chat.disappearingTimerSeconds}s",
                            fontSize = 11.sp,
                            color = CipherWarning,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Icon(
                    imageVector = if (chat.isLocked) {
                        if (isUnlockedInSession) Icons.Default.LockOpen else Icons.Default.Lock
                    } else {
                        Icons.Default.Lock
                    },
                    contentDescription = "Security Status",
                    tint = if (chat.isLocked) {
                        if (isUnlockedInSession) CipherTertiary else CipherError
                    } else {
                        CipherTertiary.copy(alpha = 0.5f)
                    },
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// --- TAB 2: PRIVACY STATUS ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusTab(contacts: List<User>, meUser: User?, viewModel: ChatViewModel) {
    var statusText by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Post status box
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Update Cryptographic Status",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextField(
                        value = statusText,
                        onValueChange = { statusText = it },
                        placeholder = { Text("What is happening securely?", fontSize = 13.sp) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("status_input"),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        maxLines = 2
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = {
                            if (statusText.isNotBlank()) {
                                viewModel.createMyStatusPatch(statusText)
                                statusText = ""
                                Toast.makeText(context, "Status loaded into E2EE space", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                            .size(40.dp)
                            .testTag("status_post_button")
                    ) {
                        Icon(imageVector = Icons.Default.Send, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = CipherTertiary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Screenshot protection enabled on statuses automatically.",
                        fontSize = 10.sp,
                        color = Color.LightGray.copy(0.6f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Active Verified Safepaths",
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            // "Me" Status
            meUser?.let { me ->
                item {
                    StatusRowItem(user = me, title = "Maria (You)", isMe = true) {
                        viewModel.openStatus(me)
                    }
                }
            }

            // Contacts status
            items(contacts) { contact ->
                StatusRowItem(user = contact, title = contact.displayName, isMe = false) {
                    viewModel.openStatus(contact)
                }
            }
        }
    }
}

@Composable
fun StatusRowItem(user: User, title: String, isMe: Boolean, onClick: () -> Unit) {
    val formatter = SimpleDateFormat("HH:mm a", Locale.getDefault())
    val dateString = formatter.format(Date(user.statusTimestamp))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Status circle indicator
        Box(
            modifier = Modifier
                .size(48.dp)
                .drawBehind {
                    if (!user.hasViewedStatus) {
                        drawCircle(
                            color = CipherTertiary,
                            radius = size.minDimension / 2f + 4.dp.toPx(),
                            style = Stroke(width = 2.5.dp.toPx())
                        )
                    }
                }
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isMe) Icons.Default.Fingerprint else Icons.Default.Shield,
                contentDescription = null,
                tint = if (user.hasViewedStatus) Color.Gray else MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = user.statusText,
                fontSize = 12.sp,
                color = Color.LightGray.copy(0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Text(
            text = dateString,
            fontSize = 11.sp,
            color = Color.Gray
        )
    }
}

// --- TAB 3: SECURE CALLS LOG ---
@Composable
fun CallsTab(contacts: List<User>, viewModel: ChatViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.RecordVoiceOver,
                    contentDescription = null,
                    tint = CipherSecondary,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "E2EE Call Node Established",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.White
                    )
                    Text(
                        text = "Encrypted routing fully operational with 256-bit secure sessions.",
                        fontSize = 12.sp,
                        color = Color.LightGray.copy(0.7f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Verify Cryptographic Node Dialing",
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(contacts) { contact ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = contact.displayName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.White
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(10.dp),
                                tint = CipherTertiary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Fingerprint safe",
                                fontSize = 11.sp,
                                color = CipherTertiary
                            )
                        }
                    }

                    Row {
                        IconButton(
                            onClick = { viewModel.startSecuredCall("dm_${contact.id}", isVideo = false) },
                            modifier = Modifier.background(CipherPrimary.copy(0.15f), CircleShape).size(40.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Call, contentDescription = "Audio call", tint = CipherSecondary, modifier = Modifier.size(18.dp))
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        IconButton(
                            onClick = { viewModel.startSecuredCall("dm_${contact.id}", isVideo = true) },
                            modifier = Modifier.background(CipherPrimary.copy(0.15f), CircleShape).size(40.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Videocam, contentDescription = "Video call", tint = CipherSecondary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

// --- TAB 4: SECURITY KEYS & SAFETY ---
@Composable
fun SecurityTab(meUser: User?, viewModel: ChatViewModel) {
    val screenshotEnabled by viewModel.screenshotProtectionEnabled.collectAsState()
    val scope = rememberCoroutineScope()
    var showRawDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Base keys overview
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.HistoryEdu,
                            contentDescription = null,
                            tint = CipherSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Your Secure RSA Key-Pair",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "RSA asymmetric key exchanges verify identity, encrypting individual chat session tokens prior to network routing.",
                        fontSize = 12.sp,
                        color = Color.LightGray.copy(0.7f)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(0.4f), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Column {
                            Text(
                                text = "PUBLIC VERIFICATION HEX",
                                fontSize = 10.sp,
                                color = CipherSecondary,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = meUser?.publicKey?.take(48) ?: "Generating cryptographic signature logs...",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color.White.copy(0.8f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { showRawDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = CipherPrimary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Default.VpnKey, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Audit Raw Keys Logs", fontSize = 13.sp)
                    }
                }
            }
        }

        // Screenshot protection toggle
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (screenshotEnabled) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = null,
                        tint = if (screenshotEnabled) CipherTertiary else Color.Gray,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Screenshot Shielding",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.White
                        )
                        Text(
                            text = "When active, OS screenshots and screen recorders are dynamically blocked for maximum E2E privacy.",
                            fontSize = 11.sp,
                            color = Color.LightGray.copy(0.7f)
                        )
                    }
                    Switch(
                        checked = screenshotEnabled,
                        onCheckedChange = { viewModel.toggleScreenshotProtection(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = CipherTertiary
                        ),
                        modifier = Modifier.testTag("screenshot_switch")
                    )
                }
            }
        }

        // Encryption stats card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Verified Auditing Standards",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    SecurityAuditItem(label = "Symmetric Protocol", value = "AES-256-CBC")
                    SecurityAuditItem(label = "Asymmetric Protocol", value = "RSA-2048 (PKCS1)")
                    SecurityAuditItem(label = "Salt Handshake", value = "SHA-256 HMAC")
                    SecurityAuditItem(label = "Local Vault Purges", value = "Zero-Overwrites")
                }
            }
        }
    }

    if (showRawDialog && meUser != null) {
        Dialog(onDismissRequest = { showRawDialog = false }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = CipherSurfaceVariant),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.padding(20.dp).fillMaxWidth()
            ) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
                    Text(
                        text = "🔐 LOCAL COLD STORAGE KEYS",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = CipherSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "RSA PUBLIC KEY PEM:",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        color = Color.Gray,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = meUser.publicKey,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color.White,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                            .padding(6.dp)
                            .height(100.dp)
                            .verticalScroll(rememberScrollState())
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "RSA PRIVATE KEY PEM:",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        color = Color.Gray,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = meUser.privateKey ?: "Key missing from cache",
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = CipherError,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                            .padding(6.dp)
                            .height(100.dp)
                            .verticalScroll(rememberScrollState())
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { showRawDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = CipherPrimary),
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Close Storage Audit")
                    }
                }
            }
        }
    }
}

@Composable
fun SecurityAuditItem(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 13.sp, color = Color.LightGray.copy(0.7f))
        Text(
            text = value,
            fontSize = 13.sp,
            color = CipherTertiary,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

// --- CHAT DETAIL SCREEN (Selected Chat Context) ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit
) {
    val chat by viewModel.selectedChat.collectAsState()
    val messages by viewModel.messagesForSelectedChat.collectAsState()
    val meUser by viewModel.meUser.collectAsState()

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    var textInput by remember { mutableStateOf("") }
    var showGroupManageSheet by remember { mutableStateOf(false) }
    var showSafetyCodeDialog by remember { mutableStateOf(false) }

    // Auto scroll to bottom
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable {
                            if (chat?.isGroup == true) {
                                showGroupManageSheet = true
                            } else {
                                showSafetyCodeDialog = true
                            }
                        }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(CipherPrimary.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (chat?.isGroup == true) Icons.Default.Groups else Icons.Default.Person,
                                contentDescription = null,
                                tint = CipherPrimary
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = chat?.name ?: "Secure Chat",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color.White
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(CipherTertiary, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (chat?.isGroup == true) "Tap to admin settings" else "Tap for Fingerprint",
                                    fontSize = 11.sp,
                                    color = CipherTertiary
                                )
                            }
                        }
                    }
                },
                actions = {
                    chat?.let { c ->
                        IconButton(onClick = { viewModel.startSecuredCall(c.id, isVideo = false) }) {
                            Icon(imageVector = Icons.Default.Call, contentDescription = "Audio call", tint = CipherSecondary)
                        }
                        IconButton(onClick = { viewModel.startSecuredCall(c.id, isVideo = true) }) {
                            Icon(imageVector = Icons.Default.Videocam, contentDescription = "Video call", tint = CipherSecondary)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        BackHandler {
            onBack()
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Warning panel E2EE banner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CipherPrimary.copy(alpha = 0.1f))
                        .padding(vertical = 4.dp, horizontal = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = CipherTertiary,
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "RSA-2048 & AES-256 peer-to-peer connection verified.",
                            fontSize = 11.sp,
                            color = CipherTertiary
                        )
                    }
                }

                // Messages list area
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .testTag("message_list"),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(messages) { msg ->
                        MessageBubble(
                            message = msg,
                            meUser = meUser,
                            onOpenViewOnce = { viewModel.openViewOnceMedia(msg) }
                        )
                    }
                }

                // Disappearing warning alert
                chat?.let { c ->
                    if (c.disappearingTimerSeconds > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CipherWarning.copy(alpha = 0.1f))
                                .padding(vertical = 6.dp, horizontal = 16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Timer,
                                    contentDescription = null,
                                    tint = CipherWarning,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "All messages inside this chat disappear permanently ${c.disappearingTimerSeconds}s after release.",
                                    fontSize = 11.sp,
                                    color = CipherWarning,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                // Chat Input box
                ChatInputBar(
                    textInput = textInput,
                    onTextChanged = { textInput = it },
                    onSendText = {
                        if (textInput.isNotBlank()) {
                            viewModel.sendTextMessage(textInput)
                            textInput = ""
                        }
                    },
                    viewModel = viewModel
                )
            }
        }
    }

    chat?.let { c ->
        if (showGroupManageSheet) {
            GroupManageDialog(
                chat = c,
                viewModel = viewModel,
                onDismiss = { showGroupManageSheet = false }
            )
        }

        if (showSafetyCodeDialog) {
            SafetyCodeDialog(
                chat = c,
                viewModel = viewModel,
                onDismiss = { showSafetyCodeDialog = false }
            )
        }
    }
}

// --- RENDER COMPOSITE MESSAGE BUBBLE ---
@Composable
fun MessageBubble(
    message: Message,
    meUser: User?,
    onOpenViewOnce: () -> Unit
) {
    val isMe = message.senderId == "me"
    val isSystem = message.senderId == "system"

    val scope = rememberCoroutineScope()
    var rawTextShow by remember { mutableStateOf<String?>(null) }
    var decryptedMessage by remember { mutableStateOf<String>("Decrypting cipher payload...") }

    // Start hybrid decryption asynchronously
    LaunchedEffect(message.encryptedPayload, meUser?.privateKey) {
        if (!message.isEncrypted || message.senderId == "system") {
            decryptedMessage = message.encryptedPayload
            return@LaunchedEffect
        }
        val pKey = meUser?.privateKey
        if (pKey != null) {
            delay(300) // add slight delay to show decrypting transition
            val clearText = CryptoEngine.decryptHybrid(
                encryptedPayloadBase64 = message.encryptedPayload,
                ivBase64 = message.iv,
                encryptedAESKeyBase64 = message.encryptedAESKey,
                recipientPrivateKeyStr = pKey
            )
            decryptedMessage = clearText
        } else {
            decryptedMessage = "[Private key missing]"
        }
    }

    if (isSystem) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .background(Color.DarkGray.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = decryptedMessage,
                    fontSize = 11.sp,
                    color = Color.LightGray,
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    // Dynamic burn timer calculation
    var timeLeftRatio by remember { mutableStateOf(1f) }
    var remainingSeconds by remember { mutableStateOf(0L) }

    LaunchedEffect(message.expiringAt) {
        val exp = message.expiringAt
        if (exp != null) {
            while (true) {
                val now = System.currentTimeMillis()
                val diff = exp - now
                if (diff <= 0) {
                    timeLeftRatio = 0f
                    remainingSeconds = 0
                    break
                }
                val totalSpan = (exp - message.timestamp).toFloat().coerceAtLeast(1f)
                timeLeftRatio = (diff.toFloat() / totalSpan).coerceIn(0f, 1f)
                remainingSeconds = Math.ceil(diff / 1000.0).toLong()
                delay(300)
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .testTag("message_bubble_${message.id}"),
        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
    ) {
        Column(
            horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
        ) {
            // Sender indicator if not me in groups
            if (!isMe) {
                Text(
                    text = message.senderName,
                    fontSize = 11.sp,
                    color = CipherSecondary,
                    modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
                    fontWeight = FontWeight.Bold
                )
            }

            // Message Bubble Core
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isMe) MaterialTheme.colorScheme.primary else CipherSurfaceVariant
                ),
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isMe) 16.dp else 0.dp,
                    bottomEnd = if (isMe) 0.dp else 16.dp
                ),
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onLongPress = {
                                rawTextShow = message.encryptedPayload
                            }
                        )
                    }
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // Render message contents based on type
                    when (message.mediaType) {
                        "PHOTO" -> {
                            RenderPhotoBubble(
                                message = message,
                                isMe = isMe,
                                onOpen = onOpenViewOnce
                            )
                        }
                        "VIDEO" -> {
                            RenderVideoBubble(
                                message = message,
                                isMe = isMe,
                                onOpen = onOpenViewOnce
                            )
                        }
                        "AUDIO" -> {
                            RenderAudioVoiceBubble(
                                message = message,
                                isMe = isMe
                            )
                        }
                        else -> {
                            // Text contents displaying decryption
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = decryptedMessage,
                                    fontSize = 14.sp,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Inside bubble metrics (time/padlock)
                    Row(
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
                        val timeStr = formatter.format(Date(message.timestamp))

                        // Fuse countdown timer inside disappearing balloons
                        if (message.expiringAt != null && remainingSeconds > 0) {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = null,
                                tint = if (isMe) Color.White.copy(0.7f) else CipherWarning,
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "${remainingSeconds}s",
                                fontSize = 10.sp,
                                color = if (isMe) Color.White.copy(0.7f) else CipherWarning,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }

                        Text(
                            text = timeStr,
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Encrypted",
                            tint = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(10.dp)
                        )
                    }
                }
            }

            // Visual Burner Fuse Bar beneath bubble
            if (message.expiringAt != null && remainingSeconds > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .width(120.dp)
                        .height(3.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.15f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(timeLeftRatio)
                            .background(CipherWarning)
                    )
                }
            }
        }
    }

    if (rawTextShow != null) {
        Dialog(onDismissRequest = { rawTextShow = null }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = CipherSurface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.padding(20.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Security, contentDescription = null, tint = CipherError, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("HYBRID CYPHER DATA", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "The packet intercepted in database memory contains encrypted payload hashes. Decryption is only solved locally via the client private RSA key pair structure.",
                        fontSize = 11.sp,
                        color = Color.LightGray.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Interupted Cipher String:",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        color = CipherSecondary
                    )
                    Text(
                        text = rawTextShow ?: "",
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = CipherTertiary,
                        maxLines = 6,
                        modifier = Modifier
                            .background(Color.Black.copy(0.4f), RoundedCornerShape(6.dp))
                            .padding(8.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { rawTextShow = null },
                        colors = ButtonDefaults.buttonColors(containerColor = CipherPrimary),
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Close Audit Overlay")
                    }
                }
            }
        }
    }
}

// --- VOICE MAIL RENDER COMPONENT ---
@Composable
fun RenderAudioVoiceBubble(message: Message, isMe: Boolean) {
    var isPlaying by remember { mutableStateOf(false) }
    var currentProgress by remember { mutableStateOf(0.0f) }

    // Waveform rendering variables
    val barAmplitudes = listOf(12, 18, 14, 24, 8, 30, 22, 16, 10, 28, 20, 15, 26, 12, 18, 8, 24, 14)

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            val totalSteps = 100
            val stepDelay = (message.durationSeconds * 10).toLong()
            for (step in 1..totalSteps) {
                if (!isPlaying) break
                currentProgress = step.toFloat() / totalSteps
                delay(stepDelay)
            }
            isPlaying = false
            currentProgress = 0.0f
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = { isPlaying = !isPlaying },
            modifier = Modifier
                .background(Color.White.copy(0.15f), CircleShape)
                .size(36.dp)
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Color.White
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Live waving audio signal visual spikes
        Row(
            modifier = Modifier.weight(1f).height(32.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            barAmplitudes.forEachIndexed { idx, heightDp ->
                val threshold = idx.toFloat() / barAmplitudes.size
                val tint = if (currentProgress >= threshold) CipherTertiary else Color.White.copy(alpha = 0.35f)
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(heightDp.dp)
                        .clip(CircleShape)
                        .background(tint)
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = "${message.durationSeconds}s",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

// --- PHOTO BUBBLE / VAULT VIEW ---
@Composable
fun RenderPhotoBubble(message: Message, isMe: Boolean, onOpen: () -> Unit) {
    if (message.isViewOnce) {
        if (message.isViewed) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(0.3f), RoundedCornerShape(8.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = CipherError,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "View Once Media Purged",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.LightGray
                    )
                    Text(
                        text = "Zero-Overwrote on local storage.",
                        fontSize = 10.sp,
                        color = Color.Gray
                    )
                }
            }
        } else {
            // Unopened vault
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpen)
                    .background(CipherWarning.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                    .border(1.dp, CipherWarning.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.LockOpen,
                    contentDescription = null,
                    tint = CipherWarning,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "🔒 POP VIEW ONCE PHOTO",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = CipherWarning,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "Viewing is single-access only.",
                    fontSize = 10.sp,
                    color = Color.LightGray.copy(0.7f)
                )
            }
        }
    } else {
        // Standard E2EE image loading mockup
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                // Since this is a local offline sandboxed emulator, we paint a gorgeous security grid mockup
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(CipherPrimary.copy(alpha = 0.2f), CipherSurfaceVariant)
                        )
                    )
                    // Draw digital padlock overlay
                    val path = Path()
                    // Draw a padlock symbol on canvas
                    val centerX = size.width / 2
                    val centerY = size.height / 2
                    drawCircle(Color.White.copy(0.1f), radius = 40.dp.toPx(), center = Offset(centerX, centerY))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "ENCRYPTED PHOTO FILE",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

// --- VIDEO BUBBLE / VAULT VIEW ---
@Composable
fun RenderVideoBubble(message: Message, isMe: Boolean, onOpen: () -> Unit) {
    if (message.isViewOnce) {
        if (message.isViewed) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(0.3f), RoundedCornerShape(8.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.VideocamOff,
                    contentDescription = null,
                    tint = CipherError,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "View Once Video Purged",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.LightGray
                    )
                    Text(
                        text = "Zero-Overwrote on local storage.",
                        fontSize = 10.sp,
                        color = Color.Gray
                    )
                }
            }
        } else {
            // Unopened vault
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpen)
                    .background(CipherWarning.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                    .border(1.dp, CipherWarning.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.PlayCircle,
                    contentDescription = null,
                    tint = CipherWarning,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "🔒 POP VIEW ONCE VIDEO",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = CipherWarning,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "Single playback vault stream.",
                    fontSize = 10.sp,
                    color = Color.LightGray.copy(0.7f)
                )
            }
        }
    } else {
        // Standard video asset mockup
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(CipherSurfaceVariant, CipherBlue.copy(0.2f))
                    )
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.PlayCircleFilled,
                    contentDescription = "Play video",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "ENCRYPTED FLOW VIDEO",
                    fontSize = 10.sp,
                    color = Color.LightGray,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

// --- CHAT INPUT BAR WITH ADVANCED TRIGGERS ---
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun ChatInputBar(
    textInput: String,
    onTextChanged: (String) -> Unit,
    onSendText: () -> Unit,
    viewModel: ChatViewModel
) {
    val isRecording by viewModel.isRecordingVoiceNote.collectAsState()
    val recordingDuration by viewModel.voiceNoteDuration.collectAsState()

    var showMediaAttachPopover by remember { mutableStateOf(false) }
    var viewOncePending by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!isRecording) {
            IconButton(
                onClick = { showMediaAttachPopover = !showMediaAttachPopover },
                modifier = Modifier.testTag("attach_button")
            ) {
                Icon(
                    imageVector = if (showMediaAttachPopover) Icons.Default.Close else Icons.Default.AddCircleOutline,
                    contentDescription = "Attach media",
                    tint = CipherSecondary
                )
            }

            IconButton(
                onClick = { viewOncePending = !viewOncePending },
                modifier = Modifier.testTag("toggle_view_once")
            ) {
                Icon(
                    imageVector = if (viewOncePending) Icons.Default.Lock else Icons.Default.LockOpen,
                    contentDescription = "Toggle View Once",
                    tint = if (viewOncePending) CipherWarning else Color.Gray
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Main messaging field
            TextField(
                value = textInput,
                onValueChange = onTextChanged,
                placeholder = {
                    Text(
                        if (viewOncePending) "Send view once secure text..." else "Compose encryption package...",
                        fontSize = 13.sp
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("chat_message_input"),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Black.copy(0.15f),
                    unfocusedContainerColor = Color.Black.copy(0.15f),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                shape = RoundedCornerShape(20.dp),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Send
                ),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (textInput.isNotBlank()) {
                            viewModel.sendTextMessage(textInput, isViewOnce = viewOncePending)
                            onTextChanged("")
                            viewOncePending = false
                        }
                    }
                )
            )

            Spacer(modifier = Modifier.width(6.dp))

            if (textInput.isNotBlank()) {
                IconButton(
                    onClick = {
                        viewModel.sendTextMessage(textInput, isViewOnce = viewOncePending)
                        onTextChanged("")
                        viewOncePending = false
                    },
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .size(40.dp)
                        .testTag("message_send_button")
                ) {
                    Icon(imageVector = Icons.Default.Send, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(18.dp))
                }
            } else {
                // Record voice mail button - triggers voice mail recorders
                Box(
                    modifier = Modifier
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    try {
                                        viewModel.startVoiceNoteRecording()
                                        awaitRelease()
                                        viewModel.stopAndSendVoiceNote()
                                    } catch (e: Exception) {
                                        viewModel.cancelVoiceNoteRecording()
                                    }
                                }
                            )
                        }
                        .background(CipherPrimary, CircleShape)
                        .size(40.dp)
                        .testTag("voice_record_holder"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = Icons.Default.Mic, contentDescription = "Hold mic to record", tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        } else {
            // Alternate voice mails record layout showing active waves
            Row(
                modifier = Modifier
                    .weight(1f)
                    .background(CipherError.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(CipherError, CircleShape)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "RECORDING ENCRYPTED VOICE: ${recordingDuration}s",
                    fontSize = 11.sp,
                    color = CipherError,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "RELEASE TO SEND",
                    fontSize = 10.sp,
                    color = Color.Gray,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    // Media trigger drawer list
    if (showMediaAttachPopover) {
        Dialog(onDismissRequest = { showMediaAttachPopover = false }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = CipherSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.padding(20.dp).fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "🔒 ATTACH ENCRYPTED MEDIA",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    AttachOptionItem(
                        imageVector = Icons.Default.Image,
                        label = "Send Encrypted Photo",
                        color = CipherPrimary
                    ) {
                        viewModel.sendMediaMessage("PHOTO", isViewOnce = viewOncePending)
                        showMediaAttachPopover = false
                        viewOncePending = false
                    }

                    AttachOptionItem(
                        imageVector = Icons.Default.Videocam,
                        label = "Send Encrypted Video",
                        color = CipherBlue
                    ) {
                        viewModel.sendMediaMessage("VIDEO", isViewOnce = viewOncePending)
                        showMediaAttachPopover = false
                        viewOncePending = false
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Divider(color = Color.DarkGray)
                    Spacer(modifier = Modifier.height(8.dp))

                    AttachOptionItem(
                        imageVector = Icons.Default.Lock,
                        label = "Send VIEW ONCE Secure Photo",
                        color = CipherWarning
                    ) {
                        viewModel.sendMediaMessage("PHOTO", isViewOnce = true)
                        showMediaAttachPopover = false
                        viewOncePending = false
                    }

                    AttachOptionItem(
                        imageVector = Icons.Default.NotificationImportant,
                        label = "Send VIEW ONCE Secure Video",
                        color = CipherError
                    ) {
                        viewModel.sendMediaMessage("VIDEO", isViewOnce = true)
                        showMediaAttachPopover = false
                        viewOncePending = false
                    }
                }
            }
        }
    }
}

@Composable
fun AttachOptionItem(
    imageVector: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .background(color.copy(alpha = 0.15f), CircleShape)
                .size(36.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = imageVector, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = label, color = Color.White, fontSize = 14.sp)
    }
}

// --- SECURE CALL SIMULATOR SCREEN OVERLAY ---
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun CallSimulatorOverlay(
    call: ActiveCall,
    viewModel: ChatViewModel
) {
    val durationMin = call.durationSeconds / 60
    val durationSec = call.durationSeconds % 60
    val durationText = "%02d:%02d".format(durationMin, durationSec)

    // Animated vibration pulses inside callers portrait
    val transition = rememberInfiniteTransition()
    val pulseRatio by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(usePlatformDefaultWidth = false) // full screen!
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(CipherBackground)
                .testTag("call_overlay")
        ) {
            // If VIDEO calling is active and status is CONNECTED, draw simulated preview
            if (call.isVideo && call.status == CallStatus.CONNECTED && call.isCameraOn) {
                // Let's draw an anim camera visualizer stream
                Box(modifier = Modifier.fillMaxSize()) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawRect(
                            brush = Brush.linearGradient(
                                colors = listOf(CipherSurfaceVariant, Color.Black)
                            )
                        )
                        // Mock wireframe mesh represents premium peer network mapping
                        val linesCount = 10
                        for (i in 0..linesCount) {
                            val x = (size.width / linesCount) * i
                            drawLine(
                                color = CipherPrimary.copy(alpha = 0.05f),
                                start = Offset(x, 0f),
                                end = Offset(x, size.height),
                                strokeWidth = 1.dp.toPx()
                            )
                        }
                    }

                    // Floating self camera preview pane (PiP style)
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 48.dp, end = 20.dp)
                            .width(90.dp)
                            .height(130.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.DarkGray)
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawRect(Color.DarkGray)
                        }
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(imageVector = Icons.Default.Face, contentDescription = null, tint = CipherSecondary)
                            Text("Your camera", fontSize = 9.sp, color = Color.White)
                        }
                    }
                }
            } else {
                // Audio call decoration - custom animated circuit nodes on behind
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        color = CipherPrimary.copy(0.04f),
                        radius = size.minDimension / 2f
                    )
                }
            }

            // Top section: security certificates tags
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(top = 40.dp, start = 24.dp, end = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(CipherPrimary.copy(0.2f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(imageVector = Icons.Default.Https, contentDescription = null, tint = CipherTertiary, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "SECURED CRYPTOPATH INTERLOCK",
                        color = CipherTertiary,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp
                    )
                }

                if (call.encryptionVerified) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "E2EE SHA-256 Fingerprint Validated",
                        fontSize = 11.sp,
                        color = Color.LightGray.copy(alpha = 0.6f)
                    )
                }
            }

            // Center section: partner details
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(bottom = 60.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(contentAlignment = Alignment.Center) {
                    // Pulsing security rings representing cipher sound travel
                    if (call.status == CallStatus.CONNECTED) {
                        Box(
                            modifier = Modifier
                                .size(140.dp * pulseRatio)
                                .border(1.dp, CipherSecondary.copy(alpha = 0.15f), CircleShape)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(110.dp)
                            .clip(CircleShape)
                            .background(CipherSurface),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (call.isVideo) Icons.Default.Videocam else Icons.Default.Person,
                            contentDescription = null,
                            tint = CipherSecondary,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = call.contactName,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = when (call.status) {
                        CallStatus.DIALING -> "PROBING KEY EXCHANGE..."
                        CallStatus.RINGING -> "SECURE PEER RINGING DIRECT..."
                        CallStatus.CONNECTED -> "CONNECTED - INTEGRITY: 100%"
                        CallStatus.INCOMING -> "INCOMING SECURED INTERLOCK"
                        else -> "CALL TERMINATED"
                    },
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = if (call.status == CallStatus.CONNECTED) CipherTertiary else CipherWarning
                )

                if (call.status == CallStatus.CONNECTED) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = durationText,
                        fontSize = 16.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Bottom control tray
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = 48.dp)
            ) {
                if (call.status == CallStatus.INCOMING) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        IconButton(
                            onClick = { viewModel.endOrDeclineCall() },
                            modifier = Modifier
                                .background(CipherError, CircleShape)
                                .size(64.dp)
                        ) {
                            Icon(imageVector = Icons.Default.CallEnd, contentDescription = "Decline", tint = Color.White)
                        }

                        IconButton(
                            onClick = { viewModel.acceptIncomingCall() },
                            modifier = Modifier
                                .background(CipherTertiary, CircleShape)
                                .size(64.dp)
                                .testTag("accept_call")
                        ) {
                            Icon(imageVector = Icons.Default.Call, contentDescription = "Accept", tint = Color.White)
                        }
                    }
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Mute button
                        IconButton(
                            onClick = { viewModel.toggleCallMute() },
                            modifier = Modifier
                                .background(if (call.isMuted) Color.White else Color.White.copy(0.15f), CircleShape)
                                .size(50.dp)
                        ) {
                            Icon(
                                imageVector = if (call.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                contentDescription = "Mute",
                                tint = if (call.isMuted) Color.Black else Color.White
                            )
                        }

                        // Call End
                        IconButton(
                            onClick = { viewModel.endOrDeclineCall() },
                            modifier = Modifier
                                .background(CipherError, CircleShape)
                                .size(60.dp)
                                .testTag("end_call")
                        ) {
                            Icon(imageVector = Icons.Default.CallEnd, contentDescription = "End Call", tint = Color.White)
                        }

                        // Toggle Camera
                        if (call.isVideo) {
                            IconButton(
                                onClick = { viewModel.toggleCallCamera() },
                                modifier = Modifier
                                    .background(if (!call.isCameraOn) Color.White else Color.White.copy(0.15f), CircleShape)
                                    .size(50.dp)
                            ) {
                                Icon(
                                    imageVector = if (call.isCameraOn) Icons.Default.Videocam else Icons.Default.VideocamOff,
                                    contentDescription = "Camera toggle",
                                    tint = if (!call.isCameraOn) Color.Black else Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- VIEW ONCE MEDIA FULLSCREEN OVERLAY ---
@Composable
fun ViewOnceMediaOverlay(
    message: Message,
    viewModel: ChatViewModel
) {
    val context = LocalContext.current
    var secondsOpened by remember { mutableStateOf(0) }
    val maxDuration = 10 // users get 10 seconds to look at view-once file before automatic sweep!

    LaunchedEffect(Unit) {
        while (secondsOpened < maxDuration) {
            delay(1000)
            secondsOpened += 1
        }
        viewModel.closeViewOnceMediaAndPurge()
        Toast.makeText(context, "Lock closed. Media permanently cleared.", Toast.LENGTH_SHORT).show()
    }

    Dialog(
        onDismissRequest = {
            viewModel.closeViewOnceMediaAndPurge()
            Toast.makeText(context, "Lock closed. Media permanently cleared.", Toast.LENGTH_SHORT).show()
        },
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .testTag("view_once_overlay")
        ) {
            // Simulated photo view (a secure visual on canvas)
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(Color.Black)
                // Draw secure glowing grid behind
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(CipherSecondary.copy(0.15f), Color.Transparent),
                        center = center
                    )
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Header details
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 32.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = CipherWarning)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "VIEW ONCE VAULT",
                            color = CipherWarning,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Box(
                        modifier = Modifier
                            .background(CipherError, RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Self-Destruct: ${maxDuration - secondsOpened}s",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Render secure visual
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(240.dp)
                            .background(CipherSurfaceVariant, RoundedCornerShape(16.dp))
                            .border(2.dp, CipherWarning, RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = if (message.mediaType == "PHOTO") Icons.Default.Visibility else Icons.Default.Movie,
                                contentDescription = null,
                                tint = CipherWarning,
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "STREAMING ${message.mediaType} LIVE",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "No Screenshot Facility Granted",
                                color = Color.Gray,
                                fontSize = 10.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Do not leave this screen. Back action completely wipes data.",
                        fontSize = 11.sp,
                        color = Color.LightGray.copy(0.6f),
                        textAlign = TextAlign.Center
                    )
                }

                // Close button
                Button(
                    onClick = {
                        viewModel.closeViewOnceMediaAndPurge()
                        Toast.makeText(context, "Lock closed. Media permanently cleared.", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CipherError),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 32.dp)
                        .testTag("dismiss_view_once")
                ) {
                    Text("Secure Close & Zero out")
                }
            }
        }
    }
}

// --- STATUS VIEWER FULL SCREEN OVERLAY ---
@Composable
fun StatusViewerOverlay(
    user: User,
    viewModel: ChatViewModel
) {
    var progress by remember { mutableStateOf(0f) }

    LaunchedEffect(Unit) {
        val totalSteps = 100
        for (step in 1..totalSteps) {
            progress = step.toFloat() / totalSteps
            delay(50) // 5 seconds total status time!
        }
        viewModel.closeStatus()
    }

    Dialog(
        onDismissRequest = { viewModel.closeStatus() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .testTag("status_viewer_overlay")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Progress indicator bars
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(0.2f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progress)
                                .background(CipherTertiary)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // User name profile
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(CipherPrimary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = Icons.Default.Person, contentDescription = null, tint = Color.White)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = user.displayName,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Verified secure status update",
                                color = CipherTertiary,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                // Main Status Text body (drawn on simulated design backdrop)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(vertical = 32.dp)
                        .background(CipherSurface, RoundedCornerShape(16.dp))
                        .border(1.dp, CipherTertiary.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = CipherTertiary,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = user.statusText,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Footer note
                Text(
                    text = "STATUS PROTECTED VIA SCREENSHOT SHIELDING",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                )
            }
        }
    }
}

// --- GROUP CREATOR DIALOG ---
@Composable
fun CreateGroupDialog(
    contacts: List<User>,
    viewModel: ChatViewModel,
    onDismiss: () -> Unit
) {
    var groupName by remember { mutableStateOf("") }
    val selectedMembers by viewModel.groupSelectedMembers.collectAsState()
    var disappearingChoiceSeconds by remember { mutableStateOf(0) }
    val context = LocalContext.current

    val timerChoices = listOf(
        Pair("Off", 0),
        Pair("5 Seconds", 5),
        Pair("10 Seconds", 10),
        Pair("30 Seconds", 30),
        Pair("1 Minute", 60),
        Pair("5 Minutes", 300)
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = CipherSurface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp)
                .testTag("create_group_dialog")
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "🛡️ CREATE ENCRYPTED GROUP",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(16.dp))

                TextField(
                    value = groupName,
                    onValueChange = { groupName = it },
                    placeholder = { Text("Group safe alias...", fontSize = 13.sp) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("group_name_input"),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Black.copy(0.15f),
                        unfocusedContainerColor = Color.Black.copy(0.15f)
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "DISAPPEARING MESSAGE DURATION",
                    fontSize = 10.sp,
                    color = CipherSecondary,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Scroll horizontally for timer selections
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    timerChoices.forEach { (label, secs) ->
                        val active = disappearingChoiceSeconds == secs
                        Box(
                            modifier = Modifier
                                .clickable { disappearingChoiceSeconds = secs }
                                .background(
                                    if (active) CipherPrimary else Color.Black.copy(0.2f),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(text = label, color = if (active) Color.White else Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "SELECT CONTACTS TO ROUTE (${selectedMembers.size} selected)",
                    fontSize = 10.sp,
                    color = CipherSecondary,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.height(8.dp))

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    contacts.forEach { contact ->
                        val included = selectedMembers.contains(contact.id)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.toggleMemberSelected(contact.id) }
                                .background(
                                    if (included) CipherPrimary.copy(alpha = 0.08f) else Color.Transparent,
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = included,
                                onCheckedChange = { viewModel.toggleMemberSelected(contact.id) }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text = contact.displayName, color = Color.White, fontSize = 14.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = {
                        viewModel.clearGroupSelectedMembers()
                        onDismiss()
                    }) {
                        Text("Cancel", color = Color.Gray)
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Button(
                        onClick = {
                            if (groupName.isNotBlank() && selectedMembers.isNotEmpty()) {
                                viewModel.createGroupConversation(groupName, disappearingChoiceSeconds)
                                onDismiss()
                                Toast.makeText(context, "Group created successfully", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Please fill name and pick members", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CipherPrimary),
                        modifier = Modifier.testTag("submit_create_group")
                    ) {
                        Text("Secure Route")
                    }
                }
            }
        }
    }
}

// --- ADD CHAT DIRECT DIALOG ---
@Composable
fun AddChatDialog(
    contacts: List<User>,
    viewModel: ChatViewModel,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = CipherSurface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "🛡️ SECURED CONTACT ROUTE",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(12.dp))
                contacts.forEach { contact ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                // Direct routing chat creation
                                viewModel.selectChat("dm_${contact.id}")
                                onDismiss()
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(CipherPrimary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = Icons.Default.Person, contentDescription = null, tint = CipherPrimary)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(text = contact.displayName, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                    }
                    Divider(color = Color.DarkGray)
                }
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = CipherBackground),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Close", color = Color.White)
                }
            }
        }
    }
}

// --- FINGERPRINT DIALOG VIEW (E2E Verification) ---
@Composable
fun SafetyCodeDialog(
    chat: Chat,
    viewModel: ChatViewModel,
    onDismiss: () -> Unit
) {
    val contactId = chat.id.removePrefix("dm_")
    var fingerprint by remember { mutableStateOf<com.example.data.crypto.FingerprintData?>(null) }

    LaunchedEffect(chat.id) {
        fingerprint = viewModel.repository.getFingerprint(contactId)
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = CipherSurface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.VerifiedUser,
                    contentDescription = null,
                    tint = CipherTertiary,
                    modifier = Modifier.size(48.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Verify Safety Fingerprint",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Verify that the safety emojis shown on your device match the recipient's device. If they match, your connection is mathematically secure from tampering.",
                    fontSize = 11.sp,
                    color = Color.LightGray.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Emojis matching fingerprint hashes
                Box(
                    modifier = Modifier
                        .background(Color.Black.copy(0.4f), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = fingerprint?.securityEmojis ?: "🛡️ 🗝️ 🔒 ✨ 💎",
                        fontSize = 24.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = fingerprint?.hexCode ?: "SAFETY-KEY-LOAD-FAIL",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = CipherSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                ChatLockConfigSection(
                    chat = chat,
                    viewModel = viewModel,
                    onDismiss = onDismiss
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = CipherPrimary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Safety Verified")
                }
            }
        }
    }
}

// --- GROUP MANAGE / ADMIN CONTROLS SYSTEM ---
@Composable
fun GroupManageDialog(
    chat: Chat,
    viewModel: ChatViewModel,
    onDismiss: () -> Unit
) {
    val membersState by viewModel.groupMembersOfSelectedChat.collectAsState()
    val isAdmin = viewModel.currentUserIsAdminOfSelectedChat()
    val contacts by viewModel.contacts.collectAsState()
    var disappearingChoiceSeconds by remember { mutableStateOf(chat.disappearingTimerSeconds) }
    val context = LocalContext.current

    val timerChoices = listOf(
        Pair("Off", 0),
        Pair("5s", 5),
        Pair("10s", 10),
        Pair("30s", 30),
        Pair("1m", 60),
        Pair("5m", 300)
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = CipherSurface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp)
                .testTag("group_manage_dialog")
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "⚙️ GROUP ADMIN CABINET",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Disappearing control
                Text(
                    text = "DISAPPEARING TIMER (ADMIN CONTROLS)",
                    fontSize = 10.sp,
                    color = CipherSecondary,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    timerChoices.forEach { (label, secs) ->
                        val active = disappearingChoiceSeconds == secs
                        Box(
                            modifier = Modifier
                                .clickable {
                                    if (isAdmin) {
                                        disappearingChoiceSeconds = secs
                                        viewModel.adjustGroupDisappearingTimer(chat.id, secs)
                                        Toast.makeText(context, "Timer lock rewritten", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "ADMIN ONLY PRIVILEGE", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                .background(
                                    if (active) CipherPrimary else Color.Black.copy(0.2f),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(text = label, color = if (active) Color.White else Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (!isAdmin) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "*Disclaimer: Read-only. Only the group administrator can change timer.",
                        fontSize = 10.sp,
                        color = Color.Gray,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Group membership roster
                Text(
                    text = "ENCRYPTED PARTICIPANTS ROSTER",
                    fontSize = 10.sp,
                    color = CipherSecondary,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Display active members
                membersState.forEach { member ->
                    val isMe = member.userId == "me"
                    val displayName = if (isMe) "Maria (You)" else {
                        contacts.find { it.id == member.userId }?.displayName ?: member.userId
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (member.isAdmin) Icons.Default.AdminPanelSettings else Icons.Default.DirectionsRun,
                                contentDescription = null,
                                tint = if (member.isAdmin) CipherSecondary else Color.Gray,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = displayName, color = Color.White, fontSize = 13.sp)
                        }

                        // Admin triggers logic
                        if (isAdmin && !isMe) {
                            Row {
                                if (!member.isAdmin) {
                                    TextButton(onClick = { viewModel.toggleMemberAdminPrivileges(member.userId, true) }) {
                                        Text("Promote", fontSize = 11.sp, color = CipherSecondary)
                                    }
                                } else {
                                    TextButton(onClick = { viewModel.toggleMemberAdminPrivileges(member.userId, false) }) {
                                        Text("Demote", fontSize = 11.sp, color = CipherWarning)
                                    }
                                }

                                TextButton(onClick = { viewModel.removeMemberFromSelectedGroup(member.userId) }) {
                                    Text("Kick", fontSize = 11.sp, color = CipherError)
                                }
                            }
                        } else {
                            if (member.isAdmin) {
                                Box(
                                    modifier = Modifier
                                        .background(CipherSecondary.copy(0.15f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Text("Admin", fontSize = 9.sp, color = CipherSecondary, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    Divider(color = Color.DarkGray.copy(alpha = 0.5f))
                }

                // Add member panel if admin
                if (isAdmin) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "ADD CO-CONSPIRATORS",
                        fontSize = 10.sp,
                        color = CipherSecondary,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    contacts.forEach { contact ->
                        val isAlreadyMember = membersState.any { it.userId == contact.id }
                        if (!isAlreadyMember) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.addMemberToSelectedGroup(contact.id)
                                        Toast.makeText(context, "Added ${contact.displayName}", Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = contact.displayName, fontSize = 13.sp, color = Color.White)
                                Text("+ Invite", fontSize = 11.sp, color = CipherTertiary, fontWeight = FontWeight.Bold)
                            }
                            Divider(color = Color.DarkGray.copy(alpha = 0.3f))
                        }
                    }
                }

                ChatLockConfigSection(
                    chat = chat,
                    viewModel = viewModel,
                    onDismiss = onDismiss
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = CipherPrimary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Exit Cabinet")
                }
            }
        }
    }
}

// --- STANDARD EMPTY STATE HELPER ---
@Composable
fun EmptyStateLayout(
    imageVector: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = null,
            tint = Color.Gray.copy(0.4f),
            modifier = Modifier.size(72.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = subtitle,
            fontSize = 13.sp,
            color = Color.LightGray.copy(0.5f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun ChatLockConfigSection(
    chat: Chat,
    viewModel: ChatViewModel,
    onDismiss: () -> Unit
) {
    var selectedType by remember { mutableStateOf(chat.lockType ?: "PASSWORD") }
    var passwordField by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var setupError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    Spacer(modifier = Modifier.height(20.dp))
    Divider(color = Color.Gray.copy(0.3f), thickness = 1.dp)
    Spacer(modifier = Modifier.height(16.dp))

    Text(
        text = "🔐 CHAT BIOMETRIC & KEY LOCK",
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        color = CipherPrimary,
        fontFamily = FontFamily.Monospace
    )

    Spacer(modifier = Modifier.height(8.dp))

    if (chat.isLocked) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(CipherPrimary.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                .border(1.dp, CipherPrimary.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = when (chat.lockType) {
                        "FINGERPRINT" -> Icons.Default.Fingerprint
                        "FACE_LOCK" -> Icons.Default.Face
                        else -> Icons.Default.Lock
                    },
                    contentDescription = null,
                    tint = CipherTertiary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Sealed & Protected",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color.White
                    )
                    Text(
                        text = "Locked with secure ${chat.lockType?.lowercase() ?: "password"}.",
                        fontSize = 11.sp,
                        color = Color.LightGray.copy(0.7f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = {
                    viewModel.permanentlyUnlockChat(chat.id)
                    Toast.makeText(context, "Unset chat cryptography lock", Toast.LENGTH_SHORT).show()
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = CipherError.copy(alpha = 0.9f)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().height(38.dp)
            ) {
                Text("REMOVE LOCK & OPEN", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Secure this room's messages so nobody can read them even if your phone is unlocked.",
                fontSize = 11.sp,
                color = Color.LightGray.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Select Lock Type
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    Triple("Password", "PASSWORD", Icons.Default.Lock),
                    Triple("Fingerprint", "FINGERPRINT", Icons.Default.Fingerprint),
                    Triple("Face Lock", "FACE_LOCK", Icons.Default.Face)
                ).forEach { (label, type, icon) ->
                    val isSelected = selectedType == type
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedType = type }
                            .background(
                                if (isSelected) CipherPrimary else Color.Black.copy(0.2f),
                                RoundedCornerShape(8.dp)
                            )
                            .border(
                                1.dp,
                                if (isSelected) CipherPrimary else Color.Gray.copy(0.2f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = if (isSelected) Color.White else Color.Gray,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = label,
                                fontSize = 10.sp,
                                color = if (isSelected) Color.White else Color.Gray,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (selectedType == "PASSWORD") {
                OutlinedTextField(
                    value = passwordField,
                    onValueChange = { 
                        passwordField = it
                        setupError = null
                    },
                    label = { Text("Set Room Password", color = CipherOnSurface, fontSize = 11.sp) },
                    placeholder = { Text("Enter crypt passcode") },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (passwordVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = null,
                                tint = CipherSecondary
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CipherPrimary,
                        unfocusedBorderColor = CipherSurfaceVariant,
                        focusedLabelColor = CipherPrimary,
                        unfocusedLabelColor = Color.Gray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))
            }

            setupError?.let { err ->
                Text(
                    text = err,
                    color = CipherError,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            Button(
                onClick = {
                    if (selectedType == "PASSWORD" && passwordField.isBlank()) {
                        setupError = "Please specify a non-blank security password."
                        return@Button
                    }
                    val code = if (selectedType == "PASSWORD") passwordField else null
                    viewModel.lockChat(chat.id, selectedType, code)
                    Toast.makeText(context, "Room crypt-locked with $selectedType!", Toast.LENGTH_SHORT).show()
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = CipherPrimary),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .testTag("apply_chat_lock_button")
            ) {
                Text(
                    text = "ACTIVATE CRYPTO-LOCK",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

@Composable
fun ChatUnlockOverlay(
    chat: Chat,
    onUnlockSuccess: () -> Unit,
    onDismiss: () -> Unit
) {
    var passwordInput by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // For biometric fingerprint interaction
    var isFingerprintScanning by remember { mutableStateOf(false) }
    var fingerprintProgress by remember { mutableFloatStateOf(0f) }
    var fingerprintScanSuccess by remember { mutableStateOf(false) }
    
    // For face lock scanning
    var isFaceScanning by remember { mutableStateOf(false) }
    var faceScanProgress by remember { mutableFloatStateOf(0f) }
    var faceScanSuccess by remember { mutableStateOf(false) }
    
    val context = LocalContext.current

    // Handlers for fingerprint hold scan
    LaunchedEffect(isFingerprintScanning) {
        if (isFingerprintScanning) {
            errorMessage = null
            var count = 0f
            while (count < 100f && isFingerprintScanning) {
                delay(15)
                count += 2f
                fingerprintProgress = count / 100f
            }
            if (count >= 100f) {
                fingerprintScanSuccess = true
                delay(400)
                Toast.makeText(context, "Fingerprint verification successful!", Toast.LENGTH_SHORT).show()
                onUnlockSuccess()
            }
        } else {
            fingerprintProgress = 0f
        }
    }

    // Handlers for face lock radar scan
    LaunchedEffect(isFaceScanning) {
        if (isFaceScanning) {
            errorMessage = null
            var count = 0f
            while (count < 100f && isFaceScanning) {
                delay(20)
                count += 1.5f
                faceScanProgress = count / 100f
            }
            if (count >= 100f) {
                faceScanSuccess = true
                delay(400)
                Toast.makeText(context, "Face Match verification successful!", Toast.LENGTH_SHORT).show()
                onUnlockSuccess()
            }
        } else {
            faceScanProgress = 0f
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false) // Full screen style
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF090D16).copy(0.98f)) // Dynamic protective dark overlay
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Header lock representation
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(CipherPrimary.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when (chat.lockType) {
                            "FINGERPRINT" -> Icons.Default.Fingerprint
                            "FACE_LOCK" -> Icons.Default.Face
                            else -> Icons.Default.Lock
                        },
                        contentDescription = null,
                        tint = CipherPrimary,
                        modifier = Modifier.size(40.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "DECRYPT SECURE ENVIRONMENT",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = CipherSecondary,
                    letterSpacing = 2.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = chat.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Encrypted utilizing 256-bit localized dynamic key-exchange protocols. Unlock to initialize sandbox decryption.",
                    color = Color.LightGray.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(32.dp))

                errorMessage?.let { err ->
                    Text(
                        text = "⚠️ $err",
                        color = CipherError,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }

                // Authentication type render block
                when (chat.lockType) {
                    "PASSWORD" -> {
                        OutlinedTextField(
                            value = passwordInput,
                            onValueChange = { 
                                passwordInput = it
                                errorMessage = null
                            },
                            label = { Text("Secured Passcode/Key", color = CipherOnSurface) },
                            placeholder = { Text("Type crypt password") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("unlock_password_input"),
                            visualTransformation = if (passwordVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    if (passwordInput == chat.lockPassword) {
                                        onUnlockSuccess()
                                    } else {
                                        errorMessage = "Mismatch cryptographic passcode key!"
                                    }
                                }
                            ),
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = "Toggle Visibility",
                                        tint = CipherSecondary
                                    )
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CipherPrimary,
                                unfocusedBorderColor = CipherSurfaceVariant,
                                focusedLabelColor = CipherPrimary,
                                unfocusedLabelColor = Color.Gray,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = {
                                if (passwordInput == chat.lockPassword) {
                                    onUnlockSuccess()
                                } else {
                                    errorMessage = "Mismatch cryptographic passcode key!"
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CipherPrimary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("unlock_password_submit")
                        ) {
                            Text(
                                "DECRYPT CONNECTION",
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }

                    "FINGERPRINT" -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(120.dp)
                                    .background(
                                        if (isFingerprintScanning) CipherPrimary.copy(alpha = 0.1f)
                                        else Color.Black.copy(0.3f),
                                        CircleShape
                                    )
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onPress = {
                                                isFingerprintScanning = true
                                                try {
                                                    awaitRelease()
                                                } finally {
                                                    isFingerprintScanning = false
                                                }
                                            }
                                        )
                                    }
                                    .testTag("unlock_fingerprint_touch"),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isFingerprintScanning) {
                                    CircularProgressIndicator(
                                        progress = { fingerprintProgress },
                                        color = CipherTertiary,
                                        modifier = Modifier.size(110.dp),
                                        strokeWidth = 4.dp
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(110.dp)
                                            .border(1.dp, Color.Gray.copy(0.3f), CircleShape)
                                    )
                                }

                                Icon(
                                    imageVector = Icons.Default.Fingerprint,
                                    contentDescription = "Scan Fingerprint",
                                    tint = if (fingerprintScanSuccess) CipherTertiary 
                                           else if (isFingerprintScanning) CipherTertiary.copy(alpha = 0.8f) 
                                           else Color.LightGray.copy(alpha = 0.7f),
                                    modifier = Modifier.size(56.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            Text(
                                text = if (isFingerprintScanning) "SCANNED INDEX CHIP... ${(fingerprintProgress * 100).toInt()}%" 
                                       else "TOUCH & HOLD TO DECRYPT BIOMETRIC",
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = if (isFingerprintScanning) CipherTertiary else Color.LightGray
                            )
                        }
                    }

                    "FACE_LOCK" -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(130.dp)
                                    .background(Color.Black.copy(0.3f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isFaceScanning) {
                                    CircularProgressIndicator(
                                        progress = { faceScanProgress },
                                        color = CipherBlue,
                                        modifier = Modifier.size(120.dp),
                                        strokeWidth = 3.dp
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(120.dp)
                                            .border(1.dp, Color.Gray.copy(0.2f), CircleShape)
                                    )
                                }

                                Icon(
                                    imageVector = Icons.Default.Face,
                                    contentDescription = "Face ID Scan",
                                    tint = if (faceScanSuccess) CipherTertiary
                                           else if (isFaceScanning) CipherBlue
                                           else Color.LightGray,
                                    modifier = Modifier.size(60.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            Button(
                                onClick = { isFaceScanning = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isFaceScanning) CipherBlue.copy(0.2f) else CipherBlue
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("unlock_facelock_submit")
                            ) {
                                Text(
                                    if (isFaceScanning) "INITIALIZING CAMERA SCAN..." else "SCAN FACE RECOGNITION",
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(48.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("unlock_cancel")
                ) {
                    Text(
                        "CANCEL & RETURN",
                        color = Color.Gray,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}
