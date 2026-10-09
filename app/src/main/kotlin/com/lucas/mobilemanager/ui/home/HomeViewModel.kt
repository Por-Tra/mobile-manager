package com.lucas.mobilemanager.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lucas.mobilemanager.domain.model.ManualActivation
import com.lucas.mobilemanager.domain.model.Profile
import com.lucas.mobilemanager.domain.model.StrictSession
import com.lucas.mobilemanager.domain.onboarding.OnboardingState
import com.lucas.mobilemanager.domain.profiles.DeleteProfile
import com.lucas.mobilemanager.domain.profiles.ManualActivationPolicy
import com.lucas.mobilemanager.domain.profiles.ManualDuration
import com.lucas.mobilemanager.domain.profiles.ProfileOrdering
import com.lucas.mobilemanager.domain.repository.ManualActivationRepository
import com.lucas.mobilemanager.domain.repository.ProfileRepository
import com.lucas.mobilemanager.domain.repository.StrictSessionRepository
import com.lucas.mobilemanager.domain.time.ScheduleEvaluator
import com.lucas.mobilemanager.domain.time.TimeProvider
import com.lucas.mobilemanager.permissions.PermissionChecker
import com.lucas.mobilemanager.session.StrictSessionService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

data class ProfileItem(
    val profile: Profile,
    /** Le profil s'applique en ce moment (activé à la main, ou activé et dans une de ses plages). */
    val activeNow: Boolean,
    /** Activé à la main en ce moment. */
    val manual: Boolean = false,
    /** Fin de l'activation manuelle, `null` si elle dure jusqu'à désactivation. */
    val manualUntil: Instant? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val permissionChecker: PermissionChecker,
    private val profileRepository: ProfileRepository,
    private val strictSessionRepository: StrictSessionRepository,
    private val manualActivationRepository: ManualActivationRepository,
    private val deleteProfile: DeleteProfile,
    private val timeProvider: TimeProvider,
) : ViewModel() {

    private val _permissions = MutableStateFlow(OnboardingState.from(permissionChecker.currentStatuses()))
    val permissions: StateFlow<OnboardingState> = _permissions.asStateFlow()

    /** Incrémenté à chaque retour sur l'écran pour recalculer « actif maintenant ». */
    private val refreshTick = MutableStateFlow(0)

    /** Profils dans l'ordre de priorité (le premier l'emporte en cas de chevauchement). */
    val profiles: StateFlow<List<ProfileItem>> = combine(
        profileRepository.observeProfiles(),
        manualActivationRepository.activation,
        refreshTick,
    ) { profiles, manual, _ ->
        val now = timeProvider.localNow()
        val activeManual = manual?.takeIf { it.isActive(now.toInstant()) }
        ProfileOrdering.sorted(profiles).map { profile ->
            val isManual = activeManual?.profileId == profile.id
            ProfileItem(
                profile = profile,
                activeNow = isManual || profile.enabled && profile.autoActivation &&
                    ScheduleEvaluator.isAnyActive(profile.schedules, now),
                manual = isManual,
                manualUntil = activeManual?.until?.takeIf { isManual },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Session de concentration en cours, `null` sinon. */
    val session: StateFlow<StrictSession?> = combine(
        strictSessionRepository.observeCurrent(),
        refreshTick,
    ) { session, _ -> session?.takeIf { it.isRunning(timeProvider.now()) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun refresh() {
        _permissions.value = OnboardingState.from(permissionChecker.currentStatuses())
        refreshTick.value++
        viewModelScope.launch {
            // Filet de sécurité : si le service de session a été arrêté, on le relance (app au premier plan).
            if (strictSessionRunning()) StrictSessionService.start(context)
            // Une activation manuelle expirée est ignorée par le moteur ; on la nettoie au passage.
            val manual = manualActivationRepository.activation.first()
            if (manual != null && !manual.isActive(timeProvider.now())) manualActivationRepository.clear()
        }
    }

    private suspend fun strictSessionRunning(): Boolean =
        strictSessionRepository.current()?.isRunning(timeProvider.now()) == true

    /** Les règles ne se modifient pas pendant une session de concentration. */
    private fun editable(action: suspend () -> Unit) {
        if (session.value != null) return
        viewModelScope.launch { if (!strictSessionRunning()) action() }
    }

    fun setEnabled(profileId: Long, enabled: Boolean) = editable {
        profileRepository.setEnabled(profileId, enabled)
    }

    /** Remplace l'éventuelle activation manuelle en cours ; un profil désactivé est réactivé. */
    fun activateManually(profile: Profile, duration: ManualDuration) = editable {
        if (!profile.enabled) profileRepository.setEnabled(profile.id, true)
        manualActivationRepository.activate(profile.id, ManualActivationPolicy.until(duration, timeProvider.localNow()))
    }

    /** Désactivable à tout moment (l'engagement ferme, c'est la session de concentration). */
    fun deactivateManual() = editable { manualActivationRepository.clear() }

    fun move(profileId: Long, offset: Int) = editable {
        val ordered = profiles.value.map { it.profile }
        val from = ordered.indexOfFirst { it.id == profileId }
        val to = from + offset
        if (from < 0 || to !in ordered.indices) return@editable
        ProfileOrdering.reorder(ordered, from, to).forEach { profileRepository.upsert(it) }
    }

    fun delete(profileId: Long) = editable { deleteProfile(profileId) }

    private fun ManualActivation.isActive(now: Instant) = until == null || now.isBefore(until)
}
