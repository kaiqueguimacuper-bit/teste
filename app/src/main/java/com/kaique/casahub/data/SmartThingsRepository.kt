package com.kaique.casahub.data

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Cliente da API REST oficial da SmartThings (https://api.smartthings.com/v1).
 * Autentica com um token de acesso pessoal (PAT) criado em https://account.smartthings.com/tokens.
 */
class SmartThingsRepository(
    private val token: String,
    private val base: String = "https://api.smartthings.com/v1",
) : HomeRepository {

    override val isDemo = false

    // ---------- HTTP ----------

    private val log = ArrayDeque<ApiLogEntry>()

    override fun diagnostics(): List<ApiLogEntry> = synchronized(log) { log.toList().asReversed() }

    private fun record(method: String, url: String, code: Int, started: Long, detail: String?) {
        val path = url.removePrefix(base)
        synchronized(log) {
            log.addLast(ApiLogEntry(System.currentTimeMillis(), method, path, code, System.currentTimeMillis() - started, detail))
            while (log.size > 60) log.removeFirst()
        }
    }

    /** Extrai a mensagem de erro que a SmartThings manda no corpo da resposta. */
    private fun errorDetail(text: String): String? = runCatching {
        val err = JSONObject(text).optJSONObject("error") ?: return@runCatching null
        val details = err.optJSONArray("details")
        val first = details?.optJSONObject(0)?.optString("message")?.takeIf { it.isNotBlank() }
        first ?: err.optString("message").takeIf { it.isNotBlank() }
    }.getOrNull()

    private suspend fun request(method: String, url: String, body: JSONObject? = null): JSONObject =
        withContext(Dispatchers.IO) {
            val started = System.currentTimeMillis()
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 7_000
                readTimeout = if (method == "POST") 9_000 else 12_000
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Accept", "application/json")
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
            }
            // Se a chamada for cancelada (tempo esgotado, tela fechada), derruba a conexão na hora:
            // sem isso a leitura bloqueada seguraria a fila de comandos até o timeout do socket.
            val guard = launch(start = CoroutineStart.UNDISPATCHED) {
                try { awaitCancellation() } finally { conn.disconnect() }
            }
            try {
                if (body != null) conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (code !in 200..299) {
                    val detail = errorDetail(text)
                    record(method, url, code, started, detail ?: text.take(200))
                    val msg = when (code) {
                        401 -> "Token inválido ou expirado. Gere um novo token."
                        403 -> "O token não tem permissão para isso. Crie o token marcando todas as permissões."
                        404 -> "Não encontrado na SmartThings."
                        409 -> "O aparelho está desligado ou sem conexão com a SmartThings."
                        422 -> "Comando recusado pelo aparelho" + (detail?.let { ": $it" } ?: ".")
                        429 -> "Muitos comandos seguidos. Aguarde um instante."
                        in 500..599 -> "A SmartThings está instável agora ($code). Tente de novo."
                        else -> "Erro da SmartThings ($code)" + (detail?.let { ": $it" } ?: "")
                    }
                    throw ApiException(code, msg)
                }
                record(method, url, code, started, null)
                if (text.isBlank() || !text.trimStart().startsWith("{")) JSONObject() else JSONObject(text)
            } catch (e: IOException) {
                ensureActive() // cancelada de propósito: não é falta de internet
                record(method, url, -1, started, e.javaClass.simpleName + ": " + e.message)
                throw ApiException(-1, "Sem conexão com a SmartThings. Verifique a internet.")
            } finally {
                guard.cancel()
                conn.disconnect()
            }
        }

    /** Busca todas as páginas de uma lista (campo items + _links.next.href). */
    private suspend fun list(url: String): List<JSONObject> {
        val out = mutableListOf<JSONObject>()
        var next: String? = url
        var guard = 0
        while (next != null && guard++ < 20) {
            val page = request("GET", next)
            val items = page.optJSONArray("items") ?: JSONArray()
            for (i in 0 until items.length()) items.optJSONObject(i)?.let(out::add)
            next = page.optJSONObject("_links")?.optJSONObject("next")?.optString("href")?.takeIf { it.isNotBlank() }
        }
        return out
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    // ---------- API ----------

    override suspend fun locations(): List<Location> =
        list("$base/locations").map { Location(it.optString("locationId"), it.optString("name", "Minha casa")) }

    override suspend fun rooms(locationId: String): List<Room> =
        list("$base/locations/${enc(locationId)}/rooms").map { Room(it.optString("roomId"), it.optString("name")) }

    override suspend fun devices(locationId: String): List<Device> =
        list("$base/devices?locationId=${enc(locationId)}&includeHealth=true").map { d ->
            val caps = mutableSetOf<String>()
            val capComponents = mutableMapOf<String, String>()
            val capVersions = mutableMapOf<String, Int>()
            var category: String? = null
            val comps = d.optJSONArray("components") ?: JSONArray()
            // "main" primeiro: se a mesma capacidade aparecer em outro componente, vale a do main.
            val ordered = (0 until comps.length()).mapNotNull { comps.optJSONObject(it) }
                .sortedBy { if (it.optString("id") == "main") 0 else 1 }
            for (comp in ordered) {
                val compId = comp.optString("id").ifBlank { "main" }
                val cs = comp.optJSONArray("capabilities") ?: JSONArray()
                for (j in 0 until cs.length()) {
                    val c = cs.optJSONObject(j) ?: continue
                    val id = c.optString("id").ifBlank { null } ?: continue
                    if (id !in caps) {
                        caps += id
                        capComponents[id] = compId
                        capVersions[id] = c.optInt("version", 1)
                    }
                }
                if (category == null) {
                    val cats = comp.optJSONArray("categories")
                    if (cats != null && cats.length() > 0) category = cats.optJSONObject(0)?.optString("name")
                }
            }
            val label = d.optString("label").ifBlank { d.optString("name", "Dispositivo") }
            val health = d.optJSONObject("healthState")?.optString("state")
            Device(
                id = d.optString("deviceId"),
                name = label,
                roomId = d.optString("roomId").ifBlank { null },
                capabilities = caps,
                category = category,
                state = DeviceState(online = health?.let { if (it == "UNKNOWN") null else it == "ONLINE" }),
                capComponents = capComponents,
                capVersions = capVersions,
            )
        }

    override suspend fun status(deviceId: String): DeviceState {
        val json = request("GET", "$base/devices/${enc(deviceId)}/status")
        return parseStatus(mergeComponents(json.optJSONObject("components")))
    }

    override suspend fun command(deviceId: String, cmd: Command) {
        val args = JSONArray().apply { cmd.args.forEach { put(if (it is Map<*, *>) JSONObject(it) else it) } }
        val body = JSONObject().put(
            "commands",
            JSONArray().put(
                JSONObject()
                    .put("component", cmd.component)
                    .put("capability", cmd.capability)
                    .put("command", cmd.command)
                    .put("arguments", args),
            ),
        )
        val res = request("POST", "$base/devices/${enc(deviceId)}/commands", body)
        val results = res.optJSONArray("results")
        if (results != null) {
            for (i in 0 until results.length()) {
                val st = results.optJSONObject(i)?.optString("status").orEmpty()
                if (st.equals("FAILED", true)) throw ApiException(422, "O aparelho não executou o comando.")
            }
        }
    }

    override suspend fun refreshDevice(deviceId: String, component: String) {
        command(deviceId, Command(Caps.REFRESH, "refresh", component = component))
    }

    private val argCache = mutableMapOf<String, List<String>>()

    override suspend fun commandArgValues(capability: String, version: Int, command: String): List<String> {
        val key = "$capability/$version/$command"
        argCache[key]?.let { return it }
        val def = request("GET", "$base/capabilities/${enc(capability)}/$version")
        val args = def.optJSONObject("commands")?.optJSONObject(command)?.optJSONArray("arguments") ?: JSONArray()
        val values = mutableListOf<String>()
        for (i in 0 until args.length()) {
            val en = args.optJSONObject(i)?.optJSONObject("schema")?.optJSONArray("enum") ?: continue
            for (j in 0 until en.length()) en.optString(j).takeIf { it.isNotBlank() }?.let(values::add)
            if (values.isNotEmpty()) break
        }
        argCache[key] = values
        return values
    }

    override suspend fun scenes(locationId: String): List<Scene> =
        list("$base/scenes?locationId=${enc(locationId)}").map {
            Scene(it.optString("sceneId"), it.optString("sceneName", "Cena"))
        }

    override suspend fun runScene(sceneId: String) {
        request("POST", "$base/scenes/${enc(sceneId)}/execute", JSONObject())
    }

    companion object {
        private fun JSONObject.attr(cap: String, attr: String): Any? {
            val v = optJSONObject(cap)?.optJSONObject(attr)?.opt("value")
            return if (v == null || v == JSONObject.NULL) null else v
        }

        private fun JSONObject.unit(cap: String, attr: String): String? =
            optJSONObject(cap)?.optJSONObject(attr)?.optString("unit")?.takeIf { it.isNotBlank() }

        private fun Any?.strings(): List<String> {
            val arr = this as? JSONArray ?: return emptyList()
            return (0 until arr.length()).mapNotNull { i ->
                when (val v = arr.opt(i)) {
                    is String -> v
                    is JSONObject -> v.optString("id").ifBlank { null }
                    else -> null
                }
            }
        }

        private fun Any?.num(): Double? = when (this) {
            is Number -> toDouble()
            is String -> toDoubleOrNull()
            else -> null
        }

        /** Junta as capacidades de todos os componentes; as do "main" têm prioridade. */
        fun mergeComponents(components: JSONObject?): JSONObject {
            val merged = JSONObject()
            if (components == null) return merged
            val names = components.keys().asSequence().toList().sortedBy { if (it == "main") 1 else 0 }
            for (n in names) {
                val comp = components.optJSONObject(n) ?: continue
                for (cap in comp.keys()) {
                    if (n != "main" && merged.has(cap)) continue
                    merged.put(cap, comp.opt(cap))
                }
            }
            return merged
        }

        fun parseStatus(m: JSONObject): DeviceState = DeviceState(
            online = (m.attr(Caps.HEALTH, "healthStatus") as? String)?.let { it.equals("online", true) },
            switchOn = (m.attr(Caps.SWITCH, "switch") as? String)?.let { it == "on" },
            level = m.attr(Caps.LEVEL, "level").num()?.toInt(),
            colorTemp = m.attr(Caps.COLOR_TEMP, "colorTemperature").num()?.toInt(),
            hue = m.attr(Caps.COLOR, "hue").num(),
            saturation = m.attr(Caps.COLOR, "saturation").num(),
            temperature = m.attr(Caps.TEMP, "temperature").num(),
            tempUnit = m.unit(Caps.TEMP, "temperature"),
            humidity = m.attr(Caps.HUMIDITY, "humidity").num(),
            heatingSetpoint = m.attr(Caps.THERMOSTAT_HEAT, "heatingSetpoint").num(),
            coolingSetpoint = m.attr(Caps.THERMOSTAT_COOL, "coolingSetpoint").num(),
            thermostatMode = m.attr(Caps.THERMOSTAT_MODE, "thermostatMode") as? String,
            acMode = m.attr(Caps.AC_MODE, "airConditionerMode") as? String,
            acModes = m.attr(Caps.AC_MODE, "supportedAcModes").strings(),
            fanMode = m.attr(Caps.AC_FAN, "fanMode") as? String,
            fanModes = m.attr(Caps.AC_FAN, "supportedAcFanModes").strings(),
            energy = m.attr(Caps.ENERGY, "energy").num(),
            channel = m.attr(Caps.CHANNEL, "tvChannel")?.toString()?.takeIf { it.isNotBlank() },
            channelName = (m.attr(Caps.CHANNEL, "tvChannelName") as? String)?.takeIf { it.isNotBlank() },
            inputSource = (m.attr(Caps.INPUT_SAMSUNG, "inputSource") ?: m.attr(Caps.INPUT, "inputSource")) as? String,
            inputSources = m.attr(Caps.INPUT_SAMSUNG, "supportedInputSourcesMap").strings()
                .ifEmpty { m.attr(Caps.INPUT, "supportedInputSources").strings() },
            playback = m.attr(Caps.PLAYBACK, "playbackStatus") as? String,
            locked = (m.attr(Caps.LOCK, "lock") as? String)?.let { it == "locked" },
            contactOpen = (m.attr(Caps.CONTACT, "contact") as? String)?.let { it == "open" },
            motion = (m.attr(Caps.MOTION, "motion") as? String)?.let { it == "active" },
            waterLeak = (m.attr(Caps.WATER, "water") as? String)?.let { it == "wet" },
            battery = m.attr(Caps.BATTERY, "battery").num()?.toInt(),
            power = m.attr(Caps.POWER, "power").num(),
            volume = m.attr(Caps.VOLUME, "volume").num()?.toInt(),
            muted = (m.attr(Caps.MUTE, "mute") as? String)?.let { it == "muted" || it == "mute" },
            shadeLevel = m.attr(Caps.SHADE_LEVEL, "shadeLevel").num()?.toInt(),
            shadeState = m.attr(Caps.SHADE, "windowShade") as? String,
        )
    }
}
