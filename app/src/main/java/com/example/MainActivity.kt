package com.example

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import android.content.Context
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.compose.AsyncImagePainter
import coil.ImageLoader
import coil.decode.ImageDecoderDecoder
import coil.decode.GifDecoder

// Custom modern color scheme constants ("Sophisticated Dark" theme)
val CosmicBackground = Color(0xFF1C1B1F) // Deep obsidian black (#1C1B1F)
val CosmicCard = Color(0xFF2B2930)       // Dark grayish-purple (#2B2930)
val CosmicCardLight = Color(0xFF49454F)  // Gray-purple highlight (#49454F)
val NeonBlue = Color(0xFFD0BCFF)         // Light Lavender Purple (#D0BCFF)
val NeonCyan = Color(0xFFEADDFF)         // Pastel lavender (#EADDFF)
val GlowGold = Color(0xFFFFB74D)         // Soft warm Amber/Orange
val SoftWhite = Color(0xFFE6E1E5)        // Pale gray-white (#E6E1E5)
val MutedGray = Color(0xFFCAC4D0)        // Muted gray-lavender (#CAC4D0)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme(darkTheme = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = CosmicBackground
                ) {
                    val viewModel: MainViewModel = viewModel()
                    IrcRadioApp(viewModel)
                }
            }
        }
    }
}

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val changeLog: String
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun IrcRadioApp(viewModel: MainViewModel) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val connectionState by viewModel.ircClient.connectionState.collectAsStateWithLifecycle()
    val messages by viewModel.ircClient.messages.collectAsStateWithLifecycle()
    val currentChannel by viewModel.ircClient.currentChannel.collectAsStateWithLifecycle()
    val joinedChannels by viewModel.ircClient.joinedChannels.collectAsStateWithLifecycle()
    val channelUsers by viewModel.ircClient.channelUsers.collectAsStateWithLifecycle()
    val currentNick by viewModel.ircClient.currentNick.collectAsStateWithLifecycle()
    val quitMessage by viewModel.ircClient.quitMessage.collectAsStateWithLifecycle()
    val loginPassword by viewModel.ircClient.loginPassword.collectAsStateWithLifecycle()

    val playbackState by viewModel.radioPlayer.playbackState.collectAsStateWithLifecycle()
    val currentStation by viewModel.radioPlayer.currentStation.collectAsStateWithLifecycle()
    val volume by viewModel.radioPlayer.volume.collectAsStateWithLifecycle()

    var nicknameInput by remember { mutableStateOf(currentNick) }
    var passwordInput by remember { mutableStateOf(loginPassword) }
    var channelInput by remember { mutableStateOf("#thaiirc") }
    var chatMessageInput by remember { mutableStateOf("") }

    LaunchedEffect(currentNick) {
        if (nicknameInput.isEmpty() || nicknameInput.startsWith("Thai")) {
            nicknameInput = currentNick
        }
    }

    LaunchedEffect(loginPassword) {
        passwordInput = loginPassword
    }

    val autoJoinChannels by viewModel.ircClient.autoJoinChannels.collectAsStateWithLifecycle()
    val mentionNotificationEnabled by viewModel.ircClient.mentionNotificationEnabled.collectAsStateWithLifecycle()

    LaunchedEffect(autoJoinChannels) {
        val firstAutoJoin = autoJoinChannels.split(",").firstOrNull { it.trim().isNotEmpty() }?.trim()
        if (firstAutoJoin != null) {
            channelInput = if (firstAutoJoin.startsWith("#")) firstAutoJoin else "#$firstAutoJoin"
        }
    }

    var showUserListDialog by remember { mutableStateOf(false) }
    var showJoinChannelDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    
    var currentTab by remember { mutableStateOf(AppTab.CHAT) }

    // File Upload States
    var isUploading by remember { mutableStateOf(false) }
    var uploadProgress by remember { mutableStateOf(0f) }
    var uploadError by remember { mutableStateOf<String?>(null) }
    var uploadedUrl by remember { mutableStateOf<String?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                isUploading = true
                uploadProgress = 0f
                uploadError = null
                uploadedUrl = null
                
                val resultUrl = withContext(Dispatchers.IO) {
                    try {
                        FileUploader.uploadFile(context, uri) { progress ->
                            uploadProgress = progress
                        }
                    } catch (e: Exception) {
                        null
                    }
                }
                
                isUploading = false
                if (resultUrl != null) {
                    uploadedUrl = resultUrl
                    // Copy to clipboard
                    try {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("Uploaded File Link", resultUrl)
                        clipboard.setPrimaryClip(clip)
                    } catch (e: Exception) {
                        // ignore clipboard errors
                    }
                    
                    // Add to chat input
                    chatMessageInput = if (chatMessageInput.isEmpty()) {
                        resultUrl
                    } else {
                        "$chatMessageInput $resultUrl"
                    }
                    
                    Toast.makeText(context, "อัปโหลดเสร็จแล้ว! ลิงก์ถูกนำไปใส่ในช่องแชทและคัดลอกลงคลิปบอร์ดแล้ว", Toast.LENGTH_LONG).show()
                } else {
                    uploadError = "อัปโหลดล้มเหลว กรุณาลองใหม่อีกครั้ง"
                    Toast.makeText(context, "อัปโหลดล้มเหลว", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Auto Update States
    var showUpdateDialog by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val url = URL("https://app.thaiirc.com/update.json")
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val jsonText = connection.inputStream.bufferedReader().use { it.readText() }
                    
                    val versionCodePattern = "\"versionCode\"\\s*:\\s*(\\d+)".toRegex()
                    val versionNamePattern = "\"versionName\"\\s*:\\s*\"([^\"]+)\"".toRegex()
                    val apkUrlPattern = "\"apkUrl\"\\s*:\\s*\"([^\"]+)\"".toRegex()
                    val changeLogPattern = "\"changeLog\"\\s*:\\s*\"([^\"]+)\"".toRegex()
                    
                    val vcMatch = versionCodePattern.find(jsonText)
                    val vnMatch = versionNamePattern.find(jsonText)
                    val apkMatch = apkUrlPattern.find(jsonText)
                    val clMatch = changeLogPattern.find(jsonText)
                    
                    if (vcMatch != null && vnMatch != null && apkMatch != null) {
                        val remoteVersionCode = vcMatch.groupValues[1].toInt()
                        val remoteVersionName = vnMatch.groupValues[1]
                        val remoteApkUrl = apkMatch.groupValues[1]
                        val remoteChangeLog = clMatch?.groupValues[1] ?: ""
                        
                        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                        val currentVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            packageInfo.longVersionCode
                        } else {
                            @Suppress("DEPRECATION")
                            packageInfo.versionCode.toLong()
                        }
                        
                        if (remoteVersionCode > currentVersionCode) {
                            updateInfo = UpdateInfo(
                                versionCode = remoteVersionCode,
                                versionName = remoteVersionName,
                                apkUrl = remoteApkUrl,
                                changeLog = remoteChangeLog
                            )
                            showUpdateDialog = true
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    LaunchedEffect(connectionState) {
        if (connectionState == IrcConnectionState.CONNECTED) {
            currentTab = AppTab.CHAT
        }
    }

    val activeChannelUsers = channelUsers[currentChannel] ?: emptySet()
    val keyboardController = LocalSoftwareKeyboardController.current

    // Listen for error messages to show Toast
    LaunchedEffect(Unit) {
        viewModel.ircClient.errorFlow.collectLatest { errMsg ->
            Toast.makeText(context, errMsg, Toast.LENGTH_SHORT).show()
        }
    }

    // Auto update nick input state when nickname changes in client (e.g. random suffix)
    LaunchedEffect(currentNick) {
        nicknameInput = currentNick
    }

    // Sync foreground service life cycle with active connection or radio playback state
    LaunchedEffect(connectionState, playbackState) {
        val isServiceNeeded = (connectionState == IrcConnectionState.CONNECTED ||
                connectionState == IrcConnectionState.CONNECTING ||
                playbackState == PlaybackState.PLAYING ||
                playbackState == PlaybackState.BUFFERING)

        val intent = Intent(context, ThaiIrcService::class.java)
        if (isServiceNeeded) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            try {
                context.stopService(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ImageFromResource(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.5.dp, NeonBlue, RoundedCornerShape(8.dp))
                        )
                        Column {
                            Text(
                                text = "ThaiIRC",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = SoftWhite
                                )
                            )
                            Text(
                                text = "irc.thaiirc.com:6667",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 10.sp,
                                    color = MutedGray
                                )
                            )
                        }
                    }
                },
                actions = {
                    // Status Badge
                    ConnectionStatusBadge(connectionState)
                    
                    val notifyPermissionLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestPermission()
                    ) { isGranted ->
                        if (isGranted) {
                            viewModel.ircClient.saveSettings(mentionNotificationEnabledVal = true)
                            Toast.makeText(context, "เปิดการแจ้งเตือนสำเร็จ", Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.ircClient.saveSettings(mentionNotificationEnabledVal = false)
                            Toast.makeText(context, "กรุณาอนุญาตการแจ้งเตือนเพื่อรับการแจ้งเตือนการแท็กชื่อ", Toast.LENGTH_LONG).show()
                        }
                    }

                    IconButton(
                        onClick = {
                            val nextVal = !mentionNotificationEnabled
                            if (nextVal && android.os.Build.VERSION.SDK_INT >= 33) {
                                if (androidx.core.content.ContextCompat.checkSelfPermission(
                                        context,
                                        android.Manifest.permission.POST_NOTIFICATIONS
                                    ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                                ) {
                                    notifyPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    viewModel.ircClient.saveSettings(mentionNotificationEnabledVal = true)
                                    Toast.makeText(context, "เปิดการแจ้งเตือนการแท็กชื่อ", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                viewModel.ircClient.saveSettings(mentionNotificationEnabledVal = nextVal)
                                val msgText = if (nextVal) "เปิดการแจ้งเตือนการแท็กชื่อ" else "ปิดการแจ้งเตือนการแท็กชื่อ"
                                Toast.makeText(context, msgText, Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (mentionNotificationEnabled) Icons.Filled.Notifications else Icons.Filled.NotificationsOff,
                            contentDescription = "แจ้งเตือนการแท็กชื่อ",
                            tint = if (mentionNotificationEnabled) NeonBlue else MutedGray
                        )
                    }

                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(Icons.Filled.Settings, contentDescription = "ตั้งค่า", tint = SoftWhite)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CosmicBackground,
                    titleContentColor = SoftWhite
                )
            )
        },
        bottomBar = {
            val isKeyboardVisible = WindowInsets.isImeVisible
            if (!isKeyboardVisible && connectionState != IrcConnectionState.DISCONNECTED && connectionState != IrcConnectionState.ERROR) {
                NavigationBar(
                    containerColor = CosmicCard,
                    contentColor = SoftWhite
                ) {
                    NavigationBarItem(
                        selected = currentTab == AppTab.CHAT,
                        onClick = { currentTab = AppTab.CHAT },
                        icon = { Icon(Icons.Filled.Chat, contentDescription = "ห้องแชท") },
                        label = { Text("ห้องแชท") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = CosmicBackground,
                            selectedTextColor = NeonBlue,
                            indicatorColor = NeonBlue,
                            unselectedIconColor = MutedGray,
                            unselectedTextColor = MutedGray
                        )
                    )
                    NavigationBarItem(
                        selected = false,
                        onClick = { filePickerLauncher.launch("*/*") },
                        icon = { Icon(Icons.Filled.CloudUpload, contentDescription = "ส่งไฟล์/รูป", tint = NeonBlue) },
                        label = { Text("ส่งไฟล์/รูป") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = CosmicBackground,
                            selectedTextColor = NeonBlue,
                            indicatorColor = NeonBlue,
                            unselectedIconColor = NeonBlue,
                            unselectedTextColor = SoftWhite
                        )
                    )
                    NavigationBarItem(
                        selected = currentTab == AppTab.RADIO,
                        onClick = { currentTab = AppTab.RADIO },
                        icon = { Icon(Icons.Filled.Radio, contentDescription = "ฟังเพลง/วิทยุ") },
                        label = { Text("วิทยุออนไลน์") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = CosmicBackground,
                            selectedTextColor = NeonBlue,
                            indicatorColor = NeonBlue,
                            unselectedIconColor = MutedGray,
                            unselectedTextColor = MutedGray
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .background(CosmicBackground)
        ) {
            if (connectionState == IrcConnectionState.DISCONNECTED || connectionState == IrcConnectionState.ERROR) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // SECTION 1: Radio Player Widget (Visible on setup screen)
                    RadioPlayerCard(
                        currentStation = currentStation,
                        playbackState = playbackState,
                        volume = volume,
                        stations = viewModel.radioPlayer.stations,
                        onPlayPause = { viewModel.radioPlayer.togglePlayPause() },
                        onStop = { viewModel.radioPlayer.stop() },
                        onStationSelect = { viewModel.radioPlayer.selectStation(it) },
                        onVolumeChange = { viewModel.radioPlayer.setVolume(it) }
                    )

                    // Connection Configuration Panel
                    ConnectionSetupPanel(
                        nickname = nicknameInput,
                        onNicknameChange = { nicknameInput = it },
                        password = passwordInput,
                        onPasswordChange = { passwordInput = it },
                        defaultChannel = channelInput,
                        onChannelChange = { channelInput = it },
                        isConnecting = connectionState == IrcConnectionState.CONNECTING,
                        onConnect = {
                            keyboardController?.hide()
                            viewModel.ircClient.updateCurrentChannel(channelInput)
                            viewModel.ircClient.connect(nicknameInput, password = passwordInput)
                        }
                    )
                }
            } else {
                // When connected, switch views based on selected tab
                if (currentTab == AppTab.CHAT) {
                    // Connected Chat Interface
                    ChatInterfacePanel(
                        modifier = Modifier.weight(1f),
                        currentChannel = currentChannel,
                        joinedChannels = joinedChannels,
                        messages = messages.filter { it.target == null || it.target == currentChannel },
                        activeUsersCount = activeChannelUsers.size,
                        chatInput = chatMessageInput,
                        currentNick = currentNick,
                        onChatInputChange = { chatMessageInput = it },
                        onSendMessage = {
                            viewModel.ircClient.sendChatMessage(currentChannel, chatMessageInput)
                            chatMessageInput = ""
                        },
                        onChannelSelect = { viewModel.ircClient.updateCurrentChannel(it) },
                        onLeaveChannel = { viewModel.ircClient.sendRaw("PART $it") },
                        onJoinChannelClick = { showJoinChannelDialog = true },
                        onShowUserList = { showUserListDialog = true },
                        onDisconnect = { viewModel.ircClient.disconnect() }
                    )
                } else {
                    // Separate page/tab for the Radio Player
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "วิทยุออนไลน์",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = SoftWhite
                            ),
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        RadioPlayerCard(
                            currentStation = currentStation,
                            playbackState = playbackState,
                            volume = volume,
                            stations = viewModel.radioPlayer.stations,
                            onPlayPause = { viewModel.radioPlayer.togglePlayPause() },
                            onStop = { viewModel.radioPlayer.stop() },
                            onStationSelect = { viewModel.radioPlayer.selectStation(it) },
                            onVolumeChange = { viewModel.radioPlayer.setVolume(it) }
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = "ควบคุมการเล่นวิทยุออนไลน์ได้ที่นี่ เพลงจะเล่นอย่างต่อเนื่องในพื้นหลังแม้ในขณะที่คุณสลับกลับไปที่แชทหรือปิดหน้าจอ",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MutedGray,
                                textAlign = TextAlign.Center
                            ),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            }
        }
    }

    // Dialog: User List
    if (showUserListDialog) {
        UserListDialog(
            channel = currentChannel,
            users = activeChannelUsers,
            onDismiss = { showUserListDialog = false },
            onUserClick = { user ->
                var clean = user.trim()
                while (clean.isNotEmpty() && (clean.startsWith("@") || clean.startsWith("+") || clean.startsWith("%") || clean.startsWith("~") || clean.startsWith("&"))) {
                    clean = clean.substring(1)
                }
                val tag = "@$clean "
                if (chatMessageInput.isEmpty()) {
                    chatMessageInput = tag
                } else {
                    chatMessageInput = if (chatMessageInput.endsWith(" ")) {
                        chatMessageInput + tag
                    } else {
                        chatMessageInput + " " + tag
                    }
                }
                showUserListDialog = false
            }
        )
    }

    // Dialog: Join Channel
    if (showJoinChannelDialog) {
        JoinChannelDialog(
            onJoin = { chan ->
                viewModel.ircClient.sendRaw("JOIN $chan")
                showJoinChannelDialog = false
            },
            onDismiss = { showJoinChannelDialog = false }
        )
    }

    // Dialog: App Settings / About
    if (showSettingsDialog) {
        SettingsDialog(
            ircClient = viewModel.ircClient,
            onDismiss = { showSettingsDialog = false }
        )
    }

    // Dialog: App Update Alert
    if (showUpdateDialog && updateInfo != null) {
        AlertDialog(
            onDismissRequest = { showUpdateDialog = false },
            containerColor = CosmicCard,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.SystemUpdate,
                        contentDescription = null,
                        tint = NeonBlue,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "พบเวอร์ชันใหม่ (${updateInfo!!.versionName})",
                        color = SoftWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            },
            text = {
                Column {
                    Text(
                        text = "กรุณาอัปเดตแอปพลิเคชันเป็นเวอร์ชันล่าสุดเพื่อการใช้งานที่ราบรื่นและฟีเจอร์ใหม่ๆ",
                        color = SoftWhite,
                        fontSize = 14.sp
                    )
                    if (updateInfo!!.changeLog.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "มีอะไรใหม่ในเวอร์ชันนี้:",
                            color = NeonBlue,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = updateInfo!!.changeLog,
                            color = MutedGray,
                            fontSize = 13.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showUpdateDialog = false
                        try {
                            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(updateInfo!!.apkUrl))
                            context.startActivity(browserIntent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "ไม่สามารถเปิดเบราว์เซอร์ได้", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonBlue)
                ) {
                    Text("อัปเดตทันที", color = CosmicBackground, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showUpdateDialog = false }
                ) {
                    Text("ภายหลัง", color = MutedGray)
                }
            }
        )
    }

    // Dialog: File Uploading Progress
    if (isUploading) {
        AlertDialog(
            onDismissRequest = {}, // Prevent closing by outside clicks
            containerColor = CosmicCard,
            confirmButton = {},
            title = {
                Text(
                    text = "กำลังอัปโหลดรูปภาพ/ไฟล์",
                    color = SoftWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        progress = uploadProgress,
                        color = NeonBlue,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "อัปโหลดแล้ว ${(uploadProgress * 100).toInt()}%",
                        color = NeonBlue,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "กรุณารอสักครู่...",
                        color = MutedGray,
                        fontSize = 13.sp
                    )
                }
            }
        )
    }
}

// Helper to safely load the custom app logo resource
@Composable
fun ImageFromResource(modifier: Modifier = Modifier) {
    // The logo generated is saved as app_logo_1783469563451
    val context = LocalContext.current
    val resourceId = remember {
        context.resources.getIdentifier("app_logo_1783469563451", "drawable", context.packageName)
    }
    
    if (resourceId != 0) {
        androidx.compose.foundation.Image(
            painter = painterResource(id = resourceId),
            contentDescription = "App Logo",
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    } else {
        // Fallback default icon
        Box(
            modifier = modifier.background(NeonBlue),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Chat, contentDescription = "Logo", tint = Color.White)
        }
    }
}

@Composable
fun ConnectionStatusBadge(state: IrcConnectionState) {
    val (text, color) = when (state) {
        IrcConnectionState.DISCONNECTED -> "ออฟไลน์" to Color.Gray
        IrcConnectionState.CONNECTING -> "กำลังเชื่อมต่อ..." to GlowGold
        IrcConnectionState.CONNECTED -> "ออนไลน์" to Color.Green
        IrcConnectionState.ERROR -> "ข้อผิดพลาด" to Color.Red
    }

    // Infinite pulsing alpha transition for connecting state
    val transition = rememberInfiniteTransition(label = "pulse")
    val alpha by if (state == IrcConnectionState.CONNECTING) {
        transition.animateFloat(
            initialValue = 0.3f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(800, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "alpha"
        )
    } else {
        remember { mutableStateOf(1.0f) }
    }

    Row(
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
            .border(1.dp, color.copy(alpha = alpha), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.Bold,
                color = color,
                fontSize = 11.sp
            )
        )
    }
}

@Composable
fun RadioPlayerCard(
    currentStation: RadioStation?,
    playbackState: PlaybackState,
    volume: Float,
    stations: List<RadioStation>,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onStationSelect: (RadioStation) -> Unit,
    onVolumeChange: (Float) -> Unit
) {
    val isPlaying = playbackState == PlaybackState.PLAYING
    val isBuffering = playbackState == PlaybackState.BUFFERING

    // Vinyl spinning rotation angle
    val infiniteTransition = rememberInfiniteTransition(label = "vinyl")
    val rotation by if (isPlaying) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(4000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "rotation"
        )
    } else {
        remember { mutableStateOf(0f) }
    }

    // "Sophisticated Dark" Player Colors: Background #EADDFF, Text #21005D
    val playerBgColor = Color(0xFFEADDFF)
    val playerTextColor = Color(0xFF21005D)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        colors = CardDefaults.cardColors(containerColor = playerBgColor),
        shape = RoundedCornerShape(24.dp), // M3 3xl/rounded-3xl
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Row 1: Active Station Info & Equalizer
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Spinning Vinyl with the Theme Colors
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(playerTextColor) // Deep dark purple body
                        .rotate(rotation)
                        .border(1.5.dp, Color.White.copy(alpha = 0.6f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    // Outer grooves
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                    )
                    // Inner label with music icon
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFD0BCFF)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.MusicNote,
                            contentDescription = null,
                            tint = playerTextColor,
                            modifier = Modifier.size(11.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Station details
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Now Playing",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = playerTextColor.copy(alpha = 0.7f),
                            letterSpacing = 1.sp
                        )
                    )
                    Text(
                        text = currentStation?.name ?: "ยังไม่ได้เลือกสถานี",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = playerTextColor
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Audio Status Equalizer Bars / Buffering indicator
                if (isPlaying) {
                    EqualizerWaveAnimation(color = playerTextColor)
                } else if (isBuffering) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = playerTextColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Row 2: Selectable Station Chips (Port 8000 & 8002)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                stations.forEach { station ->
                    val isSelected = currentStation?.id == station.id
                    // Highlight selected with #D0BCFF, unselected with White/40
                    val chipBg = if (isSelected) Color(0xFFD0BCFF) else Color.White.copy(alpha = 0.4f)
                    val borderAlpha = if (isSelected) 0.2f else 0.1f

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .clip(RoundedCornerShape(12.dp)) // rounded-xl
                            .background(chipBg)
                            .border(1.dp, playerTextColor.copy(alpha = borderAlpha), RoundedCornerShape(12.dp))
                            .clickable { onStationSelect(station) },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                if (station.id == "icecast") Icons.Filled.Headphones else Icons.Filled.Podcasts,
                                contentDescription = null,
                                tint = playerTextColor,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = if (station.id == "icecast") "MQuest" else "Live Radio",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = playerTextColor
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Row 3: Player Controls & Volume Slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Play/Pause Button (#21005D, icon White)
                IconButton(
                    onClick = onPlayPause,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(playerTextColor)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = "เล่น/หยุด",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Stop Button (#21005D 20% opacity, icon #21005D)
                IconButton(
                    onClick = onStop,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(playerTextColor.copy(alpha = 0.2f))
                ) {
                    Icon(
                        imageVector = Icons.Filled.Stop,
                        contentDescription = "หยุดสนิท",
                        tint = playerTextColor,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Volume slider
                Icon(
                    imageVector = if (volume == 0f) Icons.Filled.VolumeMute else Icons.Filled.VolumeUp,
                    contentDescription = "ระดับเสียง",
                    tint = playerTextColor,
                    modifier = Modifier.size(18.dp)
                )
                
                Slider(
                    value = volume,
                    onValueChange = onVolumeChange,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 4.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = playerTextColor,
                        activeTrackColor = playerTextColor,
                        inactiveTrackColor = playerTextColor.copy(alpha = 0.2f)
                    )
                )
            }
        }
    }
}

@Composable
fun EqualizerWaveAnimation(color: Color = NeonCyan) {
    val infiniteTransition = rememberInfiniteTransition(label = "eq")
    
    val height1 by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(350, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "h1"
    )
    val height2 by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(450, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "h2"
    )
    val height3 by infiniteTransition.animateFloat(
        initialValue = 0.1f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = FastOutLinearInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "h3"
    )
    val height4 by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "h4"
    )

    Row(
        modifier = Modifier
            .height(24.dp)
            .width(22.dp)
            .padding(horizontal = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        val bars = listOf(height1, height2, height3, height4)
        bars.forEach { height ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(height)
                    .clip(RoundedCornerShape(1.dp))
                    .background(color)
            )
        }
    }
}

@Composable
fun ConnectionSetupPanel(
    nickname: String,
    onNicknameChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    defaultChannel: String,
    onChannelChange: (String) -> Unit,
    isConnecting: Boolean,
    onConnect: () -> Unit
) {
    var passwordVisible by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        colors = CardDefaults.cardColors(containerColor = CosmicCard),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "ยินดีต้อนรับสู่แชทสยามน่ารัก ThaiIRC.com",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = SoftWhite,
                    textAlign = TextAlign.Center
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = "เข้าแชทคุยสด irc.thaiirc.com ขอและฟังเพลง 24 ชม.",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MutedGray,
                    textAlign = TextAlign.Center
                ),
                modifier = Modifier.fillMaxWidth()
            )

            // Nickname Input
            OutlinedTextField(
                value = nickname,
                onValueChange = onNicknameChange,
                label = { Text("ชื่อเล่น (Nickname)") },
                placeholder = { Text("เช่น Thai1234") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null, tint = NeonBlue) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonBlue,
                    unfocusedBorderColor = CosmicCardLight,
                    focusedLabelColor = NeonBlue,
                    unfocusedLabelColor = MutedGray,
                    focusedTextColor = SoftWhite,
                    unfocusedTextColor = SoftWhite,
                    focusedContainerColor = CosmicBackground,
                    unfocusedContainerColor = CosmicBackground
                ),
                modifier = Modifier.fillMaxWidth()
            )

            // Password Input (ใต้ช่อง Nickname ตามที่ผู้ใช้ต้องการ)
            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChange,
                label = { Text("รหัสผ่าน (Password - ถ้ามี)") },
                placeholder = { Text("ใส่รหัสผ่านเพื่อลงทะเบียน/เข้าระบบ SASL") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null, tint = NeonBlue) },
                trailingIcon = {
                    val image = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(image, contentDescription = null, tint = NeonBlue)
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonBlue,
                    unfocusedBorderColor = CosmicCardLight,
                    focusedLabelColor = NeonBlue,
                    unfocusedLabelColor = MutedGray,
                    focusedTextColor = SoftWhite,
                    unfocusedTextColor = SoftWhite,
                    focusedContainerColor = CosmicBackground,
                    unfocusedContainerColor = CosmicBackground
                ),
                modifier = Modifier.fillMaxWidth()
            )

            // Default Channel Input
            OutlinedTextField(
                value = defaultChannel,
                onValueChange = onChannelChange,
                label = { Text("ห้องแชทเริ่มต้น (Channel)") },
                placeholder = { Text("เช่น #thaiirc") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Tag, contentDescription = null, tint = NeonBlue) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonBlue,
                    unfocusedBorderColor = CosmicCardLight,
                    focusedLabelColor = NeonBlue,
                    unfocusedLabelColor = MutedGray,
                    focusedTextColor = SoftWhite,
                    unfocusedTextColor = SoftWhite,
                    focusedContainerColor = CosmicBackground,
                    unfocusedContainerColor = CosmicBackground
                ),
                modifier = Modifier.fillMaxWidth()
            )

            // Connect Button
            Button(
                onClick = onConnect,
                enabled = !isConnecting && nickname.trim().isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonBlue,
                    disabledContainerColor = CosmicCardLight
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                if (isConnecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = SoftWhite
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("กำลังเชื่อมต่อ...", fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Filled.Login, contentDescription = null, tint = SoftWhite)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("เข้าสู่แชท", fontWeight = FontWeight.Bold, color = SoftWhite)
                }
            }
        }
    }
}

@Composable
fun ChatInterfacePanel(
    modifier: Modifier = Modifier,
    currentChannel: String,
    joinedChannels: Set<String>,
    messages: List<IrcMessage>,
    activeUsersCount: Int,
    chatInput: String,
    currentNick: String = "",
    onChatInputChange: (String) -> Unit,
    onSendMessage: () -> Unit,
    onChannelSelect: (String) -> Unit,
    onLeaveChannel: (String) -> Unit,
    onJoinChannelClick: () -> Unit,
    onShowUserList: () -> Unit,
    onDisconnect: () -> Unit
) {
    val listState = rememberLazyListState()

    val onTagUser: (String) -> Unit = { user ->
        var clean = user.trim()
        while (clean.isNotEmpty() && (clean.startsWith("@") || clean.startsWith("+") || clean.startsWith("%") || clean.startsWith("~") || clean.startsWith("&"))) {
            clean = clean.substring(1)
        }
        val tag = "@$clean "
        if (chatInput.isEmpty()) {
            onChatInputChange(tag)
        } else {
            val newText = if (chatInput.endsWith(" ")) {
                chatInput + tag
            } else {
                chatInput + " " + tag
            }
            onChatInputChange(newText)
        }
    }

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        // Chat room state indicator header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .background(CosmicCard, RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.ChatBubble, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = currentChannel.ifEmpty { "หน้าล็อบบี้ระบบ" },
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = SoftWhite
                ),
                modifier = Modifier.weight(1f)
            )

            if (currentChannel.isNotEmpty()) {
                // Member counter button
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(CosmicCardLight)
                        .clickable { onShowUserList() }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Filled.People, contentDescription = null, tint = SoftWhite, modifier = Modifier.size(14.dp))
                    Text(
                        text = "$activeUsersCount คน",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = SoftWhite
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Disconnect button
            IconButton(
                onClick = onDisconnect,
                modifier = Modifier.size(30.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.PowerSettingsNew,
                    contentDescription = "ตัดสาย",
                    tint = Color.Red,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Horizontal Channel Selector Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Joined channels list
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                joinedChannels.forEach { chan ->
                    val isSelected = currentChannel == chan
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) NeonCyan else CosmicCard)
                            .clickable { onChannelSelect(chan) }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = chan,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) CosmicBackground else SoftWhite
                                )
                            )
                            if (joinedChannels.size > 1) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "ออกห้อง",
                                    tint = if (isSelected) CosmicBackground.copy(alpha = 0.6f) else MutedGray,
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clickable { onLeaveChannel(chan) }
                                )
                            }
                        }
                    }
                }
            }

            // Join other room button
            IconButton(
                onClick = onJoinChannelClick,
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(CosmicCard)
            ) {
                Icon(Icons.Filled.Add, contentDescription = "เข้าห้องเพิ่ม", tint = NeonCyan, modifier = Modifier.size(16.dp))
            }
        }

        // Chat Message List
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .background(CosmicCard.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                .border(0.5.dp, CosmicCardLight, RoundedCornerShape(12.dp))
        ) {
            if (messages.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Filled.Forum,
                        contentDescription = null,
                        tint = CosmicCardLight,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "ไม่มีข้อความในห้องนี้",
                        style = MaterialTheme.typography.bodyMedium.copy(color = MutedGray)
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages, key = { it.id }) { msg ->
                        ChatBubbleItem(msg, currentNick, onTagUser)
                    }
                }
            }
        }

        // Chat input pane
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(12.dp)
        ) {
            // Keyboard field + Send Action
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = chatInput,
                    onValueChange = onChatInputChange,
                    placeholder = { Text("พิมพ์ข้อความที่นี่...") },
                    maxLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonBlue,
                        unfocusedBorderColor = CosmicCardLight,
                        focusedTextColor = SoftWhite,
                        unfocusedTextColor = SoftWhite,
                        focusedContainerColor = CosmicCard,
                        unfocusedContainerColor = CosmicCard
                    ),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )

                IconButton(
                    onClick = onSendMessage,
                    enabled = chatInput.trim().isNotEmpty(),
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (chatInput.trim().isNotEmpty()) NeonBlue else CosmicCardLight)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Send,
                        contentDescription = "ส่งข้อความ",
                        tint = SoftWhite,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ChatBubbleItem(message: IrcMessage, currentNick: String = "", onTagUser: (String) -> Unit = {}) {
    val formatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val timeString = remember(message.timestamp) { formatter.format(Date(message.timestamp)) }

    if (message.isSystem) {
        // System centered messages
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            SelectionContainer {
                Text(
                    text = "${message.text} ($timeString)",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.sp,
                        color = MutedGray,
                        textAlign = TextAlign.Center
                    ),
                    modifier = Modifier
                        .background(CosmicCard.copy(alpha = 0.8f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    } else {
        // Chat dialog messages
        val isSystemSender = message.sender == "System" || message.sender == "*"
        val isMe = message.sender != null && message.sender.equals(currentNick, ignoreCase = true)
        
        if (isSystemSender) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                SelectionContainer {
                    Text(
                        text = "${message.sender}: ${message.text} ($timeString)",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            color = MutedGray,
                            textAlign = TextAlign.Center
                        ),
                        modifier = Modifier
                            .background(CosmicCard.copy(alpha = 0.8f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        } else {
            val isMentioned = remember(message.text, currentNick) {
                currentNick.isNotEmpty() && message.text.contains(currentNick, ignoreCase = true)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start,
                verticalAlignment = Alignment.Top
            ) {
                // Left avatar (Received)
                if (!isMe) {
                    val senderName = message.sender ?: "?"
                    AvatarView(
                        senderName = senderName,
                        fallbackBgColor = CosmicCardLight,
                        fallbackTextColor = NeonBlue,
                        fontSize = 11.sp,
                        modifier = Modifier.clickable { onTagUser(senderName) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }

                // Chat bubble body
                val bubbleBg = if (isMe) Color(0xFF381E72) else Color(0xFF49454F)
                val bubbleShape = if (isMe) {
                    RoundedCornerShape(topStart = 16.dp, topEnd = 0.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
                } else {
                    RoundedCornerShape(topStart = 0.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
                }

                val borderStroke = if (isMentioned && !isMe) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF22D3EE)) else null

                Column(
                    modifier = Modifier
                        .widthIn(max = 260.dp)
                        .background(color = bubbleBg, shape = bubbleShape)
                        .let { if (borderStroke != null) it.border(borderStroke, bubbleShape) else it }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    // Sender name
                    if (!isMe) {
                        message.sender?.let { sender ->
                            Text(
                                text = sender,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = NeonBlue,
                                    fontSize = 11.sp
                                ),
                                modifier = Modifier.clickable { onTagUser(sender) }
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                        }
                    }

                    // Chat text content with mIRC colors and SelectionContainer for Copy support
                    SelectionContainer {
                        Text(
                            text = parseMircColors(message.text),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = SoftWhite,
                                fontSize = 14.sp
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Time receipt
                    Text(
                        text = timeString,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.sp,
                            color = MutedGray,
                            textAlign = TextAlign.End
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Right avatar (Sent)
                if (isMe) {
                    Spacer(modifier = Modifier.width(8.dp))
                    val senderName = message.sender ?: "M"
                    AvatarView(
                        senderName = senderName,
                        fallbackBgColor = Color(0xFFD0BCFF),
                        fallbackTextColor = Color(0xFF381E72),
                        fontSize = 11.sp,
                        modifier = Modifier.clickable { onTagUser(senderName) }
                    )
                }
            }
        }
    }
}

@Composable
fun AvatarView(
    senderName: String,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    fallbackBgColor: Color = CosmicCardLight,
    fallbackTextColor: Color = NeonBlue,
    fontSize: TextUnit = 11.sp
) {
    val context = LocalContext.current
    val cleanNick = remember(senderName) {
        var clean = senderName.trim()
        while (clean.isNotEmpty() && (clean.startsWith("@") || clean.startsWith("+") || clean.startsWith("%") || clean.startsWith("~") || clean.startsWith("&"))) {
            clean = clean.substring(1)
        }
        clean
    }
    
    val avatarUrl = remember(cleanNick) {
        if (cleanNick.isNotEmpty()) {
            "https://www.thaiirc.com/avatars/${cleanNick.lowercase()}.gif"
        } else {
            ""
        }
    }

    val imageLoader = remember {
        ImageLoader.Builder(context)
            .components {
                if (Build.VERSION.SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()
    }

    val initial = remember(senderName) { getFirstGrapheme(senderName) }

    if (avatarUrl.isNotEmpty()) {
        SubcomposeAsyncImage(
            model = avatarUrl,
            imageLoader = imageLoader,
            contentDescription = senderName,
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(fallbackBgColor)
                .border(1.dp, MutedGray.copy(alpha = 0.2f), CircleShape),
            contentScale = ContentScale.Crop
        ) {
            val state = painter.state
            if (state is AsyncImagePainter.State.Success) {
                SubcomposeAsyncImageContent()
            } else {
                // Error, empty or loading (show initials as fallback)
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initial,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = fallbackTextColor,
                            fontSize = fontSize
                        )
                    )
                }
            }
        }
    } else {
        Box(
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(fallbackBgColor)
                .border(1.dp, MutedGray.copy(alpha = 0.2f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initial,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = fallbackTextColor,
                    fontSize = fontSize
                )
            )
        }
    }
}

// Safe helper to extract the first grapheme (supporting surrogate pairs/emojis)
fun getFirstGrapheme(name: String): String {
    if (name.isEmpty()) return "?"
    return try {
        val codePoint = name.codePointAt(0)
        String(Character.toChars(codePoint)).uppercase()
    } catch (e: Exception) {
        name.take(1).uppercase()
    }
}

// Map a nickname to a distinct attractive color for chat identification
fun getNickColor(nick: String): Color {
    if (nick == "System" || nick == "Server") return Color.Gray
    val hash = nick.hashCode()
    val colors = listOf(
        Color(0xFF60A5FA), // Blue
        Color(0xFF34D399), // Emerald
        Color(0xFFFBBF24), // Amber
        Color(0xFFF87171), // Red
        Color(0xFFC084FC), // Purple
        Color(0xFFF472B6), // Pink
        Color(0xFF2DD4BF), // Teal
        Color(0xFFFB7185)  // Rose
    )
    return colors[Math.abs(hash) % colors.size]
}

fun parseMircColors(input: String): AnnotatedString {
    val builder = AnnotatedString.Builder()
    var i = 0
    val n = input.length
    
    var boldStart: Int? = null
    var underlineStart: Int? = null
    var italicStart: Int? = null
    var colorStart: Int? = null
    
    var currentFg: Color? = null
    var currentBg: Color? = null
    
    val mircColors = mapOf(
        0 to Color(0xFFFFFFFF), // White
        1 to Color(0xFFE5E5E5), // Black (Map to very light grey so readable on dark mode)
        2 to Color(0xFF60A5FA), // Dark Blue -> Light Blue for dark mode
        3 to Color(0xFF34D399), // Dark Green -> Emerald
        4 to Color(0xFFF87171), // Red
        5 to Color(0xFFB91C1C), // Brown/Dark Red
        6 to Color(0xFFC084FC), // Purple
        7 to Color(0xFFF59E0B), // Orange
        8 to Color(0xFFFBBF24), // Yellow
        9 to Color(0xFF34D399), // Light Green
        10 to Color(0xFF2DD4BF), // Teal
        11 to Color(0xFF22D3EE), // Cyan
        12 to Color(0xFF93C5FD), // Light Blue
        13 to Color(0xFFF472B6), // Pink
        14 to Color(0xFF9CA3AF), // Grey
        15 to Color(0xFFE5E7EB)  // Light Grey
    )
    
    while (i < n) {
        val char = input[i]
        when (char) {
            '\u0002' -> { // Bold
                if (boldStart != null) {
                    builder.addStyle(SpanStyle(fontWeight = FontWeight.Bold), boldStart, builder.length)
                    boldStart = null
                } else {
                    boldStart = builder.length
                }
                i++
            }
            '\u001F' -> { // Underline
                if (underlineStart != null) {
                    builder.addStyle(SpanStyle(textDecoration = TextDecoration.Underline), underlineStart, builder.length)
                    underlineStart = null
                } else {
                    underlineStart = builder.length
                }
                i++
            }
            '\u001D', '\u0016' -> { // Italic
                if (italicStart != null) {
                    builder.addStyle(SpanStyle(fontStyle = FontStyle.Italic), italicStart, builder.length)
                    italicStart = null
                } else {
                    italicStart = builder.length
                }
                i++
            }
            '\u0003' -> { // Color
                if (colorStart != null) {
                    val fg = currentFg ?: Color.Unspecified
                    val bg = currentBg ?: Color.Unspecified
                    if (fg != Color.Unspecified || bg != Color.Unspecified) {
                        builder.addStyle(SpanStyle(color = fg, background = bg), colorStart, builder.length)
                    }
                    colorStart = null
                }
                
                i++ // skip \u0003
                
                // Read fg color code (up to 2 digits)
                var fgStr = ""
                while (i < n && input[i].isDigit() && fgStr.length < 2) {
                    fgStr += input[i]
                    i++
                }
                
                if (fgStr.isNotEmpty()) {
                    val fgCode = fgStr.toIntOrNull() ?: -1
                    currentFg = mircColors[fgCode]
                    
                    // Check for background color separated by comma
                    if (i < n && input[i] == ',') {
                        i++ // skip ','
                        var bgStr = ""
                        while (i < n && input[i].isDigit() && bgStr.length < 2) {
                            bgStr += input[i]
                            i++
                        }
                        if (bgStr.isNotEmpty()) {
                            val bgCode = bgStr.toIntOrNull() ?: -1
                            currentBg = mircColors[bgCode]
                        } else {
                            currentBg = null
                        }
                    } else {
                        currentBg = null
                    }
                    colorStart = builder.length
                } else {
                    currentFg = null
                    currentBg = null
                }
            }
            '\u000F' -> { // Reset all
                if (boldStart != null) {
                    builder.addStyle(SpanStyle(fontWeight = FontWeight.Bold), boldStart, builder.length)
                    boldStart = null
                }
                if (underlineStart != null) {
                    builder.addStyle(SpanStyle(textDecoration = TextDecoration.Underline), underlineStart, builder.length)
                    underlineStart = null
                }
                if (italicStart != null) {
                    builder.addStyle(SpanStyle(fontStyle = FontStyle.Italic), italicStart, builder.length)
                    italicStart = null
                }
                if (colorStart != null) {
                    val fg = currentFg ?: Color.Unspecified
                    val bg = currentBg ?: Color.Unspecified
                    if (fg != Color.Unspecified || bg != Color.Unspecified) {
                        builder.addStyle(SpanStyle(color = fg, background = bg), colorStart, builder.length)
                    }
                    colorStart = null
                }
                currentFg = null
                currentBg = null
                i++
            }
            else -> {
                builder.append(char)
                i++
            }
        }
    }
    
    // Close any open spans at the end
    if (boldStart != null) {
        builder.addStyle(SpanStyle(fontWeight = FontWeight.Bold), boldStart, builder.length)
    }
    if (underlineStart != null) {
        builder.addStyle(SpanStyle(textDecoration = TextDecoration.Underline), underlineStart, builder.length)
    }
    if (italicStart != null) {
        builder.addStyle(SpanStyle(fontStyle = FontStyle.Italic), italicStart, builder.length)
    }
    if (colorStart != null) {
        val fg = currentFg ?: Color.Unspecified
        val bg = currentBg ?: Color.Unspecified
        if (fg != Color.Unspecified || bg != Color.Unspecified) {
            builder.addStyle(SpanStyle(color = fg, background = bg), colorStart, builder.length)
        }
    }
    
    val result = builder.toAnnotatedString()
    val plainText = result.text
    val urlPattern = "(https?://[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}[a-zA-Z0-9/._?#=&+%-]*)|(www\\.[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}[a-zA-Z0-9/._?#=&+%-]*)".toRegex(RegexOption.IGNORE_CASE)
    
    val finalBuilder = AnnotatedString.Builder(result)
    val matches = urlPattern.findAll(plainText)
    for (match in matches) {
        val start = match.range.first
        val end = match.range.last + 1
        val rawUrl = match.value
        val destinationUrl = if (rawUrl.lowercase().startsWith("http")) rawUrl else "http://$rawUrl"
        
        try {
            finalBuilder.addLink(
                url = LinkAnnotation.Url(
                    url = destinationUrl,
                    styles = TextLinkStyles(
                        style = SpanStyle(
                            color = Color(0xFF22D3EE),
                            textDecoration = TextDecoration.Underline
                        )
                    )
                ),
                start = start,
                end = end
            )
        } catch (e: Throwable) {
            finalBuilder.addStringAnnotation(
                tag = "URL",
                annotation = destinationUrl,
                start = start,
                end = end
            )
            finalBuilder.addStyle(
                style = SpanStyle(
                    color = Color(0xFF22D3EE),
                    textDecoration = TextDecoration.Underline
                ),
                start = start,
                end = end
            )
        }
    }
    return finalBuilder.toAnnotatedString()
}

@Composable
fun UserListDialog(
    channel: String,
    users: Set<String>,
    onDismiss: () -> Unit,
    onUserClick: (String) -> Unit = {}
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "สมาชิกในห้อง $channel (${users.size} คน)",
                color = SoftWhite,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp)
            ) {
                if (users.isEmpty()) {
                    Text(
                        text = "กำลังดึงข้อมูลรายชื่อ...",
                        color = MutedGray,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(users.toList()) { user ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(CosmicCardLight.copy(alpha = 0.5f))
                                    .clickable { onUserClick(user) }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AvatarView(
                                    senderName = user,
                                    size = 28.dp,
                                    fallbackBgColor = getNickColor(user),
                                    fallbackTextColor = CosmicBackground,
                                    fontSize = 12.sp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = user,
                                    color = SoftWhite,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("ปิด", color = NeonCyan)
            }
        },
        containerColor = CosmicCard
    )
}

@Composable
fun JoinChannelDialog(
    onJoin: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var textInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "เข้าร่วมห้องแชทใหม่",
                color = SoftWhite,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            OutlinedTextField(
                value = textInput,
                onValueChange = { textInput = it },
                label = { Text("ชื่อห้อง (เช่น #thaiirc)") },
                placeholder = { Text("#ห้องแชท") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonBlue,
                    unfocusedBorderColor = CosmicCardLight,
                    focusedTextColor = SoftWhite,
                    unfocusedTextColor = SoftWhite
                ),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    if (textInput.trim().isNotEmpty()) {
                        val finalChan = if (textInput.trim().startsWith("#")) textInput.trim() else "#${textInput.trim()}"
                        onJoin(finalChan)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = NeonBlue)
            ) {
                Text("เข้าร่วม")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("ยกเลิก", color = MutedGray)
            }
        },
        containerColor = CosmicCard
    )
}

@Composable
fun SettingsDialog(
    ircClient: IrcClient,
    onDismiss: () -> Unit
) {
    val currentNick by ircClient.currentNick.collectAsStateWithLifecycle()
    val currentQuitMessage by ircClient.quitMessage.collectAsStateWithLifecycle()
    val serverAddress by ircClient.serverAddress.collectAsStateWithLifecycle()
    val serverPort by ircClient.serverPort.collectAsStateWithLifecycle()
    val useSsl by ircClient.useSsl.collectAsStateWithLifecycle()
    val authMode by ircClient.authMode.collectAsStateWithLifecycle()
    val serverPassword by ircClient.serverPassword.collectAsStateWithLifecycle()
    val saslUsername by ircClient.saslUsername.collectAsStateWithLifecycle()
    val saslPassword by ircClient.saslPassword.collectAsStateWithLifecycle()
    val autoJoinChannels by ircClient.autoJoinChannels.collectAsStateWithLifecycle()
    val rejoinOpenedChannels by ircClient.rejoinOpenedChannels.collectAsStateWithLifecycle()
    val autoRunCommands by ircClient.autoRunCommands.collectAsStateWithLifecycle()
    val mentionNotificationEnabled by ircClient.mentionNotificationEnabled.collectAsStateWithLifecycle()

    val useZnc by ircClient.useZnc.collectAsStateWithLifecycle()
    val zncUsername by ircClient.zncUsername.collectAsStateWithLifecycle()
    val zncNetwork by ircClient.zncNetwork.collectAsStateWithLifecycle()
    val zncPassword by ircClient.zncPassword.collectAsStateWithLifecycle()

    var nickInput by remember { mutableStateOf(currentNick) }
    var quitMessageInput by remember { mutableStateOf(currentQuitMessage) }
    var serverAddressInput by remember { mutableStateOf(serverAddress) }
    var serverPortInput by remember { mutableStateOf(serverPort.toString()) }
    var useSslInput by remember { mutableStateOf(useSsl) }
    var authModeInput by remember { mutableStateOf(authMode) }
    var serverPasswordInput by remember { mutableStateOf(serverPassword) }
    var saslUsernameInput by remember { mutableStateOf(saslUsername) }
    var saslPasswordInput by remember { mutableStateOf(saslPassword) }
    var autoJoinChannelsInput by remember { mutableStateOf(autoJoinChannels) }
    var rejoinOpenedChannelsInput by remember { mutableStateOf(rejoinOpenedChannels) }
    var autoRunCommandsInput by remember { mutableStateOf(autoRunCommands) }
    var mentionNotificationEnabledInput by remember { mutableStateOf(mentionNotificationEnabled) }

    var useZncInput by remember { mutableStateOf(useZnc) }
    var zncUsernameInput by remember { mutableStateOf(zncUsername) }
    var zncNetworkInput by remember { mutableStateOf(zncNetwork) }
    var zncPasswordInput by remember { mutableStateOf(zncPassword) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "ตั้งค่าระบบ & โปรไฟล์",
                color = SoftWhite,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 450.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Section 1: Profile Settings
                Text("ข้อมูลโปรไฟล์", color = NeonCyan, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)

                OutlinedTextField(
                    value = nickInput,
                    onValueChange = { nickInput = it },
                    label = { Text("ชื่อเล่นใหม่ (NICK)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonBlue,
                        unfocusedBorderColor = CosmicCardLight,
                        focusedTextColor = SoftWhite,
                        unfocusedTextColor = SoftWhite,
                        focusedLabelColor = NeonBlue,
                        unfocusedLabelColor = MutedGray
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = quitMessageInput,
                    onValueChange = { quitMessageInput = it },
                    label = { Text("Quit Message") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonBlue,
                        unfocusedBorderColor = CosmicCardLight,
                        focusedTextColor = SoftWhite,
                        unfocusedTextColor = SoftWhite,
                        focusedLabelColor = NeonBlue,
                        unfocusedLabelColor = MutedGray
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Section 2: Server Settings
                Text("เซิร์ฟเวอร์", color = NeonCyan, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = serverAddressInput,
                        onValueChange = { serverAddressInput = it },
                        label = { Text("Server Address") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonBlue,
                            unfocusedBorderColor = CosmicCardLight,
                            focusedTextColor = SoftWhite,
                            unfocusedTextColor = SoftWhite,
                            focusedLabelColor = NeonBlue,
                            unfocusedLabelColor = MutedGray
                        ),
                        modifier = Modifier.weight(0.65f)
                    )

                    OutlinedTextField(
                        value = serverPortInput,
                        onValueChange = { serverPortInput = it },
                        label = { Text("Port") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonBlue,
                            unfocusedBorderColor = CosmicCardLight,
                            focusedTextColor = SoftWhite,
                            unfocusedTextColor = SoftWhite,
                            focusedLabelColor = NeonBlue,
                            unfocusedLabelColor = MutedGray
                        ),
                        modifier = Modifier.weight(0.35f)
                    )
                }

                // Checkbox SSL
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { useSslInput = !useSslInput }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = useSslInput,
                        onCheckedChange = { useSslInput = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = NeonBlue,
                            uncheckedColor = MutedGray,
                            checkmarkColor = CosmicBackground
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("เชื่อมต่อแบบปลอดภัย (SSL / TLS)", color = SoftWhite, style = MaterialTheme.typography.bodyMedium)
                }

                // Section 3: Authentication Mode
                Text("โหมดยืนยันตัวตน (Authentication)", color = NeonCyan, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)

                val authModes = listOf("None", "Server password", "Username with password (SASL)")
                authModes.forEach { mode ->
                    val modeText = when (mode) {
                        "None" -> "ไม่ยืนยันตัวตน (None)"
                        "Server password" -> "รหัสผ่านเซิร์ฟเวอร์ (Server password)"
                        "Username with password (SASL)" -> "SASL (Username & Password)"
                        else -> mode
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { authModeInput = mode }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = authModeInput == mode,
                            onClick = { authModeInput = mode },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = NeonBlue,
                                unselectedColor = MutedGray
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(modeText, color = SoftWhite, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                if (authModeInput == "Server password") {
                    OutlinedTextField(
                        value = serverPasswordInput,
                        onValueChange = { serverPasswordInput = it },
                        label = { Text("รหัสผ่านเซิร์ฟเวอร์ (Server Password)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonBlue,
                            unfocusedBorderColor = CosmicCardLight,
                            focusedTextColor = SoftWhite,
                            unfocusedTextColor = SoftWhite,
                            focusedLabelColor = NeonBlue,
                            unfocusedLabelColor = MutedGray
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (authModeInput == "Username with password (SASL)") {
                    OutlinedTextField(
                        value = saslUsernameInput,
                        onValueChange = { saslUsernameInput = it },
                        label = { Text("SASL Username") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonBlue,
                            unfocusedBorderColor = CosmicCardLight,
                            focusedTextColor = SoftWhite,
                            unfocusedTextColor = SoftWhite,
                            focusedLabelColor = NeonBlue,
                            unfocusedLabelColor = MutedGray
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = saslPasswordInput,
                        onValueChange = { saslPasswordInput = it },
                        label = { Text("SASL Password") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonBlue,
                            unfocusedBorderColor = CosmicCardLight,
                            focusedTextColor = SoftWhite,
                            unfocusedTextColor = SoftWhite,
                            focusedLabelColor = NeonBlue,
                            unfocusedLabelColor = MutedGray
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Section 3.5: ZNC Bouncer Settings
                Text("ตั้งค่า ZNC Bouncer", color = NeonCyan, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { useZncInput = !useZncInput }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = useZncInput,
                        onCheckedChange = { useZncInput = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = NeonBlue,
                            uncheckedColor = MutedGray,
                            checkmarkColor = CosmicBackground
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("ใช้งานผ่าน ZNC Bouncer", color = SoftWhite, style = MaterialTheme.typography.bodyMedium)
                }

                if (useZncInput) {
                    OutlinedTextField(
                        value = zncUsernameInput,
                        onValueChange = { zncUsernameInput = it },
                        label = { Text("ZNC Username") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonBlue,
                            unfocusedBorderColor = CosmicCardLight,
                            focusedTextColor = SoftWhite,
                            unfocusedTextColor = SoftWhite,
                            focusedLabelColor = NeonBlue,
                            unfocusedLabelColor = MutedGray
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = zncNetworkInput,
                        onValueChange = { zncNetworkInput = it },
                        label = { Text("ZNC Network (ไม่จำเป็นต้องใส่)") },
                        placeholder = { Text("เช่น thaiirc") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonBlue,
                            unfocusedBorderColor = CosmicCardLight,
                            focusedTextColor = SoftWhite,
                            unfocusedTextColor = SoftWhite,
                            focusedLabelColor = NeonBlue,
                            unfocusedLabelColor = MutedGray
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = zncPasswordInput,
                        onValueChange = { zncPasswordInput = it },
                        label = { Text("ZNC Password") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonBlue,
                            unfocusedBorderColor = CosmicCardLight,
                            focusedTextColor = SoftWhite,
                            unfocusedTextColor = SoftWhite,
                            focusedLabelColor = NeonBlue,
                            unfocusedLabelColor = MutedGray
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Section 4: Auto-Join Channels
                Text("ห้องแชทอัตโนมัติ (Auto-Join Channels)", color = NeonCyan, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)

                OutlinedTextField(
                    value = autoJoinChannelsInput,
                    onValueChange = { autoJoinChannelsInput = it },
                    label = { Text("ห้องที่เข้าอัตโนมัติ (คั่นด้วยจุลภาค ,)") },
                    placeholder = { Text("เช่น #thaiirc,#siam") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonBlue,
                        unfocusedBorderColor = CosmicCardLight,
                        focusedTextColor = SoftWhite,
                        unfocusedTextColor = SoftWhite,
                        focusedLabelColor = NeonBlue,
                        unfocusedLabelColor = MutedGray
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Checkbox Rejoin
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { rejoinOpenedChannelsInput = !rejoinOpenedChannelsInput }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = rejoinOpenedChannelsInput,
                        onCheckedChange = { rejoinOpenedChannelsInput = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = NeonBlue,
                            uncheckedColor = MutedGray,
                            checkmarkColor = CosmicBackground
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("เชื่อมต่อห้องเดิมที่เปิดค้างไว้ล่าสุด (Rejoin)", color = SoftWhite, style = MaterialTheme.typography.bodyMedium)
                }

                // Section 5: Auto-Run Commands
                Text("คำสั่งรันอัตโนมัติ (Auto-Run Commands)", color = NeonCyan, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)

                OutlinedTextField(
                    value = autoRunCommandsInput,
                    onValueChange = { autoRunCommandsInput = it },
                    label = { Text("คำสั่งรันอัตโนมัติ (1 คำสั่งต่อ 1 บรรทัด)") },
                    placeholder = { Text("เช่น /msg NickServ identify 123456") },
                    singleLine = false,
                    maxLines = 4,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonBlue,
                        unfocusedBorderColor = CosmicCardLight,
                        focusedTextColor = SoftWhite,
                        unfocusedTextColor = SoftWhite,
                        focusedLabelColor = NeonBlue,
                        unfocusedLabelColor = MutedGray
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Section 6: Notification Settings
                Text("การแจ้งเตือน (Notifications)", color = NeonCyan, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)

                val context = LocalContext.current
                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    if (isGranted) {
                        Toast.makeText(context, "เปิดการแจ้งเตือนสำเร็จ", Toast.LENGTH_SHORT).show()
                    } else {
                        mentionNotificationEnabledInput = false
                        Toast.makeText(context, "กรุณาอนุญาตการแจ้งเตือนในระบบเพื่อรับการแท็กชื่อ", Toast.LENGTH_LONG).show()
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val nextValue = !mentionNotificationEnabledInput
                            if (nextValue && android.os.Build.VERSION.SDK_INT >= 33) {
                                if (androidx.core.content.ContextCompat.checkSelfPermission(
                                        context,
                                        android.Manifest.permission.POST_NOTIFICATIONS
                                    ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                                ) {
                                    notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                }
                            }
                            mentionNotificationEnabledInput = nextValue
                        }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = mentionNotificationEnabledInput,
                        onCheckedChange = { nextValue ->
                            if (nextValue && android.os.Build.VERSION.SDK_INT >= 33) {
                                if (androidx.core.content.ContextCompat.checkSelfPermission(
                                        context,
                                        android.Manifest.permission.POST_NOTIFICATIONS
                                    ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                                ) {
                                    notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                }
                            }
                            mentionNotificationEnabledInput = nextValue
                        },
                        colors = CheckboxDefaults.colors(
                            checkedColor = NeonBlue,
                            uncheckedColor = MutedGray,
                            checkmarkColor = CosmicBackground
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("แจ้งเตือนเมื่อมีคนแท็กชื่อเรา หรือส่งข้อความส่วนตัว", color = SoftWhite, style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val portNum = serverPortInput.toIntOrNull() ?: 6667
                    ircClient.saveSettings(
                        nick = nickInput.trim(),
                        quitMsg = quitMessageInput.trim(),
                        server = serverAddressInput.trim(),
                        port = portNum,
                        ssl = useSslInput,
                        auth = authModeInput,
                        serverPass = serverPasswordInput.trim(),
                        saslUser = saslUsernameInput.trim(),
                        saslPass = saslPasswordInput.trim(),
                        autoJoin = autoJoinChannelsInput.trim(),
                        rejoin = rejoinOpenedChannelsInput,
                        autoRun = autoRunCommandsInput.trim(),
                        useZncVal = useZncInput,
                        zncUser = zncUsernameInput.trim(),
                        zncNet = zncNetworkInput.trim(),
                        zncPass = zncPasswordInput.trim(),
                        mentionNotificationEnabledVal = mentionNotificationEnabledInput
                    )
                    
                    if (nickInput.trim() != currentNick && nickInput.trim().isNotEmpty()) {
                        ircClient.updateNick(nickInput.trim())
                    }
                    if (quitMessageInput.trim() != currentQuitMessage) {
                        ircClient.updateQuitMessage(quitMessageInput.trim())
                    }

                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = NeonBlue)
            ) {
                Text("บันทึก")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("ยกเลิก", color = MutedGray)
            }
        },
        containerColor = CosmicCard
    )
}

enum class AppTab {
    CHAT,
    RADIO
}
