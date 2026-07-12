package com.example

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Socket
import java.nio.charset.Charset

enum class IrcConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

data class IrcMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sender: String?, // nick of the sender, or server name, or "System"
    val target: String?, // channel (e.g., #thaiirc) or user, or null for system
    val text: String,
    val isSystem: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

data class IrcChannelInfo(
    val name: String,
    val usersCount: Int,
    val topic: String
)

fun getCleanNick(userStr: String): String {
    return userStr.removePrefix("~")
        .removePrefix("&")
        .removePrefix("@")
        .removePrefix("%")
        .removePrefix("+")
}

fun getPrefix(userStr: String): String {
    return if (userStr.startsWith("~") || userStr.startsWith("&") || userStr.startsWith("@") || userStr.startsWith("%") || userStr.startsWith("+")) {
        userStr.take(1)
    } else {
        ""
    }
}

class IrcClient(private val context: android.content.Context? = null) {
    private val _connectionState = MutableStateFlow(IrcConnectionState.DISCONNECTED)
    val connectionState: StateFlow<IrcConnectionState> = _connectionState.asStateFlow()

    private val _messages = MutableStateFlow<List<IrcMessage>>(emptyList())
    val messages: StateFlow<List<IrcMessage>> = _messages.asStateFlow()

    private val _currentChannel = MutableStateFlow("#thaiirc")
    val currentChannel: StateFlow<String> = _currentChannel.asStateFlow()

    private val _joinedChannels = MutableStateFlow<Set<String>>(emptySet())
    val joinedChannels: StateFlow<Set<String>> = _joinedChannels.asStateFlow()

    private val _channelUsers = MutableStateFlow<Map<String, Set<String>>>(emptyMap()) // Channel -> Users set
    val channelUsers: StateFlow<Map<String, Set<String>>> = _channelUsers.asStateFlow()

    private val _currentNick = MutableStateFlow("Thai${(1000..9999).random()}")
    val currentNick: StateFlow<String> = _currentNick.asStateFlow()

    private val _quitMessage = MutableStateFlow("Quit: app.thaiirc.com - live radio V8.0")
    val quitMessage: StateFlow<String> = _quitMessage.asStateFlow()

    private val _serverChannelsList = MutableStateFlow<List<IrcChannelInfo>>(emptyList())
    val serverChannelsList: StateFlow<List<IrcChannelInfo>> = _serverChannelsList.asStateFlow()

    private val _isFetchingChannelList = MutableStateFlow(false)
    val isFetchingChannelList: StateFlow<Boolean> = _isFetchingChannelList.asStateFlow()

    private val tempChannelList = mutableListOf<IrcChannelInfo>()

    fun fetchChannelList() {
        tempChannelList.clear()
        _serverChannelsList.value = emptyList()
        _isFetchingChannelList.value = true
        sendRaw("LIST")
    }

    fun openQuery(nick: String) {
        if (nick.trim().isEmpty() || nick == _currentNick.value) return
        val cleanNick = nick.trim()
        _joinedChannels.value = _joinedChannels.value + cleanNick
        _currentChannel.value = cleanNick
    }

    fun leaveChannelOrQuery(target: String) {
        if (target.startsWith("#")) {
            sendRaw("PART $target")
        } else {
            _joinedChannels.value = _joinedChannels.value - target
            if (_currentChannel.value == target) {
                _currentChannel.value = _joinedChannels.value.firstOrNull() ?: ""
            }
        }
    }

    // Configurable connection settings
    private val _serverAddress = MutableStateFlow("irc.thaiirc.com")
    val serverAddress: StateFlow<String> = _serverAddress.asStateFlow()

    private val _serverPort = MutableStateFlow(6667)
    val serverPort: StateFlow<Int> = _serverPort.asStateFlow()

    private val _useSsl = MutableStateFlow(false)
    val useSsl: StateFlow<Boolean> = _useSsl.asStateFlow()

    private val _authMode = MutableStateFlow("None") // "None", "Server password", "Username with password (SASL)"
    val authMode: StateFlow<String> = _authMode.asStateFlow()

    private val _serverPassword = MutableStateFlow("")
    val serverPassword: StateFlow<String> = _serverPassword.asStateFlow()

    private val _saslUsername = MutableStateFlow("")
    val saslUsername: StateFlow<String> = _saslUsername.asStateFlow()

    private val _saslPassword = MutableStateFlow("")
    val saslPassword: StateFlow<String> = _saslPassword.asStateFlow()

    private val _autoJoinChannels = MutableStateFlow("")
    val autoJoinChannels: StateFlow<String> = _autoJoinChannels.asStateFlow()

    private val _rejoinOpenedChannels = MutableStateFlow(true)
    val rejoinOpenedChannels: StateFlow<Boolean> = _rejoinOpenedChannels.asStateFlow()

    private val _autoRunCommands = MutableStateFlow("")
    val autoRunCommands: StateFlow<String> = _autoRunCommands.asStateFlow()

    private val _useZnc = MutableStateFlow(false)
    val useZnc: StateFlow<Boolean> = _useZnc.asStateFlow()

    private val _zncUsername = MutableStateFlow("")
    val zncUsername: StateFlow<String> = _zncUsername.asStateFlow()

    private val _zncNetwork = MutableStateFlow("")
    val zncNetwork: StateFlow<String> = _zncNetwork.asStateFlow()

    private val _zncPassword = MutableStateFlow("")
    val zncPassword: StateFlow<String> = _zncPassword.asStateFlow()

    private val _loginPassword = MutableStateFlow("")
    val loginPassword: StateFlow<String> = _loginPassword.asStateFlow()

    private val _mentionNotificationEnabled = MutableStateFlow(true)
    val mentionNotificationEnabled: StateFlow<Boolean> = _mentionNotificationEnabled.asStateFlow()

    private var lastConnectSaslFailed = false

    private val _errorFlow = MutableSharedFlow<String>()
    val errorFlow: SharedFlow<String> = _errorFlow.asSharedFlow()

    private var socket: Socket? = null
    private var reader: BufferedReader? = null
    private var writer: BufferedWriter? = null
    private var connectionJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    // Last used connection parameters for auto-reconnection
    private var lastNick: String = ""
    private var lastServer: String = "irc.thaiirc.com"
    private var lastPort: Int = 6667
    private var userRequestedDisconnect = false
    private var reconnectJob: Job? = null

    // We changed the encoding back to UTF-8 as requested by the user
    private val thaiCharset: Charset = java.nio.charset.StandardCharsets.UTF_8

    init {
        loadSettings()
    }

    private fun loadSettings() {
        val prefs = context?.getSharedPreferences("thaiirc_prefs", android.content.Context.MODE_PRIVATE) ?: return
        _currentNick.value = prefs.getString("nick", _currentNick.value) ?: _currentNick.value
        _quitMessage.value = prefs.getString("quit_message", _quitMessage.value) ?: _quitMessage.value
        _serverAddress.value = prefs.getString("server_address", _serverAddress.value) ?: _serverAddress.value
        _serverPort.value = prefs.getInt("server_port", _serverPort.value)
        _useSsl.value = prefs.getBoolean("use_ssl", _useSsl.value)
        _authMode.value = prefs.getString("auth_mode", _authMode.value) ?: _authMode.value
        _serverPassword.value = prefs.getString("server_password", _serverPassword.value) ?: _serverPassword.value
        _saslUsername.value = prefs.getString("sasl_username", _saslUsername.value) ?: _saslUsername.value
        _saslPassword.value = prefs.getString("sasl_password", _saslPassword.value) ?: _saslPassword.value
        _loginPassword.value = prefs.getString("login_password", _loginPassword.value) ?: _loginPassword.value
        _autoJoinChannels.value = prefs.getString("auto_join_channels", _autoJoinChannels.value) ?: _autoJoinChannels.value
        _rejoinOpenedChannels.value = prefs.getBoolean("rejoin_opened_channels", _rejoinOpenedChannels.value)
        _autoRunCommands.value = prefs.getString("auto_run_commands", _autoRunCommands.value) ?: _autoRunCommands.value
        
        _useZnc.value = prefs.getBoolean("use_znc", _useZnc.value)
        _zncUsername.value = prefs.getString("znc_username", _zncUsername.value) ?: _zncUsername.value
        _zncNetwork.value = prefs.getString("znc_network", _zncNetwork.value) ?: _zncNetwork.value
        _zncPassword.value = prefs.getString("znc_password", _zncPassword.value) ?: _zncPassword.value
        _mentionNotificationEnabled.value = prefs.getBoolean("mention_notification_enabled", _mentionNotificationEnabled.value)

        // Update current channel to match auto join or default
        val firstAutoJoin = _autoJoinChannels.value.split(",").firstOrNull { it.trim().isNotEmpty() }?.trim()
        if (firstAutoJoin != null) {
            _currentChannel.value = if (firstAutoJoin.startsWith("#")) firstAutoJoin else "#$firstAutoJoin"
        }
    }

    fun saveSettings(
        nick: String? = null,
        quitMsg: String? = null,
        server: String? = null,
        port: Int? = null,
        ssl: Boolean? = null,
        auth: String? = null,
        serverPass: String? = null,
        saslUser: String? = null,
        saslPass: String? = null,
        loginPass: String? = null,
        autoJoin: String? = null,
        rejoin: Boolean? = null,
        autoRun: String? = null,
        useZncVal: Boolean? = null,
        zncUser: String? = null,
        zncNet: String? = null,
        zncPass: String? = null,
        mentionNotificationEnabledVal: Boolean? = null
    ) {
        val prefs = context?.getSharedPreferences("thaiirc_prefs", android.content.Context.MODE_PRIVATE) ?: return
        with(prefs.edit()) {
            nick?.let { 
                _currentNick.value = it
                putString("nick", it) 
            }
            quitMsg?.let { 
                _quitMessage.value = it
                putString("quit_message", it) 
            }
            server?.let { 
                _serverAddress.value = it
                putString("server_address", it) 
            }
            port?.let { 
                _serverPort.value = it
                putInt("server_port", it) 
            }
            ssl?.let { 
                _useSsl.value = it
                putBoolean("use_ssl", it) 
            }
            auth?.let { 
                _authMode.value = it
                putString("auth_mode", it) 
            }
            serverPass?.let { 
                _serverPassword.value = it
                putString("server_password", it) 
            }
            saslUser?.let { 
                _saslUsername.value = it
                putString("sasl_username", it) 
            }
            saslPass?.let { 
                _saslPassword.value = it
                putString("sasl_password", it) 
            }
            loginPass?.let { 
                _loginPassword.value = it
                putString("login_password", it) 
            }
            autoJoin?.let { 
                _autoJoinChannels.value = it
                putString("auto_join_channels", it) 
            }
            rejoin?.let { 
                _rejoinOpenedChannels.value = it
                putBoolean("rejoin_opened_channels", it) 
            }
            autoRun?.let { 
                _autoRunCommands.value = it
                putString("auto_run_commands", it) 
            }
            useZncVal?.let {
                _useZnc.value = it
                putBoolean("use_znc", it)
            }
            zncUser?.let {
                _zncUsername.value = it
                putString("znc_username", it)
            }
            zncNet?.let {
                _zncNetwork.value = it
                putString("znc_network", it)
            }
            zncPass?.let {
                _zncPassword.value = it
                putString("znc_password", it)
            }
            mentionNotificationEnabledVal?.let {
                _mentionNotificationEnabled.value = it
                putBoolean("mention_notification_enabled", it)
            }
            apply()
        }
    }

    fun updateNick(newNick: String) {
        val trimmed = newNick.trim().replace(" ", "")
        if (trimmed.isNotEmpty()) {
            if (_connectionState.value == IrcConnectionState.CONNECTED) {
                sendRaw("NICK $trimmed")
            } else {
                _currentNick.value = trimmed
                saveSettings(nick = trimmed)
            }
        }
    }

    fun updateQuitMessage(newMsg: String) {
        _quitMessage.value = newMsg
        saveSettings(quitMsg = newMsg)
    }

    fun updateCurrentChannel(channel: String) {
        _currentChannel.value = channel
    }

    fun connect(nick: String, server: String = _serverAddress.value, port: Int = _serverPort.value, password: String = "") {
        val trimmedNick = nick.trim().replace(" ", "")
        if (trimmedNick.isEmpty()) {
            scope.launch { _errorFlow.emit("กรุณาใส่ชื่อเล่น (Nickname)") }
            return
        }

        // Save last connection state
        lastNick = trimmedNick
        lastServer = server
        lastPort = port
        userRequestedDisconnect = false
        reconnectJob?.cancel()
        lastConnectSaslFailed = false

        if (_connectionState.value == IrcConnectionState.CONNECTED || _connectionState.value == IrcConnectionState.CONNECTING) return

        _currentNick.value = trimmedNick
        _loginPassword.value = password
        saveSettings(nick = trimmedNick, server = server, port = port, loginPass = password)
        _connectionState.value = IrcConnectionState.CONNECTING
        addSystemMessage("กำลังเชื่อมต่อเซิร์ฟเวอร์ $server:$port...")

        connectionJob = scope.launch {
            try {
                val s = if (_useSsl.value) {
                    javax.net.ssl.SSLSocketFactory.getDefault().createSocket(server, port)
                } else {
                    Socket(server, port)
                }
                s.keepAlive = true // Enable keep alive to keep background connection stable
                s.tcpNoDelay = true
                socket = s
                reader = BufferedReader(InputStreamReader(s.getInputStream(), thaiCharset))
                writer = BufferedWriter(OutputStreamWriter(s.getOutputStream(), thaiCharset))

                _connectionState.value = IrcConnectionState.CONNECTED
                addSystemMessage("เชื่อมต่อสำเร็จ! กำลังยืนยันตัวตนด้วยชื่อเล่น $trimmedNick...")

                // Send handshake
                val ident = buildString {
                    append("app")
                    val poolDigits = listOf('0', '1')
                    val poolChars = listOf('a', 'b', 'c', 'd', 'e', 'f')
                    for (i in 0 until 7) {
                        if (i % 2 == 0) {
                            append(poolDigits.random())
                        } else {
                            append(poolChars.random())
                        }
                    }
                }
                
                // If ZNC is enabled, send PASS formatted as username[/network]:password
                if (_useZnc.value && _zncUsername.value.isNotEmpty()) {
                    val zncPass = buildString {
                        append(_zncUsername.value.trim())
                        if (_zncNetwork.value.trim().isNotEmpty()) {
                            append("/")
                            append(_zncNetwork.value.trim())
                        }
                        append(":")
                        append(_zncPassword.value.trim())
                    }
                    sendRaw("PASS $zncPass")
                } else {
                    // If SASL mode is selected or a login password is provided, request sasl capability
                    if (_authMode.value == "Username with password (SASL)" || _loginPassword.value.isNotEmpty()) {
                        sendRaw("CAP REQ :sasl")
                    }
                    
                    // Send PASS if server password mode is selected
                    if (_authMode.value == "Server password" && _serverPassword.value.isNotEmpty()) {
                        sendRaw("PASS ${_serverPassword.value}")
                    }
                }

                sendRaw("NICK $trimmedNick")
                sendRaw("USER $ident 0 * :ThaiIRC Client App")

                // Start listening
                listenLoop()
            } catch (e: Exception) {
                Log.e("IrcClient", "Connection error", e)
                _connectionState.value = IrcConnectionState.ERROR
                addSystemMessage("การเชื่อมต่อล้มเหลว: ${e.localizedMessage ?: "ไม่สามารถเชื่อมต่อได้"}")
                disconnectInternal()

                // If it wasn't a manual user action, retry connecting in a few seconds
                if (!userRequestedDisconnect) {
                    scheduleReconnection()
                }
            }
        }
    }

    fun disconnect() {
        userRequestedDisconnect = true
        reconnectJob?.cancel()
        addSystemMessage("กำลังตัดการเชื่อมต่อ...")
        scope.launch {
            if (_connectionState.value == IrcConnectionState.CONNECTED) {
                sendRaw("QUIT :${_quitMessage.value}")
                kotlinx.coroutines.delay(200)
            }
            withContext(Dispatchers.Main) {
                disconnectInternal()
            }
        }
    }

    private fun disconnectInternal() {
        _connectionState.value = IrcConnectionState.DISCONNECTED
        try {
            socket?.close()
        } catch (e: Exception) {}
        socket = null
        reader = null
        writer = null
        connectionJob?.cancel()
        connectionJob = null
    }

    private fun scheduleReconnection() {
        if (userRequestedDisconnect) return
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            // Wait 5 seconds before retrying
            addSystemMessage("กำลังจะเชื่อมต่อใหม่ในอีก 5 วินาที...")
            kotlinx.coroutines.delay(5000)
            if (!userRequestedDisconnect && _connectionState.value != IrcConnectionState.CONNECTED && _connectionState.value != IrcConnectionState.CONNECTING) {
                withContext(Dispatchers.Main) {
                    connect(lastNick, lastServer, lastPort)
                }
            }
        }
    }

    fun sendChatMessage(target: String, message: String) {
        val trimmedMsg = message.trim()
        if (trimmedMsg.isEmpty()) return

        if (_connectionState.value != IrcConnectionState.CONNECTED) {
            addSystemMessage("ข้อผิดพลาด: ยังไม่ได้เชื่อมต่อเซิร์ฟเวอร์")
            return
        }

        if (trimmedMsg.startsWith("/")) {
            handleCommand(trimmedMsg)
        } else {
            sendRaw("PRIVMSG $target :$trimmedMsg")
            addMessage(IrcMessage(sender = _currentNick.value, target = target, text = trimmedMsg))
        }
    }

    fun handleCommand(cmd: String) {
        val parts = cmd.trim().split(" ", limit = 2)
        val commandName = parts[0].uppercase()
        val arg = if (parts.size > 1) parts[1] else ""

        when (commandName) {
            "/JOIN" -> {
                if (arg.isNotEmpty()) {
                    val channel = if (arg.startsWith("#")) arg else "#$arg"
                    sendRaw("JOIN $channel")
                } else {
                    addSystemMessage("วิธีใช้: /join #ชื่อห้อง")
                }
            }
            "/PART" -> {
                val channel = if (arg.isNotEmpty()) arg else _currentChannel.value
                sendRaw("PART $channel")
            }
            "/NICK" -> {
                if (arg.isNotEmpty()) {
                    sendRaw("NICK $arg")
                } else {
                    addSystemMessage("วิธีใช้: /nick ชื่อเล่นใหม่")
                }
            }
            "/ME" -> {
                if (arg.isNotEmpty()) {
                    val channel = _currentChannel.value
                    sendRaw("PRIVMSG $channel :\u0001ACTION $arg\u0001")
                    addMessage(IrcMessage(sender = "*", target = channel, text = "${_currentNick.value} $arg"))
                } else {
                    addSystemMessage("วิธีใช้: /me ข้อความการกระทำ")
                }
            }
            "/QUIT" -> {
                sendRaw("QUIT :$arg")
                disconnectInternal()
            }
            "/MSG" -> {
                val msgParts = arg.split(" ", limit = 2)
                if (msgParts.size == 2) {
                    val target = msgParts[0]
                    val text = msgParts[1]
                    sendRaw("PRIVMSG $target :$text")
                    addMessage(IrcMessage(sender = "${_currentNick.value} ➔ $target", target = target, text = text))
                } else {
                    addSystemMessage("วิธีใช้: /msg ชื่อคนรับ ข้อความ")
                }
            }
            "/OP" -> {
                if (arg.isNotEmpty()) {
                    sendRaw("MODE ${_currentChannel.value} +o $arg")
                } else {
                    addSystemMessage("วิธีใช้: /op ชื่อเล่น")
                }
            }
            "/DEOP" -> {
                if (arg.isNotEmpty()) {
                    sendRaw("MODE ${_currentChannel.value} -o $arg")
                } else {
                    addSystemMessage("วิธีใช้: /deop ชื่อเล่น")
                }
            }
            "/VOICE" -> {
                if (arg.isNotEmpty()) {
                    sendRaw("MODE ${_currentChannel.value} +v $arg")
                } else {
                    addSystemMessage("วิธีใช้: /voice ชื่อเล่น")
                }
            }
            "/DEVOICE" -> {
                if (arg.isNotEmpty()) {
                    sendRaw("MODE ${_currentChannel.value} -v $arg")
                } else {
                    addSystemMessage("วิธีใช้: /devoice ชื่อเล่น")
                }
            }
            "/KICK" -> {
                if (arg.isNotEmpty()) {
                    sendRaw("KICK ${_currentChannel.value} $arg :Moderation")
                } else {
                    addSystemMessage("วิธีใช้: /kick ชื่อเล่น")
                }
            }
            "/BAN" -> {
                if (arg.isNotEmpty()) {
                    sendRaw("MODE ${_currentChannel.value} +b $arg!*@*")
                } else {
                    addSystemMessage("วิธีใช้: /ban ชื่อเล่น")
                }
            }
            "/UNBAN" -> {
                if (arg.isNotEmpty()) {
                    sendRaw("MODE ${_currentChannel.value} -b $arg!*@*")
                } else {
                    addSystemMessage("วิธีใช้: /unban ชื่อเล่น")
                }
            }
            else -> {
                sendRaw(cmd.substring(1))
            }
        }
    }

    fun sendRaw(line: String) {
        scope.launch {
            try {
                writer?.let {
                    it.write("$line\r\n")
                    it.flush()
                    Log.d("IrcClient", "SENT: $line")
                }
            } catch (e: Exception) {
                Log.e("IrcClient", "Error sending line: $line", e)
                addSystemMessage("ล้มเหลวในการส่งข้อความ: ${e.localizedMessage}")
            }
        }
    }

    private suspend fun listenLoop() {
        withContext(Dispatchers.IO) {
            try {
                var line: String?
                while (reader?.readLine().also { line = it } != null) {
                    val currentLine = line ?: break
                    Log.d("IrcClient", "RECV: $currentLine")
                    parseIrcLine(currentLine)
                }
            } catch (e: Exception) {
                Log.e("IrcClient", "Error in listen loop", e)
            } finally {
                withContext(Dispatchers.Main) {
                    if (_connectionState.value == IrcConnectionState.CONNECTED || _connectionState.value == IrcConnectionState.CONNECTING) {
                        if (!userRequestedDisconnect) {
                            addSystemMessage("การเชื่อมต่อเซิร์ฟเวอร์ขาดหาย กำลังพยายามเชื่อมต่อใหม่...")
                        } else {
                            addSystemMessage("การเชื่อมต่อเซิร์ฟเวอร์ขาดหาย...")
                        }
                        disconnectInternal()
                    }
                    if (!userRequestedDisconnect) {
                        scheduleReconnection()
                    }
                }
            }
        }
    }

    private fun parseIrcLine(line: String) {
        try {
            if (line.startsWith("PING ")) {
                val server = line.substring(5)
                sendRaw("PONG $server")
                return
            }

            var prefix = ""
            var remaining = line

            if (line.startsWith(":")) {
                val spaceIndex = line.indexOf(' ')
                if (spaceIndex != -1) {
                    prefix = line.substring(1, spaceIndex)
                    remaining = line.substring(spaceIndex + 1)
                }
            }

            val parts = remaining.split(" :", limit = 2)
            val mainParts = parts[0].trim().split(" ")
            val command = mainParts[0].uppercase()
            val params = mainParts.subList(1, mainParts.size)
            val trailing = if (parts.size > 1) parts[1] else ""

            val senderNick = if (prefix.contains("!")) {
                prefix.substringBefore("!")
            } else {
                prefix
            }

            when (command) {
                "PRIVMSG" -> {
                    val target = if (params.isNotEmpty()) params[0] else ""
                    val isAction = trailing.startsWith("\u0001ACTION ") && trailing.endsWith("\u0001")
                    val messageText = if (isAction) {
                        trailing.substring(8, trailing.length - 1)
                    } else {
                        trailing
                    }
                    
                    val dispSender = if (isAction) "*" else senderNick
                    val dispText = if (isAction) "$senderNick $messageText" else messageText

                    val msgTarget = if (target == _currentNick.value && !target.startsWith("#")) {
                        senderNick
                    } else {
                        target
                    }

                    if (target == _currentNick.value && !target.startsWith("#")) {
                        if (!_joinedChannels.value.contains(senderNick)) {
                            _joinedChannels.value = _joinedChannels.value + senderNick
                        }
                    }

                    addMessage(IrcMessage(sender = dispSender, target = msgTarget, text = dispText))

                    // Trigger tag notification
                    if (senderNick != _currentNick.value) {
                        val currentNickName = _currentNick.value
                        val isMention = messageText.contains(currentNickName, ignoreCase = true)
                        val isPrivateMessage = target.isNotEmpty() && !target.startsWith("#") && target == currentNickName
                        if (isMention || isPrivateMessage) {
                            val notifyTarget = if (isPrivateMessage) senderNick else target
                            showNotification(senderNick, messageText, notifyTarget)
                        }
                    }
                }
                "NOTICE" -> {
                    val target = if (params.isNotEmpty()) params[0] else ""
                    if (target.startsWith("#")) {
                        addMessage(IrcMessage(sender = senderNick, target = target, text = "📢 [Notice] $trailing"))
                    } else {
                        addMessage(IrcMessage(sender = if (senderNick.isNotEmpty()) senderNick else "Notice", target = "*Status*", text = trailing))
                    }
                }
                "JOIN" -> {
                    val channel = if (trailing.isNotEmpty()) trailing else (if (params.isNotEmpty()) params[0] else "")
                    if (channel.isNotEmpty()) {
                        if (senderNick == _currentNick.value) {
                            _joinedChannels.value = _joinedChannels.value + channel
                            _currentChannel.value = channel
                            addSystemMessage("คุณได้เข้าร่วมห้องแชท $channel แล้ว")
                        } else {
                            addMessage(IrcMessage(sender = "System", target = channel, text = "➡ $senderNick เข้าสู่ห้องแชท", isSystem = true))
                            val currentList = _channelUsers.value[channel] ?: emptySet()
                            val cleaned = currentList.filter { getCleanNick(it) != senderNick }.toSet()
                            updateChannelUsers(channel, cleaned + senderNick)
                        }
                        sendRaw("NAMES $channel")
                    }
                }
                "PART" -> {
                    val channel = if (params.isNotEmpty()) params[0] else trailing
                    if (channel.isNotEmpty()) {
                        if (senderNick == _currentNick.value) {
                            _joinedChannels.value = _joinedChannels.value - channel
                            if (_currentChannel.value == channel) {
                                _currentChannel.value = _joinedChannels.value.firstOrNull() ?: ""
                            }
                            addSystemMessage("คุณได้ออกจากห้องแชท $channel แล้ว")
                        } else {
                            addMessage(IrcMessage(sender = "System", target = channel, text = "⬅ $senderNick ออกจากห้องแชท", isSystem = true))
                            val currentList = _channelUsers.value[channel] ?: emptySet()
                            val cleaned = currentList.filter { getCleanNick(it) != senderNick }.toSet()
                            updateChannelUsers(channel, cleaned)
                        }
                    }
                }
                "QUIT" -> {
                    val reason = trailing
                    _joinedChannels.value.forEach { chan ->
                        val users = _channelUsers.value[chan] ?: emptySet()
                        if (users.any { getCleanNick(it) == senderNick }) {
                            addMessage(IrcMessage(sender = "System", target = chan, text = "❌ $senderNick ออกจากการเชื่อมต่อ ($reason)", isSystem = true))
                            val cleaned = users.filter { getCleanNick(it) != senderNick }.toSet()
                            updateChannelUsers(chan, cleaned)
                        }
                    }
                }
                "NICK" -> {
                    val newNick = if (trailing.isNotEmpty()) trailing else (if (params.isNotEmpty()) params[0] else "")
                    if (newNick.isNotEmpty()) {
                        if (senderNick == _currentNick.value) {
                            _currentNick.value = newNick
                            addSystemMessage("เปลี่ยนชื่อเล่นเป็น: $newNick")
                        } else {
                            _joinedChannels.value.forEach { chan ->
                                val users = _channelUsers.value[chan] ?: emptySet()
                                val oldEntry = users.firstOrNull { getCleanNick(it) == senderNick }
                                if (oldEntry != null) {
                                    val prefix = getPrefix(oldEntry)
                                    val newEntry = prefix + newNick
                                    addMessage(IrcMessage(sender = "System", target = chan, text = "✎ $senderNick เปลี่ยนชื่อเล่นเป็น $newNick", isSystem = true))
                                    val cleaned = users.filter { getCleanNick(it) != senderNick }.toSet()
                                    updateChannelUsers(chan, cleaned + newEntry)
                                }
                            }
                        }
                    }
                }
                "KICK" -> {
                    val channel = params.getOrNull(0) ?: ""
                    val kickedUser = params.getOrNull(1) ?: ""
                    val reason = if (trailing.isNotEmpty()) trailing else (params.getOrNull(2) ?: "")
                    if (channel.isNotEmpty() && kickedUser.isNotEmpty()) {
                        if (kickedUser == _currentNick.value) {
                            _joinedChannels.value = _joinedChannels.value - channel
                            if (_currentChannel.value == channel) {
                                _currentChannel.value = _joinedChannels.value.firstOrNull() ?: ""
                            }
                            addSystemMessage("คุณถูกเตะออกจากห้องแชท $channel โดย $senderNick ($reason)")
                        } else {
                            addMessage(IrcMessage(sender = "System", target = channel, text = "⚡ $kickedUser ถูกเตะออกจากห้องแชทโดย $senderNick ($reason)", isSystem = true))
                            val currentList = _channelUsers.value[channel] ?: emptySet()
                            val cleaned = currentList.filter { getCleanNick(it) != kickedUser }.toSet()
                            updateChannelUsers(channel, cleaned)
                        }
                    }
                }
                "KILL" -> {
                    val killedUser = params.getOrNull(0) ?: ""
                    val reason = trailing
                    if (killedUser.isNotEmpty()) {
                        if (killedUser == _currentNick.value) {
                            disconnect()
                            addSystemMessage("คุณถูก Kill จากเซิร์ฟเวอร์ ($reason)")
                        } else {
                            _joinedChannels.value.forEach { chan ->
                                val users = _channelUsers.value[chan] ?: emptySet()
                                val oldEntry = users.firstOrNull { getCleanNick(it) == killedUser }
                                if (oldEntry != null) {
                                    addMessage(IrcMessage(sender = "System", target = chan, text = "☠ $killedUser ถูกตัดการเชื่อมต่อจากระบบ (KILL) ($reason)", isSystem = true))
                                    val cleaned = users.filter { getCleanNick(it) != killedUser }.toSet()
                                    updateChannelUsers(chan, cleaned)
                                }
                            }
                        }
                    }
                }
                "MODE" -> {
                    val channel = params.getOrNull(0) ?: ""
                    val mode = params.getOrNull(1) ?: ""
                    val targetUser = params.getOrNull(2) ?: ""
                    if (channel.startsWith("#") && targetUser.isNotEmpty() && mode.length >= 2) {
                        val isAdd = mode.startsWith("+")
                        val modeChar = mode.substring(1, 2)
                        
                        if (modeChar == "b") {
                            val actionStr = if (isAdd) "ถูกแบน (BAN)" else "ถูกปลดแบน (UNBAN)"
                            addMessage(IrcMessage(sender = "System", target = channel, text = "🚫 $targetUser $actionStr ในห้องแชทนี้", isSystem = true))
                        } else {
                            val matchingPrefix = when (modeChar) {
                                "q" -> "~"
                                "a" -> "&"
                                "o" -> "@"
                                "h" -> "%"
                                "v" -> "+"
                                else -> ""
                            }
                            
                            if (matchingPrefix.isNotEmpty()) {
                                val currentList = _channelUsers.value[channel] ?: emptySet()
                                val oldEntry = currentList.firstOrNull { getCleanNick(it) == targetUser }
                                if (oldEntry != null) {
                                    val newEntry = if (isAdd) {
                                        matchingPrefix + targetUser
                                    } else {
                                        targetUser
                                    }
                                    val cleaned = currentList.filter { getCleanNick(it) != targetUser }.toSet()
                                    updateChannelUsers(channel, cleaned + newEntry)
                                    val actionName = if (isAdd) "แต่งตั้งสิทธิ์" else "ลดสิทธิ์"
                                    val roleName = when (matchingPrefix) {
                                        "~" -> "Owner"
                                        "&" -> "Admin"
                                        "@" -> "Operator"
                                        "%" -> "Half-Op"
                                        "+" -> "Voice"
                                        else -> ""
                                    }
                                    addMessage(IrcMessage(sender = "System", target = channel, text = "⚙ $senderNick ได้ $actionName $roleName ให้กับ $targetUser", isSystem = true))
                                }
                            }
                        }
                    }
                }
                "321" -> {
                    tempChannelList.clear()
                    _isFetchingChannelList.value = true
                }
                "322" -> {
                    _isFetchingChannelList.value = true
                    val channelName = params.getOrNull(1) ?: ""
                    val usersCount = params.getOrNull(2)?.toIntOrNull() ?: 0
                    val topic = trailing
                    if (channelName.isNotEmpty()) {
                        tempChannelList.add(IrcChannelInfo(channelName, usersCount, topic))
                    }
                }
                "323" -> {
                    _serverChannelsList.value = tempChannelList.toList()
                    _isFetchingChannelList.value = false
                }
                "353" -> {
                    val channel = params.getOrNull(2) ?: ""
                    if (channel.isNotEmpty()) {
                        val userList = trailing.split(" ")
                            .filter { it.isNotEmpty() }
                            .toSet()

                        val currentList = _channelUsers.value[channel] ?: emptySet()
                        val currentCleaned = currentList.filter { oldUser ->
                            val cleanOld = getCleanNick(oldUser)
                            userList.none { getCleanNick(it) == cleanOld }
                        }.toSet()
                        updateChannelUsers(channel, currentCleaned + userList)
                    }
                }
                "366" -> {
                    // End of NAMES list.
                }
                "CAP" -> {
                    if (params.size >= 2 && params[1].uppercase() == "ACK" && trailing.contains("sasl")) {
                        sendRaw("AUTHENTICATE PLAIN")
                    }
                }
                "AUTHENTICATE" -> {
                    if (params.firstOrNull() == "+" || trailing == "+") {
                        val user = if (_loginPassword.value.isNotEmpty()) _currentNick.value else _saslUsername.value
                        val pass = if (_loginPassword.value.isNotEmpty()) _loginPassword.value else _saslPassword.value
                        val authStr = "\u0000$user\u0000$pass"
                        val base64 = android.util.Base64.encodeToString(authStr.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
                        sendRaw("AUTHENTICATE $base64")
                    }
                }
                "903" -> {
                    addMessage(IrcMessage(sender = "System", target = "*Status*", text = "ยืนยันตัวตน SASL สำเร็จ"))
                    sendRaw("CAP END")
                }
                "904", "905" -> {
                    addMessage(IrcMessage(sender = "System", target = "*Status*", text = "ยืนยันตัวตน SASL ล้มเหลว"))
                    if (_loginPassword.value.isNotEmpty()) {
                        lastConnectSaslFailed = true
                    }
                    sendRaw("CAP END")
                }
                "001", "002", "003", "004", "005", "251", "252", "253", "254", "255", "396", "372", "375", "376" -> {
                    addMessage(IrcMessage(sender = "Server", target = "*Status*", text = trailing))
                    if (command == "001") {
                        if (_loginPassword.value.isNotEmpty() && lastConnectSaslFailed) {
                            addMessage(IrcMessage(sender = "System", target = "*Status*", text = "เนื่องจากยืนยัน SASL ไม่ผ่าน ระบบจะพยายามทำการลงทะเบียนชื่อเล่นให้ท่านโดยใช้อีเมล์ user@thaiirc.com..."))
                            sendRaw("PRIVMSG NickServ :REGISTER ${_loginPassword.value} user@thaiirc.com")
                            lastConnectSaslFailed = false
                        }
                        scope.launch {
                            kotlinx.coroutines.delay(1000)
                            
                            val channelsToJoin = mutableSetOf<String>()
                            
                            if (_autoJoinChannels.value.isNotEmpty()) {
                                _autoJoinChannels.value.split(",")
                                    .map { it.trim() }
                                    .filter { it.isNotEmpty() }
                                    .forEach {
                                        val chan = if (it.startsWith("#")) it else "#$it"
                                        channelsToJoin.add(chan)
                                    }
                            }

                            if (_currentChannel.value.isNotEmpty()) {
                                channelsToJoin.add(_currentChannel.value)
                            }
                            
                            if (_rejoinOpenedChannels.value) {
                                _joinedChannels.value.filter { it.isNotEmpty() }.forEach {
                                    channelsToJoin.add(it)
                                }
                            }
                            
                            if (channelsToJoin.isNotEmpty()) {
                                val firstChan = channelsToJoin.first()
                                _currentChannel.value = firstChan
                                
                                channelsToJoin.forEach { chan ->
                                    sendRaw("JOIN $chan")
                                }
                            } else {
                                _currentChannel.value = ""
                            }
                            
                            if (_autoRunCommands.value.isNotEmpty()) {
                                kotlinx.coroutines.delay(1000)
                                _autoRunCommands.value.split("\n")
                                    .map { it.trim() }
                                    .filter { it.isNotEmpty() }
                                    .forEach { cmd ->
                                        if (cmd.startsWith("/")) {
                                            handleCommand(cmd)
                                        } else {
                                            sendRaw(cmd)
                                        }
                                    }
                            }
                        }
                    }
                }
                "433" -> {
                    addMessage(IrcMessage(sender = "System", target = "*Status*", text = "ชื่อเล่น ${_currentNick.value} ถูกใช้งานอยู่แล้ว กำลังสุ่มชื่อเล่นอื่นแทน..."))
                    val baseNick = _currentNick.value.replace("_", "")
                    val altNick = if (baseNick.length > 5) {
                        baseNick.take(5) + (100..999).random()
                    } else {
                        baseNick + (100..999).random()
                    }
                    _currentNick.value = altNick
                    sendRaw("NICK $altNick")
                }
                else -> {
                    if (trailing.isNotEmpty()) {
                        addMessage(IrcMessage(sender = "Server", target = "*Status*", text = trailing))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("IrcClient", "Error parsing: $line", e)
        }
    }

    private fun addSystemMessage(text: String) {
        val msg = IrcMessage(
            sender = "System",
            target = null,
            text = text,
            isSystem = true
        )
        _messages.value = _messages.value + msg
    }

    private fun addMessage(msg: IrcMessage) {
        _messages.value = _messages.value + msg
    }

    private fun updateChannelUsers(channel: String, users: Set<String>) {
        val currentMap = _channelUsers.value.toMutableMap()
        currentMap[channel] = users
        _channelUsers.value = currentMap
    }

    private fun createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val ctx = context ?: return
            val name = "IRC Mention Notification"
            val descriptionText = "แจ้งเตือนเมื่อมีผู้พูดถึงหรือแท็กชื่อเล่นของคุณในห้องแชท"
            val importance = android.app.NotificationManager.IMPORTANCE_DEFAULT
            val channel = android.app.NotificationChannel("irc_mention_channel", name, importance).apply {
                description = descriptionText
            }
            val notificationManager: android.app.NotificationManager =
                ctx.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showNotification(sender: String, messageText: String, target: String) {
        val ctx = context ?: return
        if (!_mentionNotificationEnabled.value) return

        createNotificationChannel()

        val intent = android.content.Intent(ctx, MainActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            ctx, 
            0, 
            intent, 
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val builder = androidx.core.app.NotificationCompat.Builder(ctx, "irc_mention_channel")
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("คุณถูกแท็กในห้อง $target")
            .setContentText("$sender: $messageText")
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        val notificationManager = androidx.core.app.NotificationManagerCompat.from(ctx)
        try {
            if (android.os.Build.VERSION.SDK_INT < 33 || 
                androidx.core.content.ContextCompat.checkSelfPermission(
                    ctx, 
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
            }
        } catch (e: SecurityException) {
            Log.e("IrcClient", "SecurityException showing notification", e)
        }
    }
}
