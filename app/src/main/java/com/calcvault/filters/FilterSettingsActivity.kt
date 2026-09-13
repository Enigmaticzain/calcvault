package com.calcvault.ui.filters

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.calcvault.R
import com.calcvault.emotional.themes.ThemeApplicator
import com.calcvault.storage.provider.StorageManager
import com.calcvault.ui.common.GlassUi
import org.json.JSONObject

/**
 * FilterSettingsActivity - Configuration UI for video call filters
 *
 * Features:
 * - Enable/disable emoji effects
 * - Enable/disable overlay filters
 * - Default intensity setting
 * - Auto-quality adaptation toggle
 * - Effect preview
 */
class FilterSettingsActivity : AppCompatActivity() {

    private lateinit var switchEmojiEffects: Switch
    private lateinit var switchOverlayFilters: Switch
    private lateinit var switchAutoQuality: Switch
    private lateinit var sliderDefaultIntensity: SeekBar
    private lateinit var tvIntensityValue: TextView
    private lateinit var spinnerDefaultFilter: Spinner
    private lateinit var btnSave: Button
    private lateinit var btnCancel: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_filter_settings)

        initializeViews()
        loadSettings()
        setupListeners()
        applyVisualChrome()
    }

    override fun onResume() {
        super.onResume()
        applyVisualChrome()
    }

    override fun onDestroy() {
        super.onDestroy()
        ThemeApplicator.detach(this)
    }

    private fun initializeViews() {
        switchEmojiEffects = findViewById(R.id.switchEmojiEffects)
        switchOverlayFilters = findViewById(R.id.switchOverlayFilters)
        switchAutoQuality = findViewById(R.id.switchAutoQuality)
        sliderDefaultIntensity = findViewById(R.id.sliderDefaultIntensity)
        tvIntensityValue = findViewById(R.id.tvIntensityValue)
        spinnerDefaultFilter = findViewById(R.id.spinnerDefaultFilter)
        btnSave = findViewById(R.id.btnSave)
        btnCancel = findViewById(R.id.btnCancel)

        setupFilterSpinner()
    }

    private fun setupFilterSpinner() {
        val filterOptions = listOf(
            "None",
            "Warm Tone",
            "Cool Tone",
            "Dark Tone",
            "Sparkle",
            "Glow Aura",
            "Soft Blur",
            "Floating Particles"
        )
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, filterOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerDefaultFilter.adapter = adapter
    }

    private fun loadSettings() {
        val config = readJson("filter_config")
        switchEmojiEffects.isChecked = config.optBoolean("emoji_enabled", true)
        switchOverlayFilters.isChecked = config.optBoolean("overlay_enabled", true)
        switchAutoQuality.isChecked = config.optBoolean("auto_quality", true)

        val intensity = config.optInt("default_intensity", 70)
        sliderDefaultIntensity.progress = intensity
        tvIntensityValue.text = "$intensity%"

        val defaultFilter = config.optString("default_filter", "NONE")
        val filterIndex = when (defaultFilter) {
            "WARM_TONE" -> 1
            "COOL_TONE" -> 2
            "DARK_TONE" -> 3
            "SPARKLE" -> 4
            "GLOW_AURA" -> 5
            "SOFT_BLUR" -> 6
            "FLOATING_PARTICLES" -> 7
            else -> 0
        }
        spinnerDefaultFilter.setSelection(filterIndex)
    }

    private fun setupListeners() {
        sliderDefaultIntensity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                tvIntensityValue.text = "$progress%"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })

        btnSave.setOnClickListener { saveSettings() }
        btnCancel.setOnClickListener { finish() }
    }

    private fun saveSettings() {
        val config = JSONObject().apply {
            put("emoji_enabled", switchEmojiEffects.isChecked)
            put("overlay_enabled", switchOverlayFilters.isChecked)
            put("auto_quality", switchAutoQuality.isChecked)
            put("default_intensity", sliderDefaultIntensity.progress)
            put("default_filter", getSelectedFilterName())
        }
        saveJson("filter_config", config)
        finish()
    }

    private fun getSelectedFilterName(): String {
        return when (spinnerDefaultFilter.selectedItemPosition) {
            1 -> "WARM_TONE"
            2 -> "COOL_TONE"
            3 -> "DARK_TONE"
            4 -> "SPARKLE"
            5 -> "GLOW_AURA"
            6 -> "SOFT_BLUR"
            7 -> "FLOATING_PARTICLES"
            else -> "NONE"
        }
    }

    private fun readJson(key: String): JSONObject {
        val raw = StorageManager.read(key) ?: return JSONObject()
        return runCatching { JSONObject(String(raw, Charsets.UTF_8)) }.getOrDefault(JSONObject())
    }

    private fun saveJson(key: String, payload: JSONObject) {
        StorageManager.write(key, payload.toString().toByteArray(Charsets.UTF_8))
    }

    private fun applyVisualChrome() {
        GlassUi.applyThemeChrome(this)
        val content = findViewById<android.view.ViewGroup>(android.R.id.content) ?: return
        val scroll = (0 until content.childCount)
            .map { content.getChildAt(it) }
            .filterIsInstance<ScrollView>()
            .lastOrNull()
        scroll?.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        val panel = scroll?.getChildAt(0) as? LinearLayout ?: return
        GlassUi.markPanel(panel, alpha = 118, radiusDp = 26)
    }
}
