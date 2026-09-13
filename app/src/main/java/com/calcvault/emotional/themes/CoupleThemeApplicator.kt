package com.calcvault.emotional.themes

import android.app.Activity
import android.graphics.Color
import android.view.ViewGroup
import android.widget.FrameLayout

object CoupleThemeApplicator {
    private var coupleTheme: DynamicCoupleTheme? = null
    private var sceneView: SceneBackgroundView? = null
    private var characterView: CoupleCharacterView? = null
    private var isActive = false

    fun activate(activity: Activity, rootContainer: ViewGroup) {
        if (isActive) return

        coupleTheme = DynamicCoupleTheme(activity)

        sceneView = SceneBackgroundView(activity).apply {
            layoutParams = FrameLayout.LayoutParams(-1, -1)
            setScene(coupleTheme!!.getScene())
            startAnimation()
        }

        characterView = CoupleCharacterView(activity).apply {
            layoutParams = FrameLayout.LayoutParams(-1, -1)
            setInteraction(coupleTheme!!.getInteraction())
            startAnimation()
        }

        if (rootContainer is FrameLayout) {
            rootContainer.addView(sceneView, 0)
            rootContainer.addView(characterView, 1)
        }

        isActive = true
    }

    fun deactivate() {
        sceneView?.stopAnimation()
        characterView?.stopAnimation()
        coupleTheme?.cleanup()
        sceneView = null
        characterView = null
        coupleTheme = null
        isActive = false
    }

    fun onMessageReceived(from: String, content: String) {
        coupleTheme?.onMessageReceived(from, content)
        characterView?.setInteraction(coupleTheme?.getInteraction() ?: return)
    }

    fun onMoodUpdate(mood: String) {
        coupleTheme?.onMoodUpdate(mood)
        sceneView?.setScene(coupleTheme?.getScene() ?: return)
    }

    fun getMessageBubbleStyle(from: String): MessageBubbleStyle {
        return when (from.lowercase()) {
            "zain" -> MessageBubbleStyle(
                backgroundColor = Color.parseColor("#3700b3"),
                textColor = Color.WHITE,
                characterEmoji = "👨",
                characterName = "Zain"
            )
            "sanu" -> MessageBubbleStyle(
                backgroundColor = Color.parseColor("#c2185b"),
                textColor = Color.WHITE,
                characterEmoji = "👩",
                characterName = "Sanu"
            )
            else -> MessageBubbleStyle(
                backgroundColor = Color.parseColor("#424242"),
                textColor = Color.WHITE,
                characterEmoji = "💬",
                characterName = from
            )
        }
    }

    fun getConfig() = coupleTheme?.getConfig()

    fun setConfig(config: CoupleThemeConfig) {
        coupleTheme?.setConfig(config)
    }

    fun setScene(scene: SceneType) {
        coupleTheme?.setScene(scene)
        sceneView?.setScene(scene)
    }

    fun setInteraction(interaction: CharacterInteraction) {
        coupleTheme?.setInteraction(interaction)
        characterView?.setInteraction(interaction)
    }

    fun isActive() = isActive
}

data class MessageBubbleStyle(
    val backgroundColor: Int,
    val textColor: Int,
    val characterEmoji: String,
    val characterName: String
)
