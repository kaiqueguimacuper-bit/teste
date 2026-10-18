package com.kaique.casahub.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

/** O que o app precisa guardar entre sessões (implementado por [Prefs] no Android). */
interface Settings {
    var token: String?
    var demo: Boolean
    var locationId: String?
    var favorites: Set<String>
    var autoRefresh: Boolean
    fun clearSession()

    /** TV pareada pelo Wi-Fi (IP, token de autorização, MAC para ligar) por aparelho. */
    fun localTv(deviceId: String): LocalTvInfo? = null
    fun saveLocalTv(deviceId: String, info: LocalTvInfo?) {}
}

data class LocalTvInfo(val ip: String, val token: String? = null, val mac: String? = null, val name: String? = null)

/** Situação do controle direto pelo Wi-Fi de uma TV, para a tela. */
data class LocalLink(
    val state: LocalState = LocalState.IDLE,
    val ip: String? = null,
    val name: String? = null,
    val scanning: Boolean = false,
    val candidates: List<FoundTv> = emptyList(),
    val notFound: Boolean = false,
)

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
    /** Teclas aceitas por cada TV (lidas da SmartThings), para o diagnóstico. */
    val remoteKeys: Map<String, List<String>> = emptyMap(),
    /** Controle direto pelo Wi-Fi, por TV. */
    val local: Map<String, LocalLink> = emptyMap(),
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

private const val COMMAND_TIMEOUT_MS = 6_000L
private const val MAX_QUEUE = 6
private const val SEND_SPACING_MS = 90L

/** Favoritos iniciais do modo demo, para a tela Início já mostrar o essencial. */
val DEMO_FAVORITES = setOf("d3", "d1", "d4", "d7")

class HomeStore(
    private val scope: CoroutineScope,
    private val settings: Settings,
    private val realRepo: (String) -> HomeRepository = { SmartThingsRepository(it) },
    private val demoRepo: () -> HomeRepository = { DemoRepository() },
    private val settleDelayMs: Long = 900,
    private val localFactory: (LocalTvInfo, (String) -> Unit) -> LocalTvRemote = { info, onToken ->
        LocalTvRemote(info.ip, info.token, scope, onToken)
    },
    private val finder: suspend () -> List<FoundTv> = { LocalTvFinder.scan() },
    private val waker: (String) -> Unit = { mac -> LocalTvFinder.wake(mac) },
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
                if (r.isDemo && settings.favorites.isEmpty()) {
                    settings.favorites = DEMO_FAVORITES
                    _state.update { it.copy(favorites = DEMO_FAVORITES) }
                }
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
        synchronized(locals) { locals.values.forEach { it.stop() }; locals.clear() }
        localWatch.values.forEach { it.cancel() }; localWatch.clear()
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
                val now = System.currentTimeMillis()
                val merged = withStatus.map { d ->
                    val cur = s.devices.firstOrNull { it.id == d.id }
                    val recentlySynced = now - (synchronized(syncedAt) { syncedAt[d.id] } ?: 0L) < 30_000
                    when {
                        cur != null && (d.id in s.pending || recentlySynced) -> d.copy(state = cur.state)
                        else -> d
                    }
                }
                s.copy(
                    loading = false, refreshing = false, error = null,
                    rooms = rooms, devices = merged, scenes = scenes,
                    lastUpdated = System.currentTimeMillis(),
                )
            }
            // O aparelho aberto na tela é relido com "refresh", para mostrar volume/canal reais.
            focusedId?.let { id -> _state.value.devices.firstOrNull { it.id == id }?.let { scheduleSync(it, delayMs = 0) } }
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

    /** Liga/desliga. Com [force], tenta mesmo se a SmartThings diz que o aparelho está offline (TV em espera). */
    fun toggle(device: Device, force: Boolean = false) {
        val on = device.state.switchOn ?: (if (force) device.state.online == true else return)
        if (device.kind == DeviceKind.TV) {
            val local = localFor(device.id)
            if (on && local != null && local.send("KEY_POWER") { send(device, device.cmd(Caps.SWITCH, "off"), true) { it.copy(switchOn = false) } }) {
                setDeviceState(device.id, (currentState(device)).copy(switchOn = false))
                return
            }
            if (!on) settings.localTv(device.id)?.mac?.let { mac -> scope.launch(kotlinx.coroutines.Dispatchers.IO) { waker(mac) } }
        }
        send(device, device.cmd(Caps.SWITCH, if (on) "off" else "on"), force) { it.copy(switchOn = !on) }
    }

    private fun currentState(device: Device) = _state.value.devices.firstOrNull { it.id == device.id }?.state ?: device.state

    // ---------- Controle direto pelo Wi-Fi ----------

    private val locals = mutableMapOf<String, LocalTvRemote>()
    private val localWatch = mutableMapOf<String, Job>()
    private val demoLocal = mutableSetOf<String>()
    private val autoTried = mutableSetOf<String>()

    private fun localFor(id: String): LocalTvRemote? = synchronized(locals) { locals[id] }?.takeIf { it.isConnected }

    private fun setLink(id: String, f: (LocalLink) -> LocalLink) =
        _state.update { it.copy(local = it.local + (id to f(it.local[id] ?: LocalLink()))) }

    private fun startLocal(device: Device, info: LocalTvInfo) {
        synchronized(locals) { locals.remove(device.id) }?.stop()
        localWatch.remove(device.id)?.cancel()
        val remote = localFactory(info) { tok ->
            settings.saveLocalTv(device.id, (settings.localTv(device.id) ?: info).copy(token = tok))
        }
        synchronized(locals) { locals[device.id] = remote }
        setLink(device.id) { it.copy(ip = info.ip, name = info.name, state = LocalState.CONNECTING, candidates = emptyList(), notFound = false) }
        localWatch[device.id] = scope.launch { remote.state.collect { st -> setLink(device.id) { it.copy(state = st) } } }
        remote.start()
    }

    /** Procura a TV no Wi-Fi e conecta direto. Uma TV: conecta; várias: mostra a lista para escolher. */
    fun connectLocal(device: Device) {
        if (repo?.isDemo == true) {
            demoLocal += device.id
            setLink(device.id) { LocalLink(state = LocalState.CONNECTED, ip = "192.168.0.20", name = "Samsung (simulada)") }
            return
        }
        setLink(device.id) { it.copy(scanning = true, notFound = false, candidates = emptyList()) }
        scope.launch {
            val found = runCatching { finder() }.getOrDefault(emptyList())
            setLink(device.id) { it.copy(scanning = false) }
            when (found.size) {
                0 -> {
                    setLink(device.id) { it.copy(notFound = true) }
                    _state.update { it.copy(message = "Nenhuma TV Samsung encontrada neste Wi-Fi. O celular e a TV precisam estar na mesma rede.") }
                }
                1 -> pickLocal(device, found[0])
                else -> setLink(device.id) { it.copy(candidates = found) }
            }
        }
    }

    fun pickLocal(device: Device, tv: FoundTv) {
        val old = settings.localTv(device.id)
        val info = LocalTvInfo(tv.ip, old?.token?.takeIf { old.ip == tv.ip }, tv.mac ?: old?.mac, tv.name)
        settings.saveLocalTv(device.id, info)
        startLocal(device, info)
    }

    fun forgetLocal(device: Device) {
        synchronized(locals) { locals.remove(device.id) }?.stop()
        localWatch.remove(device.id)?.cancel()
        demoLocal -= device.id
        settings.saveLocalTv(device.id, null)
        _state.update { it.copy(local = it.local - device.id) }
    }

    fun send(device: Device, cmd: Command, force: Boolean = false, optimistic: (DeviceState) -> DeviceState) {
        val r = repo ?: return
        if (device.state.online == false && !force) {
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

    // ---------- Controle remoto e comandos rápidos ----------

    private val queues = mutableMapOf<String, Mutex>()
    private val waiting = mutableMapOf<String, Int>()
    private val syncJobs = mutableMapOf<String, Job>()
    private val keyNames = mutableMapOf<String, Map<RemoteKey, String>>()
    private val useKeypad = mutableSetOf<String>()
    private val syncedAt = mutableMapOf<String, Long>()
    private var focusedId: String? = null

    fun deviceClosed() { focusedId = null }

    private fun queue(id: String) = synchronized(queues) { queues.getOrPut(id) { Mutex() } }
    private val lastSentAt = mutableMapOf<String, Long>()
    private val lastOkAt = mutableMapOf<String, Long>()

    /**
     * Espaça os envios do mesmo aparelho em [SEND_SPACING_MS] para chegarem na ordem,
     * mas NÃO espera a resposta do anterior: um comando lento nunca segura os próximos.
     */
    private suspend fun paced(id: String) {
        queue(id).withLock {
            val wait = (lastSentAt[id] ?: 0L) + SEND_SPACING_MS - System.currentTimeMillis()
            if (wait > 0) delay(wait)
            lastSentAt[id] = System.currentTimeMillis()
        }
    }

    /**
     * Limita comandos em andamento por aparelho: repetições automáticas (dedo segurando) só
     * entram se houver no máximo 1 a caminho; toques normais entram até [MAX_QUEUE].
     */
    private fun admit(id: String, coalesce: Boolean): Boolean = synchronized(waiting) {
        val n = waiting[id] ?: 0
        if (coalesce && n >= 2) return false
        if (n >= MAX_QUEUE) return false
        waiting[id] = n + 1
        true
    }

    private fun done(id: String, ok: Boolean) {
        synchronized(waiting) {
            waiting[id] = ((waiting[id] ?: 1) - 1).coerceAtLeast(0)
            if (ok) lastOkAt[id] = System.currentTimeMillis()
        }
    }

    /** Mostra o erro só se nenhum comando mais novo do mesmo aparelho já funcionou (evita alarme falso). */
    private fun reportError(id: String, startedAt: Long, e: Exception, fallback: String) {
        val newerWorked = synchronized(waiting) { (lastOkAt[id] ?: 0L) > startedAt }
        if (newerWorked) return
        val msg = if (e is TimeoutCancellationException) "A TV demorou para responder. Tente de novo." else e.message
        _state.update { it.copy(message = msg ?: fallback) }
    }

    /**
     * Comando rápido (teclas, volume, canal): sai na hora, em paralelo, espaçado só o
     * suficiente para manter a ordem. Com [coalesce], repetições de dedo segurando são
     * descartadas se a TV ainda não respondeu às anteriores (não acumula atraso).
     * Depois de comandos de volume/canal/fonte o app relê o estado real do aparelho.
     */
    fun fire(
        device: Device,
        cmd: Command?,
        coalesce: Boolean = false,
        optimistic: ((DeviceState) -> DeviceState)? = null,
    ) {
        // Pelo Wi-Fi: sai na hora, sem fila e sem nuvem. Se a conexão cair, vai pela nuvem sozinho.
        val localKey = cmd?.let { LocalKeys.of(it) }
        val local = localFor(device.id)
        if (cmd != null && localKey != null && local != null) {
            val before = currentState(device)
            if (local.send(localKey) { cloudFire(device, cmd, false, optimistic, before) }) {
                if (optimistic != null) setDeviceState(device.id, optimistic(before))
                synchronized(waiting) { lastOkAt[device.id] = System.currentTimeMillis() }
                if (TvCommands.isRelative(cmd)) scheduleSync(device, delayMs = 1_500)
                return
            }
        }
        cloudFire(device, cmd, coalesce, optimistic, null)
    }

    private fun cloudFire(
        device: Device,
        cmd: Command?,
        coalesce: Boolean,
        optimistic: ((DeviceState) -> DeviceState)?,
        alreadyApplied: DeviceState?,
    ) {
        val r = repo ?: return
        if (cmd == null) {
            _state.update { it.copy(message = "Este aparelho não aceita esse comando.") }
            return
        }
        if (device.state.online == false) {
            _state.update { it.copy(message = "${device.name} está sem conexão. Ligue o aparelho e tente de novo.") }
            return
        }
        if (!admit(device.id, coalesce)) return
        val before = alreadyApplied ?: currentState(device)
        if (optimistic != null && alreadyApplied == null) setDeviceState(device.id, optimistic(before))
        val started = System.currentTimeMillis()
        scope.launch {
            var ok = false
            try {
                paced(device.id)
                withTimeout(COMMAND_TIMEOUT_MS) { r.command(device.id, cmd) }
                ok = true
                if (TvCommands.isRelative(cmd)) scheduleSync(device)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException && e !is TimeoutCancellationException) throw e
                if (optimistic != null && synchronized(waiting) { (lastOkAt[device.id] ?: 0L) <= started }) setDeviceState(device.id, before)
                reportError(device.id, started, e, "Não foi possível enviar o comando.")
                scheduleSync(device)
            } finally {
                done(device.id, ok)
            }
        }
    }

    /** Envia uma tecla do controle no formato que a TV aceita, com plano B pelo keypadInput. */
    fun pressKey(device: Device, key: RemoteKey, coalesce: Boolean = false) {
        val local = localFor(device.id)
        if (local != null && local.send(LocalKeys.of(key)) { cloudKey(device, key, false) }) {
            synchronized(waiting) { lastOkAt[device.id] = System.currentTimeMillis() }
            return
        }
        cloudKey(device, key, coalesce)
    }

    /** Número do teclado: pelo Wi-Fi se conectado, senão pela nuvem (keypadInput). */
    fun pressNumber(device: Device, n: Int) {
        val local = localFor(device.id)
        if (local != null && local.send("KEY_$n") { fire(device, TvCommands.number(device, n)) }) return
        fire(device, TvCommands.number(device, n))
    }

    private fun cloudKey(device: Device, key: RemoteKey, coalesce: Boolean) {
        val r = repo ?: return
        if (!device.has(Caps.REMOTE) && !device.has(Caps.KEYPAD)) {
            _state.update { it.copy(message = "Esta TV não recebe teclas de navegação pela SmartThings.") }
            return
        }
        if (device.state.online == false) {
            _state.update { it.copy(message = "${device.name} está sem conexão. Ligue a TV e tente de novo.") }
            return
        }
        if (!admit(device.id, coalesce)) return
        val started = System.currentTimeMillis()
        scope.launch {
            var ok = false
            try {
                paced(device.id)
                val keypad = device.id in useKeypad
                val cmd = TvCommands.key(device, key, keyNames[device.id]?.get(key), preferKeypad = keypad)
                    ?: throw ApiException(0, "Esta TV não recebe teclas de navegação pela SmartThings.")
                try {
                    withTimeout(COMMAND_TIMEOUT_MS) { r.command(device.id, cmd) }
                } catch (e: ApiException) {
                    // Plano B: se a TV recusar o samsungvd.remoteControl, tenta o keypadInput.
                    val canFallback = !keypad && cmd.capability == Caps.REMOTE && device.has(Caps.KEYPAD) &&
                        e.code in setOf(400, 403, 404, 422)
                    if (!canFallback) throw e
                    withTimeout(COMMAND_TIMEOUT_MS) { r.command(device.id, TvCommands.key(device, key, preferKeypad = true)!!) }
                    useKeypad += device.id
                }
                ok = true
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException && e !is TimeoutCancellationException) throw e
                reportError(device.id, started, e, "A TV não recebeu a tecla.")
            } finally {
                done(device.id, ok)
            }
        }
    }

    /**
     * Ao abrir um aparelho: pede para ele atualizar o estado na nuvem e relê tudo,
     * e, para TVs, descobre os nomes de tecla que o modelo aceita.
     */
    fun deviceOpened(device: Device) {
        val r = repo ?: return
        focusedId = device.id
        scheduleSync(device, delayMs = 0)
        refreshInBackground(device)
        if (device.kind == DeviceKind.TV && synchronized(locals) { locals[device.id] } == null) {
            val saved = settings.localTv(device.id)
            when {
                saved != null -> startLocal(device, saved)
                // Primeira vez: procura a TV no Wi-Fi sozinho (uma vez por sessão), sem precisar tocar em nada.
                !r.isDemo && autoTried.add(device.id) -> connectLocal(device)
            }
        }
        if (device.has(Caps.REMOTE) && device.id !in keyNames) {
            scope.launch {
                val accepted = runCatching {
                    r.commandArgValues(Caps.REMOTE, device.capVersions[Caps.REMOTE] ?: 1, "send")
                }.getOrDefault(emptyList())
                keyNames[device.id] = TvCommands.resolveKeys(accepted)
                _state.update { it.copy(remoteKeys = it.remoteKeys + (device.id to accepted)) }
            }
        }
    }

    /**
     * Relê o estado real do aparelho pouco depois do último comando.
     * Só faz leitura (GET): nunca segura a fila de comandos, então as teclas não esperam por ela.
     */
    private fun scheduleSync(device: Device, delayMs: Long = settleDelayMs + 600) {
        val r = repo ?: return
        synchronized(syncJobs) {
            syncJobs[device.id]?.cancel()
            syncJobs[device.id] = scope.launch {
                delay(delayMs)
                val fresh = runCatching { r.status(device.id) }.getOrNull() ?: return@launch
                applyFresh(device.id, fresh)
            }
        }
    }

    private fun applyFresh(id: String, fresh: DeviceState) {
        if (id in _state.value.pending) return
        // Se o usuário mandou comandos depois que a leitura começou, ela já está velha: descarta.
        if (synchronized(waiting) { (waiting[id] ?: 0) > 0 }) return
        val cur = _state.value.devices.firstOrNull { it.id == id }?.state
        setDeviceState(id, fresh.copy(online = fresh.online ?: cur?.online))
        synchronized(syncedAt) { syncedAt[id] = System.currentTimeMillis() }
    }

    /** "refresh" faz a TV reenviar volume/canal à nuvem. Roda à parte, fora da fila, uma vez ao abrir. */
    private fun refreshInBackground(device: Device) {
        val r = repo ?: return
        if (!device.has(Caps.REFRESH)) return
        scope.launch {
            val ok = runCatching {
                withTimeout(8_000) { r.refreshDevice(device.id, device.componentFor(Caps.REFRESH)) }
            }.isSuccess
            if (ok) scheduleSync(device, delayMs = 1_200)
        }
    }

    fun diagnostics(): List<ApiLogEntry> = repo?.diagnostics().orEmpty()

    /** Desliga tudo o que estiver ligado entre os dispositivos visíveis. */
    fun allOff() {
        val targets = _state.value.visibleDevices.filter { it.canToggle && it.state.switchOn == true && it.state.online != false }
        if (targets.isEmpty()) {
            _state.update { it.copy(message = "Nada ligado aqui.") }
            return
        }
        targets.forEach { d -> send(d, d.cmd(Caps.SWITCH, "off")) { it.copy(switchOn = false) } }
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
    fun notify(text: String) = _state.update { it.copy(message = text) }
    fun clearError() = _state.update { it.copy(error = null) }
    fun setAutoRefresh(on: Boolean) {
        settings.autoRefresh = on
        _state.update { it.copy(autoRefresh = on) }
    }
}
