package com.kaique.casahub.data

data class Location(val id: String, val name: String)

data class Room(val id: String, val name: String)

data class Scene(val id: String, val name: String)

/** Estado lido de /devices/{id}/status. Campos nulos = o aparelho não tem essa capacidade. */
data class DeviceState(
    val online: Boolean? = null,
    val switchOn: Boolean? = null,
    val level: Int? = null,
    val colorTemp: Int? = null,
    val hue: Double? = null,
    val saturation: Double? = null,
    val temperature: Double? = null,
    val tempUnit: String? = null,
    val humidity: Double? = null,
    val heatingSetpoint: Double? = null,
    val coolingSetpoint: Double? = null,
    val thermostatMode: String? = null,
    val acMode: String? = null,
    val acModes: List<String> = emptyList(),
    val fanMode: String? = null,
    val fanModes: List<String> = emptyList(),
    val locked: Boolean? = null,
    val contactOpen: Boolean? = null,
    val motion: Boolean? = null,
    val waterLeak: Boolean? = null,
    val battery: Int? = null,
    val power: Double? = null,
    val energy: Double? = null,
    val volume: Int? = null,
    val muted: Boolean? = null,
    val channel: String? = null,
    val channelName: String? = null,
    val inputSource: String? = null,
    val inputSources: List<String> = emptyList(),
    val playback: String? = null,
    val shadeLevel: Int? = null,
    val shadeState: String? = null,
)

enum class DeviceKind { LIGHT, PLUG, TV, SPEAKER, THERMOSTAT, AC, LOCK, CONTACT, MOTION, LEAK, SHADE, SENSOR, APPLIANCE, CAMERA, OTHER }

data class Device(
    val id: String,
    val name: String,
    val roomId: String?,
    val capabilities: Set<String>,
    val category: String?,
    val state: DeviceState = DeviceState(),
) {
    fun has(cap: String) = cap in capabilities

    val canToggle: Boolean get() = has(Caps.SWITCH)

    val kind: DeviceKind
        get() {
            val c = category?.lowercase().orEmpty()
            return when {
                c.contains("television") || c == "tv" || has(Caps.REMOTE) || has(Caps.CHANNEL) -> DeviceKind.TV
                c.contains("light") || has(Caps.COLOR) || has(Caps.COLOR_TEMP) -> DeviceKind.LIGHT
                c.contains("speaker") || c.contains("networkaudio") -> DeviceKind.SPEAKER
                c.contains("airconditioner") || has(Caps.AC_MODE) -> DeviceKind.AC
                has(Caps.THERMOSTAT_HEAT) || has(Caps.THERMOSTAT_COOL) || c.contains("thermostat") -> DeviceKind.THERMOSTAT
                has(Caps.LOCK) -> DeviceKind.LOCK
                has(Caps.SHADE) || c.contains("blind") || c.contains("shade") -> DeviceKind.SHADE
                c.contains("camera") || has("videoCamera") -> DeviceKind.CAMERA
                c.contains("washer") || c.contains("dryer") || c.contains("refrigerator") ||
                    c.contains("dishwasher") || c.contains("oven") || c.contains("robotcleaner") -> DeviceKind.APPLIANCE
                has(Caps.WATER) -> DeviceKind.LEAK
                has(Caps.CONTACT) -> DeviceKind.CONTACT
                has(Caps.MOTION) -> DeviceKind.MOTION
                has(Caps.VOLUME) -> DeviceKind.SPEAKER
                has(Caps.LEVEL) -> DeviceKind.LIGHT
                c.contains("plug") || c.contains("switch") || has(Caps.POWER) -> DeviceKind.PLUG
                has(Caps.TEMP) || has(Caps.HUMIDITY) -> DeviceKind.SENSOR
                has(Caps.SWITCH) -> DeviceKind.PLUG
                else -> DeviceKind.OTHER
            }
        }

    /** Uma linha curta de status para o card. */
    val summary: String
        get() {
            val s = state
            if (s.online == false) return "Offline"
            val parts = mutableListOf<String>()
            when {
                s.locked != null -> parts += if (s.locked) "Trancada" else "Destrancada"
                s.contactOpen != null -> parts += if (s.contactOpen) "Aberta" else "Fechada"
                s.waterLeak != null -> parts += if (s.waterLeak) "Vazamento!" else "Seco"
                s.motion != null && s.switchOn == null -> parts += if (s.motion) "Movimento" else "Sem movimento"
                s.shadeLevel != null -> parts += if (s.shadeLevel == 0) "Fechada" else "${s.shadeLevel}% aberta"
                s.switchOn != null -> {
                    parts += if (s.switchOn) "Ligado" else "Desligado"
                    if (s.switchOn) when (kind) {
                        DeviceKind.LIGHT -> s.level?.let { parts += "$it%" }
                        DeviceKind.TV -> (s.channel?.let { "Canal $it" } ?: s.inputSource?.let { Labels.input(it) })?.let { parts += it }
                        DeviceKind.AC -> s.coolingSetpoint?.let { parts += "${fmt(it)}°" }
                        else -> {}
                    }
                }
            }
            if (kind != DeviceKind.AC || s.switchOn != true) s.temperature?.let { parts += "${fmt(it)}°${s.tempUnit ?: "C"}" }
            s.humidity?.let { parts += "${fmt(it)}%" }
            s.power?.let { if (it > 0) parts += "${fmt(it)} W" }
            if (parts.isEmpty()) parts += if (s.online == true) "Online" else "—"
            return parts.joinToString(" · ")
        }

    val isActive: Boolean
        get() = state.switchOn == true || state.contactOpen == true || state.motion == true ||
            state.locked == false || state.waterLeak == true || (state.shadeLevel ?: 0) > 0
}

data class Command(val capability: String, val command: String, val args: List<Any> = emptyList(), val component: String = "main")

object Caps {
    const val SWITCH = "switch"
    const val LEVEL = "switchLevel"
    const val COLOR_TEMP = "colorTemperature"
    const val COLOR = "colorControl"
    const val TEMP = "temperatureMeasurement"
    const val HUMIDITY = "relativeHumidityMeasurement"
    const val THERMOSTAT_HEAT = "thermostatHeatingSetpoint"
    const val THERMOSTAT_COOL = "thermostatCoolingSetpoint"
    const val THERMOSTAT_MODE = "thermostatMode"
    const val AC_MODE = "airConditionerMode"
    const val AC_FAN = "airConditionerFanMode"
    const val LOCK = "lock"
    const val CONTACT = "contactSensor"
    const val MOTION = "motionSensor"
    const val WATER = "waterSensor"
    const val BATTERY = "battery"
    const val POWER = "powerMeter"
    const val ENERGY = "energyMeter"
    const val VOLUME = "audioVolume"
    const val MUTE = "audioMute"
    const val SHADE = "windowShade"
    const val SHADE_LEVEL = "windowShadeLevel"
    const val HEALTH = "healthCheck"
    const val REMOTE = "samsungvd.remoteControl"
    const val KEYPAD = "keypadInput"
    const val CHANNEL = "tvChannel"
    const val INPUT = "mediaInputSource"
    const val INPUT_SAMSUNG = "samsungvd.mediaInputSource"
    const val PLAYBACK = "mediaPlayback"
    const val LAUNCH = "custom.launchapp"
}

/** Teclas do controle remoto: nome em samsungvd.remoteControl e em keypadInput. */
enum class RemoteKey(val samsung: String, val keypad: String) {
    UP("UP", "UP"), DOWN("DOWN", "DOWN"), LEFT("LEFT", "LEFT"), RIGHT("RIGHT", "RIGHT"),
    OK("OK", "SELECT"), BACK("BACK", "BACK"), HOME("HOME", "HOME"), MENU("MENU", "MENU"),
}

data class TvApp(val name: String, val appId: String)

/** Monta os comandos da TV conforme o que o aparelho suporta. */
object TvCommands {
    val apps = listOf(
        TvApp("Netflix", "3201907018807"),
        TvApp("YouTube", "111299001912"),
        TvApp("Prime Video", "3201910019365"),
        TvApp("Max", "3201601007230"),
    )

    fun key(d: Device, k: RemoteKey): Command? = when {
        d.has(Caps.REMOTE) -> Command(Caps.REMOTE, "send", listOf(k.samsung))
        d.has(Caps.KEYPAD) -> Command(Caps.KEYPAD, "sendKey", listOf(k.keypad))
        else -> null
    }

    fun number(d: Device, n: Int): Command? =
        if (d.has(Caps.KEYPAD)) Command(Caps.KEYPAD, "sendKey", listOf("NUMBER$n")) else null

    fun channel(d: Device, number: String): Command? =
        if (d.has(Caps.CHANNEL)) Command(Caps.CHANNEL, "setTvChannel", listOf(number)) else null

    fun channelUp(d: Device): Command? = if (d.has(Caps.CHANNEL)) Command(Caps.CHANNEL, "channelUp") else null
    fun channelDown(d: Device): Command? = if (d.has(Caps.CHANNEL)) Command(Caps.CHANNEL, "channelDown") else null
    fun volumeUp(d: Device): Command? = if (d.has(Caps.VOLUME)) Command(Caps.VOLUME, "volumeUp") else null
    fun volumeDown(d: Device): Command? = if (d.has(Caps.VOLUME)) Command(Caps.VOLUME, "volumeDown") else null
    fun mute(d: Device, on: Boolean): Command? = if (d.has(Caps.MUTE)) Command(Caps.MUTE, if (on) "mute" else "unmute") else null

    fun input(d: Device, source: String): Command? = when {
        d.has(Caps.INPUT_SAMSUNG) -> Command(Caps.INPUT_SAMSUNG, "setInputSource", listOf(source))
        d.has(Caps.INPUT) -> Command(Caps.INPUT, "setInputSource", listOf(source))
        else -> null
    }

    fun playback(d: Device, action: String): Command? =
        if (d.has(Caps.PLAYBACK)) Command(Caps.PLAYBACK, action) else null

    fun launch(d: Device, app: TvApp): Command? =
        if (d.has(Caps.LAUNCH)) Command(Caps.LAUNCH, "launchApp", listOf(app.appId)) else null
}

/** Nomes em português para valores da SmartThings. */
object Labels {
    fun input(id: String): String = when (id.lowercase()) {
        "dtv", "digitaltv", "tv" -> "TV"
        "hdmi1" -> "HDMI 1"
        "hdmi2" -> "HDMI 2"
        "hdmi3" -> "HDMI 3"
        "hdmi4" -> "HDMI 4"
        "usb" -> "USB"
        "av", "av1" -> "AV"
        else -> id
    }

    fun acMode(m: String): String = when (m) {
        "cool" -> "Frio"
        "heat" -> "Calor"
        "dry" -> "Seco"
        "wind", "fan", "fanOnly" -> "Ventilar"
        "auto", "aIComfort" -> "Auto"
        else -> m.replaceFirstChar { it.uppercase() }
    }

    fun fanMode(m: String): String = when (m) {
        "auto" -> "Auto"
        "low" -> "Baixa"
        "medium" -> "Média"
        "high" -> "Alta"
        "turbo" -> "Turbo"
        else -> m.replaceFirstChar { it.uppercase() }
    }
}

fun fmt(v: Double): String = if (v % 1.0 == 0.0) v.toInt().toString() else String.format(java.util.Locale.US, "%.1f", v)
