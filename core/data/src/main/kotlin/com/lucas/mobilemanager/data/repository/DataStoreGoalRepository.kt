package com.lucas.mobilemanager.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lucas.mobilemanager.domain.goals.*
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

class DataStoreGoalRepository @Inject constructor(private val store: DataStore<Preferences>) : GoalRepository {
    override val goals = store.data.map { decodeGoals(it[GOALS]) }
    override val temporaryAccess = store.data.map { decodeAccess(it[ACCESS]) }

    override suspend fun save(goal: PersonalGoal) {
        store.edit { prefs ->
            val all = decodeGoals(prefs[GOALS]).filterNot { it.id == goal.id } + goal
            prefs[GOALS] = JSONArray().apply { all.forEach { put(encode(it)) } }.toString()
        }
    }

    override suspend fun delete(id: String) {
        store.edit { prefs -> prefs[GOALS] = JSONArray().apply {
            decodeGoals(prefs[GOALS]).filterNot { it.id == id }.forEach { put(encode(it)) }
        }.toString() }
    }

    override suspend fun grant(access: TemporaryAccess) {
        store.edit { prefs ->
            val all = decodeAccess(prefs[ACCESS]).filter { it.packageName != access.packageName && it.until > access.startedAt } + access
            prefs[ACCESS] = encodeAccess(all)
        }
    }

    override suspend fun revoke(packageName: String) {
        store.edit { it[ACCESS] = encodeAccess(decodeAccess(it[ACCESS]).filterNot { a -> a.packageName == packageName }) }
    }

    override suspend fun claimReminder(key: String, date: LocalDate): Boolean {
        var claimed = false
        store.edit {
            val existing = runCatching { JSONObject(it[REMINDERS] ?: "{}") }.getOrDefault(JSONObject())
            val kept = JSONObject()
            existing.keys().forEach { k -> if (existing.optString(k) == date.toString()) kept.put(k, date.toString()) }
            if (!kept.has(key)) { kept.put(key, date.toString()); claimed = true }
            it[REMINDERS] = kept.toString()
        }
        return claimed
    }

    private fun decodeGoals(raw: String?): List<PersonalGoal> = objects(raw).mapNotNull { o -> runCatching {
        PersonalGoal(o.getString("id"), o.getString("title"), LocalDate.parse(o.getString("due")),
            strings(o.getJSONArray("packages")).toSet(), o.getInt("minutes"),
            objects(o.optJSONArray("slots")?.toString()).map { s -> PreparationSlot(
                strings(s.getJSONArray("days")).map(DayOfWeek::valueOf).toSet(), LocalTime.parse(s.getString("start")), s.getInt("minutes")) },
            o.optBoolean("reminders"), o.optBoolean("completed"))
    }.getOrNull() }

    private fun encode(g: PersonalGoal) = JSONObject().put("id", g.id).put("title", g.title)
        .put("due", g.dueDate.toString()).put("packages", JSONArray(g.packages.sorted())).put("minutes", g.dailyBudgetMinutes)
        .put("reminders", g.remindersEnabled).put("completed", g.completed)
        .put("slots", JSONArray().apply { g.slots.forEach { s -> put(JSONObject()
            .put("days", JSONArray(s.days.sorted().map { it.name })).put("start", s.start.toString()).put("minutes", s.minutes)) } })

    private fun decodeAccess(raw: String?) = objects(raw).mapNotNull { o -> runCatching {
        TemporaryAccess(o.getString("package"), Instant.parse(o.getString("start")), Instant.parse(o.getString("until")),
            o.getInt("boot"), o.getLong("elapsed"))
    }.getOrNull() }

    private fun encodeAccess(all: List<TemporaryAccess>) = JSONArray().apply { all.forEach { a ->
        put(JSONObject().put("package", a.packageName).put("start", a.startedAt.toString()).put("until", a.until.toString())
            .put("boot", a.bootCount).put("elapsed", a.elapsedUntilMs))
    } }.toString()

    private fun objects(raw: String?): List<JSONObject> = runCatching {
        val array = JSONArray(raw ?: "[]")
        (0 until array.length()).mapNotNull { array.optJSONObject(it) }
    }.getOrDefault(emptyList())
    private fun strings(array: JSONArray) = (0 until array.length()).map { array.getString(it) }

    private companion object {
        val GOALS = stringPreferencesKey("personal_goals_v1")
        val ACCESS = stringPreferencesKey("temporary_access_v1")
        val REMINDERS = stringPreferencesKey("goal_reminders_v1")
    }
}
