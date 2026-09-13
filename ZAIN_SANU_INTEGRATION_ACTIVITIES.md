# ZAIN-SANU INTEGRATION GUIDE

## 📌 Overview

This guide shows how to integrate the Zain-Sanu Storage System with existing CalcVault components (ChatActivity, CallActivity, MediaActivity).

---

## 1️⃣ App-Level Setup

### Step 1: Modify CalcVaultApplication

```kotlin
// CalcVaultApplication.kt
class CalcVaultApplication : Application() {
    companion object {
        lateinit var instance: CalcVaultApplication
            private set
    }
    
    // Storage system instance - singleton
    lateinit var storageSystem: ZainSanuStorageSystem
        private set
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        initializeStorageSystem()
    }
    
    private fun initializeStorageSystem() {
        storageSystem = ZainSanuStorageSystem(this)
        
        // Initialize asynchronously on background thread
        Thread {
            try {
                if (storageSystem.initialize()) {
                    Log.d("CalcVault", "✓ Storage system initialized")
                } else {
                    Log.e("CalcVault", "✗ Storage system initialization failed")
                }
            } catch (e: Exception) {
                Log.e("CalcVault", "✗ Storage initialization error", e)
            }
        }.start()
    }
    
    fun getStorageSystem() = storageSystem
}
```

### Step 2: Update AndroidManifest.xml

```xml
<application
    android:name=".CalcVaultApplication"
    android:allowBackup="true|false"
    ...>
    
    <!-- Add storage permissions -->
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
    <uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE" />
    
    <!-- Activities -->
    <activity android:name=".activities.ChatActivity" />
    <activity android:name=".activities.CallActivity" />
    <activity android:name=".activities.MediaActivity" />
    
</application>
```

---

## 2️⃣ ChatActivity Integration

### Modified ChatActivity.kt

```kotlin
class ChatActivity : AppCompatActivity() {
    private lateinit var storageSystem: ZainSanuStorageSystem
    private lateinit var messageAdapter: MessageAdapter
    private lateinit var messagesRecyclerView: RecyclerView
    
    // Message recording
    private var mediaRecorder: MediaRecorder? = null
    private var recordingFile: File? = null
    private var isRecording = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)
        
        // Get singleton storage system
        storageSystem = (application as CalcVaultApplication).getStorageSystem()
        
        setupUI()
        setupMessageAdapter()
        loadChatHistory()
    }
    
    // ═════════════════════════════════════════════════════════
    // RECORDING & ATTACHMENT HANDLING
    // ═════════════════════════════════════════════════════════
    
    fun startRecording() {
        recordingFile = File(cacheDir, "recording_${System.currentTimeMillis()}.m4a")
        
        mediaRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(128000)
            setAudioSamplingRate(44100)
            setOutputFile(recordingFile!!.absolutePath)
            
            try {
                prepare()
                start()
                isRecording = true
                updateRecordingUI()
            } catch (e: IOException) {
                Log.e("ChatActivity", "Failed to start recording", e)
                Toast.makeText(this, "Recording failed", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    fun stopRecording() {
        if (!isRecording) return
        
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            isRecording = false
            
            // Process completed recording
            if (recordingFile != null && recordingFile!!.exists()) {
                onRecordingComplete(recordingFile!!)
            }
        } catch (e: Exception) {
            Log.e("ChatActivity", "Failed to stop recording", e)
        }
    }
    
    private fun onRecordingComplete(file: File) {
        Log.d("ChatActivity", "Recording complete: ${file.length()} bytes")
        
        lifecycleScope.launch(Dispatchers.Default) {
            try {
                // Store through Zain-Sanu system
                val result = storageSystem.storeFile(
                    sourceFile = file,
                    currentUser = SessionManager.getLocalUserId(),  // "Zain" or "Sanu"
                    fileType = "audio"
                )
                
                runOnUiThread {
                    if (result.success) {
                        // Add message with stored file
                        val message = Message(
                            id = UUID.randomUUID().toString(),
                            text = "🎙️ Voice message",
                            attachmentPath = result.filePath,
                            attachmentThumbnail = result.thumbnailPath,
                            attachmentOwner = result.owner,
                            isRemote = !result.isLocallyStored,
                            timestamp = System.currentTimeMillis()
                        )
                        
                        messageDB.insertMessage(message)
                        messageAdapter.addMessage(message)
                        messagesRecyclerView.smoothScrollToPosition(
                            messageAdapter.itemCount - 1
                        )
                        
                        // Clear input
                        recordingButton.text = "🎤 Record"
                        
                    } else {
                        // Handle storage failure
                        AlertDialog.Builder(this@ChatActivity)
                            .setTitle("Cannot Store Recording")
                            .setMessage(result.reason)
                            .setPositiveButton("OK") { _, _ ->
                                Log.d("ChatActivity", "Storage error acknowledged: ${result.reason}")
                            }
                            .show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Log.e("ChatActivity", "Recording processing error", e)
                    Toast.makeText(this@ChatActivity, 
                        "Error processing recording: ${e.message}", 
                        Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    // ═════════════════════════════════════════════════════════
    // MESSAGE DISPLAY & PLAYBACK
    // ═════════════════════════════════════════════════════════
    
    fun onPlayAttachment(message: Message) {
        if (!message.hasAttachment) return
        
        lifecycleScope.launch(Dispatchers.IO) {
            val accessCheck = storageSystem.canAccessFile(
                userId = SessionManager.getLocalUserId(),
                fileId = message.attachmentId ?: message.id
            )
            
            runOnUiThread {
                when {
                    accessCheck.canAccess -> {
                        // File is accessible - play it
                        playFile(message.attachmentPath!!)
                    }
                    accessCheck.isRemote && !accessCheck.canAccess -> {
                        // Remote file that's locked
                        showRemoteFileLocked(
                            ownerName = accessCheck.owner,
                            fileName = message.attachmentName ?: "recording",
                            fileSize = message.attachmentSize ?: 0,
                            prompt = accessCheck.prompt
                        )
                    }
                    else -> {
                        // File not found or other error
                        Toast.makeText(this@ChatActivity, 
                            "Cannot access file: ${accessCheck.prompt}", 
                            Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }
    
    private fun showRemoteFileLocked(
        ownerName: String,
        fileName: String,
        fileSize: Long,
        prompt: String
    ) {
        AlertDialog.Builder(this)
            .setTitle("📱 Remote File")
            .setMessage("""
                File: $fileName
                Size: ${formatBytes(fileSize)}
                Owner: $ownerName
                
                $prompt
            """.trimIndent())
            .setPositiveButton("Got it") { _, _ -> }
            .setNegativeButton("Ask via message") { _, _ ->
                sendAccessRequest(ownerName, fileName)
            }
            .show()
    }
    
    private fun playFile(filePath: String) {
        val mediaPlayer = MediaPlayer().apply {
            setDataSource(filePath)
            setOnPreparedListener { start() }
            setOnCompletionListener { release() }
            prepareAsync()
        }
    }
    
    private fun formatBytes(bytes: Long): String {
        return storageSystem.quotaManager.formatSize(bytes)
    }
    
    private fun sendAccessRequest(ownerName: String, fileName: String) {
        val message = Message(
            id = UUID.randomUUID().toString(),
            text = "Can you connect your phone to help me access $fileName?",
            timestamp = System.currentTimeMillis()
        )
        messageDB.insertMessage(message)
        messageAdapter.addMessage(message)
    }
    
    private fun setupMessageAdapter() {
        messageAdapter = MessageAdapter(
            onPlayClicked = { message -> onPlayAttachment(message) },
            onDownloadClicked = { message -> onDownloadAttachment(message) }
        )
        messagesRecyclerView.adapter = messageAdapter
    }
    
    private fun onDownloadAttachment(message: Message) {
        // Download from PC if available
        if (storageSystem.remoteFileRefManager.getRef(message.id) != null) {
            downloadFromPC(message)
        }
    }
    
    private fun downloadFromPC(message: Message) {
        // Implementation for downloading from PC backup
    }
    
    private fun loadChatHistory() {
        // Load messages from database
    }
    
    private fun setupUI() {
        // Setup views
    }
    
    private fun updateRecordingUI() {
        // Update UI during recording
    }
}
```

---

## 3️⃣ CallActivity Integration

### Modified CallActivity.kt

```kotlin
class CallActivity : AppCompatActivity() {
    private lateinit var storageSystem: ZainSanuStorageSystem
    
    private var isRecordingCall = false
    private var callRecordingFile: File? = null
    private var volumeRecorder: AudioRecord? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        storageSystem = (application as CalcVaultApplication).getStorageSystem()
    }
    
    // ═════════════════════════════════════════════════════════
    // CALL RECORDING
    // ═════════════════════════════════════════════════════════
    
    fun startCallRecording() {
        callRecordingFile = File(getExternalFilesDir("recordings"), 
            "call_${System.currentTimeMillis()}.mp4")
        isRecordingCall = true
        
        // Start recording (implementation depends on your call framework)
        Log.d("CallActivity", "Call recording started")
    }
    
    fun stopCallRecording() {
        if (!isRecordingCall) return
        
        isRecordingCall = false
        
        if (callRecordingFile != null && callRecordingFile!!.exists()) {
            onCallRecordingComplete(callRecordingFile!!)
        }
    }
    
    private fun onCallRecordingComplete(file: File) {
        Log.d("CallActivity", "Call recording complete: ${file.name} (${file.length()} bytes)")
        
        lifecycleScope.launch(Dispatchers.Default) {
            try {
                // Store through Zain-Sanu system
                val result = storageSystem.storeFile(
                    sourceFile = file,
                    currentUser = SessionManager.getLocalUserId(),
                    fileType = "video"  // Call recordings are video
                )
                
                runOnUiThread {
                    if (result.success) {
                        Log.d("CallActivity", "✓ Call recording stored successfully")
                        
                        // Save metadata to database
                        val recording = CallRecording(
                            id = UUID.randomUUID().toString(),
                            callId = getCurrentCallId(),
                            fileName = file.name,
                            filePath = result.filePath,
                            thumbnailPath = result.thumbnailPath,
                            fileSize = file.length(),
                            owner = result.owner,
                            isRemote = !result.isLocallyStored,
                            timestamp = System.currentTimeMillis()
                        )
                        
                        callDB.insertRecording(recording)
                        
                        // Show notification
                        showRecordingSaveNotification(recording)
                        
                    } else {
                        Log.e("CallActivity", "Failed to store recording: ${result.reason}")
                        
                        // Show error dialog
                        AlertDialog.Builder(this@CallActivity)
                            .setTitle("Storage Error")
                            .setMessage("Could not save call recording:\n${result.reason}")
                            .setPositiveButton("OK", null)
                            .show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Log.e("CallActivity", "Call recording storage error", e)
                    Toast.makeText(this@CallActivity,
                        "Error saving call: ${e.message}",
                        Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    // ═════════════════════════════════════════════════════════
    // PLAYBACK
    // ═════════════════════════════════════════════════════════
    
    fun playCallRecording(recordingId: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val recording = callDB.getRecording(recordingId) ?: return@launch
            
            val accessCheck = storageSystem.canAccessFile(
                userId = SessionManager.getLocalUserId(),
                fileId = recordingId
            )
            
            runOnUiThread {
                if (accessCheck.canAccess) {
                    // Can play - file is accessible
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(Uri.fromFile(File(recording.filePath)), "video/mp4")
                    }
                    startActivity(intent)
                    
                } else if (accessCheck.isRemote) {
                    // Remote recording - show locked message
                    showRecordingLockedDialog(
                        recording.fileName,
                        recording.fileSize,
                        accessCheck.owner
                    )
                } else {
                    // File not found
                    Toast.makeText(this@CallActivity,
                        "Call recording not found",
                        Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun showRecordingSaveNotification(recording: CallRecording) {
        val message = if (recording.isRemote) {
            "${recording.fileName} stored on ${recording.owner}'s device"
        } else {
            "${recording.fileName} saved locally"
        }
        
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
    
    private fun showRecordingLockedDialog(
        fileName: String,
        fileSize: Long,
        ownerName: String
    ) {
        AlertDialog.Builder(this)
            .setTitle("📱 Call Recording on ${ownerName}'s Device")
            .setMessage("""
                Recording: $fileName
                Size: ${storageSystem.quotaManager.formatSize(fileSize)}
                
                This recording is stored on $ownerName's device.
                Ask them to connect to your PC to access it.
            """.trimIndent())
            .setPositiveButton("OK", null)
            .show()
    }
    
    private fun getCurrentCallId(): String {
        // Return current call ID from call framework
        return ""
    }
}

// Data model
data class CallRecording(
    val id: String,
    val callId: String,
    val fileName: String,
    val filePath: String,
    val thumbnailPath: String?,
    val fileSize: Long,
    val owner: String,
    val isRemote: Boolean,
    val timestamp: Long
)
```

---

## 4️⃣ MediaActivity Integration

### Modified MediaActivity.kt

```kotlin
class MediaActivity : AppCompatActivity() {
    private lateinit var storageSystem: ZainSanuStorageSystem
    private lateinit var mediaAdapter: MediaAdapter
    private lateinit var mediaRecyclerView: RecyclerView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_media)
        
        storageSystem = (application as CalcVaultApplication).getStorageSystem()
        
        setupUI()
        loadMedia()
    }
    
    // ═════════════════════════════════════════════════════════
    // MEDIA LOADING & DISPLAY
    // ═════════════════════════════════════════════════════════
    
    private fun loadMedia() {
        lifecycleScope.launch(Dispatchers.IO) {
            val mediaItems = mutableListOf<MediaItem>()
            
            // Load local media
            val localFiles = getLocalMediaFiles()
            localFiles.forEach { file ->
                val owner = storageSystem.ownershipManager.determineOwner(file)
                mediaItems.add(
                    MediaItem(
                        id = file.absolutePath,
                        name = file.name,
                        path = file.absolutePath,
                        size = file.length(),
                        owner = owner.toString(),
                        isRemote = false,
                        thumbnail = getThumbnailForFile(file),
                        canAccess = true
                    )
                )
            }
            
            // Load remote references
            val remoteRefs = storageSystem.remoteFileRefManager.getAllRefs()
            remoteRefs.forEach { ref ->
                mediaItems.add(
                    MediaItem(
                        id = ref.id,
                        name = ref.name,
                        path = "", // Remote file
                        size = ref.size,
                        owner = ref.owner,
                        isRemote = true,
                        thumbnail = ref.thumbnailPath,
                        canAccess = ref.accessState == UserOwnershipManager.AccessState.AVAILABLE
                    )
                )
            }
            
            runOnUiThread {
                mediaAdapter.submitList(mediaItems)
            }
        }
    }
    
    private fun getLocalMediaFiles(): List<File> {
        val mediaFiles = mutableListOf<File>()
        
        // Scan all storage directories
        val storageRoot = File(getExternalFilesDir(null)?.parent, "calcvault/storage")
        if (storageRoot.exists()) {
            storageRoot.walk().forEach { file ->
                if (file.isFile && isMediaFile(file)) {
                    mediaFiles.add(file)
                }
            }
        }
        
        return mediaFiles
    }
    
    private fun isMediaFile(file: File): Boolean {
        val mediaExtensions = setOf("mp3", "wav", "m4a", "mp4", "mkv", "mov", 
                                   "jpg", "png", "gif", "webp", "pdf", "doc", "docx")
        return file.extension.lowercase() in mediaExtensions
    }
    
    private fun getThumbnailForFile(file: File): String? {
        return try {
            val thumbnail = storageSystem.thumbnailGenerator.generateThumbnail(file)
            thumbnail?.path
        } catch (e: Exception) {
            Log.w("MediaActivity", "Failed to generate thumbnail for ${file.name}", e)
            null
        }
    }
    
    // ═════════════════════════════════════════════════════════
    // USER INTERACTION
    // ═════════════════════════════════════════════════════════
    
    fun onMediaItemClicked(item: MediaItem) {
        if (item.isRemote) {
            handleRemoteMediaClick(item)
        } else {
            handleLocalMediaClick(item)
        }
    }
    
    private fun handleLocalMediaClick(item: MediaItem) {
        lifecycleScope.launch(Dispatchers.IO) {
            val accessCheck = storageSystem.canAccessFile(
                userId = SessionManager.getLocalUserId(),
                fileId = item.id
            )
            
            runOnUiThread {
                if (accessCheck.canAccess) {
                    openMediaFile(item.path)
                } else {
                    Toast.makeText(this@MediaActivity,
                        "Cannot access: ${accessCheck.prompt}",
                        Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    private fun handleRemoteMediaClick(item: MediaItem) {
        // Show detailed view for remote file
        showRemoteMediaDetail(item)
    }
    
    private fun showRemoteMediaDetail(item: MediaItem) {
        AlertDialog.Builder(this)
            .setTitle(item.name)
            .setMessage("""
                Owner: ${item.owner}
                Size: ${storageSystem.quotaManager.formatSize(item.size)}
                Status: ${if (item.canAccess) "Available" else "Locked"}
                
                ${if (!item.canAccess) "Ask ${item.owner} to connect to PC" else "Ready to access"}
            """.trimIndent())
            .setPositiveButton(if (item.canAccess) "Open" else "OK") { _, _ ->
                if (item.canAccess) {
                    // Open remote file
                }
            }
            .show()
    }
    
    private fun openMediaFile(path: String) {
        val uri = Uri.fromFile(File(path))
        val mimeType = getMimeType(path)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
        }
        startActivity(intent)
    }
    
    private fun getMimeType(path: String): String {
        return when (path.substringAfterLast('.').lowercase()) {
            "mp3", "wav", "m4a" -> "audio/*"
            "mp4", "mkv", "mov" -> "video/*"
            "jpg", "png", "gif" -> "image/*"
            "pdf" -> "application/pdf"
            else -> "application/octet-stream"
        }
    }
    
    private fun setupUI() {
        mediaAdapter = MediaAdapter(
            onItemClicked = { item -> onMediaItemClicked(item) },
            onDeleteClicked = { item -> onDeleteMedia(item) }
        )
        mediaRecyclerView.adapter = mediaAdapter
    }
    
    private fun onDeleteMedia(item: MediaItem) {
        // Handle deletion (with appropriate permissions)
    }
}

// Data model
data class MediaItem(
    val id: String,
    val name: String,
    val path: String,
    val size: Long,
    val owner: String,
    val isRemote: Boolean,
    val thumbnail: String?,
    val canAccess: Boolean
)
```

---

## 5️⃣ Storage Settings/Dashboard

### StorageSettingsFragment.kt

```kotlin
class StorageSettingsFragment : Fragment() {
    private lateinit var storageSystem: ZainSanuStorageSystem
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        storageSystem = (requireActivity().application as CalcVaultApplication).getStorageSystem()
        
        setupStorageMetrics()
        setupBackupButton()
        setupCleanupButton()
    }
    
    private fun setupStorageMetrics() {
        lifecycleScope.launch(Dispatchers.IO) {
            val summary = storageSystem.getSystemSummary()
            
            runOnUiThread {
                metricsTextView.text = summary
            }
        }
    }
    
    private fun setupBackupButton() {
        backupButton.setOnClickListener {
            if (storageSystem.pcBackupManager.isConnectedToPC()) {
                showBackupDialog()
            } else {
                showNoPCDialog()
            }
        }
    }
    
    private fun showBackupDialog() {
        val progressDialog = ProgressDialog(requireContext()).apply {
            setTitle("Backing up Calcvault")
            setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
            setCancelable(false)
            show()
        }
        
        lifecycleScope.launch(Dispatchers.IO) {
            val result = storageSystem.performPCBackup { progress ->
                runOnUiThread {
                    progressDialog.progress = progress.getProgressPercentage()
                    progressDialog.setMessage(
                        "${progress.currentFile}\n${storageSystem.quotaManager.formatSize(progress.processedBytes)} / " +
                        "${storageSystem.quotaManager.formatSize(progress.totalBytes)}"
                    )
                }
            }
            
            runOnUiThread {
                progressDialog.dismiss()
                
                if (result.success) {
                    AlertDialog.Builder(requireContext())
                        .setTitle("Backup Complete")
                        .setMessage("""
                            Files backed up: ${result.filesBackedUp}
                            Total size: ${storageSystem.quotaManager.formatSize(result.bytesBackedUp)}
                        """.trimIndent())
                        .setPositiveButton("OK", null)
                        .show()
                } else {
                    showError("Backup failed", result.errors.first())
                }
            }
        }
    }
    
    private fun showNoPCDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("PC Not Connected")
            .setMessage("Connect your phone to a PC via USB to backup files.")
            .setPositiveButton("OK", null)
            .show()
    }
    
    private fun setupCleanupButton() {
        cleanupButton.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                val result = storageSystem.quotaManager.cleanupOrphaned(daysOld = 30)
                
                runOnUiThread {
                    Toast.makeText(
                        requireContext(),
                        "Cleaned up ${result.filesRemoved} orphaned files",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
    
    private fun showError(title: String, message: String) {
        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }
}
```

---

## ✅ Integration Checklist

- [ ] Create CalcVaultApplication class with storageSystem singleton
- [ ] Update AndroidManifest.xml with storage permissions
- [ ] Modify ChatActivity to use storeFile() for recordings
- [ ] Add playback support with canAccessFile() checks
- [ ] Implement CallActivity recording storage
- [ ] Add call recording access control
- [ ] Create MediaActivity with local + remote file browsing
- [ ] Implement StorageSettingsFragment with backup UI
- [ ] Add PC backup progress tracking
- [ ] Handle quota exceeded scenarios
- [ ] Implement remote file locking UI
- [ ] Add storage metrics dashboard

---

## 🔗 Data Model Adjustments

### Message.kt

```kotlin
data class Message(
    val id: String,
    val fromUserId: String,
    val toUserId: String,
    val text: String,
    val timestamp: Long,
    
    // Attachment info
    val attachmentId: String? = null,
    val attachmentPath: String? = null,
    val attachmentName: String? = null,
    val attachmentSize: Long? = null,
    val attachmentThumbnail: String? = null,
    val attachmentOwner: String? = null,
    val isRemote: Boolean = false,
    
    // Metadata
    val isRead: Boolean = false,
    val archived: Boolean = false
) {
    val hasAttachment: Boolean
        get() = attachmentId != null
}
```

### CallRecording.kt

```kotlin
data class CallRecording(
    val id: String,
    val callId: String,
    val callerId: String,
    val calleeId: String,
    val startTime: Long,
    val duration: Long,
    
    val fileName: String,
    val filePath: String,
    val fileSize: Long,
    val thumbnailPath: String?,
    
    val owner: String,              // Zain or Sanu
    val isRemote: Boolean,          // Stored on another device
    val accessState: String,        // LOCKED, AVAILABLE, etc.
    
    val createdAt: Long,
    val transcription: String? = null
)
```

---

## 📊 Example Data Flow

```
User Action: Chat.recordVoiceMessage()
    ↓
Call: storageSystem.storeFile(recordingFile, "Sanu", "audio")
    ↓
Ownership Check:
    - File size: 45 MB (< 1GB)
    - Can store as Sanu: YES
    ↓
Result: {
    success: true,
    filePath: "/calcvault/storage/Sanu/media/audio/recording_123.m4a",
    owner: "SANU",
    isLocallyStored: true,
    thumbnailPath: "/calcvault/thumbnails/recording_123.jpg"
}
    ↓
Display in Chat:
    - Show message with thumbnail
    - Mark as "Local" (not remote)
    - Enable playback button
    ↓
User Action: Message.play()
    ↓
Call: storageSystem.canAccessFile("Sanu", "recording_123")
    ↓
Result: {
    canAccess: true,
    isRemote: false,
    owner: "SANU"
}
    ↓
Play: MediaPlayer.setDataSource(filePath); play()
```

---

**Status**: ✅ COMPLETE - Ready for implementation
