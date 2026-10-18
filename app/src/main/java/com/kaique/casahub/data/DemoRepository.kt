package com.kaique.casahub.data

import kotlinx.coroutines.delay

/** Casa simulada para testar o app sem conta SmartThings. Os comandos alteram o estado na memória. */
class DemoRepository : HomeRepository {

    override val isDemo = true

    private val loc = Location("demo", "Casa (demo)")

    private val rooms = listOf(
        Room("sala", "Sala"),
        Room("cozinha", "Cozinha"),
        Room("quarto", "Quarto"),
        Room("entrada", "Entrada"),
        Room("area", "Área de serviço"),
    )

    private val devices = mutableListOf(
        Device("d1", "Luz da sala", "sala", setOf(Caps.SWITCH, Caps.LEVEL, Caps.COLOR_TEMP), "Light",
            DeviceState(online = true, switchOn = true, level = 70, colorTemp = 3000)),
        Device("d2", "Fita LED da TV", "sala", setOf(Caps.SWITCH, Caps.LEVEL, Caps.COLOR), "Light",
            DeviceState(online = true, switchOn = false, level = 40, hue = 66.0, saturation = 90.0)),
        Device("d3", "TV da sala", "sala",
            setOf(Caps.SWITCH, Caps.VOLUME, Caps.MUTE, Caps.CHANNEL, Caps.REMOTE, Caps.KEYPAD, Caps.INPUT_SAMSUNG, Caps.PLAYBACK, Caps.LAUNCH),
            "Television",
            DeviceState(online = true, switchOn = true, volume = 18, muted = false, channel = "7", channelName = "Globo",
                inputSource = "dtv", inputSources = listOf("dtv", "HDMI1", "HDMI2", "USB"), playback = "stopped")),
        Device("d4", "Ar-condicionado", "quarto",
            setOf(Caps.SWITCH, Caps.TEMP, Caps.THERMOSTAT_COOL, Caps.AC_MODE, Caps.AC_FAN, Caps.HUMIDITY), "AirConditioner",
            DeviceState(online = true, switchOn = true, temperature = 26.0, tempUnit = "C", humidity = 54.0, coolingSetpoint = 22.0,
                acMode = "cool", acModes = listOf("auto", "cool", "dry", "wind", "heat"),
                fanMode = "auto", fanModes = listOf("auto", "low", "medium", "high", "turbo"))),
        Device("d5", "Abajur", "quarto", setOf(Caps.SWITCH, Caps.LEVEL), "Light",
            DeviceState(online = true, switchOn = false, level = 30)),
        Device("d6", "Cortina", "quarto", setOf(Caps.SHADE, Caps.SHADE_LEVEL), "Blind",
            DeviceState(online = true, shadeLevel = 100, shadeState = "open")),
        Device("d7", "Fechadura", "entrada", setOf(Caps.LOCK, Caps.BATTERY), "SmartLock",
            DeviceState(online = true, locked = true, battery = 64)),
        Device("d8", "Sensor da porta", "entrada", setOf(Caps.CONTACT, Caps.BATTERY, Caps.TEMP), "MultiFunctionalSensor",
            DeviceState(online = true, contactOpen = false, battery = 88, temperature = 24.5, tempUnit = "C")),
        Device("d9", "Sensor de presença", "entrada", setOf(Caps.MOTION, Caps.BATTERY), "MotionSensor",
            DeviceState(online = true, motion = false, battery = 12)),
        Device("d10", "Cafeteira", "cozinha", setOf(Caps.SWITCH, Caps.POWER), "SmartPlug",
            DeviceState(online = true, switchOn = false, power = 0.0)),
        Device("d11", "Luz da cozinha", "cozinha", setOf(Caps.SWITCH), "Light",
            DeviceState(online = true, switchOn = true)),
        Device("d12", "Sensor de vazamento", "area", setOf(Caps.WATER, Caps.BATTERY), "LeakSensor",
            DeviceState(online = true, waterLeak = false, battery = 95)),
        Device("d13", "Lavadora", "area", setOf(Caps.SWITCH, Caps.POWER), "Washer",
            DeviceState(online = false, switchOn = false)),
        Device("d14", "Termo-higrômetro", "sala", setOf(Caps.TEMP, Caps.HUMIDITY, Caps.BATTERY), "TempHumiditySensor",
            DeviceState(online = true, temperature = 25.2, tempUnit = "C", humidity = 58.0, battery = 77)),
    )

    private val scenes = listOf(
        Scene("s1", "Cheguei"),
        Scene("s2", "Sair de casa"),
        Scene("s3", "Cinema"),
        Scene("s4", "Boa noite"),
    )

    override suspend fun locations(): List<Location> { delay(250); return listOf(loc) }
    override suspend fun rooms(locationId: String): List<Room> = rooms
    override suspend fun devices(locationId: String): List<Device> { delay(250); return devices.toList() }
    override suspend fun status(deviceId: String): DeviceState =
        devices.first { it.id == deviceId }.state
    override suspend fun scenes(locationId: String): List<Scene> = scenes

    private fun update(id: String, f: (DeviceState) -> DeviceState) {
        val i = devices.indexOfFirst { it.id == id }
        if (i >= 0) devices[i] = devices[i].copy(state = f(devices[i].state))
    }

    override suspend fun command(deviceId: String, cmd: Command) {
        delay(300)
        val dev = devices.first { it.id == deviceId }
        if (dev.state.online == false) throw ApiException(409, "${dev.name} está offline.")
        val a0 = cmd.args.firstOrNull()
        val n = (a0 as? Number)?.toDouble()
        update(deviceId) { s ->
            when ("${cmd.capability}.${cmd.command}") {
                "switch.on" -> s.copy(switchOn = true, power = if (s.power != null) 850.0 else null)
                "audioVolume.volumeUp" -> s.copy(volume = ((s.volume ?: 0) + 1).coerceAtMost(100), muted = false)
                "audioVolume.volumeDown" -> s.copy(volume = ((s.volume ?: 0) - 1).coerceAtLeast(0))
                "tvChannel.channelUp" -> s.copy(channel = ((s.channel?.toIntOrNull() ?: 0) + 1).toString(), channelName = null)
                "tvChannel.channelDown" -> s.copy(channel = ((s.channel?.toIntOrNull() ?: 2) - 1).coerceAtLeast(1).toString(), channelName = null)
                "tvChannel.setTvChannel" -> s.copy(channel = a0?.toString(), channelName = null, inputSource = "dtv")
                "samsungvd.mediaInputSource.setInputSource", "mediaInputSource.setInputSource" -> s.copy(inputSource = a0?.toString())
                "mediaPlayback.play" -> s.copy(playback = "playing")
                "mediaPlayback.pause" -> s.copy(playback = "paused")
                "mediaPlayback.stop" -> s.copy(playback = "stopped")
                "custom.launchapp.launchApp" -> s.copy(playback = "playing", channelName = null)
                "airConditionerMode.setAirConditionerMode" -> s.copy(acMode = a0?.toString(), switchOn = true)
                "airConditionerFanMode.setFanMode" -> s.copy(fanMode = a0?.toString())
                "switch.off" -> s.copy(switchOn = false, power = if (s.power != null) 0.0 else null)
                "switchLevel.setLevel" -> s.copy(level = n?.toInt(), switchOn = (n ?: 0.0) > 0)
                "colorTemperature.setColorTemperature" -> s.copy(colorTemp = n?.toInt())
                "colorControl.setHue" -> s.copy(hue = n)
                "colorControl.setColor" -> (a0 as? Map<*, *>)?.let { m ->
                    s.copy(hue = (m["hue"] as? Number)?.toDouble(), saturation = (m["saturation"] as? Number)?.toDouble(), switchOn = true)
                } ?: s
                "lock.lock" -> s.copy(locked = true)
                "lock.unlock" -> s.copy(locked = false)
                "thermostatCoolingSetpoint.setCoolingSetpoint" -> s.copy(coolingSetpoint = n)
                "thermostatHeatingSetpoint.setHeatingSetpoint" -> s.copy(heatingSetpoint = n)
                "audioVolume.setVolume" -> s.copy(volume = n?.toInt())
                "audioMute.mute" -> s.copy(muted = true)
                "audioMute.unmute" -> s.copy(muted = false)
                "windowShade.open" -> s.copy(shadeLevel = 100, shadeState = "open")
                "windowShade.close" -> s.copy(shadeLevel = 0, shadeState = "closed")
                "windowShadeLevel.setShadeLevel" -> s.copy(shadeLevel = n?.toInt(), shadeState = if ((n ?: 0.0) > 0) "partially open" else "closed")
                else -> s
            }
        }
    }

    override suspend fun runScene(sceneId: String) {
        delay(400)
        fun set(id: String, on: Boolean) = update(id) { it.copy(switchOn = on) }
        when (sceneId) {
            "s1" -> { set("d1", true); set("d11", true); update("d7") { it.copy(locked = false) } }
            "s2" -> {
                devices.filter { it.canToggle && it.state.online != false }.forEach { set(it.id, false) }
                update("d7") { it.copy(locked = true) }
            }
            "s3" -> {
                set("d1", false); set("d2", true); set("d3", true)
                update("d6") { it.copy(shadeLevel = 0, shadeState = "closed") }
            }
            "s4" -> {
                devices.filter { it.canToggle && it.id != "d4" && it.state.online != false }.forEach { set(it.id, false) }
                update("d7") { it.copy(locked = true) }
                update("d4") { it.copy(switchOn = true, coolingSetpoint = 23.0) }
            }
        }
    }
}
