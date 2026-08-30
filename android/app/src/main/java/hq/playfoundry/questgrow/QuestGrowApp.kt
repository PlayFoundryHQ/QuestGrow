package hq.playfoundry.questgrow

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import hq.playfoundry.questgrow.data.AuthRepository
import hq.playfoundry.questgrow.data.ChildRepository
import hq.playfoundry.questgrow.data.ParentRepository
import hq.playfoundry.questgrow.data.local.DataStoreTokenStore
import hq.playfoundry.questgrow.data.local.FileOfflineQueue
import hq.playfoundry.questgrow.data.local.OfflineQueue
import hq.playfoundry.questgrow.data.local.ReadCache
import hq.playfoundry.questgrow.data.local.TokenStore
import hq.playfoundry.questgrow.data.net.ApiClientFactory
import hq.playfoundry.questgrow.data.net.QuestGrowApi
import hq.playfoundry.questgrow.data.net.TokenProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Manual dependency graph (grant §5 — deliberately no Hilt/KAPT for a
 * single-shot-buildable client). Which bearer token is attached — parent or
 * child — depends on which surface is active; [activeScope] switches it.
 */
class AppContainer(context: Context) {

    enum class Scope { NONE, PARENT, CHILD }

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val tokenStore: TokenStore = DataStoreTokenStore(context)

    private val _activeScope = MutableStateFlow(Scope.NONE)
    val activeScope: StateFlow<Scope> = _activeScope
    fun useParentScope() { _activeScope.value = Scope.PARENT }
    fun useChildScope() { _activeScope.value = Scope.CHILD }

    // baseUrl config lives in plain prefs (synchronous); Settings persists it
    // and the container is rebuilt on next launch.
    private val prefs = context.getSharedPreferences("questgrow_prefs", Context.MODE_PRIVATE)
    val baseUrl: String = prefs.getString("base_url", null)?.takeIf { it.isNotBlank() }
        ?: BuildConfig.DEFAULT_BASE_URL

    /** persisted synchronously — the caller restarts the process straight after. */
    @Suppress("ApplySharedPref")
    fun setBaseUrl(url: String) {
        prefs.edit().putString("base_url", url.trim().ifBlank { BuildConfig.DEFAULT_BASE_URL }).commit()
    }

    private val tokenProvider = TokenProvider {
        when (_activeScope.value) {
            Scope.PARENT -> tokenStore.parentTokenBlocking()
            Scope.CHILD -> tokenStore.childTokenBlocking()
            Scope.NONE -> null
        }
    }

    val api: QuestGrowApi = ApiClientFactory.create(baseUrl, tokenProvider)

    private val queue: OfflineQueue = FileOfflineQueue(File(context.filesDir, "questgrow/offline_queue.json"))
    val readCache = ReadCache(File(context.filesDir, "questgrow/cache"))

    val authRepo = AuthRepository(api, tokenStore, onForget = { readCache.clear() })
    val childRepo = ChildRepository(api, queue, readCache)
    val parentRepo = ParentRepository(api)

    /** true when the device currently has validated internet. */
    val online = MutableStateFlow(true)

    fun observeConnectivity(context: Context) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { online.value = true; flush() }
            override fun onLost(network: Network) { online.value = false }
            override fun onCapabilitiesChanged(n: Network, caps: NetworkCapabilities) {
                online.value = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            }
        }
        runCatching { cm.registerDefaultNetworkCallback(cb) }
    }

    fun flush() {
        if (_activeScope.value == Scope.CHILD || tokenStore.childTokenBlocking() != null) {
            appScope.launch { runCatching { childRepo.flushQueue() } }
        }
    }
}

class QuestGrowApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.observeConnectivity(this)
    }
}

/**
 * Cleanly relaunch the app after a config change (e.g. the backend URL).
 * Starts a fresh task at the launcher entry point and ends the current
 * process, so ``Application.onCreate`` rebuilds ``AppContainer`` from the
 * (already persisted) config. Nothing about auth / tokens / TTLs changes —
 * the DataStore-backed session survives the restart.
 */
fun Context.restartApp() {
    val intent = packageManager.getLaunchIntentForPackage(packageName)
        ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK) }
    if (intent != null) {
        startActivity(intent)
        Runtime.getRuntime().exit(0)
    }
}
