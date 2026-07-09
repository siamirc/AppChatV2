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

class IrcClient(private val context: android.content.Context? = null) {
    private val _connectionState = MutableStateFlow(IrcConnectionState.DISCONNECTED)
    val connectionState: StateFlow<IrcConnectionState> = _connectionState.asStateFlow()

    private val _messages = MutableStateFlow<List<IrcMessage>>(emptyList())
    val messages: StateFlow<List<IrcMessage>> = _messages.asStateFlow()

    private val _currentChannel = MutableStateFlow("#thaiirc")
    val currentChannel: StateFlow<String> = _currentChannel.asStateFlow()

    private val _joinedChannels = MutableStateFlow<Set<String>>(setOf("#thaiirc"))
    val joinedChannels: StateFlow<Set<String>> = _joinedChannels.asStateFlow()

    private val _channelUsers = MutableStateFlow<Map<String, Set<String>>>(emptyMap()) // Channel -> Users set
    val channelUsers: StateFlow<Map<String, Set<String>>> = _channelUsers.asStateFlow()

    private val _currentNick = MutableStateFlow("Thai${(1000..9999).random()}")
    val currentNick: StateFlow<String> = _currentNick.asStateFlow()

    private val _quitMessage = MutableStateFlow("Quit: app.thaiirc.com - live radio v5.0")
    val quitMessage: StateFlow<String> = _quitMessage.asStateFlow()

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

    private val _autoJoinChannels = MutableStateFlow("#thaiirc")
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
        zncPass: String? = null
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
                val ident = "Thai${(1000..9999).random()}"
                
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

    private fun handleCommand(cmd: String) {
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

                    addMessage(IrcMessage(sender = dispSender, target = target, text = dispText))
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
                            // Remove from names
                            val currentList = _channelUsers.value[channel] ?: emptySet()
                            updateChannelUsers(channel, currentList - senderNick)
                        }
                    }
                }
                "QUIT" -> {
                    val reason = trailing
                    _joinedChannels.value.forEach { chan ->
                        val users = _channelUsers.value[chan] ?: emptySet()
                        if (users.contains(senderNick)) {
                            addMessage(IrcMessage(sender = "System", target = chan, text = "❌ $senderNick ออกจากการเชื่อมต่อ ($reason)", isSystem = true))
                            updateChannelUsers(chan, users - senderNick)
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
                                if (users.contains(senderNick)) {
                                    addMessage(IrcMessage(sender = "System", target = chan, text = "✎ $senderNick เปลี่ยนชื่อเล่นเป็น $newNick", isSystem = true))
                                    updateChannelUsers(chan, (users - senderNick) + newNick)
                                }
                            }
                        }
                    }
                }
                "353" -> {
                    // NAMREPLY format: <client> <symbol> <channel> :[prefix]user1 [prefix]user2
                    val channel = params.getOrNull(2) ?: ""
                    if (channel.isNotEmpty()) {
                        val userList = trailing.split(" ")
                            .map { it.replace(Regex("^[@+~&%]"), "") }
                            .filter { it.isNotEmpty() }
                            .toSet()

                        val currentList = _channelUsers.value[channel] ?: emptySet()
                        updateChannelUsers(channel, currentList + userList)
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
                    addSystemMessage("ยืนยันตัวตน SASL สำเร็จ")
                    sendRaw("CAP END")
                }
                "904", "905" -> {
                    addSystemMessage("ยืนยันตัวตน SASL ล้มเหลว")
                    if (_loginPassword.value.isNotEmpty()) {
                        lastConnectSaslFailed = true
                    }
                    sendRaw("CAP END")
                }
                "001", "002", "003", "004", "372", "375", "376" -> {
                    addSystemMessage(trailing)
                    if (command == "001") {
                        if (_loginPassword.value.isNotEmpty() && lastConnectSaslFailed) {
                            addSystemMessage("เนื่องจากยืนยัน SASL ไม่ผ่าน ระบบจะพยายามทำการลงทะเบียนชื่อเล่นให้ท่านโดยใช้อีเมล์ user@thaiirc.com...")
                            sendRaw("PRIVMSG NickServ :REGISTER ${_loginPassword.value} user@thaiirc.com")
                            lastConnectSaslFailed = false
                        }
                        scope.launch {
                            kotlinx.coroutines.delay(1000)
                            
                            // Collect channels to join
                            val channelsToJoin = mutableSetOf<String>()
                            
                            // 1. Configured auto-join channels
                            if (_autoJoinChannels.value.isNotEmpty()) {
                                _autoJoinChannels.value.split(",")
                                    .map { it.trim() }
                                    .filter { it.isNotEmpty() }
                                    .forEach {
                                        val chan = if (it.startsWith("#")) it else "#$it"
                                        channelsToJoin.add(chan)
                                    }
                            }

                            // 2. Also ensure current channel (entered on login screen) is added
                            if (_currentChannel.value.isNotEmpty()) {
                                channelsToJoin.add(_currentChannel.value)
                            }
                            
                            // 3. Previously joined channels (rejoin)
                            if (_rejoinOpenedChannels.value) {
                                _joinedChannels.value.filter { it.isNotEmpty() }.forEach {
                                    channelsToJoin.add(it)
                                }
                            }
                            
                            // Fallback if empty
                            if (channelsToJoin.isEmpty()) {
                                channelsToJoin.add("#thaiirc")
                            }
                            
                            // Set current channel to the first one in the list
                            val firstChan = channelsToJoin.first()
                            _currentChannel.value = firstChan
                            
                            // Join all channels
                            channelsToJoin.forEach { chan ->
                                sendRaw("JOIN $chan")
                            }
                            
                            // 3. Auto-run commands
                            if (_autoRunCommands.value.isNotEmpty()) {
                                kotlinx.coroutines.delay(1000) // Delay slightly to let JOIN complete
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
                    addSystemMessage("ชื่อเล่น ${_currentNick.value} ถูกใช้งานอยู่แล้ว กำลังสุ่มชื่อเล่นอื่นแทน...")
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
                        addSystemMessage(trailing)
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
}
