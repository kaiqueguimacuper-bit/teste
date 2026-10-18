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
}

class ApiException(val code: Int, message: String) : Exception(message)
