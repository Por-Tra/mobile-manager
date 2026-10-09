package com.lucas.mobilemanager.ui.profiles

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.lucas.mobilemanager.apps.InstalledApp
import com.lucas.mobilemanager.apps.InstalledApps
import com.lucas.mobilemanager.domain.model.Profile
import com.lucas.mobilemanager.domain.model.Schedule
import com.lucas.mobilemanager.domain.profiles.ProfileOrdering
import com.lucas.mobilemanager.domain.profiles.ProfileValidator
import com.lucas.mobilemanager.domain.repository.ProfileRepository
import com.lucas.mobilemanager.domain.repository.StrictSessionRepository
import com.lucas.mobilemanager.domain.rules.Rule
import com.lucas.mobilemanager.domain.rules.RuleAction
import com.lucas.mobilemanager.domain.rules.Target
import com.lucas.mobilemanager.domain.time.TimeProvider
import com.lucas.mobilemanager.navigation.ProfileEditRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime
import javax.inject.Inject

enum class RuleChoice { BLOCK, LIMIT, ALLOW }

data class AppRuleDraft(
    val packageName: String,
    val label: String,
    val installed: Boolean,
    val choice: RuleChoice,
    val limitMinutes: Int = DEFAULT_LIMIT_MINUTES,
) {
    companion object {
        const val DEFAULT_LIMIT_MINUTES = 30
    }
}

data class ProfileDraft(
    val name: String = "",
    val autoActivation: Boolean = true,
    val schedules: List<Schedule> = listOf(Schedule(Schedule.WEEKDAYS, LocalTime.of(9, 0), LocalTime.of(17, 0))),
    /** « Bloquer toutes les apps » : les règles par app deviennent des exceptions. */
    val blockAll: Boolean = false,
    val appRules: List<AppRuleDraft> = emptyList(),
)

data class ProfileEditState(
    val loading: Boolean = true,
    val isDefaultRules: Boolean = false,
    val isNew: Boolean = true,
    val draft: ProfileDraft = ProfileDraft(),
    val errors: Set<ProfileValidator.Error> = emptySet(),
    /** Session de concentration en cours : lecture seule. */
    val locked: Boolean = false,
    val pickerOpen: Boolean = false,
    val installedApps: List<InstalledApp> = emptyList(),
    val query: String = "",
    val saved: Boolean = false,
)

/** Création / édition d'un profil, ou des règles par défaut (même éditeur, sans nom ni plages). */
@HiltViewModel
class ProfileEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val profileRepository: ProfileRepository,
    private val strictSessionRepository: StrictSessionRepository,
    private val installedApps: InstalledApps,
    private val timeProvider: TimeProvider,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<ProfileEditRoute>()
    private var original: Profile? = null

    /** Règles que l'éditeur n'affiche pas encore (zones, phase 7) : conservées telles quelles. */
    private var preservedRules: List<Rule> = emptyList()

    private val _state = MutableStateFlow(ProfileEditState(isDefaultRules = route.defaultRules))
    val state: StateFlow<ProfileEditState> = _state.asStateFlow()

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val locked = strictSessionRepository.current()?.isRunning(timeProvider.now()) == true
        val draft = when {
            route.defaultRules -> draftFrom(name = "", auto = false, schedules = emptyList(), rules = profileRepository.observeGlobalRules().first())
            route.profileId != 0L -> {
                val profile = profileRepository.observeProfiles().first().firstOrNull { it.id == route.profileId }
                original = profile
                profile?.let { draftFrom(it.name, it.autoActivation, it.schedules, it.rules) } ?: ProfileDraft()
            }
            else -> ProfileDraft()
        }
        _state.update {
            it.copy(loading = false, isNew = route.profileId == 0L && !route.defaultRules, draft = draft, locked = locked)
        }
    }

    private suspend fun draftFrom(name: String, auto: Boolean, schedules: List<Schedule>, rules: List<Rule>): ProfileDraft {
        val appRules = mutableListOf<AppRuleDraft>()
        val preserved = mutableListOf<Rule>()
        var blockAll = false
        for (rule in rules) {
            val target = rule.target
            when {
                target is Target.AllApps && rule.action == RuleAction.Block -> blockAll = true
                target is Target.App -> appRules += appRuleDraft(target.packageName, rule.action)
                else -> preserved += rule
            }
        }
        preservedRules = preserved
        return ProfileDraft(name, auto, schedules, blockAll, appRules.sortedBy { it.label.lowercase() })
    }

    private suspend fun appRuleDraft(packageName: String, action: RuleAction): AppRuleDraft {
        val label = installedApps.label(packageName)
        return AppRuleDraft(
            packageName = packageName,
            label = label ?: packageName,
            installed = label != null,
            choice = when (action) {
                RuleAction.Block -> RuleChoice.BLOCK
                is RuleAction.Limit -> RuleChoice.LIMIT
                RuleAction.Allow -> RuleChoice.ALLOW
            },
            limitMinutes = (action as? RuleAction.Limit)?.dailyLimit?.toMinutes()?.toInt()
                ?: AppRuleDraft.DEFAULT_LIMIT_MINUTES,
        )
    }

    // --- Champs simples ---

    fun setName(name: String) = editDraft { it.copy(name = name) }

    fun setAutoActivation(auto: Boolean) = editDraft { it.copy(autoActivation = auto) }

    fun setBlockAll(blockAll: Boolean) = editDraft { it.copy(blockAll = blockAll) }

    // --- Plages ---

    fun addSchedule() = editDraft {
        it.copy(schedules = it.schedules + Schedule(Schedule.EVERY_DAY, LocalTime.of(9, 0), LocalTime.of(17, 0)))
    }

    fun removeSchedule(index: Int) = editDraft { it.copy(schedules = it.schedules.filterIndexed { i, _ -> i != index }) }

    fun toggleDay(index: Int, day: DayOfWeek) = updateSchedule(index) {
        it.copy(days = if (day in it.days) it.days - day else it.days + day)
    }

    fun setDays(index: Int, days: Set<DayOfWeek>) = updateSchedule(index) { it.copy(days = days) }

    fun setStart(index: Int, time: LocalTime) = updateSchedule(index) { it.copy(start = time) }

    fun setEnd(index: Int, time: LocalTime) = updateSchedule(index) { it.copy(end = time) }

    private fun updateSchedule(index: Int, change: (Schedule) -> Schedule) = editDraft { draft ->
        draft.copy(schedules = draft.schedules.mapIndexed { i, s -> if (i == index) change(s) else s })
    }

    // --- Règles par app ---

    fun openPicker() {
        _state.update { it.copy(pickerOpen = true, query = "") }
        if (state.value.installedApps.isEmpty()) {
            viewModelScope.launch {
                val apps = installedApps.blockable()
                _state.update { it.copy(installedApps = apps) }
            }
        }
    }

    fun closePicker() = _state.update { it.copy(pickerOpen = false) }

    fun setQuery(query: String) = _state.update { it.copy(query = query) }

    /** Ajoute ou retire une app depuis le sélecteur. Nouvelle app : bloquée, ou autorisée si tout est bloqué. */
    fun togglePickerApp(app: InstalledApp) = editDraft { draft ->
        if (draft.appRules.any { it.packageName == app.packageName }) {
            draft.copy(appRules = draft.appRules.filterNot { it.packageName == app.packageName })
        } else {
            val choice = if (draft.blockAll) RuleChoice.ALLOW else RuleChoice.BLOCK
            draft.copy(appRules = draft.appRules + AppRuleDraft(app.packageName, app.label, installed = true, choice))
        }
    }

    fun setChoice(packageName: String, choice: RuleChoice) = updateAppRule(packageName) { it.copy(choice = choice) }

    fun setLimit(packageName: String, minutes: Int) = updateAppRule(packageName) { it.copy(limitMinutes = minutes) }

    fun removeApp(packageName: String) = editDraft { d -> d.copy(appRules = d.appRules.filterNot { it.packageName == packageName }) }

    private fun updateAppRule(packageName: String, change: (AppRuleDraft) -> AppRuleDraft) = editDraft { draft ->
        draft.copy(appRules = draft.appRules.map { if (it.packageName == packageName) change(it) else it })
    }

    private fun editDraft(change: (ProfileDraft) -> ProfileDraft) {
        if (state.value.locked) return
        _state.update { it.copy(draft = change(it.draft), errors = emptySet()) }
    }

    // --- Enregistrement ---

    fun save() {
        val current = state.value
        if (current.locked || current.loading) return
        viewModelScope.launch {
            val rules = buildRules(current.draft)
            if (current.isDefaultRules) {
                val errors = ProfileValidator.validateRules(rules)
                if (errors.isNotEmpty()) return@launch _state.update { it.copy(errors = errors) }
                profileRepository.replaceGlobalRules(rules)
            } else {
                val others = profileRepository.observeProfiles().first().filter { it.id != route.profileId }
                val profile = Profile(
                    id = original?.id ?: 0,
                    name = current.draft.name.trim(),
                    priority = original?.priority ?: ProfileOrdering.nextPriority(others),
                    schedules = current.draft.schedules,
                    rules = rules,
                    autoActivation = current.draft.autoActivation,
                    enabled = original?.enabled ?: true,
                )
                val errors = ProfileValidator.validate(profile, others.map { it.name })
                if (errors.isNotEmpty()) return@launch _state.update { it.copy(errors = errors) }
                profileRepository.upsert(profile)
            }
            _state.update { it.copy(saved = true) }
        }
    }

    private fun buildRules(draft: ProfileDraft): List<Rule> = buildList {
        if (draft.blockAll && !state.value.isDefaultRules) add(Rule(Target.AllApps, RuleAction.Block))
        draft.appRules.forEach { app ->
            val action = when (app.choice) {
                RuleChoice.BLOCK -> RuleAction.Block
                RuleChoice.LIMIT -> RuleAction.Limit(Duration.ofMinutes(app.limitMinutes.toLong()))
                RuleChoice.ALLOW -> RuleAction.Allow
            }
            add(Rule(Target.App(app.packageName), action))
        }
        addAll(preservedRules)
    }
}
