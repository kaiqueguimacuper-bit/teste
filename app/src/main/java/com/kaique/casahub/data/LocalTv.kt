package com.kaique.casahub.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Base64
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager

/*
 * Controle direto pela rede local (Wi-Fi) das TVs Samsung (Tizen, 2016+).
 * A TV expõe um WebSocket em 8002 (wss, com token) ou 8001 (ws). A tecla vai do celular
 * para a TV em poucos milissegundos, sem passar pela nuvem da SmartThings.
 */

/** Teclas da TV no protocolo local da Samsung. */
object LocalKeys {
    fun of(k: RemoteKey): String = when (k) {
        RemoteKey.UP -> "KEY_UP"
        RemoteKey.DOWN -> "KEY_DOWN"
        RemoteKey.LEFT -> "KEY_LEFT"
        RemoteKey.RIGHT -> "KEY_RIGHT"
        RemoteKey.OK -> "KEY_ENTER"
        RemoteKey.BACK -> "KEY_RETURN"
        RemoteKey.HOME -> "KEY_HOME"
        RemoteKey.MENU -> "KEY_MENU"
    }

    /** Converte um comando da SmartThings na tecla local equivalente (null = só pela nuvem). */
    fun of(c: Command): String? = when ("${c.capability}.${c.command}") {
        "audioVolume.volumeUp" -> "KEY_VOLUP"
        "audioVolume.volumeDown" -> "KEY_VOLDOWN"
        "tvChannel.channelUp" -> "KEY_CHUP"
        "tvChannel.channelDown" -> "KEY_CHDOWN"
        "audioMute.mute", "audioMute.unmute" -> "KEY_MUTE"
        "mediaPlayback.play" -> "KEY_PLAY"
        "mediaPlayback.pause" -> "KEY_PAUSE"
        "mediaPlayback.stop" -> "KEY_STOP"
        "mediaPlayback.fastForward" -> "KEY_FF"
        "mediaPlayback.rewind" -> "KEY_REWIND"
        "keypadInput.sendKey" -> (c.args.firstOrNull() as? String)?.let { a ->
            when {
                a.startsWith("NUMBER") -> "KEY_" + a.removePrefix("NUMBER")
                a == "SELECT" -> "KEY_ENTER"
                a == "BACK" -> "KEY_RETURN"
                else -> "KEY_$a"
            }
        }
        "samsungvd.remoteControl.send" -> (c.args.firstOrNull() as? String)?.let { a ->
            when (a) {
                "OK", "SELECT" -> "KEY_ENTER"
                "BACK", "RETURN" -> "KEY_RETURN"
                else -> if (a.startsWith("KEY_")) a else "KEY_$a"
            }
        }
        else -> null
    }
}

data class FoundTv(val ip: String, val name: String, val model: String?, val mac: String?)

enum class LocalState { IDLE, CONNECTING, WAITING_APPROVAL, CONNECTED, DENIED, UNREACHABLE }

/** Cliente WebSocket mínimo (RFC 6455), só o que a TV precisa: texto, ping/pong, close. */
internal class WsConnection(private val socket: Socket) {
    private val input: InputStream = BufferedInputStream(socket.getInputStream())
    private val output: OutputStream = socket.getOutputStream()
    private val rnd = SecureRandom()

    fun handshake(host: String, port: Int, path: String) {
        val key = Base64.getEncoder().encodeToString(ByteArray(16).also(rnd::nextBytes))
        val req = "GET $path HTTP/1.1\r\nHost: $host:$port\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n" +
            "Sec-WebSocket-Key: $key\r\nSec-WebSocket-Version: 13\r\n\r\n"
        output.write(req.toByteArray(Charsets.US_ASCII)); output.flush()
        val status = readLine()
        if (!status.contains(" 101")) throw IOException("TV recusou a conexão: $status")
        while (readLine().isNotEmpty()) { /* cabeçalhos */ }
    }

    private fun readLine(): String {
        val sb = StringBuilder()
        while (true) {
            val b = input.read()
            if (b < 0) throw EOFException()
            if (b == '\n'.code) return sb.toString().trimEnd('\r')
            sb.append(b.toChar())
            if (sb.length > 8192) throw IOException("cabeçalho grande demais")
        }
    }

    @Synchronized
    fun send(opcode: Int, payload: ByteArray) {
        val header = java.io.ByteArrayOutputStream()
        header.write(0x80 or opcode)
        val len = payload.size
        when {
            len < 126 -> header.write(0x80 or len)
            len < 65536 -> { header.write(0x80 or 126); header.write(len ushr 8); header.write(len and 0xFF) }
            else -> { header.write(0x80 or 127); for (i in 7 downTo 0) header.write(((len.toLong() ushr (8 * i)) and 0xFF).toInt()) }
        }
        val mask = ByteArray(4).also(rnd::nextBytes)
        header.write(mask)
        val masked = ByteArray(len) { i -> (payload[i].toInt() xor mask[i % 4].toInt()).toByte() }
        output.write(header.toByteArray() + masked)
        output.flush()
    }

    fun sendText(text: String) = send(0x1, text.toByteArray(Charsets.UTF_8))

    /** Lê a próxima mensagem de texto (responde ping, junta fragmentos). Null = conexão fechada. */
    fun readText(): String? {
        val buf = java.io.ByteArrayOutputStream()
        while (true) {
            val b0 = input.read(); if (b0 < 0) return null
            val b1 = input.read(); if (b1 < 0) return null
            val fin = b0 and 0x80 != 0
            val opcode = b0 and 0x0F
            var len = (b1 and 0x7F).toLong()
            if (len == 126L) len = ((readByte() shl 8) or readByte()).toLong()
            else if (len == 127L) { len = 0; repeat(8) { len = (len shl 8) or readByte().toLong() } }
            val mask = if (b1 and 0x80 != 0) ByteArray(4) { readByte().toByte() } else null
            if (len > 4_000_000) throw IOException("mensagem grande demais")
            val data = ByteArray(len.toInt())
            var off = 0
            while (off < data.size) {
                val n = input.read(data, off, data.size - off)
                if (n < 0) return null
                off += n
            }
            if (mask != null) for (i in data.indices) data[i] = (data[i].toInt() xor mask[i % 4].toInt()).toByte()
            when (opcode) {
                0x8 -> return null
                0x9 -> send(0xA, data)
                0xA -> {}
                0x0, 0x1, 0x2 -> {
                    buf.write(data)
                    if (fin) return buf.toString(Charsets.UTF_8.name())
                }
            }
        }
    }

    private fun readByte(): Int { val b = input.read(); if (b < 0) throw EOFException(); return b }

    fun close() = runCatching { socket.close() }
}

/**
 * Conexão permanente com uma TV. Reconecta sozinha se a TV ou o Wi-Fi caírem.
 * [send] nunca bloqueia: a tecla entra numa fila e sai no mesmo instante pela conexão aberta.
 */
class LocalTvRemote(
    val ip: String,
    private var token: String?,
    private val scope: CoroutineScope,
    private val onToken: (String) -> Unit,
    private val appName: String = "CasaHub",
    private val ports: List<Int> = listOf(8002, 8001),
    private val connectTimeoutMs: Int = 2500,
) {
    private val _state = MutableStateFlow(LocalState.IDLE)
    val state: StateFlow<LocalState> = _state

    private val outbox = Channel<Pair<String, () -> Unit>>(capacity = 64)
    private var job: Job? = null
    @Volatile private var conn: WsConnection? = null
    @Volatile private var stopped = false

    val isConnected: Boolean get() = _state.value == LocalState.CONNECTED

    fun start() {
        if (job?.isActive == true) return
        stopped = false
        job = scope.launch(Dispatchers.IO) { runLoop() }
    }

    fun stop() {
        stopped = true
        conn?.close()
        job?.cancel()
        _state.value = LocalState.IDLE
    }

    /** Envia a tecla já. Se a conexão cair no meio, chama [onFail] (o app manda pela nuvem). */
    fun send(key: String, onFail: () -> Unit = {}): Boolean {
        if (!isConnected) return false
        return outbox.trySend(key to onFail).isSuccess
    }

    private suspend fun runLoop() {
        var backoff = 500L
        while (scope.isActive && !stopped) {
            _state.value = LocalState.CONNECTING
            val ws = openAny()
            if (ws == null) {
                _state.value = LocalState.UNREACHABLE
                delay(backoff); backoff = (backoff * 2).coerceAtMost(8_000)
                continue
            }
            conn = ws
            _state.value = LocalState.WAITING_APPROVAL
            val writer = scope.launch(Dispatchers.IO) {
                for ((key, onFail) in outbox) {
                    val ok = runCatching { ws.sendText(keyMessage(key)) }.isSuccess
                    if (!ok) { onFail(); ws.close(); break }
                }
            }
            try {
                while (!stopped) {
                    val msg = ws.readText() ?: break
                    val event = runCatching { JSONObject(msg).optString("event") }.getOrDefault("")
                    when (event) {
                        "ms.channel.connect" -> {
                            val t = runCatching { JSONObject(msg).optJSONObject("data")?.optString("token") }.getOrNull()
                            if (!t.isNullOrBlank() && t != token) { token = t; onToken(t) }
                            _state.value = LocalState.CONNECTED
                            backoff = 500L
                        }
                        "ms.channel.unauthorized" -> { _state.value = LocalState.DENIED; stopped = true }
                        "ms.channel.timeOut" -> break
                    }
                }
            } catch (_: IOException) {
            } finally {
                writer.cancel()
                ws.close()
                conn = null
            }
            if (!stopped) {
                // Teclas que ficaram na fila durante a queda vão pela nuvem.
                while (true) { val p = outbox.tryReceive().getOrNull() ?: break; p.second() }
                _state.value = LocalState.CONNECTING
                delay(backoff); backoff = (backoff * 2).coerceAtMost(8_000)
            }
        }
    }

    private fun keyMessage(key: String) = JSONObject()
        .put("method", "ms.remote.control")
        .put("params", JSONObject().put("Cmd", "Click").put("DataOfCmd", key).put("Option", "false").put("TypeOfRemote", "SendRemoteKey"))
        .toString()

    private fun openAny(): WsConnection? {
        val name = Base64.getEncoder().encodeToString(appName.toByteArray())
        for (port in ports) {
            val path = "/api/v2/channels/samsung.remote.control?name=$name" + (token?.let { "&token=$it" } ?: "")
            val ws = runCatching {
                val plain = Socket().apply {
                    tcpNoDelay = true
                    connect(InetSocketAddress(ip, port), connectTimeoutMs)
                }
                val sock: Socket = if (port == 8002 || port % 1000 == 2) {
                    (trustAll().socketFactory.createSocket(plain, ip, port, true) as SSLSocket).apply {
                        soTimeout = connectTimeoutMs * 2
                        startHandshake()
                    }
                } else plain
                sock.soTimeout = connectTimeoutMs * 2
                val w = WsConnection(sock)
                w.handshake(ip, port, path)
                sock.soTimeout = 0
                w
            }.getOrNull()
            if (ws != null) return ws
        }
        return null
    }

    companion object {
        /** A TV usa certificado próprio (autoassinado). Só aceitamos isso para IPs da rede local. */
        internal fun trustAll(): SSLContext {
            val tm = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            }
            return SSLContext.getInstance("TLS").apply { init(null, arrayOf(tm), SecureRandom()) }
        }
    }
}

/** Procura TVs Samsung na rede Wi-Fi atual. */
object LocalTvFinder {
    /** Prefixos "a.b.c." das redes locais do aparelho (Wi-Fi). */
    fun localPrefixes(): List<String> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .filter { it.isSiteLocalAddress }
            .map { it.hostAddress!!.substringBeforeLast('.') + "." }
            .distinct()
    }.getOrDefault(emptyList())

    suspend fun scan(prefixes: List<String> = localPrefixes(), port: Int = 8001, timeoutMs: Int = 450): List<FoundTv> =
        withContext(Dispatchers.IO) {
            val gate = Semaphore(64)
            coroutineScope {
                prefixes.flatMap { p -> (1..254).map { "$p$it" } }
                    .map { ip -> async { gate.withPermit { probe(ip, port, timeoutMs) } } }
                    .awaitAll()
                    .filterNotNull()
            }
        }

    /** Pergunta à TV quem ela é (GET /api/v2/ na porta 8001). */
    fun probe(ip: String, port: Int = 8001, timeoutMs: Int = 450): FoundTv? = runCatching {
        Socket().use { s ->
            s.connect(InetSocketAddress(ip, port), timeoutMs)
            s.soTimeout = 1500
            s.getOutputStream().write("GET /api/v2/ HTTP/1.1\r\nHost: $ip:$port\r\nConnection: close\r\n\r\n".toByteArray())
            val text = s.getInputStream().readBytes().decodeToString()
            val body = text.substringAfter("\r\n\r\n", "")
            val json = JSONObject(body.substring(body.indexOf('{')))
            val dev = json.optJSONObject("device") ?: JSONObject()
            val type = dev.optString("type") + json.optString("type")
            if (!type.contains("TV", ignoreCase = true) && !json.optString("name").contains("Samsung", true)) return null
            FoundTv(
                ip = ip,
                name = dev.optString("name").ifBlank { json.optString("name", "TV Samsung") },
                model = dev.optString("modelName").ifBlank { null },
                mac = dev.optString("wifiMac").ifBlank { null },
            )
        }
    }.getOrNull()

    /** Liga a TV pela rede (Wake-on-LAN), quando ela está em espera. */
    fun wake(mac: String, broadcast: String = "255.255.255.255") {
        runCatching {
            val bytes = mac.split(':', '-').map { it.toInt(16).toByte() }.toByteArray()
            if (bytes.size != 6) return
            val packet = ByteArray(6) { 0xFF.toByte() } + (1..16).flatMap { bytes.toList() }.toByteArray()
            DatagramSocket().use { s ->
                s.broadcast = true
                for (port in listOf(9, 7)) s.send(DatagramPacket(packet, packet.size, InetAddress.getByName(broadcast), port))
            }
        }
    }
}
