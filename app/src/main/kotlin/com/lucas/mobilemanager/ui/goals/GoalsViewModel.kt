package com.lucas.mobilemanager.ui.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lucas.mobilemanager.apps.InstalledApps
import com.lucas.mobilemanager.domain.goals.*
import com.lucas.mobilemanager.domain.repository.StrictSessionRepository
import com.lucas.mobilemanager.domain.time.TimeProvider
import com.lucas.mobilemanager.goals.GoalReport
import com.lucas.mobilemanager.goals.GoalReportLoader
import com.lucas.mobilemanager.goals.TemporaryAccessManager
import com.lucas.mobilemanager.lock.AppLock
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GoalsViewModel @Inject constructor(
    private val repository: GoalRepository,
    private val sessions: StrictSessionRepository,
    private val lock: AppLock,
    val time: TimeProvider,
    val temporary: TemporaryAccessManager,
    private val reports: GoalReportLoader,
    installedApps: InstalledApps,
) : ViewModel() {
    val goals = repository.goals.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val apps = flow { emit(installedApps.blockable()) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val tick = flow { while (true) { emit(time.now()); delay(1000) } }
    val today = tick.map { it.atZone(time.zone()).toLocalDate() }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), time.localNow().toLocalDate())
    val editable = combine(lock.status, sessions.observeCurrent(), tick) { status, session, now ->
        status.canModify && session?.isRunning(now) != true
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val report = MutableStateFlow<GoalReport?>(null)
    val reportLoading = MutableStateFlow(false)
    val reportFailed = MutableStateFlow(false)
    private var reportJob: kotlinx.coroutines.Job? = null

    private suspend fun canEdit() = lock.canModifyNow() && sessions.current()?.isRunning(time.now()) != true

    suspend fun save(goal: PersonalGoal): Boolean {
        if (!canEdit() || goal.dueDate.isBefore(time.localNow().toLocalDate())) return false
        repository.save(goal)
        return true
    }

    fun complete(goal: PersonalGoal) { viewModelScope.launch { if (canEdit()) repository.save(goal.copy(completed = true)) } }
    fun delete(goal: PersonalGoal) { viewModelScope.launch { if (canEdit()) repository.delete(goal.id) } }

    fun loadReport(goal: PersonalGoal) {
        reportJob?.cancel()
        report.value = null
        reportLoading.value = true
        reportFailed.value = false
        reportJob = viewModelScope.launch {
            try { report.value = reports.load(goal.packages) }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { reportFailed.value = true }
            finally { reportLoading.value = false }
        }
    }
}
