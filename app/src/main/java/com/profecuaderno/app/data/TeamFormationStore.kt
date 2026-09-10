package com.profecuaderno.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class TeamGroup(
    val name: String,
    val studentIds: List<Long>
)

data class TeamFormation(
    val id: String = UUID.randomUUID().toString(),
    val periodId: Long,
    val activityType: String,
    val activityName: String,
    val createdAt: Long = System.currentTimeMillis(),
    val teams: List<TeamGroup>
)

object TeamFormationStore {
    private const val PREFS = "team_formations_v1"
    private const val KEY = "formations"

    fun load(context: Context, periodId: Long): List<TeamFormation> =
        loadAll(context).filter { it.periodId == periodId }.sortedByDescending { it.createdAt }

    fun save(context: Context, formation: TeamFormation) {
        val items = loadAll(context).filterNot { it.id == formation.id }.toMutableList()
        items += formation
        persist(context, items)
    }

    fun delete(context: Context, id: String) {
        persist(context, loadAll(context).filterNot { it.id == id })
    }

    private fun loadAll(context: Context): List<TeamFormation> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return emptyList()
        return runCatching {
            val root = JSONArray(raw)
            buildList {
                for (i in 0 until root.length()) {
                    val obj = root.getJSONObject(i)
                    val teamsJson = obj.getJSONArray("teams")
                    val teams = buildList {
                        for (j in 0 until teamsJson.length()) {
                            val teamObj = teamsJson.getJSONObject(j)
                            val idsJson = teamObj.getJSONArray("studentIds")
                            val ids = buildList { for (k in 0 until idsJson.length()) add(idsJson.getLong(k)) }
                            add(TeamGroup(teamObj.getString("name"), ids))
                        }
                    }
                    add(
                        TeamFormation(
                            id = obj.getString("id"),
                            periodId = obj.getLong("periodId"),
                            activityType = obj.getString("activityType"),
                            activityName = obj.optString("activityName"),
                            createdAt = obj.getLong("createdAt"),
                            teams = teams
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun persist(context: Context, formations: List<TeamFormation>) {
        val root = JSONArray()
        formations.forEach { formation ->
            val obj = JSONObject()
                .put("id", formation.id)
                .put("periodId", formation.periodId)
                .put("activityType", formation.activityType)
                .put("activityName", formation.activityName)
                .put("createdAt", formation.createdAt)
            val teams = JSONArray()
            formation.teams.forEach { team ->
                teams.put(JSONObject().put("name", team.name).put("studentIds", JSONArray(team.studentIds)))
            }
            obj.put("teams", teams)
            root.put(obj)
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, root.toString()).apply()
    }
}
