# Animation Integration Examples

This file shows how to integrate the new animation systems into existing CalcVault activities.

## Example 1: Enhanced Chat Activity

```kotlin
class ChatActivity : AppCompatActivity() {
    private lateinit var advancedEngine: AdvancedAnimationEngine
    private lateinit var gestureController: GestureAnimationController
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ... existing setup ...
        
        advancedEngine = AdvancedAnimationEngine(this, binding.root)
        gestureController = GestureAnimationController(this, binding.root, advancedEngine)
        
        // Setup gesture handling
        val callback = object : GestureAnimationController.GestureCallback {
            override fun onDoubleTap(x: Float, y: Float) {
                // React to message like with heart burst
                advancedEngine.burstConfetti(x, y, colors = listOf(0xFFFF1493.toInt()))
            }
        }
        gestureController.initialize(callback)
        binding.root.setOnTouchListener(gestureController)
    }
    
    private fun displayReceivedMessage(message: MessageRecord) {
        // Existing message display code...
        val bubbleView = /* ... your bubble view ... */
        
        // Add animation
        advancedEngine.animateReceivedBubble(bubbleView)
        
        // Check for message triggers
        if (message.type == MSG_TEXT) {
            checkMessageAnimationTriggers(message.content)
        }
    }
    
    private fun checkMessageAnimationTriggers(content: String) {
        val lower = content.lowercase()
        
        when {
            lower.contains("love you") || lower.contains("❤️") -> {
                advancedEngine.burstConfetti(
                    binding.root.width / 2f,
                    binding.root.height / 3f,
                    particleCount = 30,
                    colors = listOf(0xFFFF1493.toInt(), 0xFFFFB6C1.toInt())
                )
            }
            lower.contains("good night") || lower.contains("sleep") -> {
                advancedEngine.burstSparkles(
                    binding.root.width / 2f,
                    binding.root.height / 2f,
                    color = 0xFF1A237E.toInt()
                )
            }
            lower.contains("congrats") || lower.contains("awesome") -> {
                advancedEngine.burstConfetti(
                    binding.root.width / 2f,
                    binding.root.height / 2f,
                    colors = listOf(0xFFFFD700.toInt(), 0xFF00FF00.toInt(), 0xFF0099FF.toInt())
                )
            }
        }
    }
    
    override fun onDestroy() {
        advancedEngine.cancelAllAnimations()
        gestureController.reset()
        super.onDestroy()
    }
}
```

## Example 2: Enhanced Mood Activity

```kotlin
class MoodActivity : AppCompatActivity() {
    private lateinit var moodSystem: MoodReactionSystem
    private lateinit var advancedEngine: AdvancedAnimationEngine
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ... existing setup ...
        
        advancedEngine = AdvancedAnimationEngine(this, binding.root)
        moodSystem = MoodReactionSystem(this, binding.root, advancedEngine)
    }
    
    private fun selectMood(mood: MoodReactionSystem.Mood) {
        // Smooth transition to new mood
        moodSystem.currentMood?.let { currentMood ->
            moodSystem.transitionMood(currentMood, mood, duration = 1500)
        } ?: run {
            // First mood selection
            moodSystem.setMood(mood, includeParticles = true)
        }
        
        // Save mood
        saveMoodToDatabase(mood.name)
    }
    
    private fun MoodReactionSystem.Mood.name(): String = when (this) {
        MoodReactionSystem.Mood.HAPPY -> "Happy"
        MoodReactionSystem.Mood.SAD -> "Sad"
        MoodReactionSystem.Mood.LOVE -> "Love"
        MoodReactionSystem.Mood.EXCITED -> "Excited"
        MoodReactionSystem.Mood.CALM -> "Calm"
        // ... etc
        else -> "Neutral"
    }
    
    override fun onDestroy() {
        moodSystem.clearAllEffects()
        super.onDestroy()
    }
}
```

## Example 3: Enhanced Call Activity

```kotlin
class CallActivity : AppCompatActivity() {
    private lateinit var advancedEngine: AdvancedAnimationEngine
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ... existing setup ...
        
        advancedEngine = AdvancedAnimationEngine(this, binding.root)
        
        // Setup call UI with animations
        setupCallUI()
    }
    
    private fun setupCallUI() {
        // Pulsing accept button
        binding.btnAccept.setOnClickListener {
            advancedEngine.elasticPulse(binding.btnAccept, scaleFactor = 1.1f)
            acceptCall()
        }
        
        // Incoming call ring animation
        if (isIncomingCall) {
            startIncomingRingAnimation()
        }
    }
    
    private fun startIncomingRingAnimation() {
        // Pulse the avatar/profile picture
        val animator = ObjectAnimator.ofFloat(
            binding.avatarView, View.SCALE_X,
            1.0f, 1.05f, 1.0f
        ).apply {
            duration = 1000
            repeatCount = ObjectAnimator.INFINITE
            start()
        }
    }
    
    fun onCallEstablished() {
        // Celebrate successful connection
        advancedEngine.burstSparkles(
            binding.root.width / 2f,
            binding.root.height / 2f,
            particleCount = 20,
            color = 0xFF00FF00.toInt()
        )
    }
    
    fun onCallEnded() {
        // Fade out animation
        val fadeOut = ObjectAnimator.ofFloat(binding.root, View.ALPHA, 1f, 0f).apply {
            duration = 500
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    finish()
                }
            })
            start()
        }
    }
    
    override fun onDestroy() {
        advancedEngine.cancelAllAnimations()
        super.onDestroy()
    }
}
```

## Example 4: Enhanced Media Activity with Gesture Animations

```kotlin
class MediaActivity : AppCompatActivity() {
    private lateinit var advancedEngine: AdvancedAnimationEngine
    private lateinit var gestureController: GestureAnimationController
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ... existing setup ...
        
        advancedEngine = AdvancedAnimationEngine(this, binding.root)
        gestureController = GestureAnimationController(this, binding.root, advancedEngine)
        
        val callback = object : GestureAnimationController.GestureCallback {
            override fun onDoubleTap(x: Float, y: Float) {
                // Like animation for media
                advancedEngine.burstConfetti(x, y, colors = listOf(0xFFFF1493.toInt()))
            }
            
            override fun onLongPress(x: Float, y: Float) {
                // Menu animation
                advancedEngine.burstSparkles(x, y, particleCount = 15)
            }
        }
        gestureController.initialize(callback)
        binding.grid.setOnTouchListener(gestureController)
    }
    
    private fun onMediaItemClicked(item: MediaItem) {
        // Animated transition to full screen
        advancedEngine.elasticPulse(binding.grid.findViewWithTag(item.id), 1.2f)
        
        // Show media with animation
        openMediaFullscreen(item)
    }
    
    override fun onDestroy() {
        advancedEngine.cancelAllAnimations()
        gestureController.reset()
        super.onDestroy()
    }
}
```

## Example 5: Enhanced Main Vault Activity

```kotlin
class MainVaultActivity : AppCompatActivity() {
    private lateinit var advancedEngine: AdvancedAnimationEngine
    private lateinit var gestureController: GestureAnimationController
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ... existing setup ...
        
        advancedEngine = AdvancedAnimationEngine(this, binding.root)
        gestureController = GestureAnimationController(this, binding.root, advancedEngine)
        
        addCardAnimations()
    }
    
    private fun addCardAnimations() {
        val cards = listOf(
            binding.chatCard,
            binding.callCard,
            binding.mediaCard,
            binding.filesCard,
            binding.moodCard
        )
        
        cards.forEach { card ->
            card.setOnClickListener {
                // Perform ripple and burst
                advancedEngine.createSwipeRipple(
                    card.x + card.width / 2,
                    card.y + card.height / 2
                )
                advancedEngine.burstSparkles(
                    card.x + card.width / 2,
                    card.y + card.height / 2
                )
                
                // Navigate with animation
                navigateToFeature(card.tag as String)
            }
            
            // Long press shows more info
            card.setOnLongClickListener {
                advancedEngine.elasticPulse(card)
                Toast.makeText(this, "Feature: ${card.tag}", Toast.LENGTH_SHORT).show()
                true
            }
        }
    }
    
    override fun onDestroy() {
        advancedEngine.cancelAllAnimations()
        gestureController.reset()
        super.onDestroy()
    }
}
```

## Key Integration Points

1. **onCreate()** - Initialize animation engines
2. **onDestroy()** - Clean up animations to prevent memory leaks
3. **onClick()** - Trigger gesture animations
4. **onMessageReceived()** - Check for animation triggers
5. **UI Transitions** - Add particle effects on navigation
6. **Success/Error States** - Use color, glow, and particle effects to communicate

## Performance Considerations

- Keep particle counts between 15-40 for smooth performance
- Use MEDIUM animation mode on mid-range devices
- Test on actual devices, not just emulator
- Monitor frame rate in Android Profiler
- Remove animations from memory-constrained views

## Customization

All animations can be customized:
- Duration: Change milliseconds
- Colors: Use 0xAARRGGBB hex format
- Particle count: Adjust for more/less visual impact
- Interpolators: Choose from Android's built-in set

Happy animating! 🎨✨
