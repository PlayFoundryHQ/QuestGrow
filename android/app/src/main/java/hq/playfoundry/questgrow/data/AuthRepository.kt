package hq.playfoundry.questgrow.data

import hq.playfoundry.questgrow.core.ApiResult
import hq.playfoundry.questgrow.data.local.TokenStore
import hq.playfoundry.questgrow.data.net.ChildTokenBody
import hq.playfoundry.questgrow.data.net.LoginBody
import hq.playfoundry.questgrow.data.net.QuestGrowApi
import hq.playfoundry.questgrow.data.net.SignupBody
import hq.playfoundry.questgrow.data.net.UnlockBody
import hq.playfoundry.questgrow.data.net.apiCall

/**
 * Consumes the established auth contract verbatim:
 *   login → session token → unlock(PIN) → ParentScope token → ChildScope token
 * No refresh tokens. The account email+password are stored on the device
 * ([TokenStore]) so the everyday parent gate is PIN-only — [unlockWithPin]
 * replays login+unlock. Personal single-family simplification ([[auth-policy]]).
 */
class AuthRepository(
    private val api: QuestGrowApi,
    private val tokens: TokenStore,
    private val onForget: () -> Unit = {},
) {
    fun isOnboarded(): Boolean = tokens.accountEmailBlocking() != null

    /** Sign up, sign in, and remember the account on this device. */
    suspend fun registerParent(email: String, password: String, pin: String): ApiResult<Unit> {
        return when (val s = signUp(email, password, pin)) {
            is ApiResult.Ok -> when (val r = signInAsParent(email, password, pin)) {
                is ApiResult.Ok -> { tokens.setAccount(email, password); ApiResult.Ok(Unit) }
                is ApiResult.Failure -> r
                is ApiResult.Offline -> r
            }
            is ApiResult.Failure -> s
            is ApiResult.Offline -> s
        }
    }

    /**
     * Returning parent on a fresh device: sign in with full credentials and
     * remember the account here (so the everyday PIN gate works afterwards).
     * Used by the onboarding "قبلاً حساب دارم" path — the account already
     * exists server-side so `registerParent` would 409 on the email.
     */
    suspend fun signInExisting(email: String, password: String, pin: String): ApiResult<Unit> =
        when (val r = signInAsParent(email, password, pin)) {
            is ApiResult.Ok -> { tokens.setAccount(email, password); ApiResult.Ok(Unit) }
            is ApiResult.Failure -> r
            is ApiResult.Offline -> r
        }

    /** Everyday parent gate: PIN only, using the stored account. */
    suspend fun unlockWithPin(pin: String): ApiResult<Unit> {
        val email = tokens.accountEmailBlocking()
        val pass = tokens.accountPasswordBlocking()
        if (email == null || pass == null) {
            return ApiResult.Failure(401, "not_authenticated", "no account on this device")
        }
        return signInAsParent(email, pass, pin)
    }

    /** login + unlock in one call — returns the parent token, or the failure. */
    suspend fun signInAsParent(email: String, password: String, pin: String): ApiResult<Unit> {
        val login = apiCall { api.login(LoginBody(email.trim(), password)) }
        val session = when (login) {
            is ApiResult.Ok -> login.value.sessionToken
            is ApiResult.Failure -> return login
            is ApiResult.Offline -> return login
        }
        return when (val unlock = apiCall { api.unlock(UnlockBody(session, pin)) }) {
            is ApiResult.Ok -> {
                tokens.setParentToken(unlock.value.parentToken)
                ApiResult.Ok(Unit)
            }
            is ApiResult.Failure -> unlock
            is ApiResult.Offline -> unlock
        }
    }

    suspend fun signUp(email: String, password: String, pin: String): ApiResult<Unit> =
        apiCall { api.signup(SignupBody(email.trim(), password, pin)) }.let {
            when (it) {
                is ApiResult.Ok -> ApiResult.Ok(Unit)
                is ApiResult.Failure -> it
                is ApiResult.Offline -> it
            }
        }

    /** Parent (already unlocked) mints a per-child token; store it for the child device. */
    suspend fun issueChildToken(childId: String): ApiResult<String> =
        apiCall { api.childToken(ChildTokenBody(childId)) }.let { r ->
            when (r) {
                is ApiResult.Ok -> ApiResult.Ok(r.value.childToken)
                is ApiResult.Failure -> r
                is ApiResult.Offline -> r
            }
        }

    suspend fun useChildToken(token: String) = tokens.setChildToken(token.trim())

    suspend fun switchActiveChild(childId: String) = tokens.setActiveChild(childId)

    /**
     * Family (shared) device: every child on the account belongs on the kid
     * board's switcher — no per-child "activate" step. Mints a token for any
     * child that lacks one and drops tokens for children removed from the
     * account. Requires an active parent scope; a paired kid-only device
     * (no stored account) no-ops. Returns the child count on the device.
     */
    suspend fun syncFamilyChildren(): Int {
        if (tokens.accountEmailBlocking() == null) return tokens.deviceChildrenBlocking().size
        val kids = when (val r = apiCall { api.listChildren() }) {
            is ApiResult.Ok -> r.value
            else -> return tokens.deviceChildrenBlocking().size
        }
        val have = tokens.deviceChildrenBlocking().map { it.first }.toSet()
        val want = kids.associateBy { it.childId }
        for (k in kids) if (k.childId !in have) {
            (issueChildToken(k.childId) as? ApiResult.Ok)?.let {
                tokens.putChildToken(k.childId, k.name, it.value, makeActive = false)
            }
        }
        for (id in have - want.keys) tokens.removeChildToken(id)
        if (tokens.activeChildIdBlocking().let { it == null || it !in want }) {
            kids.firstOrNull()?.let { tokens.setActiveChild(it.childId) }
        }
        return tokens.deviceChildrenBlocking().size
    }

    fun childrenOnDevice(): List<hq.playfoundry.questgrow.data.model.DeviceChild> =
        tokens.deviceChildrenBlocking().map {
            hq.playfoundry.questgrow.data.model.DeviceChild(it.first, it.second)
        }

    fun activeChildId(): String? = tokens.activeChildIdBlocking()

    /** Parent (unlocked) mints a short 6-digit code for a child's own device. */
    suspend fun createPairingCode(childId: String): ApiResult<String> =
        apiCall { api.pairingCode(hq.playfoundry.questgrow.data.net.ChildTokenBody(childId)) }.let { r ->
            when (r) {
                is ApiResult.Ok -> ApiResult.Ok(r.value.code)
                is ApiResult.Failure -> r
                is ApiResult.Offline -> r
            }
        }

    /** Child device: exchange a 6-digit code for its long-lived child token. */
    suspend fun pairWithCode(code: String): ApiResult<Unit> =
        when (val r = apiCall { api.pair(hq.playfoundry.questgrow.data.net.PairBody(code.trim())) }) {
            is ApiResult.Ok -> { tokens.setChildToken(r.value.childToken); ApiResult.Ok(Unit) }
            is ApiResult.Failure -> r
            is ApiResult.Offline -> r
        }

    suspend fun signOutParent() = tokens.setParentToken(null)

    suspend fun forgetEverything() {
        tokens.clearAll()
        onForget()
    }
}
