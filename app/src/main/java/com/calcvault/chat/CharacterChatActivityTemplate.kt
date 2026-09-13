package com.calcvault.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.appcompat.widget.AppCompatTextView
import android.view.View
import com.calcvault.emotional.EmotionalAnimationEngine
import com.calcvault.emotional.ThemeEngine
import com.calcvault.messaging.AppendOnlyMessageDB
import com.calcvault.messaging.MessageRecord
import kotlinx.coroutines.launch

/**
 * CharacterChatActivityTemplate - Complete example implementation
 *
 * This is a template showing how to integrate the character-driven chat system
 * into your existing ChatActivity. Copy relevant sections to your actual activity.
 *
 * BEFORE/AFTER COMPARISON:
 * - BEFORE: Generic bubbles, no character distinction, spammy background dialogue
 * - AFTER: Character-specific bubbles, intelligent background reactions, immersive chat
 */
class CharacterChatActivityTemplate : AppCompatActivity() {

    // UI Components
    private lateinit var messageRecyclerView: RecyclerView
    private lateinit var messageAdapter: CharacterAwareMessageAdapter

    // Controllers
    private lateinit var themeEngine: ThemeEngine
    private lateinit var animEngine: EmotionalAnimationEngine
    private var backgroundEngine: BackgroundCharacterInteractionEngine? = null

    // Database
    private lateinit var messageDb: AppendOnlyMessageDB

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize theme and animation engines
        themeEngine = ThemeEngine(this)
        animEngine = EmotionalAnimationEngine(this, findViewById(android.R.id.content) as ViewGroup)

        // Clear theme style cache on startup
        CharacterBubbleStyleProvider.clearCache()

        // Initialize database
        messageDb = AppendOnlyMessageDB(com.calcvault.storage.USBStorageEngine.getInstance(this))

        // Setup message adapter with character support
        setupMessageAdapter()

        // Setup background character interactions
        setupBackgroundCharacterInteraction()

        // Load and display messages
        loadMessages()
    }

    /**
     * Setup message adapter with character-aware rendering
     */
    private fun setupMessageAdapter() {
        messageAdapter = CharacterAwareMessageAdapter(
            messages = mutableListOf(),
            localId = "zain", // Current user
            themeEngine = themeEngine,
            animationsEngine = animEngine,
            onCharacterAnimationRequest = { request ->
                // Dispatch animation to engine
                executeCharacterAnimation(request)
            }
        )

        messageRecyclerView = findViewById(android.R.id.list) // Or your RecyclerView ID
        messageRecyclerView.apply {
            adapter = messageAdapter
            layoutManager = LinearLayoutManager(this@CharacterChatActivityTemplate).apply {
                stackFromEnd = true
            }
        }
    }

    /**
     * Setup background character interaction engine
     */
    private fun setupBackgroundCharacterInteraction() {
        backgroundEngine = BackgroundCharacterInteractionHelper.createAndInitialize(
            rootContainer = window.decorView.findViewById(android.R.id.content) as ViewGroup,
            animationEngine = animEngine,
            coroutineScope = lifecycleScope,
            mode = BackgroundInteractionMode.TRIGGER // RECOMMENDED: reacts to message content
        )

        // Configure interaction settings
        backgroundEngine?.apply {
            // Adjust animation speed (0.5 = half speed, 2.0 = double speed)
            idleAnimationSpeed = 1.0f

            // Adjust reaction intensity (0.5-2.0)
            triggerAnimationIntensity = 1.0f

            // Enable/disable character interactions
            enableCharacterInteractions = true
        }
    }

    /**
     * Load messages from database and display
     */
    private fun loadMessages() {
        lifecycleScope.launch {
            val messages = messageDb.getAllMessages() // Your database method

            messageAdapter.messages = messages.toMutableList()
            messageAdapter.notifyDataSetChanged()

            // Scroll to latest message
            messageRecyclerView.scrollToPosition((messageAdapter.itemCount - 1).coerceAtLeast(0))
        }
    }

    /**
     * Called when sending a new message
     */
    fun sendMessage(content: String) {
        if (content.isBlank()) return

        lifecycleScope.launch {
            // Save to database using sendTextMessage API
            val message = messageDb.sendTextMessage(
                from = "zain", // Current user
                to = "sanu", // Recipient
                content = content
            )

            // Update adapter
            messageAdapter.messages.add(message)
            messageAdapter.notifyItemInserted(messageAdapter.itemCount - 1)

            // Scroll to latest
            messageRecyclerView.scrollToPosition(messageAdapter.itemCount - 1)

            // NEW: Notify background engine for character reactions
            backgroundEngine?.onMessageArrived(
                content,
                ChatCharacter.fromSenderId(message.from)
            )

            // Clear input (in your actual activity, clear EditText here)
        }
    }

    /**
     * Handle character animation requests from MessageAdapter
     */
    private fun executeCharacterAnimation(request: CharacterAnimationRequest) {
        when (request.animationType) {
            // Love/affection reactions
            CharacterAnimationType.HEART_EYES -> {
                animEngine.triggerCustomEmojiAnimation(
                    emoji = "❤️",
                    intensity = request.intensity,
                    direction = "UP",
                    speed = if (request.intensity > 15) "FAST" else "MEDIUM"
                )
            }

            // Happy reactions
            CharacterAnimationType.SMILE -> {
                animEngine.triggerCustomEmojiAnimation(
                    emoji = "😊",
                    intensity = 6,
                    direction = "UP"
                )
            }

            // Excited reactions
            CharacterAnimationType.EXCITED_SPIN -> {
                animEngine.triggerCustomEmojiAnimation(
                    emoji = "✨",
                    intensity = request.intensity,
                    direction = "RANDOM",
                    speed = "FAST"
                )
            }

            // Surprise reactions
            CharacterAnimationType.SURPRISED -> {
                animEngine.triggerCustomEmojiAnimation(
                    emoji = "😮",
                    intensity = request.intensity,
                    direction = "UP",
                    speed = "FAST"
                )
            }

            // Sad reactions
            CharacterAnimationType.SAD_FACE -> {
                animEngine.triggerCustomEmojiAnimation(
                    emoji = "😢",
                    intensity = 8,
                    direction = "DOWN",
                    speed = "SLOW"
                )
            }

            // Blush reactions
            CharacterAnimationType.BLUSH -> {
                animEngine.triggerCustomEmojiAnimation(
                    emoji = "💕",
                    intensity = 10,
                    direction = "UP"
                )
            }

            // Movement-based (spring effects)
            CharacterAnimationType.BOUNCE,
            CharacterAnimationType.WOBBLE,
            CharacterAnimationType.NOD,
            CharacterAnimationType.WAVE -> {
                // Apply spring animation to message view
                messageRecyclerView.let { animEngine.springBubble(it) }
            }

            else -> {
                // Default: gentle sparkle
                animEngine.triggerCustomEmojiAnimation(
                    emoji = "✨",
                    intensity = 3,
                    direction = "UP"
                )
            }
        }
    }

    /**
     * Update interaction mode (called from settings)
     */
    fun setInteractionMode(mode: BackgroundInteractionMode) {
        backgroundEngine?.setInteractionMode(mode)
    }

    /**
     * Update animation intensity (called from settings)
     */
    fun setAnimationIntensity(intensity: Float) {
        backgroundEngine?.triggerAnimationIntensity = intensity.coerceIn(0.3f, 2.0f)
    }

    /**
     * Update idle animation speed (called from settings)
     */
    fun setIdleAnimationSpeed(speed: Float) {
        backgroundEngine?.idleAnimationSpeed = speed.coerceIn(0.3f, 2.0f)
    }

    /**
     * Toggle character interactions on/off
     */
    fun setCharacterInteractionsEnabled(enabled: Boolean) {
        backgroundEngine?.enableCharacterInteractions = enabled
    }

    override fun onDestroy() {
        backgroundEngine?.stop()
        super.onDestroy()
    }
}

/**
 * CharacterAwareMessageAdapter - Enhanced MessageAdapter with character styling
 *
 * This extends the existing MessageAdapter pattern with character-specific rendering.
 * INTEGRATION: Replace your current MessageAdapter with this one, or copy the
 * character-related code into your existing adapter.
 */
class CharacterAwareMessageAdapter(
    var messages: MutableList<MessageRecord>,
    private val localId: String,
    private val themeEngine: ThemeEngine,
    private val animationsEngine: EmotionalAnimationEngine,
    private val onCharacterAnimationRequest: (CharacterAnimationRequest) -> Unit = {}
) : RecyclerView.Adapter<CharacterAwareMessageAdapter.MessageViewHolder>() {

    // Character message handler
    private val characterHandler = CharacterMessageHandler(themeEngine) { request ->
        onCharacterAnimationRequest(request)
    }

    override fun getItemCount(): Int = messages.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val root = inflater.inflate(android.R.layout.simple_list_item_1, parent, false)
        return MessageViewHolder(root)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messages[position]
        val appTheme = themeEngine.getCurrentTheme()

        // Display message text (assuming you have a TextView)
        holder.messageText?.text = message.content

        // Placeholder bubble view (replace with your CardView)
        val bubble = holder.itemView.findViewById<CardView?>(android.R.id.content) ?: CardView(holder.itemView.context)

        // NEW: Apply character styling
        characterHandler.applyCharacterStyling(
            message = message,
            bubble = bubble,
            textView = holder.messageText ?: AppCompatTextView(holder.itemView.context),
            position = position
        )
    }

    inner class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val messageText: AppCompatTextView? = itemView.findViewById(android.R.id.text1)

        init {
            // Handle bubble press for animations
            itemView.setOnClickListener {
                val bubble = itemView.findViewById<CardView?>(android.R.id.content)
                if (bubble != null) {
                    characterHandler.applyPressAnimation(bubble)
                }
            }
        }
    }
}

/**
 * Extension: Settings Fragment for Character Chat Configuration
 *
 * Add this to your settings to let users configure character animations
 */
class CharacterChatSettingsTemplate {

    /**
     * Example configuration options
     */
    fun createSettingsOptions(): Map<String, Any> {
        return mapOf(
            "interaction_mode" to BackgroundInteractionMode.TRIGGER.name,
            "animation_intensity" to 1.0f,
            "idle_animation_speed" to 1.0f,
            "enable_character_reactions" to true,
            "enable_background_interactions" to true
        )
    }

    /**
     * Example: Load settings from preferences
     */
    fun loadSettings(preferences: android.content.SharedPreferences): Map<String, Any> {
        return mapOf(
            "interaction_mode" to preferences.getString(
                "interaction_mode",
                BackgroundInteractionMode.TRIGGER.name
            ) as Any,
            "animation_intensity" to preferences.getFloat("animation_intensity", 1.0f),
            "idle_animation_speed" to preferences.getFloat("idle_animation_speed", 1.0f),
            "enable_character_reactions" to preferences.getBoolean("enable_character_reactions", true),
            "enable_background_interactions" to preferences.getBoolean("enable_background_interactions", true)
        )
    }
}
