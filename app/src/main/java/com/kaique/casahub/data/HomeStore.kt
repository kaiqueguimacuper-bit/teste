package com.kaique.casahub.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** O que o app precisa guardar entre sessões (implementado por [Prefs] no Android). */
interface Settings {
    var token: String?
    var demo: Boolean
    var locationId: String?
    var favorites: Set<String>
    var autoRefresh: Boolean
    fun clearSession()
}

object RoomFilter {
    const val ALL = "__all"
    const val FAVORITES = "__fav"
    const val NONE = "__none"
}

data class UiState(
    val loggedIn: Boolean = false,
    val demo: Boolean = false,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val locations: List<Location> = emptyList(),
    val locationId: String? = null,
    val rooms: List<Room> = emptyList(),
    val devices: List<Device> = emptyList(),
    val scenes: List<Scene> = emptyList(),
    val favorites: Set<String> = emptySet(),
    val roomFilter: String = RoomFilter.ALL,
    val query: String = "",
    val pending: Set<String> = emptySet(),
    val runningScene: String? = null,
    val autoRefresh: Boolean = true,
    val lastUpdated: Long = 0L,
) {
    val locationName: String get() = locations.firstOrNull { it.id == locationId }?.name ?: "Minha casa"

    fun roomName(id: String?): String = rooms.firstOrNull { it.id == id }?.name ?: "Sem cômodo"

    /** Dispositivos depois do filtro de cômodo e da busca, favoritos primeiro. */
    val visibleDevices: List<Device>
        get() {
            val q = query.trim().lowercase()
            return devices
                .filter {
                    when (roomFilter) {
                        RoomFilter.ALL -> true
                        RoomFilter.FAVORITES -> it.id in favorites
                        RoomFilter.NONE -> it.roomId == null || rooms.none { r -> r.id == it.roomId }
                        else -> it.roomId == roomFilter
                    }
                }
                .filter { q.isEmpty() || it.name.lowercase().contains(q) || roomName(it.roomId).lowercase().contains(q) }
                .sortedWith(compareByDescending<Device> { it.id in favorites }.thenBy { it.name.lowercase() })
        }

    val onCount: Int get() = devices.count { it.state.switchOn == true }
    val offlineCount: Int get() = devices.count { it.state.online == false }
    val alerts: List<String>
        get() = buildList {
            devices.forEach { d ->
                if (d.state.waterLeak == true) add("Vazamento: ${d.name}")
                if (d.state.contactOpen == true) add("Aberta: ${d.name}")
                if (d.state.locked == false) add("Destrancada: ${d.name}")
                val b = d.state.battery
                if (b != null && b <= 15) add("Bateria fraca ($b%): ${d.name}")
            }
        }
    val avgTemperature: Double?
        get() = devices.mapNotNull { it.state.temperature }.takeIf { it.isNotEmpty() }?.average()
}

class HomeStore(
    private val scope: CoroutineScope,
    private val settings: Settings,
    private val realRepo: (String) -> HomeRepository = { SmartThingsRepository(it) },
    private val demoRepo: () -> HomeRepository = { DemoRepository() },
    private val settleDelayMs: Long = 900,
) {
    private val _state = MutableStateFlow(UiState(favorites = settings.favorites, autoRefresh = settings.autoRefresh))
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var repo: HomeRepository? = null
    private var loadJob: Job? = null
    private var refreshJob: Job? = null

    init {
        val token = settings.token
        when {
            settings.demo -> startDemo()
            !token.isNullOrBlank() -> connect(realRepo(token), saveToken = null)
        }
    }

    // ---------- Sessão ----------

    fun login(rawToken: String) {
        val token = rawToken.trim()
        if (token.length < 20) {
            _state.update { it.copy(error = "Cole o token completo (ele tem 36 caracteres).") }
            return
        }
        connect(realRepo(token), saveToken = token)
    }

    fun startDemo() {
        connect(demoRepo(), saveToken = null)
    }

    private fun connect(r: HomeRepository, saveToken: String?) {
        loadJob?.cancel()
        _state.update { it.copy(loading = true, error = null) }
        loadJob = scope.launch {
            try {
                val locs = r.locations()
                if (locs.isEmpty()) throw ApiException(0, "Nenhum local encontrado nessa conta SmartThings.")
                repo = r
                if (r.isDemo) settings.demo = true else if (saveToken != null) {
                    settings.token = saveToken; settings.demo = false
                }
                val chosen = settings.locationId?.takeIf { id -> locs.any { it.id == id } } ?: locs.first().id
                _state.update {
                    it.copy(loggedIn = true, demo = r.isDemo, locations = locs, locationId = chosen, roomFilter = RoomFilter.ALL)
                }
                loadAll(silent = false)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                repo = null
                _state.update { it.copy(loading = false, loggedIn = false, error = e.message ?: "Erro ao conectar.") }
            }
        }
    }

    fun logout() {
        loadJob?.cancel()
        repo = null
        settings.clearSession()
        _state.value = UiState(favorites = settings.favorites, autoRefresh = settings.autoRefresh)
    }

    fun selectLocation(id: String) {
        if (id == _state.value.locationId) return
        settings.locationId = id
        _state.update { it.copy(locationId = id, roomFilter = RoomFilter.ALL, devices = emptyList(), rooms = emptyList(), scenes = emptyList()) }
        scope.launch { loadAll(silent = false) }
    }

    // ---------- Carregamento ----------

    fun refresh(silent: Boolean = false) {
        if (repo == null || _state.value.loading || refreshJob?.isActive == true) return
        refreshJob = scope.launch { loadAll(silent) }
    }

    private suspend fun loadAll(silent: Boolean) {
        val r = repo ?: return
        val loc = _state.value.locationId ?: return
        _state.update { if (silent) it else it.copy(refreshing = it.devices.isNotEmpty(), loading = it.devices.isEmpty()) }
        try {
            val (rooms, devices, scenes) = coroutineScope {
                val a = async { r.rooms(loc) }
                val b = async { r.devices(loc) }
                val c = async { runCatching { r.scenes(loc) }.getOrDefault(emptyList()) }
                Triple(a.await(), b.await(), c.await())
            }
            val withStatus = fetchStatuses(r, devices)
            _state.update { s ->
                val merged = withStatus.map { d ->
                    if (d.id in s.pending) s.devices.firstOrNull { it.id == d.id } ?: d else d
                }
                s.copy(
                    loading = false, refreshing = false, error = null,
                    rooms = rooms, devices = merged, scenes = scenes,
                    lastUpdated = System.currentTimeMillis(),
                )
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            val unauthorized = (e as? ApiException)?.code == 401
            _state.update {
                it.copy(
                    loading = false, refreshing = false,
                    error = if (it.devices.isEmpty() || unauthorized) e.message else it.error,
                    message = if (!silent && it.devices.isNotEmpty()) e.message else it.message,
                )
            }
        }
    }

    private suspend fun fetchStatuses(r: HomeRepository, devices: List<Device>): List<Device> = coroutineScope {
        val gate = Semaphore(6)
        devices.map { d ->
            async {
                gate.withPermit {
                    runCatching { r.status(d.id) }.map { st ->
                        d.copy(state = st.copy(online = st.online ?: d.state.online))
                    }.getOrDefault(d)
                }
            }
        }.awaitAll()
    }

    // ---------- Comandos ----------

    fun toggle(device: Device) {
        val on = device.state.switchOn ?: return
        send(device, Command(Caps.SWITCH, if (on) "off" else "on")) { it.copy(switchOn = !on) }
    }

    fun send(device: Device, cmd: Command, optimistic: (DeviceState) -> DeviceState) {
        val r = repo ?: return
        if (device.state.online == false) {
            _state.update { it.copy(message = "${device.name} está offline.") }
            return
        }
        val before = _state.value.devices.firstOrNull { it.id == device.id }?.state ?: device.state
        setDeviceState(device.id, optimistic(before))
        _state.update { it.copy(pending = it.pending + device.id) }
        scope.launch {
            try {
                r.command(device.id, cmd)
                delay(settleDelayMs)
                val fresh = runCatching { r.status(device.id) }.getOrNull()
                if (fresh != null) setDeviceState(device.id, fresh.copy(online = fresh.online ?: before.online))
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                setDeviceState(device.id, before)
                _state.update { it.copy(message = e.message ?: "Não foi possível enviar o comando.") }
            } finally {
                _state.update { it.copy(pending = it.pending - device.id) }
            }
        }
    }

    /** Desliga tudo o que estiver ligado entre os dispositivos visíveis. */
    fun allOff() {
        val targets = _state.value.visibleDevices.filter { it.canToggle && it.state.switchOn == true && it.state.online != false }
        if (targets.isEmpty()) {
            _state.update { it.copy(message = "Nada ligado aqui.") }
            return
        }
        targets.forEach { d -> send(d, Command(Caps.SWITCH, "off")) { it.copy(switchOn = false) } }
        _state.update { it.copy(message = "Desligando ${targets.size} dispositivo(s)…") }
    }

    fun runScene(scene: Scene) {
        val r = repo ?: return
        _state.update { it.copy(runningScene = scene.id) }
        scope.launch {
            try {
                r.runScene(scene.id)
                _state.update { it.copy(message = "Cena \"${scene.name}\" executada.") }
                delay(settleDelayMs)
                loadAll(silent = true)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.update { it.copy(message = e.message ?: "Falha ao executar a cena.") }
            } finally {
                _state.update { it.copy(runningScene = null) }
            }
        }
    }

    private fun setDeviceState(id: String, st: DeviceState) {
        _state.update { s -> s.copy(devices = s.devices.map { if (it.id == id) it.copy(state = st) else it }) }
    }

    // ---------- Preferências de tela ----------

    fun toggleFavorite(id: String) {
        val fav = _state.value.favorites.let { if (id in it) it - id else it + id }
        settings.favorites = fav
        _state.update { it.copy(favorites = fav) }
    }

    fun setRoomFilter(f: String) = _state.update { it.copy(roomFilter = f) }
    fun setQuery(q: String) = _state.update { it.copy(query = q) }
    fun consumeMessage() = _state.update { it.copy(message = null) }
    fun clearError() = _state.update { it.copy(error = null) }
    fun setAutoRefresh(on: Boolean) {
        settings.autoRefresh = on
        _state.update { it.copy(autoRefresh = on) }
    }
}
