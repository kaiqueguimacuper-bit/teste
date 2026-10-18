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
    val locked: Boolean? = null,
    val contactOpen: Boolean? = null,
    val motion: Boolean? = null,
    val waterLeak: Boolean? = null,
    val battery: Int? = null,
    val power: Double? = null,
    val volume: Int? = null,
    val muted: Boolean? = null,
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
                c.contains("light") || has(Caps.COLOR) || has(Caps.COLOR_TEMP) -> DeviceKind.LIGHT
                c.contains("television") || c == "tv" -> DeviceKind.TV
                c.contains("speaker") || c.contains("networkaudio") -> DeviceKind.SPEAKER
                c.contains("airconditioner") -> DeviceKind.AC
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
                s.shadeLevel != null -> parts += "${s.shadeLevel}% aberta"
                s.switchOn != null -> {
                    parts += if (s.switchOn) "Ligado" else "Desligado"
                    if (s.switchOn && s.level != null && kind == DeviceKind.LIGHT) parts += "${s.level}%"
                }
            }
            s.temperature?.let { parts += "${fmt(it)}°${s.tempUnit ?: "C"}" }
            s.humidity?.let { parts += "${fmt(it)}%" }
            s.power?.let { if (it > 0) parts += "${fmt(it)} W" }
            if (parts.isEmpty()) parts += if (s.online == true) "Online" else "—"
            return parts.joinToString(" · ")
        }

    val isActive: Boolean
        get() = state.switchOn == true || state.contactOpen == true || state.motion == true ||
            state.locked == false || state.waterLeak == true
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
    const val LOCK = "lock"
    const val CONTACT = "contactSensor"
    const val MOTION = "motionSensor"
    const val WATER = "waterSensor"
    const val BATTERY = "battery"
    const val POWER = "powerMeter"
    const val VOLUME = "audioVolume"
    const val MUTE = "audioMute"
    const val SHADE = "windowShade"
    const val SHADE_LEVEL = "windowShadeLevel"
    const val HEALTH = "healthCheck"
}

fun fmt(v: Double): String = if (v % 1.0 == 0.0) v.toInt().toString() else String.format(java.util.Locale.US, "%.1f", v)
