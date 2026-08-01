package com.morningdigest.app.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.morningdigest.app.MorningDigestApp
import com.morningdigest.app.data.model.DigestReport
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val container get() = (getApplication<Application>() as MorningDigestApp).container

    val history: StateFlow<List<DigestReport>> = container.digestRepository.observeHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun delete(id: Long) = viewModelScope.launch { container.digestRepository.deleteReport(id) }
    fun clearAll() = viewModelScope.launch { container.digestRepository.clearHistory() }

    /** Re-inserts a just-deleted report (as a new row) so a Snackbar "Undo" can restore it. */
    fun restore(report: DigestReport) = viewModelScope.launch {
        container.digestRepository.saveReport(report.copy(id = 0L))
    }
}
