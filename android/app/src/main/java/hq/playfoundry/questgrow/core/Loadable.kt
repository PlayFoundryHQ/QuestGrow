package hq.playfoundry.questgrow.core

/**
 * The state of one asynchronously-loaded section (Phase J — parent-tab
 * loading / empty / error / retry). Distinct from the transient one-shot
 * "Saved." / "Created." action feedback, which stays a plain message.
 */
sealed interface Loadable<out T> {
    data object Idle : Loadable<Nothing>
    data object Loading : Loadable<Nothing>
    data class Loaded<T>(val value: T) : Loadable<T>

    /** [offline] distinguishes "no network" from a real server error, so the UI
     *  can say the right thing. [retry] re-runs the loader. */
    data class Failed(
        val message: String,
        val offline: Boolean = false,
    ) : Loadable<Nothing>

    val valueOrNull: T? get() = (this as? Loaded<T>)?.value
}

/** Fold an [ApiResult] into a [Loadable], mapping the payload. */
fun <T, R> ApiResult<T>.toLoadable(map: (T) -> R): Loadable<R> = when (this) {
    is ApiResult.Ok -> Loadable.Loaded(map(value))
    is ApiResult.Offline -> Loadable.Failed("You're offline.", offline = true)
    is ApiResult.Failure ->
        if (isAuthExpired) Loadable.Failed("Session expired — sign in again.")
        else Loadable.Failed(detail.ifBlank { code })
}
