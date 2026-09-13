package com.calcvault.ui.mood

import com.calcvault.auth.UnlockManager
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class MoodManager(private val unlockManager: UnlockManager, private val localUserId: String) {

    private val moods = mutableListOf<UserMood>()
    private var activeMood: UserMood? = null

    companion object {
        fun getDefaultMoods(userId: String): List<UserMood> {
            return listOf(
                UserMood(UUID.randomUUID().toString(), "Happy", "😊", createdBy = userId),
                UserMood(UUID.randomUUID().toString(), "Sad", "😔", createdBy = userId),
                UserMood(UUID.randomUUID().toString(), "Tired", "😴", createdBy = userId),
                UserMood(UUID.randomUUID().toString(), "Busy", "📴", createdBy = userId),
                UserMood(UUID.randomUUID().toString(), "Romantic", "❤️", createdBy = userId),
                UserMood(UUID.randomUUID().toString(), "Stressed", "😣", createdBy = userId)
            )
        }
    }

    init {
        loadMoods()
    }

    fun getMoods(): List<UserMood> {
        return moods.toList()
    }

    fun getActiveMood(): UserMood? {
        return activeMood
    }

    fun setActiveMood(moodId: String?): UserMood? {
        val previouslyActive = activeMood

        if (moodId == null) {
            activeMood = null
        } else {
            activeMood = moods.find { it.id == moodId }
        }

        // Update isActive flags
        moods.forEachIndexed { index, userMood ->
            val shouldBeActive = userMood.id == activeMood?.id
            if (userMood.isActive != shouldBeActive) {
                moods[index] = userMood.copy(isActive = shouldBeActive)
            }
        }

        if (previouslyActive != activeMood) {
            saveMoods()
        }
        return activeMood
    }

    fun addMood(name: String, emoji: String) {
        val newMood = UserMood(
            id = UUID.randomUUID().toString(),
            name = name,
            emoji = emoji,
            createdBy = localUserId
        )
        moods.add(newMood)
        saveMoods()
    }

    private fun loadMoods() {
        // TODO: Load moods from unlockManager when methods are available
        /* val jsonString = unlockManager.getUserMoodsJson()
        if (jsonString.isNotBlank()) {
            try {
                val jsonArray = JSONArray(jsonString)
                moods.clear()
                for (i in 0 until jsonArray.length()) {
                    val jsonObject = jsonArray.getJSONObject(i)
                    val mood = UserMood(
                        id = jsonObject.getString("id"),
                        name = jsonObject.getString("name"),
                        emoji = jsonObject.getString("emoji"),
                        intensity = jsonObject.optInt("intensity", 3),
                        isActive = jsonObject.optBoolean("isActive", false),
                        createdBy = jsonObject.getString("createdBy"),
                        visibility = MoodVisibility.valueOf(jsonObject.optString("visibility", "PUBLIC"))
                    )
                    moods.add(mood)
                }
            } catch (e: Exception) {
                moods.clear()
                moods.addAll(getDefaultMoods(localUserId))
            }
        } else { */
        moods.clear()
        moods.addAll(getDefaultMoods(localUserId))
        // }
        activeMood = moods.firstOrNull { it.isActive }
        if (moods.isEmpty()) { // Ensure defaults are loaded if parsing fails and list is empty
            moods.addAll(getDefaultMoods(localUserId))
        }
        saveMoods() // Ensure data is persisted on first load
    }

    private fun saveMoods() {
        // TODO: Save moods to unlockManager when methods are available
        val jsonArray = JSONArray()
        moods.forEach { mood ->
            val jsonObject = JSONObject().apply {
                put("id", mood.id)
                put("name", mood.name)
                put("emoji", mood.emoji)
                put("intensity", mood.intensity)
                put("isActive", mood.id == activeMood?.id)
                put("createdBy", mood.createdBy)
                put("visibility", mood.visibility.name)
            }
            jsonArray.put(jsonObject)
        }
        // unlockManager.updateUserMoodsJson(jsonArray.toString())
    }
}
