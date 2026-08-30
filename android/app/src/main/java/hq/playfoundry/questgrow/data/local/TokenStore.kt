package hq.playfoundry.questgrow.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

private val Context.tokenDataStore: DataStore<Preferences> by preferencesDataStore("questgrow_tokens")

/**
 * All device-local client state. App-private (`allowBackup=false`).
 *
 *  * [parentToken] — short-lived; cleared on 401 / sign-out. No refresh tokens.
 *  * child tokens — long-lived, **one per child activated on this device**. The
 *    family device can hold several ([putChildToken]); [childToken] is the
 *    *active* one the board currently shows. A paired kid-only device still
 *    uses the single-token path ([setChildToken]).
 *  * account email + password — stored so the parent gate can be **PIN-only**
 *    (the client replays login+unlock behind the PIN). A deliberate
 *    simplification for a single-family personal deployment ([[auth-policy]]).
 *
 * The multi-child map is mirrored in memory (updated synchronously by every
 * mutator) so the blocking getters never race a DataStore write that is still
 * flushing on its own dispatcher.
 */
interface TokenStore {
    val parentToken: Flow<String?>
    val childToken: Flow<String?>
    suspend fun setParentToken(token: String?)
    suspend fun setChildToken(token: String?)
    suspend fun clearAll()
    fun parentTokenBlocking(): String?
    fun childTokenBlocking(): String?

    suspend fun setAccount(email: String, password: String)
    fun accountEmailBlocking(): String?
    fun accountPasswordBlocking(): String?

    suspend fun setDefaultChild(id: String, name: String)
    fun defaultChildIdBlocking(): String?
    fun defaultChildNameBlocking(): String?

    /**
     * Add / replace a child's token on this device. [makeActive] switches the
     * board to this child (true for an explicit choice; false for a bulk sync).
     */
    suspend fun putChildToken(id: String, name: String, token: String, makeActive: Boolean = true)
    /** Switch which activated child the board shows. No-op if [id] isn't on the device. */
    suspend fun setActiveChild(id: String)
    /** Remove one child from this device (its token + name). */
    suspend fun removeChildToken(id: String)
    /** Every child activated on this device, in insertion order. */
    fun deviceChildrenBlocking(): List<Pair<String, String>>
    fun activeChildIdBlocking(): String?
}

class DataStoreTokenStore(private val context: Context) : TokenStore {
    private val PARENT = stringPreferencesKey("parent_token")
    private val CHILD = stringPreferencesKey("child_token")          // legacy / single-device
    private val EMAIL = stringPreferencesKey("account_email")
    private val PASS = stringPreferencesKey("account_password")
    private val CHILD_ID = stringPreferencesKey("default_child_id")
    private val CHILD_NAME = stringPreferencesKey("default_child_name")
    private val CHILD_TOKENS = stringPreferencesKey("child_tokens")   // {id: token}
    private val CHILD_NAMES = stringPreferencesKey("child_names")     // {id: name}
    private val ORDER = stringPreferencesKey("child_order")           // id,id,id
    private val ACTIVE_CHILD = stringPreferencesKey("active_child_id")

    override val parentToken: Flow<String?> = context.tokenDataStore.data.map { it[PARENT] }
    override val childToken: Flow<String?> = context.tokenDataStore.data.map { activeTokenFrom(it) }

    // --- in-memory mirror of the multi-child map (source of truth for reads) ---
    private data class Kids(
        val tokens: MutableMap<String, String> = linkedMapOf(),
        val names: MutableMap<String, String> = linkedMapOf(),
        var active: String? = null,
        var legacy: String? = null,      // single-device token (no id)
        var loaded: Boolean = false,
    )
    private val kids = Kids()

    @Volatile private var parentCache: String? = null

    private fun snapshot(): Preferences = runBlocking { context.tokenDataStore.data.first() }
    private fun get(key: Preferences.Key<String>): String? = snapshot()[key]
    private fun obj(raw: String?): JSONObject = runCatching { JSONObject(raw ?: "{}") }.getOrDefault(JSONObject())

    @Synchronized
    private fun ensureLoaded() {
        if (kids.loaded) return
        val p = snapshot()
        val toks = obj(p[CHILD_TOKENS]); val names = obj(p[CHILD_NAMES])
        val order = p[ORDER]?.split(",")?.filter { it.isNotBlank() }
            ?: toks.keys().asSequence().toList()
        for (id in order) {
            toks.optString(id, "").takeIf { it.isNotEmpty() }?.let { kids.tokens[id] = it }
            kids.names[id] = names.optString(id, id)
        }
        kids.active = p[ACTIVE_CHILD] ?: p[CHILD_ID]
        kids.legacy = p[CHILD]
        // legacy single-token device with a known child → synthesise a row
        if (kids.tokens.isEmpty() && kids.legacy != null && p[CHILD_ID] != null) {
            kids.names.putIfAbsent(p[CHILD_ID]!!, p[CHILD_NAME] ?: p[CHILD_ID]!!)
        }
        kids.loaded = true
    }

    private fun activeToken(): String? {
        ensureLoaded()
        kids.active?.let { a -> kids.tokens[a]?.let { return it } }
        return kids.legacy
    }

    /** flow variant — reads straight off the preferences object. */
    private fun activeTokenFrom(p: Preferences): String? {
        val active = p[ACTIVE_CHILD] ?: p[CHILD_ID]
        if (active != null) {
            val t = obj(p[CHILD_TOKENS]).optString(active, "")
            if (t.isNotEmpty()) return t
        }
        return p[CHILD]
    }

    private suspend fun persistKids() {
        context.tokenDataStore.edit { p ->
            p[CHILD_TOKENS] = JSONObject(kids.tokens as Map<*, *>).toString()
            p[CHILD_NAMES] = JSONObject(kids.names as Map<*, *>).toString()
            p[ORDER] = kids.names.keys.joinToString(",")
            kids.active?.let { p[ACTIVE_CHILD] = it; p[CHILD_ID] = it; p[CHILD_NAME] = kids.names[it] ?: it }
                ?: run { p.remove(ACTIVE_CHILD) }
            if (kids.legacy == null) p.remove(CHILD) else p[CHILD] = kids.legacy!!
        }
    }

    private suspend fun put(vararg kv: kotlin.Pair<Preferences.Key<String>, String?>) {
        context.tokenDataStore.edit { p -> kv.forEach { (k, v) -> if (v == null) p.remove(k) else p[k] = v } }
    }

    override suspend fun setParentToken(token: String?) { parentCache = token; put(kotlin.Pair(PARENT, token)) }

    override suspend fun setChildToken(token: String?) {
        ensureLoaded()
        kids.tokens.clear(); kids.names.clear(); kids.active = null; kids.legacy = token
        persistKids()
    }

    override suspend fun putChildToken(id: String, name: String, token: String, makeActive: Boolean) {
        ensureLoaded()
        kids.tokens[id] = token
        kids.names[id] = name
        if (makeActive || kids.active == null) kids.active = id
        kids.legacy = null
        persistKids()
    }

    override suspend fun setActiveChild(id: String) {
        ensureLoaded()
        if (!kids.tokens.containsKey(id)) return
        kids.active = id
        persistKids()
    }

    override suspend fun removeChildToken(id: String) {
        ensureLoaded()
        kids.tokens.remove(id); kids.names.remove(id)
        if (kids.active == id) kids.active = kids.tokens.keys.firstOrNull()
        persistKids()
    }

    override fun deviceChildrenBlocking(): List<Pair<String, String>> {
        ensureLoaded()
        return kids.names.entries.map { it.key to it.value }
    }

    override fun activeChildIdBlocking(): String? { ensureLoaded(); return kids.active }

    override suspend fun clearAll() {
        parentCache = null
        synchronized(this) {
            kids.tokens.clear(); kids.names.clear(); kids.active = null; kids.legacy = null; kids.loaded = true
        }
        context.tokenDataStore.edit { it.clear() }
    }

    override fun parentTokenBlocking(): String? =
        parentCache ?: runBlocking { parentToken.first() }.also { parentCache = it }
    override fun childTokenBlocking(): String? = activeToken()

    override suspend fun setAccount(email: String, password: String) =
        put(kotlin.Pair(EMAIL, email.trim()), kotlin.Pair(PASS, password))
    override fun accountEmailBlocking() = get(EMAIL)
    override fun accountPasswordBlocking() = get(PASS)

    override suspend fun setDefaultChild(id: String, name: String) {
        ensureLoaded()
        kids.names.putIfAbsent(id, name)
        kids.active = kids.active ?: id
        persistKids()
    }
    override fun defaultChildIdBlocking() = activeChildIdBlocking()
    override fun defaultChildNameBlocking(): String? {
        ensureLoaded(); return kids.active?.let { kids.names[it] }
    }
}
