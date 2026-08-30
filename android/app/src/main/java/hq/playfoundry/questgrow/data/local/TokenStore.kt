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
 * Persists client session state only (grant §12):
 *  * [parentToken] — short-lived; cleared on 401 / sign-out. The Android
 *    client does **not** implement refresh tokens (that is a reserved product
 *    decision — grant §8).
 *  * [childToken] — long-lived, per-child; this is a child's device.
 *  * [baseUrl] — the backend the app talks to.
 */
interface TokenStore {
    val parentToken: Flow<String?>
    val childToken: Flow<String?>
    suspend fun setParentToken(token: String?)
    suspend fun setChildToken(token: String?)
    suspend fun clearAll()
    fun parentTokenBlocking(): String?
    fun childTokenBlocking(): String?
}

class DataStoreTokenStore(private val context: Context) : TokenStore {
    private val PARENT = stringPreferencesKey("parent_token")
    private val CHILD = stringPreferencesKey("child_token")

    override val parentToken: Flow<String?> = context.tokenDataStore.data.map { it[PARENT] }
    override val childToken: Flow<String?> = context.tokenDataStore.data.map { it[CHILD] }

    @Volatile private var parentCache: String? = null
    @Volatile private var childCache: String? = null

    override suspend fun setParentToken(token: String?) {
        parentCache = token
        context.tokenDataStore.edit { p -> if (token == null) p.remove(PARENT) else p[PARENT] = token }
    }

    override suspend fun setChildToken(token: String?) {
        childCache = token
        context.tokenDataStore.edit { p -> if (token == null) p.remove(CHILD) else p[CHILD] = token }
    }

    override suspend fun clearAll() {
        parentCache = null; childCache = null
        context.tokenDataStore.edit { it.clear() }
    }

    override fun parentTokenBlocking(): String? =
        parentCache ?: runBlocking { parentToken.first() }.also { parentCache = it }

    override fun childTokenBlocking(): String? =
        childCache ?: runBlocking { childToken.first() }.also { childCache = it }
}
