# ZAIN-SANU STORAGE - Quick Start & Examples

## 🚀 Quick Start (5 Minutes)

### 1. Initialize in App Startup

```kotlin
class CalcVaultApp : Application() {
    lateinit var storageSystem: ZainSanuStorageSystem

    override fun onCreate() {
        super.onCreate()
        storageSystem = ZainSanuStorageSystem(this)
        // Initialize in coroutine
        lifecycleScope.launch(Dispatchers.IO) {
            if (storageSystem.initialize()) {
                Log.d("CalcVault", "Storage system ready")
            }
        }
    }
}
```

### 2. Store a File

```kotlin
// In ChatActivity when saving a recording
val recordingFile = File(cacheDir, "call_recording.mp3")
recordingFile.writeBytes(audioBytes)

val result = storageSystem.storeFile(
    sourceFile = recordingFile,
    currentUser = SessionManager.localUserId,  // "Zain" or "Sanu"
    fileType = "audio"
)

if (result.success) {
    // Display with thumbnail
    Glide.with(this)
        .load(result.thumbnailPath)
        .into(thumbnailImageView)
    
    // Save path to database
    messageDB.saveAttachmentPath(messageId, result.filePath)
}
```

### 3. Load Backup Index

```kotlin
// When Zain connects phone to PC
val backupIndex = storageSystem.backupIndexer.loadIndex("/CalcvaultBackup")
if (backupIndex != null) {
    Log.d("Backup", "Index loaded: ${backupIndex.files.size} files")
    
    // Display in UI
    val largeFiles = storageSystem.backupIndexer.getLargeFiles(backupIndex)
    listAdapter.submitList(largeFiles)
}
```

### 4. Check File Access

```kotlin
// When Sanu tries to play a large file
val accessCheck = storageSystem.canAccessFile(
    userId = SessionManager.localUserId,
    fileId = messageAttachment.fileId
)

if (accessCheck.canAccess) {
    playFile(messageAttachment.filePath)
} else {
    showSnackbar(accessCheck.prompt)
}
```

---

## 📋 Use Cases & Examples

### Use Case 1: Sanu Records a 3GB Video Call

```kotlin
// In CallActivity, after call ends
val callRecording = File(getExternalFilesDir("recordings"), "call_video.mp4")
// ... write video data ...

val result = storageSystem.storeFile(
    sourceFile = callRecording,
    currentUser = "Sanu",
    fileType = "video"
)

when {
    result.success -> {
        // File > 1GB detected
        // Routed to: /calcvault/storage/Zain/large_media/video/call_video.mp4
        // Reason: Zain is primary storage authority
        
        // Sanu gets metadata reference:
        // RemoteFileRef {
        //   id: "file_xyz",
        //   name: "call_video.mp4",
        //   size: 3_000_000_000,
        //   owner: "Zain",
        //   accessState: LOCKED,
        //   thumbnailPath: (first frame)
        // }
        
        chatAdapter.addMessageWithAttachment(
            text = "Call recording (3.0 GB) - Stored on my device",
            thumbnail = result.thumbnailPath,
            isRemote = true
        )
    }
    !result.success -> {
        Toast.makeText(this, result.reason, Toast.LENGTH_LONG).show()
    }
}
```

### Use Case 2: Zain Connects to PC - Backup Initiation

```kotlin
// In SettingsActivity, detect USB connection
if (storageSystem.pcBackupManager.isConnectedToPC()) {
    // Show option
    showBackupDialog()
}

// User clicks "Backup Now"
val progressDialog = ProgressDialog(this).apply {
    setTitle("Backing up Calcvault")
    setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
    max = 100
    show()
}

lifecycleScope.launch(Dispatchers.IO) {
    val result = storageSystem.performPCBackup { progress ->
        runOnUiThread {
            progressDialog.progress = progress.getProgressPercentage()
            progressDialog.setMessage("${progress.currentFile}\n${formatSize(progress.processedBytes)} / ${formatSize(progress.totalBytes)}")
        }
    }
    
    runOnUiThread {
        progressDialog.dismiss()
        
        if (result.success) {
            AlertDialog.Builder(this@SettingsActivity)
                .setTitle("Backup Complete")
                .setMessage("${result.filesBackedUp} files backed up\nSize: ${formatSize(result.bytesBackedUp)}")
                .setPositiveButton("OK", null)
                .show()
        } else {
            showError("Backup failed: ${result.errors.first()}")
        }
    }
}
```

### Use Case 3: Sanu Wants to Access Zain's Large File

```kotlin
// In MessageDetailActivity, Sanu clicks on remote file
val message = messageDB.getMessage(messageId)
val attachment = message.attachment

val accessCheck = storageSystem.canAccessFile("Sanu", attachment.fileId)

if (accessCheck.isRemote && !accessCheck.canAccess) {
    // Show locked state
    showLockedFileDialog(
        fileName = attachment.name,
        fileSize = attachment.size,
        owner = accessCheck.owner,
        prompt = accessCheck.prompt
    )
    // UI displays:
    // "File: call_recording.mp3 (2.0 GB)"
    // "Owner: Zain"
    // "Status: Stored on Zain's device"
    // [ASK ZAIN TO CONNECT PHONE TO PC]
} else if (accessCheck.canAccess) {
    // Show available state
    playFile(attachment.filePath)
}
```

### Use Case 4: Browse Backup Index

```kotlin
// When browsing backed-up files
val backupIndex = storageSystem.backupIndexer.loadIndex()

if (backupIndex != null) {
    // Show all files
    updateUI(backupIndex.files)
    
    // Or show large files only
    val largeFiles = storageSystem.backupIndexer.getLargeFiles(backupIndex)
    updateUI(largeFiles)
    
    // Or search
    val searchResults = storageSystem.backupIndexer.searchIndex(
        backupIndex,
        query = "call"
    )
    updateUI(searchResults)
    
    // Show stats
    val summary = storageSystem.backupIndexer.getSummary(backupIndex)
    // Output:
    // Backup Index Summary
    // ════════════════════════════════════════
    // Version: 1.0
    // Generated: 1712345678
    // Location: /CalcvaultBackup
    // 
    // Files: 1250
    // Total Size: 487.3 GB
    // Large Files: 180
    // 
    // By Type:
    //   audio: 450 files (87.2 GB)
    //   video: 280 files (350.1 GB)
    //   image: 520 files (50.0 GB)
}
```

### Use Case 5: Monitor Storage Quotas

```kotlin
// In StorageSettingsFragment
lifecycleScope.launch(Dispatchers.IO) {
    val quotas = storageSystem.quotaManager.getAllQuotas()
    
    runOnUiThread {
        // Update views for each user
        quotas.forEach { (owner, quota) ->
            updateQuotaView(
                ownerName = owner,
                totalQuota = storageSystem.quotaManager.formatSize(quota.totalQuota),
                usedSpace = storageSystem.quotaManager.formatSize(quota.usedSpace),
                freeSpace = storageSystem.quotaManager.formatSize(quota.availableSpace),
                percentUsed = quota.percentUsed
            )
        }
    }
}
```

### Use Case 6: Check if Backup Available

```kotlin
// Determine if files can be accessed locally or need backup
val status = storageSystem.getSystemStatus()

if (status.backupExists && status.backupIndexExists) {
    // All backed-up files are available
    storageSystem.remoteFileRefManager.getAllRefs().forEach { ref ->
        storageSystem.remoteFileRefManager.updateAccessState(
            ref.id,
            UserOwnershipManager.AccessState.AVAILABLE
        )
    }
    
    showNotification("All remote files now available!")
}
```

---

## 🎨 UI Integration Examples

### Remote File Card UI

```kotlin
// In MessageAdapter
if (message.isRemoteAttachment) {
    val remoteFile = storageSystem.remoteFileRefManager.getRef(message.attachmentId)
    
    remoteFileCard.apply {
        setTitle(remoteFile?.name ?: "Unknown")
        setSize(formatSize(remoteFile?.size ?: 0))
        setOwner(remoteFile?.owner ?: "Unknown")
        setThumbnail(remoteFile?.thumbnailPath)
        
        when (remoteFile?.accessState) {
            UserOwnershipManager.AccessState.LOCKED -> {
                setStatus("🔒 Stored on ${remoteFile.owner}'s device")
                setActionButton("Ask to Connect", {
                    showAccessPrompt(remoteFile.owner)
                })
            }
            UserOwnershipManager.AccessState.AVAILABLE -> {
                setStatus("✓ Available")
                setActionButton("Open", {
                    openFile(remoteFile.id)
                })
            }
            else -> {}
        }
    }
}
```

### Storage Status Dashboard

```kotlin
// In StatsActivity
lifecycleScope.launch(Dispatchers.IO) {
    val summary = storageSystem.getSystemSummary()
    
    runOnUiThread {
        summaryTextView.text = summary
        
        // Looks like:
        // ╔════════════════════════════════════════════════════════════╗
        // ║      ZAIN-SANU STORAGE SYSTEM STATUS                       ║
        // ╚════════════════════════════════════════════════════════════╝
        // 
        // Zain: 247.3 GB / 500.0 GB (49.5%)
        // Sanu: 73.2 GB / 100.0 GB (73.2%)
        // shared: 32.1 GB / 50.0 GB (64.2%)
        // ...
    }
}
```

---

## 🔧 Integration Checklist

- [ ] Initialize `ZainSanuStorageSystem` in App startup
- [ ] Use `storeFile()` when saving recordings/media
- [ ] Use `canAccessFile()` before opening remote files
- [ ] Implement thumbnail display for backup index
- [ ] Show quota status in settings
- [ ] Implement PC backup trigger
- [ ] Sync backup index with remote references
- [ ] Display locked file prompt to Sanu
- [ ] Log storage operations for debugging

---

## 📊 Data Flow Diagram

```
Sanu Records 2GB Video
    │
    ├─→ ZainSanuStorageSystem.storeFile()
    │
    ├─→ checkFileSize() → > 1GB
    │
    ├─→ determineOwner() → ZAIN
    │
    ├─→ canStore() → false (Sanu can't store as Zain)
    │
    ├─→ createRemoteReference()
    │   │
    │   └─→ RemoteFileRefManager.addRef()
    │       └─→ Saved in remote_refs.json
    │
    └─→ Result: {success: false, reason: "Stored as remote reference"}

Zain Connects to PC
    │
    ├─→ PCBackupManager.performPCBackup()
    │
    ├─→ scanDirectory() → 1250 files found
    │
    ├─→ copyFiles() → /CalcvaultBackup/
    │
    ├─→ generateThumbnails() → /CalcvaultBackup/thumbnails/
    │
    ├─→ createIndexedFileList() → List<IndexedFile>
    │
    ├─→ BackupIndexer.generateAndSaveIndex() → index.json
    │
    └─→ RemoteFileRefManager.syncWithBackupIndex()
        └─→ Update all refs to AccessState.AVAILABLE
```

---

## 🚨 Error Handling

```kotlin
// Storage denied
result.reason = "Insufficient quota: 2.0 GB > 0.5 GB available"

// Backup failed
result.errors = ["Failed to backup file.mp4: permission denied",
                 "Failed to backup file2.mp3: insufficient space"]

// Access denied
accessCheck.prompt = "Ask Zain to connect their phone to PC"
```

---

## 📱 Sample Activity Integration

```kotlin
class ChatActivity : AppCompatActivity() {
    private lateinit var storageSystem: ZainSanuStorageSystem
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        storageSystem = (application as CalcVaultApp).storageSystem
    }
    
    private fun onRecordingComplete(file: File) {
        lifecycleScope.launch(Dispatchers.Default) {
            val result = storageSystem.storeFile(
                sourceFile = file,
                currentUser = SessionManager.localUserId,
                fileType = "audio"
            )
            
            runOnUiThread {
                if (result.success) {
                    addMessageWithAttachment(
                        text = "Voice message",
                        filePath = result.filePath,
                        thumbnail = result.thumbnailPath,
                        owner = result.owner
                    )
                } else {
                    Toast.makeText(this, result.reason, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    private fun onPlayAttachment(fileId: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val canAccess = storageSystem.canAccessFile(
                SessionManager.localUserId,
                fileId
            )
            
            runOnUiThread {
                if (canAccess.canAccess) {
                    playFile(fileId)
                } else {
                    showPrompt(canAccess.prompt)
                }
            }
        }
    }
}
```

---

## 🎯 Best Practices

1. **Always check quota before large operations**
   ```kotlin
   val canStore = quotaManager.hasSpace(fileSize, "Zain")
   ```

2. **Generate thumbnails asynchronously**
   ```kotlin
   thumbnailGenerator.generateThumbnail(file) { thumbnail ->
       // Update UI on main thread
   }
   ```

3. **Batch backup operations**
   ```kotlin
   val result = performPCBackup { progress ->
       updateProgress(progress)
   }
   ```

4. **Cache access checks**
   ```kotlin
   val accessCache = mutableMapOf<String, AccessCheckResult>()
   ```

5. **Periodically cleanup orphaned files**
   ```kotlin
   scheduleDaily {
       quotaManager.cleanupOrphaned(daysOld = 30)
   }
   ```

---

**Status**: ✅ COMPLETE - Ready for implementation
