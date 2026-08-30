package hq.playfoundry.questgrow.ui.child

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import hq.playfoundry.questgrow.AppContainer
import hq.playfoundry.questgrow.core.ApiResult
import hq.playfoundry.questgrow.data.model.Celebration
import hq.playfoundry.questgrow.data.model.ChildProgress
import hq.playfoundry.questgrow.data.model.CompletionOutcome
import hq.playfoundry.questgrow.data.model.TodayView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

sealed interface TodayUi {
    data object Loading : TodayUi
    data class Ready(val view: TodayView, val queued: Int) : TodayUi
    data class Error(val message: String, val authExpired: Boolean = false) : TodayUi
    data object NeedsCode : TodayUi
}

class ChildViewModel(private val container: AppContainer) : ViewModel() {

    private val _today = MutableStateFlow<TodayUi>(TodayUi.Loading)
    val today: StateFlow<TodayUi> = _today

    private val _celebration = MutableStateFlow<Celebration?>(null)
    val celebration: StateFlow<Celebration?> = _celebration

    private val _progress = MutableStateFlow<ChildProgress?>(null)
    val progress: StateFlow<ChildProgress?> = _progress

    val online: StateFlow<Boolean> get() = container.online

    private fun today(): String = LocalDate.now().toString()
    private fun monday(): String =
        LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString()

    fun refresh() {
        container.useChildScope()
        if (container.tokenStore.childTokenBlocking() == null) {
            _today.value = TodayUi.NeedsCode
            return
        }
        _today.value = TodayUi.Loading
        viewModelScope.launch {
            container.childRepo.flushQueue()
            when (val r = container.childRepo.today(today())) {
                is ApiResult.Ok -> _today.value = TodayUi.Ready(r.value, container.childRepo.pendingCount())
                is ApiResult.Offline ->
                    _today.value = TodayUi.Error("الان به اینترنت وصل نیستی. یک کم دیگر دوباره امتحان کن.")
                is ApiResult.Failure ->
                    if (r.isAuthExpired) _today.value = TodayUi.NeedsCode
                    else _today.value = TodayUi.Error(r.detail.ifBlank { "یک مشکلی پیش آمد." })
            }
        }
    }

    /** returns the outcome so the screen can navigate to celebration / waiting. */
    fun complete(questId: String, onOutcome: (CompletionOutcome) -> Unit) {
        viewModelScope.launch {
            val outcome = container.childRepo.complete(questId, today())
            if (outcome is CompletionOutcome.Verified) loadLatestCelebration()
            onOutcome(outcome)
            refresh()
        }
    }

    private suspend fun loadLatestCelebration() {
        when (val r = container.childRepo.celebrations(null)) {
            is ApiResult.Ok -> _celebration.value = r.value.lastOrNull()
            else -> _celebration.value = null
        }
    }

    fun clearCelebration() { _celebration.value = null }

    fun loadProgress() {
        viewModelScope.launch {
            (container.childRepo.progress(monday()) as? ApiResult.Ok)?.let { _progress.value = it.value }
        }
    }

    fun setCode(code: String) {
        viewModelScope.launch {
            container.authRepo.useChildToken(code)
            refresh()
        }
    }
}
