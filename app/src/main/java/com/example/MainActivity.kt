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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    val playbackState by viewModel.radioPlayer.playbackState.collectAsStateWithLifecycle()
    val currentStation by viewModel.radioPlayer.currentStation.collectAsStateWithLifecycle()
    val volume by viewModel.radioPlayer.volume.collectAsStateWithLifecycle()

    var nicknameInput by remember { mutableStateOf(currentNick) }
    var channelInput by remember { mutableStateOf("#thaiirc") }
    var chatMessageInput by remember { mutableStateOf("") }

    var showUserListDialog by remember { mutableStateOf(false) }
    var showJoinChannelDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    
    var currentTab by remember { mutableStateOf(AppTab.CHAT) }

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
                        defaultChannel = channelInput,
                        onChannelChange = { channelInput = it },
                        isConnecting = connectionState == IrcConnectionState.CONNECTING,
                        onConnect = {
                            keyboardController?.hide()
                            viewModel.ircClient.updateCurrentChannel(channelInput)
                            viewModel.ircClient.connect(nicknameInput)
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
            onDismiss = { showUserListDialog = false }
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
            currentNick = currentNick,
            currentQuitMessage = quitMessage,
            onUpdateNick = {
                viewModel.ircClient.updateNick(it)
            },
            onUpdateQuitMessage = {
                viewModel.ircClient.updateQuitMessage(it)
            },
            onDismiss = { showSettingsDialog = false }
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
    defaultChannel: String,
    onChannelChange: (String) -> Unit,
    isConnecting: Boolean,
    onConnect: () -> Unit
) {
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
                modifier = Modifier.weight(1f),
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
                        ChatBubbleItem(msg, currentNick)
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
fun ChatBubbleItem(message: IrcMessage, currentNick: String = "") {
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
        } else {
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
                    val initial = senderName.take(1).uppercase()
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(CosmicCardLight)
                            .border(1.dp, MutedGray.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = initial,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = NeonBlue,
                                fontSize = 11.sp
                            )
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }

                // Chat bubble body
                val bubbleBg = if (isMe) Color(0xFF381E72) else Color(0xFF49454F)
                val bubbleShape = if (isMe) {
                    RoundedCornerShape(topStart = 16.dp, topEnd = 0.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
                } else {
                    RoundedCornerShape(topStart = 0.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
                }

                Column(
                    modifier = Modifier
                        .widthIn(max = 260.dp)
                        .background(color = bubbleBg, shape = bubbleShape)
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
                                )
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                        }
                    }

                    // Chat text content
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = SoftWhite,
                            fontSize = 14.sp
                        )
                    )

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
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFD0BCFF)),
                        contentAlignment = Alignment.Center
                    ) {
                        val senderName = message.sender ?: "M"
                        val initial = senderName.take(1).uppercase()
                        Text(
                            text = initial,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF381E72),
                                fontSize = 11.sp
                            )
                        )
                    }
                }
            }
        }
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

@Composable
fun UserListDialog(
    channel: String,
    users: Set<String>,
    onDismiss: () -> Unit
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
                                    .background(CosmicCardLight.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(getNickColor(user)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = user.take(1).uppercase(),
                                        fontWeight = FontWeight.Bold,
                                        color = CosmicBackground,
                                        fontSize = 12.sp
                                    )
                                }
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
    currentNick: String,
    currentQuitMessage: String,
    onUpdateNick: (String) -> Unit,
    onUpdateQuitMessage: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var nickInput by remember { mutableStateOf(currentNick) }
    var quitMessageInput by remember { mutableStateOf(currentQuitMessage) }

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
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Nickname field
                OutlinedTextField(
                    value = nickInput,
                    onValueChange = { nickInput = it },
                    label = { Text("เปลี่ยนชื่อเล่นใหม่ (NICK)") },
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

                // Quit message field
                OutlinedTextField(
                    value = quitMessageInput,
                    onValueChange = { quitMessageInput = it },
                    label = { Text("ข้อความ Quit Message") },
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

                Text(
                    text = "ระบบจะใช้ Quit Message นี้เมื่อกดตัดการเชื่อมต่อจากแชท",
                    style = MaterialTheme.typography.bodySmall.copy(color = MutedGray)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (nickInput.trim().isNotEmpty()) {
                        onUpdateNick(nickInput.trim())
                    }
                    onUpdateQuitMessage(quitMessageInput.trim())
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
