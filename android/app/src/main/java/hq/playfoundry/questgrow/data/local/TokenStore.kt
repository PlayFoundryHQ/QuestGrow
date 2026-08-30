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

private val Context.tokenDataStore: DataStore<Preferences> by preferencesDataStore("questgrow_tokens")

/**
 * All device-local client state. App-private (`allowBackup=false`).
 *
 *  * [parentToken] — short-lived; cleared on 401 / sign-out. No refresh tokens.
 *  * [childToken] — long-lived, per-child.
 *  * account email + password — stored so the parent gate can be **PIN-only**
 *    (the client replays login+unlock behind the PIN). A deliberate
 *    simplification for a single-family personal deployment ([[auth-policy]]).
 *  * default child — which child this device's board shows.
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
}

class DataStoreTokenStore(private val context: Context) : TokenStore {
    private val PARENT = stringPreferencesKey("parent_token")
    private val CHILD = stringPreferencesKey("child_token")
    private val EMAIL = stringPreferencesKey("account_email")
    private val PASS = stringPreferencesKey("account_password")
    private val CHILD_ID = stringPreferencesKey("default_child_id")
    private val CHILD_NAME = stringPreferencesKey("default_child_name")

    override val parentToken: Flow<String?> = context.tokenDataStore.data.map { it[PARENT] }
    override val childToken: Flow<String?> = context.tokenDataStore.data.map { it[CHILD] }

    @Volatile private var parentCache: String? = null
    @Volatile private var childCache: String? = null

    private fun get(key: Preferences.Key<String>): String? =
        runBlocking { context.tokenDataStore.data.first()[key] }

    private suspend fun put(vararg kv: kotlin.Pair<Preferences.Key<String>, String?>) {
        context.tokenDataStore.edit { p -> kv.forEach { (k, v) -> if (v == null) p.remove(k) else p[k] = v } }
    }

    override suspend fun setParentToken(token: String?) { parentCache = token; put(kotlin.Pair(PARENT, token)) }
    override suspend fun setChildToken(token: String?) { childCache = token; put(kotlin.Pair(CHILD, token)) }

    override suspend fun clearAll() {
        parentCache = null; childCache = null
        context.tokenDataStore.edit { it.clear() }
    }

    override fun parentTokenBlocking(): String? =
        parentCache ?: runBlocking { parentToken.first() }.also { parentCache = it }
    override fun childTokenBlocking(): String? =
        childCache ?: runBlocking { childToken.first() }.also { childCache = it }

    override suspend fun setAccount(email: String, password: String) =
        put(kotlin.Pair(EMAIL, email.trim()), kotlin.Pair(PASS, password))
    override fun accountEmailBlocking() = get(EMAIL)
    override fun accountPasswordBlocking() = get(PASS)

    override suspend fun setDefaultChild(id: String, name: String) =
        put(kotlin.Pair(CHILD_ID, id), kotlin.Pair(CHILD_NAME, name))
    override fun defaultChildIdBlocking() = get(CHILD_ID)
    override fun defaultChildNameBlocking() = get(CHILD_NAME)
}
