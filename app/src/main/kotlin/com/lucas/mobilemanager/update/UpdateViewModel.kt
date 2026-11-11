package com.lucas.mobilemanager.update

import com.lucas.mobilemanager.domain.update.AppRelease
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lucas.mobilemanager.domain.repository.StrictSessionRepository
import com.lucas.mobilemanager.domain.time.TimeProvider
import com.lucas.mobilemanager.lock.AppLock
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object Current : UpdateState
    data object Unpublished : UpdateState
    data object Incompatible : UpdateState
    data object Failed : UpdateState
    data object Restricted : UpdateState
    data class Available(val release: AppRelease) : UpdateState
    data class Downloading(val percent: Int) : UpdateState
    data class Ready(val apk: File) : UpdateState
}

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val updater: GitHubUpdater,
    private val sessions: StrictSessionRepository,
    private val time: TimeProvider,
    private val lock: AppLock,
) : ViewModel() {
    private val mutableState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state = mutableState.asStateFlow()
    private var busy = false

    private suspend fun allowed() = lock.canModifyNow() && sessions.observeCurrent().first()?.isRunning(time.now()) != true

    fun check() = run {
        mutableState.value = UpdateState.Checking
        mutableState.value = updater.latest()?.let { UpdateState.Available(it) } ?: UpdateState.Current
    }

    fun download(release: AppRelease) = run {
        mutableState.value = UpdateState.Downloading(0)
        val file = updater.download(release) { mutableState.value = UpdateState.Downloading(it) }
        mutableState.value = UpdateState.Ready(file)
    }

    fun install(action: (File) -> Unit) = run {
        val file = (mutableState.value as? UpdateState.Ready)?.apk ?: return@run
        withContext(Dispatchers.IO) { updater.validateApk(file) }
        action(file)
    }

    private fun run(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                if (allowed()) action() else mutableState.value = UpdateState.Restricted
            } catch (error: CancellationException) { throw error
            } catch (error: NoReleaseException) { mutableState.value = UpdateState.Unpublished
            } catch (error: IncompatibleReleaseException) { mutableState.value = UpdateState.Incompatible
            } catch (error: Exception) { mutableState.value = UpdateState.Failed
            } finally { busy = false }
        }
    }
}
