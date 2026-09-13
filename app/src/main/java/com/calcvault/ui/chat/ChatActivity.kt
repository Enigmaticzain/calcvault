package com.calcvault.ui.chat

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatEditText
import androidx.appcompat.widget.AppCompatTextView
import androidx.cardview.widget.CardView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.calcvault.emotional.EmotionalAnimationEngine
import com.calcvault.emotional.ThemeEngine
import com.calcvault.ui.media.VoiceRecorderActivity
import com.calcvault.emotional.themes.ThemeApplicator
import com.calcvault.emotional.themes.DoraemonView
import com.calcvault.emotional.themes.NightSkyView
import com.calcvault.emotional.themes.NatureView
import com.calcvault.messaging.AppendOnlyMessageDB
import com.calcvault.messaging.MessageRecord
import com.calcvault.messaging.importer.InstagramNativeImporter
import com.calcvault.network.NetworkMessageEngine
import com.calcvault.storage.MediaChunkManager
import com.calcvault.storage.USBStorageEngine
import com.calcvault.storage.provider.StorageManager
import com.calcvault.ui.ambient.AmbientAnimationView
import com.calcvault.ui.main.MainVaultActivity
import com.calcvault.ui.media.SecureMediaOpenHelper
import com.calcvault.ui.media.SnapCameraActivity
import com.calcvault.utils.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * ChatActivity - Secure messaging interface
 *
 * Features:
 * - Real-time message display
 * - Character-aware rendering
 * - Emotional animation triggers
 * - Theme support
 * - Network syncing
 * - Watch movie together
 */
import com.calcvault.chat.*
import com.calcvault.emotional.themes.DoraemonMessageBridge
class ChatActivity : AppCompatActivity() {

    private lateinit var themeEngine: ThemeEngine
    private lateinit var animEngine: EmotionalAnimationEngine
    private lateinit var messageDB: AppendOnlyMessageDB
    private lateinit var networkEngine: NetworkMessageEngine
    private lateinit var storageEngine: USBStorageEngine
    private lateinit var mediaManager: MediaChunkManager
    private lateinit var messageAdapter: MessageAdapter
    private var rootLayout: FrameLayout? = null
    private var backgroundEngine: BackgroundCharacterInteractionEngine? = null

    // UI Components
    private lateinit var messageRecyclerView: RecyclerView
    private lateinit var messageInput: AppCompatEditText
    private lateinit var sendButton: Button
    private lateinit var backButton: Button

    // User IDs
    private var localUserId = "zain"
    private var partnerUserId = "sanu"
    private var pendingSnapUri: Uri? = null

    companion object {
        private const val TAG = "ChatActivity"
        private const val DEFAULT_SIGNALING = "https://calcvault-signal.onrender.com"
        private const val REQUEST_CODE_PICK_IMAGE = 101
        private const val REQUEST_CODE_PICK_FILE = 102
        private const val REQUEST_CODE_CAPTURE_SNAP = 103
        private const val REQUEST_CODE_VOICE_RECORD = 104
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        // Initialize engines
        themeEngine = ThemeEngine(this)
        storageEngine = USBStorageEngine.getInstance(this)
        messageDB = AppendOnlyMessageDB(storageEngine)
        mediaManager = MediaChunkManager(storageEngine, messageDB)

        localUserId = SessionManager.localUserId.ifBlank { "zain" }
        partnerUserId = SessionManager.partnerUserId.ifBlank {
            if (localUserId == "zain") "sanu" else "zain"
        }

        networkEngine = NetworkMessageEngine(this, messageDB, storageEngine)
        runCatching {
            networkEngine.initialize(
                localUserId,
                partnerUserId,
                SessionManager.signalingUrl.ifBlank { DEFAULT_SIGNALING }
            )
        }.onFailure { error ->
            Log.e(TAG, "Chat network engine failed to initialize; continuing offline", error)
        }

        // Enable edge-to-edge so we can manually handle keyboard insets without panning
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)

        buildUI()

        // Apply theme after UI is built
        ThemeApplicator.applyActive(this)

        // Initialize background character interaction
        rootLayout?.let { root ->
            backgroundEngine = BackgroundCharacterInteractionHelper.createAndInitialize(
                rootContainer = root,
                animationEngine = animEngine,
                coroutineScope = lifecycleScope,
                mode = BackgroundInteractionMode.TRIGGER
            )
        }

        setupMessageListener()
        loadMessages()
        runInstagramImportIfAvailable()
        
        // Initialize triggers
        animEngine.initializeBuiltInTriggers()
        loadCustomTriggers()
    }

    private fun loadCustomTriggers() {
        val json = com.calcvault.auth.UnlockManager.getInstance(this).getCustomTriggersJson()
        animEngine.loadCustomTriggers(json)
    }

    private fun buildUI() {
        val root = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        
        // Modern approach to handle keyboard (IME) resizing without panning the window
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root) { view, windowInsets ->
            val insets = windowInsets.getInsets(
                androidx.core.view.WindowInsetsCompat.Type.systemBars() or 
                androidx.core.view.WindowInsetsCompat.Type.ime()
            )
            // Apply padding to the bottom so the layout shrinks when keyboard opens
            view.setPadding(0, 0, 0, insets.bottom)
            androidx.core.view.WindowInsetsCompat.CONSUMED
        }
        
        rootLayout = root
        setContentView(root)

        val theme = themeEngine.getCurrentTheme()

        // Add ambient background if no theme is active
        if (ThemeApplicator.activeTheme == ThemeApplicator.ThemeChoice.NONE) {
            root.setBackgroundColor(theme.backgroundStart)
            
            val sanctuaryThemeId = when (theme.type) {
                com.calcvault.emotional.ThemeEngine.ThemeType.SANCTUARY_STARGAZING -> "stargazing"
                com.calcvault.emotional.ThemeEngine.ThemeType.SANCTUARY_RAINYCAFE -> "rainy-cafe"
                com.calcvault.emotional.ThemeEngine.ThemeType.SANCTUARY_SUNRISE -> "sunrise"
                com.calcvault.emotional.ThemeEngine.ThemeType.SANCTUARY_AUTUMNPARK -> "autumn-park"
                com.calcvault.emotional.ThemeEngine.ThemeType.SANCTUARY_SUNSETBEACH -> "beach-sunset"
                com.calcvault.emotional.ThemeEngine.ThemeType.SANCTUARY_PEACEFULSNOW -> "peaceful-snow"
                com.calcvault.emotional.ThemeEngine.ThemeType.SANCTUARY_COUCHFIREPLACE -> "cozy-couch"
                else -> null
            }

            if (sanctuaryThemeId != null) {
                val webView = android.webkit.WebView(this).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setBackgroundColor(Color.TRANSPARENT)
                    @android.annotation.SuppressLint("SetJavaScriptEnabled")
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = true
                    settings.allowFileAccessFromFileURLs = true
                    settings.allowUniversalAccessFromFileURLs = true
                    
                    // Disable zooming and scaling
                    settings.setSupportZoom(false)
                    settings.builtInZoomControls = false
                    settings.displayZoomControls = false
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    overScrollMode = View.OVER_SCROLL_NEVER
                    
                    webViewClient = android.webkit.WebViewClient()
                    webChromeClient = android.webkit.WebChromeClient()
                    loadUrl("file:///android_asset/sanctuary/index.html?theme=$sanctuaryThemeId&embed=true")
                }
                root.addView(webView, 0)
            } else {
                val ambientView = AmbientAnimationView(this).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
                AmbientAnimationView.configureCalmPreset(ambientView)
                root.addView(ambientView, 0)
            }
        } else {
            root.setBackgroundColor(Color.TRANSPARENT)
        }

        // Create animation engine
        animEngine = EmotionalAnimationEngine(this, root)

        // Main chat layout with transparency effect
        val chatLayout = LinearLayout(this).apply {
            id = View.generateViewId()
            tag = "chatLayout"
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            background = glassBackground(92, 10, 10, 15, dpToPx(0))
            
            // Hide the native chat UI if the interactive React Sanctuary theme is active
            val sanctuaryThemeId = when (theme.type) {
                com.calcvault.emotional.ThemeEngine.ThemeType.SANCTUARY_STARGAZING -> "stargazing"
                com.calcvault.emotional.ThemeEngine.ThemeType.SANCTUARY_RAINYCAFE -> "rainy-cafe"
                com.calcvault.emotional.ThemeEngine.ThemeType.SANCTUARY_SUNRISE -> "sunrise"
                com.calcvault.emotional.ThemeEngine.ThemeType.SANCTUARY_AUTUMNPARK -> "autumn-park"
                com.calcvault.emotional.ThemeEngine.ThemeType.SANCTUARY_SUNSETBEACH -> "beach-sunset"
                com.calcvault.emotional.ThemeEngine.ThemeType.SANCTUARY_PEACEFULSNOW -> "peaceful-snow"
                com.calcvault.emotional.ThemeEngine.ThemeType.SANCTUARY_COUCHFIREPLACE -> "cozy-couch"
                else -> null
            }
            if (sanctuaryThemeId != null && ThemeApplicator.activeTheme == ThemeApplicator.ThemeChoice.NONE) {
                visibility = View.GONE
            }
        }
        root.addView(chatLayout)

        // Add a floating back button for when the native chat layout is hidden
        if (chatLayout.visibility == View.GONE) {
            val floatingBackBtn = Button(this).apply {
                text = "← Exit Sanctuary"
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = android.view.Gravity.TOP or android.view.Gravity.START
                    setMargins(dpToPx(16), dpToPx(32), 0, 0)
                }
                setBackgroundColor(0x88000000.toInt())
                setTextColor(Color.WHITE)
                setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8))
                setOnClickListener {
                    startActivity(Intent(this@ChatActivity, MainVaultActivity::class.java))
                    finish()
                }
            }
            root.addView(floatingBackBtn)
        }

        // Header with back button
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(56)
            )
            background = glassBackground(188, Color.red(theme.accentColor), Color.green(theme.accentColor), Color.blue(theme.accentColor), dpToPx(0))
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(16, 0, 16, 0)
        }
        chatLayout.addView(header)

        backButton = Button(this).apply {
            text = "← Back"
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setBackgroundColor(Color.TRANSPARENT)
            setTextColor(Color.WHITE)
            setOnClickListener {
                startActivity(Intent(this@ChatActivity, MainVaultActivity::class.java))
                overridePendingTransition(0, 0)
                finish()
            }
        }
        header.addView(backButton)

        val spacer = Space(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, 0, 1f)
        }
        header.addView(spacer)

        AppCompatTextView(this).apply {
            text = partnerDisplayName()
            textSize = 18f
            setTextColor(Color.WHITE)
        }.also { header.addView(it) }

        // Message RecyclerView
        messageRecyclerView = RecyclerView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            layoutManager = LinearLayoutManager(this@ChatActivity).apply {
                stackFromEnd = true
            }
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_ALWAYS
            setPadding(0, dpToPx(10), 0, dpToPx(10))
            setBackgroundColor(Color.TRANSPARENT)
        }
        chatLayout.addView(messageRecyclerView)

        // Composer: keep text visible; secondary actions expand like modern chat apps.
        val composerContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            background = glassBackground(190, 18, 18, 24, dpToPx(22))
            setPadding(dpToPx(10), dpToPx(8), dpToPx(10), dpToPx(10))
        }
        chatLayout.addView(composerContainer)

        val actionScroll = HorizontalScrollView(this).apply {
            visibility = View.GONE
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(8)
            }
        }
        composerContainer.addView(actionScroll)

        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 0)
        }
        actionScroll.addView(actionRow)

        listOf(
            "🎤 Voice" to { startVoiceRecording() },
            "📷 Photo" to { pickImage() },
            "📎 File" to { pickFile() },
            "📸 Snap" to { sendSnap() },
            "🎬 Watch" to { startWatchMovieTogether() },
            "⌨️ Glass" to { toggleGlassKeyboard() }
        ).forEach { (label, action) ->
            actionRow.addView(compactActionButton(label, action))
        }

        val inputArea = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        composerContainer.addView(inputArea)

        val attachButton = Button(this).apply {
            text = "＋"
            layoutParams = LinearLayout.LayoutParams(dpToPx(44), dpToPx(48)).apply {
                marginEnd = dpToPx(8)
            }
            styleRoundButton(this, Color.argb(150, 255, 255, 255), Color.WHITE)
            setOnClickListener {
                val showActions = actionScroll.visibility != View.VISIBLE
                actionScroll.visibility = if (showActions) View.VISIBLE else View.GONE
                text = if (showActions) "×" else "＋"
            }
        }
        inputArea.addView(attachButton)

        messageInput = AppCompatEditText(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            hint = "Type message..."
            setTextColor(Color.WHITE)
            setHintTextColor(Color.argb(128, 200, 200, 200))
            background = glassBackground(120, 255, 255, 255, dpToPx(18))
            minHeight = dpToPx(48)
            maxLines = 4
            setSingleLine(false)
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEND
            setPadding(dpToPx(14), dpToPx(8), dpToPx(14), dpToPx(8))
        }
        inputArea.addView(messageInput)

        sendButton = Button(this).apply {
            text = "➤"
            layoutParams = LinearLayout.LayoutParams(
                dpToPx(52),
                dpToPx(48)
            ).apply {
                marginStart = dpToPx(8)
            }
            styleRoundButton(this, theme.accentColor, Color.WHITE)
            setOnClickListener { sendMessage() }
        }
        inputArea.addView(sendButton)

        // Setup message adapter
        messageAdapter = MessageAdapter(mutableListOf(), localUserId, theme)
        messageRecyclerView.adapter = messageAdapter
        // configureDynamicMessageScroll(header)
    }

    private fun setupMessageListener() {
        networkEngine.onMessageReceived = { msg ->
            lifecycleScope.launch(Dispatchers.Main) {
                messageAdapter.addMessage(msg)
                messageRecyclerView.scrollToPosition((messageAdapter.itemCount - 1).coerceAtLeast(0))

                // Queue message in Doraemon theme if active
                DoraemonMessageBridge.queueChatMessage("shizuka", msg.content, 3500L)

                // Trigger emotional animations based on message content
                animEngine.checkMessageTriggers(msg.content)

                // Notify background engine for character reactions
                backgroundEngine?.onMessageArrived(msg.content, ChatCharacter.fromSenderId(msg.from))
            }
        }
    }

    private fun loadMessages() {
        lifecycleScope.launch(Dispatchers.IO) {
            val messages = runCatching {
                if (StorageManager.isReady()) messageDB.getChatMessages() else emptyList()
            }.onFailure { error ->
                Log.e(TAG, "Failed to load chat messages", error)
            }.getOrDefault(emptyList())

            withContext(Dispatchers.Main) {
                messageAdapter.messages.clear()
                messageAdapter.messages.addAll(messages)
                messageAdapter.notifyDataSetChanged()
                messageRecyclerView.scrollToPosition((messageAdapter.itemCount - 1).coerceAtLeast(0))
                if (messages.isEmpty() && !StorageManager.isReady()) {
                    Toast.makeText(this@ChatActivity, "Storage is still opening. Try Chat again in a moment.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun runInstagramImportIfAvailable() {
        lifecycleScope.launch(Dispatchers.IO) {
            if (!StorageManager.isReady()) return@launch

            val sourceDir = findInstagramSourceDir() ?: return@launch
            val summary = runCatching {
                InstagramNativeImporter(
                    context = this@ChatActivity,
                    messageDB = messageDB,
                    mediaManager = mediaManager
                ).import(
                    InstagramNativeImporter.ImportOptions(
                        sourceDir = sourceDir,
                        localUserId = localUserId,
                        partnerUserId = partnerUserId,
                        localDisplayName = SessionManager.localNickname,
                        partnerDisplayName = SessionManager.partnerNickname
                    )
                )
            }.onFailure { error ->
                Log.e(TAG, "Instagram chat import failed", error)
            }.getOrNull() ?: return@launch

            if (summary.imported || summary.error != null) {
                withContext(Dispatchers.Main) {
                    if (summary.imported) {
                        Toast.makeText(
                            this@ChatActivity,
                            "Instagram chat linked: ${summary.textMessagesImported + summary.mediaMessagesImported} items",
                            Toast.LENGTH_SHORT
                        ).show()
                        loadMessages()
                    } else {
                        Toast.makeText(
                            this@ChatActivity,
                            "Instagram link skipped: ${summary.error}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    private fun findInstagramSourceDir(): File? {
        val folderName = "zain_17882311679967359"
        val candidates = listOfNotNull(
            File(filesDir, "import/$folderName"),
            File(filesDir, folderName),
            getExternalFilesDir(null)?.let { File(it, "import/$folderName") },
            getExternalFilesDir(null)?.let { File(it, folderName) },
            File("/sdcard/Download/$folderName"),
            File("/sdcard/Documents/$folderName"),
            File(folderName)
        )
        return candidates.firstOrNull { source ->
            source.isDirectory && source.listFiles { file ->
                file.isFile && file.name.matches(Regex("""message_\d+\.html""", RegexOption.IGNORE_CASE))
            }?.isNotEmpty() == true
        }
    }

    private var isGlassKeyboardVisible = false
    private fun toggleGlassKeyboard() {
        val root = rootLayout ?: return
        val keyboardTag = "glassKeyboard"
        val existing = root.findViewWithTag<View>(keyboardTag)
        
        if (existing != null) {
            root.removeView(existing)
            isGlassKeyboardVisible = false
            return
        }

        val keyboard = LinearLayout(this).apply {
            tag = keyboardTag
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(240)
            ).apply {
                gravity = Gravity.BOTTOM
                bottomMargin = dpToPx(70) // Above composer
            }
            background = glassBackground(180, 255, 255, 255, dpToPx(20))
            setPadding(dpToPx(10), dpToPx(10), dpToPx(10), dpToPx(10))
            elevation = dpToPx(10).toFloat()
        }

        val rows = listOf(
            "QWERTYUIOP",
            "ASDFGHJKL",
            "ZXCVBNM"
        )

        for (rowStr in rows) {
            val rowLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            for (char in rowStr) {
                val btn = Button(this).apply {
                    text = char.toString()
                    layoutParams = LinearLayout.LayoutParams(0, dpToPx(44), 1f).apply {
                        setMargins(dpToPx(2), dpToPx(2), dpToPx(2), 0)
                    }
                    background = glassBackground(100, 255, 255, 255, dpToPx(8))
                    setTextColor(Color.WHITE)
                    setPadding(0, 0, 0, 0)
                    setOnClickListener {
                        messageInput.append(text)
                    }
                }
                rowLayout.addView(btn)
            }
            keyboard.addView(rowLayout)
        }

        val bottomRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(50))
        }
        
        val spaceBtn = Button(this).apply {
            text = "SPACE"
            layoutParams = LinearLayout.LayoutParams(0, dpToPx(44), 3f).apply { setMargins(dpToPx(2), dpToPx(2), dpToPx(2), dpToPx(2)) }
            background = glassBackground(100, 255, 255, 255, dpToPx(8))
            setTextColor(Color.WHITE)
            setOnClickListener { messageInput.append(" ") }
        }
        
        val delBtn = Button(this).apply {
            text = "DEL"
            layoutParams = LinearLayout.LayoutParams(0, dpToPx(44), 1f).apply { setMargins(dpToPx(2), dpToPx(2), dpToPx(2), dpToPx(2)) }
            background = glassBackground(150, 255, 100, 100, dpToPx(8))
            setTextColor(Color.WHITE)
            setOnClickListener {
                val current = messageInput.text.toString()
                if (current.isNotEmpty()) {
                    messageInput.setText(current.substring(0, current.length - 1))
                    messageInput.setSelection(messageInput.text?.length ?: 0)
                }
            }
        }
        
        bottomRow.addView(spaceBtn)
        bottomRow.addView(delBtn)
        keyboard.addView(bottomRow)

        root.addView(keyboard)
        isGlassKeyboardVisible = true
    }

    private fun sendMessage() {
        val content = messageInput.text.toString().trim()
        if (content.isBlank()) return

        messageInput.text?.clear()

        lifecycleScope.launch(Dispatchers.IO) {
            val message = MessageRecord(
                id = System.currentTimeMillis(),
                type = AppendOnlyMessageDB.MSG_TEXT,
                from = localUserId,
                to = partnerUserId,
                content = content,
                timestamp = System.currentTimeMillis(),
                delivered = false,
                read = false
            )

            messageDB.saveMessage(message)
            networkEngine.sendEncryptedPayload(
                id = message.id,
                data = content.toByteArray(Charsets.UTF_8),
                type = AppendOnlyMessageDB.MSG_TEXT
            )

            withContext(Dispatchers.Main) {
                messageAdapter.addMessage(message)
                messageRecyclerView.scrollToPosition((messageAdapter.itemCount - 1).coerceAtLeast(0))

                // Queue message in Doraemon theme if active
                DoraemonMessageBridge.queueChatMessage("doraemon", content, 3500L)

                // Trigger animations for outgoing message
                animEngine.checkMessageTriggers(content)

                // Notify background engine for character reactions
                backgroundEngine?.onMessageArrived(content, ChatCharacter.fromSenderId(message.from))
            }
        }
    }

    private fun startVoiceRecording() {
        val intent = Intent(this, VoiceRecorderActivity::class.java)
        startActivityForResult(intent, REQUEST_CODE_VOICE_RECORD)
    }

    private fun pickImage() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        try {
            startActivityForResult(
                Intent.createChooser(intent, "Select Picture"),
                REQUEST_CODE_PICK_IMAGE
            )
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot open file picker", Toast.LENGTH_SHORT).show()
        }
    }

    private fun pickFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        try {
            startActivityForResult(
                Intent.createChooser(intent, "Select File"),
                REQUEST_CODE_PICK_FILE
            )
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot open file picker", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendSnap() {
        // Open in-app snap camera with real-time filters
        val intent = Intent(this, SnapCameraActivity::class.java)
        startActivityForResult(intent, REQUEST_CODE_CAPTURE_SNAP)
    }

    private fun startWatchMovieTogether() {
        Toast.makeText(this, "🎬 Starting Watch Party...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch(Dispatchers.IO) {
            val message = MessageRecord(
                id = System.currentTimeMillis(),
                type = AppendOnlyMessageDB.MSG_VIDEO,
                from = localUserId,
                to = partnerUserId,
                content = "[Watch Movie Together - Synced Playback]",
                timestamp = System.currentTimeMillis(),
                delivered = false,
                read = false
            )

            messageDB.saveMessage(message)
            networkEngine.sendEncryptedPayload(
                id = message.id,
                data = "[movie_sync_request]".toByteArray(Charsets.UTF_8),
                type = AppendOnlyMessageDB.MSG_VIDEO
            )

            withContext(Dispatchers.Main) {
                messageAdapter.addMessage(message)
                messageRecyclerView.scrollToPosition((messageAdapter.itemCount - 1).coerceAtLeast(0))
                Toast.makeText(this@ChatActivity, "Watch party initiated!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            REQUEST_CODE_PICK_IMAGE -> if (resultCode == Activity.RESULT_OK) {
                val uri = data?.data
                if (uri != null) {
                    persistUriPermission(uri)
                    sendMediaMessage(
                        label = displayNameFor(uri, "Image"),
                        type = AppendOnlyMessageDB.MSG_IMAGE,
                        uri = uri,
                        mimeType = contentResolver.getType(uri).orEmpty().ifBlank { "image/*" }
                    )
                }
            }
            REQUEST_CODE_PICK_FILE -> if (resultCode == Activity.RESULT_OK) {
                val uri = data?.data
                if (uri != null) {
                    persistUriPermission(uri)
                    val mimeType = contentResolver.getType(uri).orEmpty()
                    val messageType = when {
                        mimeType.startsWith("image/") -> AppendOnlyMessageDB.MSG_IMAGE
                        mimeType.startsWith("video/") -> AppendOnlyMessageDB.MSG_VIDEO
                        mimeType.startsWith("audio/") -> AppendOnlyMessageDB.MSG_AUDIO
                        else -> AppendOnlyMessageDB.MSG_FILE
                    }
                    sendMediaMessage(
                        label = displayNameFor(uri, "File"),
                        type = messageType,
                        uri = uri,
                        mimeType = mimeType.ifBlank { "*/*" }
                    )
                }
            }
            REQUEST_CODE_CAPTURE_SNAP -> if (resultCode == Activity.RESULT_OK) {
                val uri = pendingSnapUri
                if (uri != null) {
                    sendMediaMessage(
                        label = "Snapchat",
                        type = AppendOnlyMessageDB.MSG_IMAGE,
                        uri = uri,
                        mimeType = "image/jpeg",
                        isSnap = true
                    )
                }
            }
            REQUEST_CODE_VOICE_RECORD -> if (resultCode == Activity.RESULT_OK) {
                val voiceFilePath = data?.getStringExtra("voice_file_path")
                val voiceDuration = data?.getStringExtra("voice_duration") ?: "0:00"
                
                if (!voiceFilePath.isNullOrEmpty()) {
                    val voiceUri = Uri.parse("file://$voiceFilePath")
                    sendVoiceMessage(voiceUri, voiceDuration)
                }
            }
        }
    }

    private fun persistUriPermission(uri: Uri) {
        runCatching {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }.onFailure {
            Log.d(TAG, "URI is not persistable: $uri")
        }
    }

    private fun sendMediaMessage(label: String, type: Int, uri: Uri, mimeType: String, isSnap: Boolean = false) {
        val mediaExtra = org.json.JSONObject().apply {
            put("ref", uri.toString())
            put("name", label)
            put("mime", mimeType)
            if (isSnap) put("isSnap", true)
        }.toString()

        lifecycleScope.launch(Dispatchers.IO) {
            val message = MessageRecord(
                id = System.currentTimeMillis(),
                type = type,
                from = localUserId,
                to = partnerUserId,
                content = label,
                timestamp = System.currentTimeMillis(),
                delivered = false,
                read = false,
                extra = mediaExtra
            )

            messageDB.saveMessage(message)
            networkEngine.sendEncryptedPayload(
                id = message.id,
                data = uri.toString().toByteArray(Charsets.UTF_8),
                type = type
            )

            withContext(Dispatchers.Main) {
                messageAdapter.addMessage(message)
                messageRecyclerView.scrollToPosition((messageAdapter.itemCount - 1).coerceAtLeast(0))
                Toast.makeText(this@ChatActivity, "$label sent!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun sendVoiceMessage(voiceUri: Uri, duration: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val message = MessageRecord(
                id = System.currentTimeMillis(),
                type = AppendOnlyMessageDB.MSG_AUDIO,
                from = localUserId,
                to = partnerUserId,
                content = "🎤 Voice Message - $duration",
                timestamp = System.currentTimeMillis(),
                delivered = false,
                read = false,
                extra = org.json.JSONObject().apply {
                    put("ref", voiceUri.toString())
                    put("duration", duration)
                    put("mime", "audio/m4a")
                }.toString()
            )

            messageDB.saveMessage(message)
            
            // Read voice file and send encrypted
            try {
                val inputStream = contentResolver.openInputStream(voiceUri)
                val voiceData = inputStream?.readBytes() ?: byteArrayOf()
                inputStream?.close()
                
                networkEngine.sendEncryptedPayload(
                    id = message.id,
                    data = voiceData,
                    type = AppendOnlyMessageDB.MSG_AUDIO
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error sending voice message: ${e.message}")
            }

            withContext(Dispatchers.Main) {
                messageAdapter.addMessage(message)
                messageRecyclerView.scrollToPosition((messageAdapter.itemCount - 1).coerceAtLeast(0))
                Toast.makeText(this@ChatActivity, "Voice message sent!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        ThemeApplicator.applyActive(this)
        
        val wallpaperPath = SessionManager.chatWallpaperPath
        if (wallpaperPath.isNotBlank()) {
            try {
                val uri = Uri.parse(wallpaperPath)
                val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    android.graphics.ImageDecoder.decodeBitmap(android.graphics.ImageDecoder.createSource(contentResolver, uri))
                } else {
                    MediaStore.Images.Media.getBitmap(contentResolver, uri)
                }
                val drawable = android.graphics.drawable.BitmapDrawable(resources, bitmap)
                drawable.colorFilter = android.graphics.PorterDuffColorFilter(Color.parseColor("#88000000"), android.graphics.PorterDuff.Mode.SRC_OVER)
                
                rootLayout?.background = drawable
                // Ensure chat layout is transparent to see wallpaper
                rootLayout?.findViewWithTag<View>("chatLayout")?.background = glassBackground(120, 10, 10, 15, 0)
            } catch (e: Exception) {
                rootLayout?.setBackgroundColor(Color.BLACK)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        backgroundEngine?.stop()
        ThemeApplicator.detach(this)
    }

    private fun compactActionButton(label: String, action: () -> Unit): Button {
        return Button(this).apply {
            text = label
            textSize = 13f
            minWidth = 0
            minHeight = 0
            isAllCaps = false
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dpToPx(42)
            ).apply {
                marginEnd = dpToPx(8)
            }
            setPadding(dpToPx(12), 0, dpToPx(12), 0)
            styleRoundButton(this, Color.argb(145, 255, 255, 255), Color.WHITE)
            setOnClickListener { action() }
        }
    }

    private fun styleRoundButton(button: Button, backgroundColor: Int, textColor: Int) {
        button.background = GradientDrawable().apply {
            cornerRadius = dpToPx(18).toFloat()
            setColor(backgroundColor)
            setStroke(1, Color.argb(60, 255, 255, 255))
        }
        button.setTextColor(textColor)
    }

    private fun glassBackground(alpha: Int, red: Int, green: Int, blue: Int, radius: Int): GradientDrawable {
        return GradientDrawable().apply {
            cornerRadius = radius.toFloat()
            setColor(Color.argb(alpha, red, green, blue))
            setStroke(1, Color.argb(48, 255, 255, 255))
        }
    }

    /**
     * Handle character animation requests from MessageAdapter
     */
    private fun executeCharacterAnimation(request: CharacterAnimationRequest) {
        when (request.animationType) {
            CharacterAnimationType.HEART_EYES -> {
                animEngine.triggerCustomEmojiAnimation(
                    emoji = "❤️",
                    intensity = request.intensity,
                    direction = "UP",
                    speed = if (request.intensity > 15) "FAST" else "MEDIUM"
                )
            }
            CharacterAnimationType.SMILE -> {
                animEngine.triggerCustomEmojiAnimation(
                    emoji = "😊",
                    intensity = 6,
                    direction = "UP"
                )
            }
            CharacterAnimationType.EXCITED_SPIN -> {
                animEngine.triggerCustomEmojiAnimation(
                    emoji = "✨",
                    intensity = request.intensity,
                    direction = "RANDOM",
                    speed = "FAST"
                )
            }
            CharacterAnimationType.SURPRISED -> {
                animEngine.triggerCustomEmojiAnimation(
                    emoji = "😮",
                    intensity = request.intensity,
                    direction = "UP",
                    speed = "FAST"
                )
            }
            CharacterAnimationType.SAD_FACE -> {
                animEngine.triggerCustomEmojiAnimation(
                    emoji = "😢",
                    intensity = 8,
                    direction = "DOWN",
                    speed = "SLOW"
                )
            }
            CharacterAnimationType.BLUSH -> {
                animEngine.triggerCustomEmojiAnimation(
                    emoji = "💕",
                    intensity = 10,
                    direction = "UP"
                )
            }
            CharacterAnimationType.BOUNCE,
            CharacterAnimationType.WOBBLE,
            CharacterAnimationType.NOD,
            CharacterAnimationType.WAVE -> {
                val root = rootLayout ?: return
                // Find the active theme view to pulse, instead of the whole root
                val themeView = root.getChildAt(0) // Usually our background theme view
                if (themeView is DoraemonView || themeView is NightSkyView || themeView is NatureView) {
                    animEngine.startCallPulse(themeView)
                    themeView.postDelayed({ animEngine.stopCallPulse(themeView) }, request.duration)
                } else {
                    // Fallback to searching by class name if needed
                    for (i in 0 until root.childCount) {
                        val child = root.getChildAt(i)
                        if (child.javaClass.simpleName.endsWith("View") && child.javaClass.simpleName.contains("Theme")) {
                            animEngine.startCallPulse(child)
                            child.postDelayed({ animEngine.stopCallPulse(child) }, request.duration)
                            break
                        }
                    }
                }
            }
            else -> {
                animEngine.triggerCustomEmojiAnimation(
                    emoji = "✨",
                    intensity = 3,
                    direction = "UP"
                )
            }
        }
    }

    private fun partnerDisplayName(): String {
        return SessionManager.partnerNickname.ifBlank { partnerUserId.ifBlank { "Partner" } }
    }

    private fun displayNameFor(uri: Uri, fallback: String): String {
        return runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        cursor.getString(0)?.takeIf { it.isNotBlank() }
                    } else {
                        null
                    }
                }
        }.getOrNull()
            ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: fallback
    }

    private fun mediaPreviewFor(message: MessageRecord): MediaPreview? {
        val isMediaType = message.type in setOf(
            AppendOnlyMessageDB.MSG_TEXT, // Text can have attachments in some cases, but usually MSG_IMAGE etc
            AppendOnlyMessageDB.MSG_IMAGE,
            AppendOnlyMessageDB.MSG_VIDEO,
            AppendOnlyMessageDB.MSG_AUDIO,
            AppendOnlyMessageDB.MSG_FILE
        )
        if (!isMediaType) return null

        val extra = runCatching { org.json.JSONObject(message.extra) }.getOrNull()
        val refStr = extra?.optString("ref").orEmpty()
        val name = extra?.optString("name").orEmpty().ifBlank { message.content.ifBlank { "Attachment" } }
        val mime = extra?.optString("mime").orEmpty().ifBlank {
            when (message.type) {
                AppendOnlyMessageDB.MSG_IMAGE -> "image/*"
                AppendOnlyMessageDB.MSG_VIDEO -> "video/*"
                AppendOnlyMessageDB.MSG_AUDIO -> "audio/*"
                else -> "*/*"
            }
        }
        
        val vaultRef = refStr.toLongOrNull()?.let { mediaManager.getMediaRef(it) }
        
        return MediaPreview(
            ref = refStr,
            openableUri = refStr.takeIf { it.isOpenableAndroidUri() }?.let(Uri::parse),
            title = name,
            mimeType = mime,
            messageType = message.type,
            isVaultMedia = vaultRef != null,
            vaultRef = vaultRef
        )
    }

    private data class MediaPreview(
        val ref: String,
        val openableUri: Uri?,
        val title: String,
        val mimeType: String,
        val messageType: Int,
        val isVaultMedia: Boolean,
        val vaultRef: MediaChunkManager.MediaRef? = null
    )

    private fun renderMediaPreview(container: FrameLayout, preview: MediaPreview?, message: MessageRecord) {
        container.removeAllViews()
        if (preview == null) {
            container.visibility = View.GONE
            return
        }

        container.visibility = View.VISIBLE
        
        // Handle thumbnails for vault media
        if (preview.isVaultMedia && preview.vaultRef != null) {
            val imageView = ImageView(this).apply {
                layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(180))
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(Color.argb(80, 0, 0, 0))
            }
            container.addView(imageView)
            
            lifecycleScope.launch(Dispatchers.IO) {
                val bitmap = mediaManager.decodeThumbnail(preview.vaultRef.thumbHash)
                withContext(Dispatchers.Main) {
                    if (bitmap != null) {
                        imageView.setImageBitmap(bitmap)
                    } else {
                        imageView.setImageResource(android.R.drawable.ic_menu_gallery)
                    }
                }
            }
            return
        }

        if (preview.messageType == AppendOnlyMessageDB.MSG_IMAGE && preview.openableUri != null) {
            val isSnap = runCatching { org.json.JSONObject(message.extra).optBoolean("isSnap", false) }.getOrDefault(false)
            if (isSnap) {
                val btn = Button(this).apply {
                    text = "📸 Tap and Hold to View Snap"
                    isAllCaps = false
                    setBackgroundColor(Color.parseColor("#E53935"))
                    setTextColor(Color.WHITE)
                    setOnTouchListener { _, event ->
                        if (event.action == MotionEvent.ACTION_DOWN) {
                            showSnapFullScreen(message, preview.openableUri)
                            true
                        } else false
                    }
                }
                container.addView(btn)
            } else {
                val imageView = ImageView(this).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dpToPx(180)
                    )
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    setBackgroundColor(Color.argb(80, 255, 255, 255))
                    contentDescription = "Tap to open ${preview.title}"
                }
                container.addView(imageView)

                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            android.graphics.ImageDecoder.decodeBitmap(android.graphics.ImageDecoder.createSource(contentResolver, preview.openableUri))
                        } else {
                            MediaStore.Images.Media.getBitmap(contentResolver, preview.openableUri)
                        }
                        withContext(Dispatchers.Main) {
                            imageView.setImageBitmap(bitmap)
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            imageView.setBackgroundColor(Color.DKGRAY)
                            imageView.setImageResource(android.R.drawable.ic_menu_report_image)
                        }
                    }
                }
            }
        } else {
            val label = when (preview.messageType) {
                AppendOnlyMessageDB.MSG_IMAGE -> "🖼 Image"
                AppendOnlyMessageDB.MSG_VIDEO -> "▶ Video"
                AppendOnlyMessageDB.MSG_AUDIO -> "▶ Audio"
                AppendOnlyMessageDB.MSG_FILE -> "📎 File"
                else -> "Attachment"
            }
            container.addView(
                AppCompatTextView(this).apply {
                    text = if (preview.ref.isBlank()) "$label preview" else "$label • Tap to open"
                    textSize = 14f
                    setTextColor(Color.WHITE)
                    background = glassBackground(105, 255, 255, 255, dpToPx(14))
                    setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10))
                    contentDescription = message.content
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                }
            )
        }
    }

    private fun openMediaIfPresent(message: MessageRecord) {
        val preview = mediaPreviewFor(message) ?: return
        if (preview.isVaultMedia) {
            openVaultMedia(message, preview.title)
            return
        }

        val uri = preview.openableUri ?: return
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, preview.mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(Intent.createChooser(intent, "Open ${preview.title}"))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "No app can open this attachment", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openVaultMedia(message: MessageRecord, title: String) {
        lifecycleScope.launch {
            val materialized = SecureMediaOpenHelper.materialize(
                activity = this@ChatActivity,
                mediaManager = mediaManager,
                message = message
            )
            if (materialized == null) {
                Toast.makeText(this@ChatActivity, "Could not open $title", Toast.LENGTH_SHORT).show()
            } else {
                SecureMediaOpenHelper.open(this@ChatActivity, materialized)
            }
        }
    }

    private fun String.isOpenableAndroidUri(): Boolean {
        if (isBlank()) return false
        return startsWith("content://", ignoreCase = true) ||
            startsWith("file://", ignoreCase = true) ||
            startsWith("android.resource://", ignoreCase = true)
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun showSnapFullScreen(message: MessageRecord, uri: Uri) {
        val dialog = android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val layout = FrameLayout(this)
        val imageView = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        val tvTimer = TextView(this).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.TOP or Gravity.END
                setMargins(48, 48, 48, 48)
            }
            textSize = 24f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#88000000"))
            setPadding(16, 8, 16, 8)
        }
        layout.addView(imageView)
        layout.addView(tvTimer)
        dialog.setContentView(layout)

        try {
            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                android.graphics.ImageDecoder.decodeBitmap(android.graphics.ImageDecoder.createSource(contentResolver, uri))
            } else {
                MediaStore.Images.Media.getBitmap(contentResolver, uri)
            }
            imageView.setImageBitmap(bitmap)
        } catch(e: Exception) {
            Toast.makeText(this, "Failed to load snap", Toast.LENGTH_SHORT).show()
            return
        }

        dialog.show()

        var timer: CountDownTimer? = null
        timer = object : CountDownTimer(10000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                tvTimer.text = "${millisUntilFinished / 1000}s"
            }
            override fun onFinish() {
                dialog.dismiss()
            }
        }.start()
        
        dialog.setOnDismissListener {
            timer?.cancel()
            kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                messageDB.softDeleteMessage(message.id, localUserId)
            }
            val idx = messageAdapter.messages.indexOfFirst { it.id == message.id }
            if (idx != -1) {
                messageAdapter.messages.removeAt(idx)
                messageAdapter.notifyItemRemoved(idx)
            }
        }
    }

    private fun configureDynamicMessageScroll(header: View) {
        // Disabled dynamic effects as requested by user to keep screen steady
        /*
        fun applyEffects() {
            val recyclerCenter = messageRecyclerView.height / 2f
            for (index in 0 until messageRecyclerView.childCount) {
                val child = messageRecyclerView.getChildAt(index)
                val childCenter = (child.top + child.bottom) / 2f
                val distance = kotlin.math.abs(childCenter - recyclerCenter)
                val normalized = (distance / messageRecyclerView.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                val side = if (child.x > messageRecyclerView.width / 2f) 1f else -1f
                child.scaleX = 1f - normalized * 0.09f
                child.scaleY = 1f - normalized * 0.09f
                child.alpha = 1f - normalized * 0.22f
                child.translationX = side * normalized * dpToPx(18)
                child.rotationY = -side * normalized * 5f
            }
        }

        messageRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                header.translationY = (-recyclerView.computeVerticalScrollOffset() * 0.08f)
                    .coerceAtLeast(-dpToPx(18).toFloat())
                applyEffects()
            }
        })
        messageRecyclerView.post { applyEffects() }
        */
    }

    /**
     * Message adapter for RecyclerView
     */
    inner class MessageAdapter(
        val messages: MutableList<MessageRecord>,
        private val localUserId: String,
        private val theme: ThemeEngine.CalcVaultTheme
    ) : RecyclerView.Adapter<MessageAdapter.MessageViewHolder>() {

        private val characterHandler = CharacterMessageHandler(themeEngine) { request ->
            // Dispatch animation to engine
            executeCharacterAnimation(request)
        }

        fun addMessage(msg: MessageRecord) {
            messages.add(msg)
            notifyItemInserted(messages.size - 1)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
            val itemView = createMessageBubble(parent)
            return MessageViewHolder(itemView)
        }

        override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
            val msg = messages[position]
            val isLocal = msg.from == localUserId

            holder.bind(msg, isLocal, theme)
        }

        override fun getItemCount(): Int = messages.size

        inner class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val row = itemView as FrameLayout
            private val bubbleCard = row.findViewWithTag<CardView>("bubbleCard")
            private val textView = bubbleCard.findViewById<AppCompatTextView>(android.R.id.text1)
            private val senderView = bubbleCard.findViewById<AppCompatTextView>(android.R.id.text2)
            private val previewContainer = bubbleCard.findViewWithTag<FrameLayout>("mediaPreview")

            @SuppressLint("SetTextI18n")
            fun bind(msg: MessageRecord, isLocal: Boolean, theme: ThemeEngine.CalcVaultTheme) {
                val mediaPreview = mediaPreviewFor(msg)
                textView.text = mediaPreview?.title ?: msg.content
                senderView.text = "${if (isLocal) "You" else partnerDisplayName()} • ${formatTime(msg.timestamp)}"
                renderMediaPreview(previewContainer, mediaPreview, msg)

                // Apply character styling
                characterHandler.applyCharacterStyling(
                    message = msg,
                    bubble = bubbleCard,
                    textView = textView,
                    position = adapterPosition
                )

                bubbleCard.setOnClickListener {
                    characterHandler.applyPressAnimation(bubbleCard)
                    openMediaIfPresent(msg)
                }

                if (isLocal) {
                    bubbleCard.setCardBackgroundColor(Color.argb(205, Color.red(theme.accentColor), Color.green(theme.accentColor), Color.blue(theme.accentColor)))
                    textView.setTextColor(Color.WHITE)
                    senderView.setTextColor(Color.argb(180, 255, 255, 255))
                    (bubbleCard.layoutParams as FrameLayout.LayoutParams).apply {
                        gravity = android.view.Gravity.END
                        marginEnd = dpToPx(12)
                        marginStart = dpToPx(60)
                    }
                } else {
                    bubbleCard.setCardBackgroundColor(Color.argb(138, 70, 70, 78))
                    textView.setTextColor(Color.WHITE)
                    senderView.setTextColor(Color.argb(178, 210, 210, 220))
                    (bubbleCard.layoutParams as FrameLayout.LayoutParams).apply {
                        gravity = android.view.Gravity.START
                        marginStart = dpToPx(12)
                        marginEnd = dpToPx(60)
                    }
                }
            }
        }

        private fun createMessageBubble(parent: ViewGroup): View {
            val row = FrameLayout(this@ChatActivity).apply {
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, dpToPx(4), 0, dpToPx(4))
                }
                clipChildren = false
                clipToPadding = false
            }

            val card = CardView(this@ChatActivity).apply {
                tag = "bubbleCard"
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                radius = dpToPx(12).toFloat()
                cardElevation = dpToPx(4).toFloat()
                setContentPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8))
            }

            val container = LinearLayout(this@ChatActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }

            val textView = AppCompatTextView(this@ChatActivity).apply {
                id = android.R.id.text1
                textSize = 16f
                maxWidth = (resources.displayMetrics.widthPixels * 0.68f).toInt()
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            container.addView(textView)

            val previewContainer = FrameLayout(this@ChatActivity).apply {
                tag = "mediaPreview"
                visibility = View.GONE
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dpToPx(8)
                }
            }
            container.addView(previewContainer)

            val senderView = AppCompatTextView(this@ChatActivity).apply {
                id = android.R.id.text2
                textSize = 12f
                setPadding(0, dpToPx(4), 0, 0)
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            container.addView(senderView)

            card.addView(container)
            row.addView(card)
            return row
        }

        private fun formatTime(timestamp: Long): String {
            val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            return sdf.format(java.util.Date(timestamp))
        }
    }
}
