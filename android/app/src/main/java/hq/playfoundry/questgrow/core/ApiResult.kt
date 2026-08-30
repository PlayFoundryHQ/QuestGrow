package hq.playfoundry.questgrow.core

/**
 * Every network call returns one of these. The Android client never invents a
 * fourth outcome — the server is authoritative (QuestGrow LEADERSHIP_PROTOCOL
 * §4 / Phase G grant §4).
 */
sealed interface ApiResult<out T> {
    data class Ok<T>(val value: T) : ApiResult<T>

    /**
     * A structured error from the backend. [code] mirrors `errors.py`
     * (`not_authenticated` / `not_authorized` / `not_found` /
     * `contract_violation` / `bad_request`); [status] is the HTTP status.
     */
    data class Failure(
        val status: Int,
        val code: String,
        val detail: String,
    ) : ApiResult<Nothing> {
        val isAuthExpired: Boolean get() = status == 401
        val isForbidden: Boolean get() = status == 403
        val isConflict: Boolean get() = status == 409
        val isNotFound: Boolean get() = status == 404
    }

    /** No response reached us (offline, DNS, timeout). Distinct from [Failure]. */
    data class Offline(val cause: Throwable?) : ApiResult<Nothing>
}

inline fun <T, R> ApiResult<T>.map(f: (T) -> R): ApiResult<R> = when (this) {
    is ApiResult.Ok -> ApiResult.Ok(f(value))
    is ApiResult.Failure -> this
    is ApiResult.Offline -> this
}

fun <T> ApiResult<T>.getOrNull(): T? = (this as? ApiResult.Ok)?.value
