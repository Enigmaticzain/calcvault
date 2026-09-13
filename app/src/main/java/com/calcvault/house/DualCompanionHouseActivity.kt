package com.calcvault.house

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.calcvault.R
import com.calcvault.emotional.themes.ThemeApplicator
import com.calcvault.ui.common.GlassUi
import com.calcvault.utils.SessionManager

class DualCompanionHouseActivity : AppCompatActivity() {

    private lateinit var applicator: DualCompanionHouseApplicator
    private lateinit var statusText: TextView
    private lateinit var roleText: TextView
    private var controlledCharacter = CharacterIdentity.ZAIN

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_dual_companion_house)

        controlledCharacter = controlledCharacterFor(SessionManager.localUserId.ifBlank { "zain" })
        statusText = findViewById(R.id.petStatusText)
        roleText = findViewById(R.id.roleText)
        val houseContainer = findViewById<FrameLayout>(R.id.houseContainer)
        val sceneView = findViewById<io.github.sceneview.SceneView>(R.id.sceneView)
        applicator = DualCompanionHouseApplicator

        sceneView.post {
            applicator.activate(this, houseContainer, sceneView)
            applicator.performTalkingPetAction(CharacterIdentity.ZAIN, TalkingPetMode.IDLE)
            applicator.performTalkingPetAction(CharacterIdentity.SANU, TalkingPetMode.IDLE)
        }

        applyChrome()
        setupRooms()
        setupDualCommands()
        setupTalkingPetControls()
        setupMoodControls()
        updateRoleCopy()
    }

    override fun onDestroy() {
        super.onDestroy()
        applicator.deactivate()
        ThemeApplicator.detach(this)
    }

    private fun applyChrome() {
        val rootContainer = findViewById<FrameLayout>(R.id.houseContainer)
        GlassUi.applyThemeChrome(this, rootContainer)

        styleClayPanel(findViewById(R.id.roomNavigation), Color.argb(220, 255, 255, 255), 28)
        styleClayPanel(findViewById(R.id.commandsPanel), Color.argb(230, 255, 255, 255), 28)
        styleClayPanel(findViewById(R.id.tomPanel), Color.parseColor("#E3F2FD"), 22)
        styleClayPanel(findViewById(R.id.angelaPanel), Color.parseColor("#FCE4EC"), 22)

        val roomButtons = listOf(R.id.btn_living, R.id.btn_kitchen, R.id.btn_bedroom, R.id.btn_activity, R.id.btn_bathroom, R.id.btn_garden)
        roomButtons.forEach { styleClayButton(findViewById(it), Color.parseColor("#FFFDE7"), true) }

        val moodButtons = mapOf(
            R.id.mood_happy to "#FFF9C4",
            R.id.mood_romantic to "#F8BBD0",
            R.id.mood_playful to "#C8E6C9",
            R.id.mood_tired to "#E1BEE7",
            R.id.mood_sad to "#CFD8DC"
        )
        moodButtons.forEach { (id, color) -> styleClayButton(findViewById(id), Color.parseColor(color), true) }

        val careButtons = listOf(R.id.cmd_feed_tom, R.id.cmd_feed_angela, R.id.cmd_bathe, R.id.cmd_sleep, R.id.cmd_minigame)
        careButtons.forEach { styleClayButton(findViewById(it), Color.parseColor("#E0F2F1"), true) }

        val interactionButtons = listOf(R.id.cmd_cook, R.id.cmd_watch, R.id.cmd_play, R.id.cmd_relax)
        interactionButtons.forEach { styleClayButton(findViewById(it), Color.parseColor("#F3E5F5"), true) }

        val tomControls = listOf(R.id.btn_tom_idle, R.id.btn_tom_listen, R.id.btn_tom_talk)
        tomControls.forEach { styleClayButton(findViewById(it), Color.parseColor("#BBDEFB"), true) }

        val angelaControls = listOf(R.id.btn_angela_idle, R.id.btn_angela_listen, R.id.btn_angela_talk)
        angelaControls.forEach { styleClayButton(findViewById(it), Color.parseColor("#F8BBD0"), true) }

        val commandsPanel = findViewById<ScrollView>(R.id.commandsPanel)
        commandsPanel.setOnScrollChangeListener { _, _, scrollY, _, _ -> applyCommandScrollEffects(commandsPanel, scrollY) }
        commandsPanel.post { applyCommandScrollEffects(commandsPanel, commandsPanel.scrollY) }
    }

    private fun setupRooms() {
        findViewById<Button>(R.id.btn_living).setOnClickListener { applicator.changeRoom(HouseRoom.LIVING) }
        findViewById<Button>(R.id.btn_kitchen).setOnClickListener { applicator.changeRoom(HouseRoom.KITCHEN) }
        findViewById<Button>(R.id.btn_bedroom).setOnClickListener { applicator.changeRoom(HouseRoom.BEDROOM) }
        findViewById<Button>(R.id.btn_activity).setOnClickListener { applicator.changeRoom(HouseRoom.ACTIVITY) }
        findViewById<Button>(R.id.btn_bathroom).setOnClickListener { applicator.changeRoom(HouseRoom.BATHROOM) }
        findViewById<Button>(R.id.btn_garden).setOnClickListener { applicator.changeRoom(HouseRoom.GARDEN) }
    }

    private fun setupDualCommands() {
        findViewById<Button>(R.id.cmd_cook).setOnClickListener {
            executeDualCommand(parameter = "cook", zoneType = ZoneType.KITCHEN)
        }
        findViewById<Button>(R.id.cmd_watch).setOnClickListener {
            executeDualCommand(parameter = "watch", zoneType = ZoneType.TV)
        }
        findViewById<Button>(R.id.cmd_play).setOnClickListener {
            executeDualCommand(parameter = "play", zoneType = ZoneType.GAME_AREA)
        }
        findViewById<Button>(R.id.cmd_relax).setOnClickListener {
            executeDualCommand(parameter = "relax", zoneType = ZoneType.SOFA)
        }
        setupCareActions()
    }

    private fun setupCareActions() {
        findViewById<Button>(R.id.cmd_feed_tom).setOnClickListener { showFoodPicker(CharacterIdentity.ZAIN) }
        findViewById<Button>(R.id.cmd_feed_angela).setOnClickListener { showFoodPicker(CharacterIdentity.SANU) }
        findViewById<Button>(R.id.cmd_bathe).setOnClickListener {
            applicator.batheCharacter(CharacterIdentity.ZAIN)
            applicator.batheCharacter(CharacterIdentity.SANU)
            applicator.changeRoom(HouseRoom.BATHROOM)
        }
        findViewById<Button>(R.id.cmd_sleep).setOnClickListener {
            applicator.putToSleep(CharacterIdentity.ZAIN)
            applicator.putToSleep(CharacterIdentity.SANU)
        }
        findViewById<Button>(R.id.cmd_minigame).setOnClickListener { showMiniGamePicker() }
    }

    private fun showFoodPicker(character: CharacterIdentity) {
        val foods = applicator.getAvailableFoods()
        if (foods.isEmpty()) {
            android.widget.Toast.makeText(this, "Fridge is empty!", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        val names = foods.map { (food, qty) -> "${food.emoji} ${food.name} (x$qty)" }.toTypedArray()
        android.app.AlertDialog.Builder(this)
            .setTitle("Feed ${if (character == CharacterIdentity.ZAIN) "Tom" else "Angela"}")
            .setItems(names) { _, which ->
                val (food, _) = foods[which]
                val taken = applicator.takeFoodFromFridge(food.id)
                if (taken != null) {
                    val result = applicator.feedCharacter(character, taken)
                    result?.let {
                        android.widget.Toast.makeText(this, it.message, android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }.show()
    }

    private fun showMiniGamePicker() {
        val games = arrayOf("🫧 Bubble Pop", "🍎 Food Catcher", "🤸 Trampoline")
        android.app.AlertDialog.Builder(this)
            .setTitle("Choose Mini-Game")
            .setItems(games) { _, which ->
                val type = when (which) {
                    0 -> MiniGameType.BUBBLE_POP
                    1 -> MiniGameType.FOOD_CATCHER
                    2 -> MiniGameType.TRAMPOLINE
                    else -> MiniGameType.BUBBLE_POP
                }
                applicator.startMiniGame(type)
                applicator.changeRoom(HouseRoom.GARDEN)
            }.show()
    }

    private fun setupTalkingPetControls() {
        bindPetControl(R.id.btn_tom_idle, CharacterIdentity.ZAIN, TalkingPetMode.IDLE)
        bindPetControl(R.id.btn_tom_listen, CharacterIdentity.ZAIN, TalkingPetMode.LISTENING)
        bindPetControl(R.id.btn_tom_talk, CharacterIdentity.ZAIN, TalkingPetMode.TALKING)
        bindPetControl(R.id.btn_angela_idle, CharacterIdentity.SANU, TalkingPetMode.IDLE)
        bindPetControl(R.id.btn_angela_listen, CharacterIdentity.SANU, TalkingPetMode.LISTENING)
        bindPetControl(R.id.btn_angela_talk, CharacterIdentity.SANU, TalkingPetMode.TALKING)
    }

    private fun bindPetControl(buttonId: Int, character: CharacterIdentity, mode: TalkingPetMode) {
        val button = findViewById<Button>(buttonId)
        val isAllowed = character == controlledCharacter
        val tint = if (character == CharacterIdentity.ZAIN) Color.parseColor("#DDEEFF") else Color.parseColor("#FFE2F0")

        button.isEnabled = isAllowed
        button.alpha = if (isAllowed) 1f else 0.46f
        styleClayButton(button, tint, isAllowed)
        button.setOnClickListener {
            applicator.performTalkingPetAction(character, mode)
            val petName = if (character == CharacterIdentity.ZAIN) "Tom" else "Angela"
            statusText.text = "$petName is ${mode.name.lowercase()}."
        }
    }

    private fun setupMoodControls() {
        findViewById<Button>(R.id.mood_happy).setOnClickListener { setMoodForControlled(EmotionalState.HAPPY) }
        findViewById<Button>(R.id.mood_romantic).setOnClickListener { setMoodForControlled(EmotionalState.ROMANTIC) }
        findViewById<Button>(R.id.mood_playful).setOnClickListener { setMoodForControlled(EmotionalState.PLAYFUL) }
        findViewById<Button>(R.id.mood_tired).setOnClickListener { setMoodForControlled(EmotionalState.TIRED) }
        findViewById<Button>(R.id.mood_sad).setOnClickListener { setMoodForControlled(EmotionalState.SAD) }
    }

    private fun executeDualCommand(parameter: String, zoneType: ZoneType) {
        applicator.performDualAction(parameter, zoneType)
        statusText.text = "Tom and Angela are doing ${parameter.lowercase()} together."
    }

    private fun setMoodForControlled(mood: EmotionalState) {
        applicator.setCharacterMood(controlledCharacter, mood)
        statusText.text = "${controlledPetName()} mood set to ${mood.name.lowercase()}."
    }

    private fun updateRoleCopy() {
        val localUser = SessionManager.localUserId.ifBlank { "zain" }
        roleText.text = if (controlledCharacter == CharacterIdentity.SANU) {
            "You are $localUser — controlling Angela. Zain controls Tom."
        } else {
            "You are $localUser — controlling Tom. Sanu controls Angela."
        }
        statusText.text = "One shared home: Tom for Zain, Angela for Sanu."
        Toast.makeText(this, "Single shared house opened", Toast.LENGTH_SHORT).show()
    }

    private fun controlledCharacterFor(userId: String): CharacterIdentity {
        return if (userId.equals("sanu", ignoreCase = true)) CharacterIdentity.SANU else CharacterIdentity.ZAIN
    }

    private fun controlledPetName(): String {
        return if (controlledCharacter == CharacterIdentity.ZAIN) "Tom" else "Angela"
    }

    private fun styleClayPanel(view: View, color: Int, radiusDp: Int) {
        view.background = GradientDrawable().apply {
            cornerRadius = dpToPx(radiusDp).toFloat()
            setColor(color)
            setStroke(dpToPx(1), Color.argb(120, 255, 255, 255))
        }
        view.elevation = dpToPx(10).toFloat()
        if (view is LinearLayout) {
            view.clipToOutline = false
        }
    }

    private fun styleClayButton(button: Button, color: Int, enabled: Boolean) {
        button.isAllCaps = false
        button.minHeight = 0
        button.minWidth = 0
        button.background = GradientDrawable().apply {
            cornerRadius = dpToPx(18).toFloat()
            setColor(if (enabled) color else Color.parseColor("#E8E2EA"))
            setStroke(dpToPx(1), Color.argb(140, 255, 255, 255))
        }
        button.setTextColor(if (enabled) Color.parseColor("#2B2431") else Color.parseColor("#8B7F90"))
        button.elevation = dpToPx(if (enabled) 7 else 2).toFloat()
        button.setPadding(dpToPx(12), 0, dpToPx(12), 0)
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun applyCommandScrollEffects(scroll: ScrollView, scrollY: Int) {
        // Disabled aggressive scroll effects to keep screen stable
        /*
        val roomNavigation = findViewById<View>(R.id.roomNavigation)
        val sceneView = findViewById<View>(R.id.sceneView)
        sceneView.translationY = -scrollY * 0.16f
        sceneView.scaleX = (1f + scrollY / 2800f).coerceAtMost(1.06f)
        sceneView.scaleY = sceneView.scaleX
        roomNavigation.translationY = -scrollY * 0.2f
        roomNavigation.alpha = (1f - scrollY / 360f).coerceIn(0.42f, 1f)
        scroll.translationZ = dpToPx((10 + scrollY / 18).coerceAtMost(24)).toFloat()

        val content = scroll.getChildAt(0) as? LinearLayout ?: return
        val viewportCenter = scrollY + scroll.height * 0.46f
        for (index in 0 until content.childCount) {
            val child = content.getChildAt(index)
            val childCenter = child.top + child.height / 2f
            val distance = kotlin.math.abs(childCenter - viewportCenter)
            val normalized = (distance / scroll.height.coerceAtLeast(1)).coerceIn(0f, 1f)
            val direction = if (childCenter < viewportCenter) -1f else 1f
            child.scaleX = 1f - normalized * 0.08f
            child.scaleY = 1f - normalized * 0.08f
            child.translationY = direction * normalized * dpToPx(18)
            child.translationX = direction * normalized * dpToPx(10)
            child.alpha = 1f - normalized * 0.22f
        }
        */
    }
}
