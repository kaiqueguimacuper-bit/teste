package com.kaique.casahub.data

interface HomeRepository {
    val isDemo: Boolean
    suspend fun locations(): List<Location>
    suspend fun rooms(locationId: String): List<Room>
    suspend fun devices(locationId: String): List<Device>
    suspend fun status(deviceId: String): DeviceState
    suspend fun command(deviceId: String, cmd: Command)
    suspend fun scenes(locationId: String): List<Scene>
    suspend fun runScene(sceneId: String)

    /** Pede ao aparelho para atualizar o estado na nuvem (capacidade "refresh"). */
    suspend fun refreshDevice(deviceId: String, component: String = "main") {}

    /** Valores aceitos por um argumento de comando, lidos da definição da capacidade. */
    suspend fun commandArgValues(capability: String, version: Int, command: String): List<String> = emptyList()

    /** Últimas chamadas à API, para a tela de diagnóstico. */
    fun diagnostics(): List<ApiLogEntry> = emptyList()
}

data class ApiLogEntry(val time: Long, val method: String, val path: String, val code: Int, val ms: Long, val detail: String?)

class ApiException(val code: Int, message: String) : Exception(message)
