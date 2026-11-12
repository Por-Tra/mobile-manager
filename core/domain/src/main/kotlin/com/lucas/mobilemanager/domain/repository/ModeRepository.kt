package com.lucas.mobilemanager.domain.repository

import com.lucas.mobilemanager.domain.mode.ModeSettings
import kotlinx.coroutines.flow.Flow

interface ModeRepository {
    val settings: Flow<ModeSettings>

    suspend fun update(transform: (ModeSettings) -> ModeSettings)
}
