package com.lucas.mobilemanager.zones

import android.content.Context
import com.lucas.mobilemanager.di.ApplicationScope
import com.lucas.mobilemanager.domain.zones.ZoneDetectorFile
import com.lucas.mobilemanager.domain.zones.ZoneDetectorParser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Détecteurs de zones en vigueur : la version personnalisée (`filesDir/zone_detectors.json`) si
 * elle existe et est valide, sinon celle livrée dans les assets.
 */
@Singleton
class ZoneDetectorStore @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope scope: CoroutineScope,
) {
    private val overrideFile = File(context.filesDir, FILE_NAME)

    private val _detectors = MutableStateFlow(ZoneDetectorFile.EMPTY)
    val detectors: StateFlow<ZoneDetectorFile> = _detectors.asStateFlow()

    private val _isCustom = MutableStateFlow(false)
    val isCustom: StateFlow<Boolean> = _isCustom.asStateFlow()

    init {
        scope.launch(Dispatchers.IO) { reload() }
    }

    /** Texte JSON actuellement en vigueur, pour l'éditeur. */
    suspend fun currentText(): String = withContext(Dispatchers.IO) {
        if (_isCustom.value) overrideFile.readText() else assetText()
    }

    /** Enregistre une version personnalisée, uniquement si elle est valide. */
    suspend fun save(text: String): ZoneDetectorParser.Result = withContext(Dispatchers.IO) {
        val result = ZoneDetectorParser.parse(text)
        if (result is ZoneDetectorParser.Result.Valid) {
            overrideFile.writeText(text)
            _detectors.value = result.file
            _isCustom.value = true
        }
        result
    }

    /** Revient aux détecteurs livrés avec l'app. */
    suspend fun reset() = withContext(Dispatchers.IO) {
        overrideFile.delete()
        reload()
    }

    private fun reload() {
        val custom = overrideFile.takeIf { it.exists() }?.readText()?.let(ZoneDetectorParser::parse)
        if (custom is ZoneDetectorParser.Result.Valid) {
            _detectors.value = custom.file
            _isCustom.value = true
            return
        }
        val shipped = ZoneDetectorParser.parse(assetText())
        _detectors.value = (shipped as? ZoneDetectorParser.Result.Valid)?.file ?: ZoneDetectorFile.EMPTY
        _isCustom.value = false
    }

    private fun assetText(): String = context.assets.open(FILE_NAME).bufferedReader().use { it.readText() }

    private companion object {
        const val FILE_NAME = "zone_detectors.json"
    }
}
