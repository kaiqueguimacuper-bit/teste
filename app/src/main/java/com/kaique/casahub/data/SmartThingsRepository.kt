package com.kaique.casahub.data

import kotlinx.coroutines.Dispatchers
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

    private suspend fun request(method: String, url: String, body: JSONObject? = null): JSONObject =
        withContext(Dispatchers.IO) {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 10_000
                readTimeout = 15_000
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Accept", "application/json")
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
            }
            try {
                if (body != null) conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (code !in 200..299) {
                    val msg = when (code) {
                        401 -> "Token inválido ou expirado. Gere um novo token."
                        403 -> "O token não tem permissão para isso. Marque todas as permissões ao criar o token."
                        404 -> "Não encontrado na SmartThings."
                        429 -> "Muitas requisições. Aguarde alguns segundos."
                        else -> "Erro da SmartThings ($code)"
                    }
                    throw ApiException(code, msg)
                }
                if (text.isBlank()) JSONObject() else JSONObject(text)
            } catch (e: IOException) {
                throw ApiException(-1, "Sem conexão com a SmartThings. Verifique a internet.")
            } finally {
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
        list("$base/devices?locationId=${enc(locationId)}").map { d ->
            val caps = mutableSetOf<String>()
            var category: String? = null
            val comps = d.optJSONArray("components") ?: JSONArray()
            for (i in 0 until comps.length()) {
                val comp = comps.optJSONObject(i) ?: continue
                if (comp.optString("id") != "main") continue
                val cs = comp.optJSONArray("capabilities") ?: JSONArray()
                for (j in 0 until cs.length()) cs.optJSONObject(j)?.optString("id")?.let(caps::add)
                val cats = comp.optJSONArray("categories")
                if (cats != null && cats.length() > 0) category = cats.optJSONObject(0)?.optString("name")
            }
            val label = d.optString("label").ifBlank { d.optString("name", "Dispositivo") }
            val health = d.optJSONObject("healthState")?.optString("state")
            Device(
                id = d.optString("deviceId"),
                name = label,
                roomId = d.optString("roomId").ifBlank { null },
                capabilities = caps,
                category = category,
                state = DeviceState(online = health?.let { it == "ONLINE" }),
            )
        }

    override suspend fun status(deviceId: String): DeviceState {
        val json = request("GET", "$base/devices/${enc(deviceId)}/status")
        val main = json.optJSONObject("components")?.optJSONObject("main") ?: JSONObject()
        return parseStatus(main)
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
        request("POST", "$base/devices/${enc(deviceId)}/commands", body)
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
            muted = (m.attr(Caps.MUTE, "mute") as? String)?.let { it == "muted" },
            shadeLevel = m.attr(Caps.SHADE_LEVEL, "shadeLevel").num()?.toInt(),
            shadeState = m.attr(Caps.SHADE, "windowShade") as? String,
        )
    }
}
