package com.calcvault.ui.settings

import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.calcvault.emotional.themes.*

class CoupleThemeSettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            setBackgroundColor(0xFF0F0F14.toInt())
        }
        setContentView(root)

        val title = TextView(this).apply {
            text = "Couple Theme Settings"
            textSize = 24f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, 0, 0, 24)
        }
        root.addView(title)

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
        }
        root.addView(scroll)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        scroll.addView(content)

        val config = CoupleThemeApplicator.getConfig() ?: CoupleThemeConfig()

        // Enable Characters
        addToggleSetting(
            content,
            "Show Characters",
            config.enableCharacters
        ) { enabled ->
            CoupleThemeApplicator.setConfig(config.copy(enableCharacters = enabled))
        }

        // Enable Interactions
        addToggleSetting(
            content,
            "Enable Interactions",
            config.enableInteractions
        ) { enabled ->
            CoupleThemeApplicator.setConfig(config.copy(enableInteractions = enabled))
        }

        // Animation Intensity
        addSliderSetting(
            content,
            "Animation Intensity",
            config.animationIntensity
        ) { intensity ->
            CoupleThemeApplicator.setConfig(config.copy(animationIntensity = intensity))
        }

        // Auto Scene Switch
        addToggleSetting(
            content,
            "Auto Scene Switch",
            config.autoSceneSwitch
        ) { enabled ->
            CoupleThemeApplicator.setConfig(config.copy(autoSceneSwitch = enabled))
        }

        // Scene Selection
        addSceneSelector(content, config.lockedScene)

        // Interaction Preview
        addInteractionSelector(content)

        // Back Button
        val btnBack = Button(this).apply {
            text = "BACK"
            setBackgroundColor(0xFF3700B3.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            layoutParams = LinearLayout.LayoutParams(-1, 120).also {
                it.topMargin = 24
            }
            setOnClickListener { finish() }
        }
        root.addView(btnBack)
    }

    private fun addToggleSetting(
        parent: LinearLayout,
        label: String,
        initialValue: Boolean,
        onChange: (Boolean) -> Unit
    ) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(-1, -2).also { it.bottomMargin = 16 }
        }

        val text = TextView(this).apply {
            text = label
            textSize = 16f
            setTextColor(0xFFFFFFFF.toInt())
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        container.addView(text)

        val toggle = Switch(this).apply {
            isChecked = initialValue
            setOnCheckedChangeListener { _, isChecked -> onChange(isChecked) }
        }
        container.addView(toggle)

        parent.addView(container)
    }

    private fun addSliderSetting(
        parent: LinearLayout,
        label: String,
        initialValue: Float,
        onChange: (Float) -> Unit
    ) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(-1, -2).also { it.bottomMargin = 16 }
        }

        val text = TextView(this).apply {
            text = "$label: ${(initialValue * 100).toInt()}%"
            textSize = 16f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, 0, 0, 8)
        }
        container.addView(text)

        val slider = SeekBar(this).apply {
            max = 100
            progress = (initialValue * 100).toInt()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    text.text = "$label: $progress%"
                    onChange(progress / 100f)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        container.addView(slider)

        parent.addView(container)
    }

    private fun addSceneSelector(parent: LinearLayout, lockedScene: SceneType?) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(-1, -2).also { it.bottomMargin = 16 }
        }

        val text = TextView(this).apply {
            text = "Lock Scene"
            textSize = 16f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, 0, 0, 8)
        }
        container.addView(text)

        val scenes = listOf("None") + SceneType.values().map { it.name }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, scenes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

        val spinner = Spinner(this).apply {
            this.adapter = adapter
            setSelection(lockedScene?.let { scenes.indexOf(it.name) } ?: 0)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    val selected = if (position == 0) null else SceneType.valueOf(scenes[position])
                    val config = CoupleThemeApplicator.getConfig() ?: CoupleThemeConfig()
                    CoupleThemeApplicator.setConfig(config.copy(lockedScene = selected))
                    if (selected != null) {
                        CoupleThemeApplicator.setScene(selected)
                    }
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }
        container.addView(spinner)

        parent.addView(container)
    }

    private fun addInteractionSelector(parent: LinearLayout) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(-1, -2).also { it.bottomMargin = 16 }
        }

        val text = TextView(this).apply {
            text = "Test Interaction"
            textSize = 16f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, 0, 0, 8)
        }
        container.addView(text)

        val interactions = CharacterInteraction.values().map { it.name }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, interactions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

        val spinner = Spinner(this).apply {
            this.adapter = adapter
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    CoupleThemeApplicator.setInteraction(CharacterInteraction.values()[position])
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }
        container.addView(spinner)

        parent.addView(container)
    }
}
