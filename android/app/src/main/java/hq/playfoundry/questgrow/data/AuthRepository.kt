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

    suspend fun signOutParent() = tokens.setParentToken(null)

    suspend fun forgetEverything() {
        tokens.clearAll()
        onForget()
    }
}
