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
 * Consumes the established auth contract verbatim (grant §8):
 *   login → session token → unlock(PIN) → ParentScope token → ChildScope token
 * No refresh tokens, no new gate semantics, no recovery flows.
 */
class AuthRepository(
    private val api: QuestGrowApi,
    private val tokens: TokenStore,
) {
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

    suspend fun forgetEverything() = tokens.clearAll()
}
