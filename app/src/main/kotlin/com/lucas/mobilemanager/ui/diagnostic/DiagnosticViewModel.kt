package com.lucas.mobilemanager.ui.diagnostic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lucas.mobilemanager.diagnostic.DiagnosticStore
import com.lucas.mobilemanager.diagnostic.HierarchyCapture
import com.lucas.mobilemanager.domain.repository.StrictSessionRepository
import com.lucas.mobilemanager.domain.time.TimeProvider
import com.lucas.mobilemanager.domain.zones.UiNode
import com.lucas.mobilemanager.domain.zones.ZoneDetectorParser
import com.lucas.mobilemanager.zones.ZoneDetectorStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Résultat de la dernière action sur l'éditeur de détecteurs. */
sealed interface EditorMessage {
    data object Valid : EditorMessage
    data object Saved : EditorMessage
    data object Reset : EditorMessage
    data class Invalid(val message: String) : EditorMessage
}

@HiltViewModel
class DiagnosticViewModel @Inject constructor(
    private val diagnosticStore: DiagnosticStore,
    private val detectorStore: ZoneDetectorStore,
    strictSessionRepository: StrictSessionRepository,
    timeProvider: TimeProvider,
) : ViewModel() {

    val overlayEnabled: StateFlow<Boolean> = diagnosticStore.overlayEnabled
    val capture: StateFlow<HierarchyCapture?> = diagnosticStore.capture
    val isCustom: StateFlow<Boolean> = detectorStore.isCustom

    /** Pendant une session de concentration, on ne peut pas affaiblir les détecteurs. */
    val locked: StateFlow<Boolean> = strictSessionRepository.observeCurrent()
        .map { it?.isRunning(timeProvider.now()) == true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _editorText = MutableStateFlow("")
    val editorText: StateFlow<String> = _editorText.asStateFlow()

    private val _message = MutableStateFlow<EditorMessage?>(null)
    val message: StateFlow<EditorMessage?> = _message.asStateFlow()

    init {
        viewModelScope.launch { _editorText.value = detectorStore.currentText() }
    }

    fun setOverlayEnabled(enabled: Boolean) = diagnosticStore.setOverlayEnabled(enabled)

    fun clearCapture() = diagnosticStore.clear()

    fun setEditorText(text: String) {
        _editorText.value = text
        _message.value = null
    }

    fun check() {
        _message.value = when (val result = ZoneDetectorParser.parse(editorText.value)) {
            is ZoneDetectorParser.Result.Valid -> EditorMessage.Valid
            is ZoneDetectorParser.Result.Invalid -> EditorMessage.Invalid(result.message)
        }
    }

    fun save() {
        if (locked.value) return
        viewModelScope.launch {
            _message.value = when (val result = detectorStore.save(editorText.value)) {
                is ZoneDetectorParser.Result.Valid -> EditorMessage.Saved
                is ZoneDetectorParser.Result.Invalid -> EditorMessage.Invalid(result.message)
            }
        }
    }

    fun reset() {
        if (locked.value) return
        viewModelScope.launch {
            detectorStore.reset()
            _editorText.value = detectorStore.currentText()
            _message.value = EditorMessage.Reset
        }
    }

    companion object {
        /** Signal JSON prêt à coller dans un détecteur, construit à partir d'un nœud capturé. */
        fun signalFor(node: UiNode): String {
            val viewId = node.viewId
            val desc = node.desc
            val text = node.text
            val parts = buildList {
                when {
                    viewId != null -> add("\"viewId\": \"${viewId.substringAfter(":id/").escape()}\"")
                    desc != null -> add("\"desc\": [\"${desc.escape()}\"]")
                    text != null -> add("\"text\": [\"${text.escape()}\"]")
                }
                if (node.selected) add("\"selected\": true")
            }
            return "{ ${parts.joinToString(", ")} }"
        }

        private fun String.escape() = replace("\\", "\\\\").replace("\"", "\\\"")
    }
}
