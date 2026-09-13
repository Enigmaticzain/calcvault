package com.calcvault.ui.filters

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import com.calcvault.R

/**
 * FilterControlPanel - Floating UI for filter triggers during video calls
 *
 * Features:
 * - Emoji trigger buttons (1-tap activation)
 * - Filter selector dropdown
 * - Effect intensity slider
 * - Toggle on/off
 * - Collapsible design
 */
class FilterControlPanel @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private lateinit var btnTogglePanel: ImageButton
    private lateinit var containerEmojis: LinearLayout
    private lateinit var spinnerFilters: Spinner
    private lateinit var sliderIntensity: SeekBar
    private lateinit var tvIntensity: TextView
    private lateinit var btnClearEffects: Button
    private lateinit var tvStatus: TextView

    var onEmojiTrigger: ((VideoFilterEngine.EmojiType) -> Unit)? = null
    var onFilterSelected: ((VideoFilterEngine.FilterType, Float) -> Unit)? = null
    var onClearEffects: (() -> Unit)? = null

    private var isExpanded = false
    private val emojiButtons = mutableMapOf<VideoFilterEngine.EmojiType, ImageButton>()

    init {
        setupUI()
    }

    private fun setupUI() {
        LayoutInflater.from(context).inflate(R.layout.panel_filter_control, this, true)

        btnTogglePanel = findViewById(R.id.btnTogglePanel)
        containerEmojis = findViewById(R.id.containerEmojis)
        spinnerFilters = findViewById(R.id.spinnerFilters)
        sliderIntensity = findViewById(R.id.sliderIntensity)
        tvIntensity = findViewById(R.id.tvIntensity)
        btnClearEffects = findViewById(R.id.btnClearEffects)
        tvStatus = findViewById(R.id.tvStatus)

        setupEmojiButtons()
        setupFilterSpinner()
        setupIntensitySlider()
        setupToggleButton()
        setupClearButton()

        containerEmojis.visibility = View.GONE
    }

    private fun setupEmojiButtons() {
        val emojiTypes = listOf(
            VideoFilterEngine.EmojiType.HEART,
            VideoFilterEngine.EmojiType.FIRE,
            VideoFilterEngine.EmojiType.LAUGH,
            VideoFilterEngine.EmojiType.STAR,
            VideoFilterEngine.EmojiType.SPARKLE,
            VideoFilterEngine.EmojiType.KISS,
            VideoFilterEngine.EmojiType.LOVE_EYES
        )

        emojiTypes.forEach { emoji ->
            val btn = ImageButton(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(8, 8, 8, 8) }
                text = emoji.emoji
                textSize = 24f
                setBackgroundColor(ContextCompat.getColor(context, R.color.cv_button_neutral))
                setOnClickListener {
                    onEmojiTrigger?.invoke(emoji)
                    updateStatus("${emoji.emoji} shower triggered!")
                }
            }
            containerEmojis.addView(btn)
            emojiButtons[emoji] = btn
        }
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

        val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, filterOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerFilters.adapter = adapter

        spinnerFilters.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val filterType = when (position) {
                    0 -> VideoFilterEngine.FilterType.NONE
                    1 -> VideoFilterEngine.FilterType.WARM_TONE
                    2 -> VideoFilterEngine.FilterType.COOL_TONE
                    3 -> VideoFilterEngine.FilterType.DARK_TONE
                    4 -> VideoFilterEngine.FilterType.SPARKLE
                    5 -> VideoFilterEngine.FilterType.GLOW_AURA
                    6 -> VideoFilterEngine.FilterType.SOFT_BLUR
                    7 -> VideoFilterEngine.FilterType.FLOATING_PARTICLES
                    else -> VideoFilterEngine.FilterType.NONE
                }
                val intensity = sliderIntensity.progress / 100f
                onFilterSelected?.invoke(filterType, intensity)
                updateStatus("${filterOptions[position]} applied")
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun setupIntensitySlider() {
        sliderIntensity.max = 100
        sliderIntensity.progress = 70
        sliderIntensity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                tvIntensity.text = "Intensity: ${progress}%"
                if (fromUser) {
                    val filterType = getSelectedFilterType()
                    if (filterType != VideoFilterEngine.FilterType.NONE) {
                        onFilterSelected?.invoke(filterType, progress / 100f)
                    }
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
        tvIntensity.text = "Intensity: 70%"
    }

    private fun setupToggleButton() {
        btnTogglePanel.setOnClickListener {
            isExpanded = !isExpanded
            containerEmojis.visibility = if (isExpanded) View.VISIBLE else View.GONE
            spinnerFilters.visibility = if (isExpanded) View.VISIBLE else View.GONE
            sliderIntensity.visibility = if (isExpanded) View.VISIBLE else View.GONE
            tvIntensity.visibility = if (isExpanded) View.VISIBLE else View.GONE
            btnClearEffects.visibility = if (isExpanded) View.VISIBLE else View.GONE
            tvStatus.visibility = if (isExpanded) View.VISIBLE else View.GONE

            btnTogglePanel.rotation = if (isExpanded) 180f else 0f
        }
    }

    private fun setupClearButton() {
        btnClearEffects.setOnClickListener {
            onClearEffects?.invoke()
            spinnerFilters.setSelection(0)
            sliderIntensity.progress = 70
            updateStatus("All effects cleared")
        }
    }

    private fun getSelectedFilterType(): VideoFilterEngine.FilterType {
        return when (spinnerFilters.selectedItemPosition) {
            0 -> VideoFilterEngine.FilterType.NONE
            1 -> VideoFilterEngine.FilterType.WARM_TONE
            2 -> VideoFilterEngine.FilterType.COOL_TONE
            3 -> VideoFilterEngine.FilterType.DARK_TONE
            4 -> VideoFilterEngine.FilterType.SPARKLE
            5 -> VideoFilterEngine.FilterType.GLOW_AURA
            6 -> VideoFilterEngine.FilterType.SOFT_BLUR
            7 -> VideoFilterEngine.FilterType.FLOATING_PARTICLES
            else -> VideoFilterEngine.FilterType.NONE
        }
    }

    private fun updateStatus(message: String) {
        tvStatus.text = message
        tvStatus.postDelayed({ tvStatus.text = "" }, 2000)
    }

    fun collapse() {
        if (isExpanded) {
            isExpanded = false
            containerEmojis.visibility = View.GONE
            spinnerFilters.visibility = View.GONE
            sliderIntensity.visibility = View.GONE
            tvIntensity.visibility = View.GONE
            btnClearEffects.visibility = View.GONE
            tvStatus.visibility = View.GONE
            btnTogglePanel.rotation = 0f
        }
    }
}
