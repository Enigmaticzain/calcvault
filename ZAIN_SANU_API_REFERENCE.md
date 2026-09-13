# ZAIN-SANU STORAGE - API Reference & Data Models

## 📚 Complete API Reference

---

## ZainSanuStorageSystem (Main Orchestrator)

### Initialization

```kotlin
// Single instance at app start
val storageSystem = ZainSanuStorageSystem(context)

// Initialize all components (async)
val success = storageSystem.initialize(): Boolean
```

### Core Operations

#### storeFile()
Store a file with automatic ownership determination and routing.

```kotlin
suspend fun storeFile(
    sourceFile: File,
    currentUser: String,           // "Zain" or "Sanu"
    fileType: String              // "audio", "video", "image", "document", "archive"
): FileStorageResult

// Returns:
data class FileStorageResult(
    val success: Boolean,
    val filePath: String?,         // Final stored path
    val owner: String?,            // "Zain" or "Sanu"
    val size: Long = 0,
    val thumbnailPath: String?,
    val isLocallyStored: Boolean,
    val reason: String?            // Error message if failed
)
```

**Example:**
```kotlin
val result = storageSystem.storeFile(
    sourceFile = File("/tmp/recording.m4a"),
    currentUser = "Sanu",
    fileType = "audio"
)

if (result.success) {
    val path = result.filePath  // e.g., "/calcvault/storage/Sanu/media/audio/recording.m4a"
    val owner = result.owner    // "SANU"
}
```

#### canAccessFile()
Check if user can access a file.

```kotlin
suspend fun canAccessFile(
    userId: String,        // "Zain" or "Sanu"
    fileId: String
): AccessCheckResult

// Returns:
data class AccessCheckResult(
    val canAccess: Boolean,
    val owner: String?,
    val isRemote: Boolean,
    val prompt: String
)
```

**Example:**
```kotlin
val access = storageSystem.canAccessFile("Sanu", "file_123")
if (access.canAccess) {
    playFile(filePath)
} else {
    showPrompt(access.prompt)  // "Ask Zain to connect to PC"
}
```

#### performPCBackup()
Trigger PC backup process.

```kotlin
suspend fun performPCBackup(
    progressCallback: (progress: BackupProgress) -> Unit = {}
): BackupResult

// Returns:
data class BackupResult(
    val success: Boolean,
    val filesBackedUp: Int,
    val bytesBackedUp: Long,
    val errors: List<String>,
    val indexPath: String?,
    val duration: Long
)
```

**Example:**
```kotlin
val result = storageSystem.performPCBackup { progress ->
    updateProgressBar(progress.getProgressPercentage())
}

if (result.success) {
    Log.d("Backup", "Backed up ${result.filesBackedUp} files")
}
```

#### getSystemStatus()
Get overall system status.

```kotlin
fun getSystemStatus(): SystemStatus

// Returns:
data class SystemStatus(
    val isInitialized: Boolean,
    val storageRoot: String,
    val backupRoot: String,
    val backupExists: Boolean,
    val backupIndexExists: Boolean,
    val totalStorageUsed: Long,
    val storagePaths: Map<String, String>
)
```

#### getSystemSummary()
Get formatted system summary for display.

```kotlin
suspend fun getSystemSummary(): String

// Example output:
/*
╔════════════════════════════════════════════════════════════╗
║      ZAIN-SANU STORAGE SYSTEM STATUS                       ║
╚════════════════════════════════════════════════════════════╝

Zain: 247.3 GB / 500.0 GB (49.5%)
  Audio: 87.2 GB (150 files)
  Video: 150.1 GB (35 files)
  Image: 10.0 GB (2500 files)

Sanu: 73.2 GB / 100.0 GB (73.2%)
  Audio: 23.1 GB (220 files)
  Video: 35.0 GB (8 files)
  Image: 15.1 GB (1200 files)

Shared: 32.1 GB / 50.0 GB (64.2%)
  Documents: 32.1 GB (145 files)

Remote Files: 18 references
Backup: Available (487.3 GB)
*/
```

---

## UserOwnershipManager

### File Owner Enum

```kotlin
enum class FileOwner {
    ZAIN,     // Files >  1GB
    SANU,     // Files <= 1GB
    SHARED    // Common content
}
```

### File Ownership Model

```kotlin
data class FileOwnership(
    val fileId: String,
    val fileName: String,
    val size: Long,
    val owner: FileOwner,
    val path: String,
    val accessibleBy: List<String>,  // ["Zain", "Sanu"] or just ["Zain"]
    val createdAt: Long,
    val lastAccessedAt: Long?
)

data class RemoteFileRef(
    val id: String,
    val name: String,
    val size: Long,
    val owner: String,           // "Zain" or "Sanu"
    val accessState: AccessState, // LOCKED, AVAILABLE, SYNCING, NOT_FOUND
    val thumbnailPath: String?,
    val originalPath: String,
    val remoteDevice: String = "PrimaryPhone"
)

enum class AccessState {
    LOCKED,        // File exists on remote device, cannot access
    AVAILABLE,     // File available in PC backup
    SYNCING,       // Currently syncing
    NOT_FOUND      // File not found
}
```

### Methods

```kotlin
// Determine file owner based on size
fun determineOwner(file: File): FileOwner

// Check if user can access file directly
fun canAccessDirectly(userId: String, fileOwner: FileOwner): Boolean

// Get access prompt message
fun getAccessPrompt(userId: String, fileOwner: FileOwner): String

// Check file size threshold
fun isLargeFile(sizeBytes: Long): Boolean
```

---

## BackupIndexer

### Data Models

```kotlin
data class IndexedFile(
    val id: String,
    val name: String,
    val path: String,
    val size: Long,
    val owner: String,
    val type: String,              // "audio", "video", etc.
    val thumbnail: String?,
    val modified: Long,
    val checksum: String?
)

data class BackupIndex(
    val version: String = "1.0",
    val generatedAt: Long,
    val location: String,
    val statistics: IndexStatistics,
    val files: List<IndexedFile>
)

data class IndexStatistics(
    val totalFiles: Int,
    val totalSize: Long,
    val largeFilesCount: Int,
    val totalByType: Map<String, Int>,  // {"audio": 450, "video": 280}
    val totalByOwner: Map<String, Int>  // {"Zain": 300, "Sanu": 450}
)
```

### Methods

```kotlin
// Generate and save index
suspend fun generateAndSaveIndex(
    backupPath: String,
    files: List<File>? = null
): BackupIndex?

// Load existing index
fun loadIndex(backupPath: String): BackupIndex?

// Search index
fun searchIndex(index: BackupIndex, query: String): List<IndexedFile>

// Get large files only
fun getLargeFiles(index: BackupIndex): List<IndexedFile>

// Get files by owner
fun getFilesByOwner(index: BackupIndex, owner: String): List<IndexedFile>

// Calculate statistics
fun calculateStatistics(files: List<IndexedFile>): IndexStatistics

// Get formatted summary
fun getSummary(index: BackupIndex): String
```

---

## ThumbnailGenerator

### File Type Detection

```kotlin
enum class FileType {
    AUDIO,
    VIDEO,
    IMAGE,
    DOCUMENT,
    ARCHIVE,
    UNKNOWN
}
```

### Methods

```kotlin
// Generate thumbnail (auto-detects type)
fun generateThumbnail(file: File): Bitmap?

// Generate video thumbnail (first frame)
fun generateVideoThumbnail(file: File): Bitmap?

// Generate image thumbnail (scaled)
fun generateImageThumbnail(file: File): Bitmap?

// Generate audio thumbnail (waveform or icon)
fun generateAudioThumbnail(file: File): Bitmap?

// Check if file supports thumbnails
fun supportsThumbnail(file: File): Boolean

// Get MIME type
fun getMimeType(file: File): String
```

### Dimensions

```kotlin
const val THUMBNAIL_WIDTH = 200
const val THUMBNAIL_HEIGHT = 200
const val AUDIO_WAVEFORM_HEIGHT = 100
const val AUDIO_WAVEFORM_WIDTH = 300
```

---

## PCBackupManager

### Data Models

```kotlin
data class BackupConfig(
    val backupPath: String = "/CalcvaultBackup",
    val chunkSize: Long = 10_000_000,  // 10 MB
    val concurrentTransfers: Int = 3,
    val verifyChecksums: Boolean = true,
    val generateThumbnails: Boolean = true
)

data class BackupProgress(
    val status: BackupStatus,
    val currentFile: String,
    val processedBytes: Long,
    val totalBytes: Long,
    val filesProcessed: Int,
    val totalFiles: Int,
    val startTime: Long,
    val estimatedTimeRemaining: Long
) {
    fun getProgressPercentage(): Int = 
        (processedBytes * 100 / totalBytes).toInt()
}

enum class BackupStatus {
    IDLE,
    INITIALIZING,
    SCANNING,
    BACKING_UP,
    GENERATING_INDEX,
    GENERATING_THUMBNAILS,
    VERIFYING,
    COMPLETE,
    ERROR,
    CANCELLED
}
```

### Methods

```kotlin
// Check PC connection
fun isConnectedToPC(): Boolean

// Start backup
suspend fun startBackup(
    config: BackupConfig = BackupConfig()
): BackupResult

// Generate thumbnails for backup
suspend fun generateBackupThumbnails(
    backupPath: String
): Int  // Returns count of generated thumbnails

// Create indexed file list
suspend fun createIndexedFileList(
    backupPath: String
): List<IndexedFile>

// Verify backup integrity
suspend fun verifyBackup(
    backupPath: String
): BackupResult

// Clear backup data
fun clearBackup(backupPath: String): Boolean
```

---

## RemoteFileRefManager

### Methods

```kotlin
// Load references from disk
fun load(): Boolean

// Save references to disk
fun save(): Boolean

// Add reference
fun addRef(ref: RemoteFileRef): String  // Returns ref ID

// Get reference
fun getRef(refId: String): RemoteFileRef?

// Get all references
fun getAllRefs(): List<RemoteFileRef>

// Get references by owner
fun getRefsByOwner(owner: String): List<RemoteFileRef>

// Update access state
fun updateAccessState(refId: String, state: AccessState): Boolean

// Update thumbnail
fun updateThumbnail(refId: String, thumbnailPath: String): Boolean

// Remove reference
fun removeRef(refId: String): Boolean

// Clear all references
fun clear()

// Search by name
fun searchByName(query: String): List<RemoteFileRef>

// Get locked references (cannot access)
fun getLockedRefs(): List<RemoteFileRef>

// Get available references (can access)
fun getAvailableRefs(): List<RemoteFileRef>

// Get statistics
fun getStats(): RefStatistics

// Sync with backup index
fun syncWithBackupIndex(index: BackupIndex): Int  // Returns count updated
```

### Storage

Remote file references are stored at: `/calcvault/storage/remote_refs.json`

---

## StorageQuotaManager

### Data Models

```kotlin
data class QuotaInfo(
    val owner: String,
    val totalQuota: Long,       // bytes
    val usedSpace: Long,        // bytes
    val availableSpace: Long,   // bytes
    val percentUsed: Int
)

data class CleanupResult(
    val success: Boolean,
    val filesRemoved: Int,
    val spaceFree: Long,
    val errors: List<String>
)
```

### Default Quotas

```kotlin
const val QUOTA_ZAIN = 500_000_000_000L    // 500 GB
const val QUOTA_SANU = 100_000_000_000L   // 100 GB
const val QUOTA_SHARED = 50_000_000_000L  // 50 GB
```

### Methods

```kotlin
// Get storage path for file
fun getStoragePath(
    fileOwner: FileOwner,
    fileName: String,
    fileType: String
): String

// Check if can store file
fun canStoreAs(owner: String, fileSize: Long): Boolean

// Get quota info
fun getQuotaInfo(owner: String): QuotaInfo

// Get all quotas
fun getAllQuotas(): Map<String, QuotaInfo>

// Check if space available
fun hasSpace(fileSize: Long, owner: String): Boolean

// Get utilization percentage
fun getUtilizationPercent(owner: String): Int

// Calculate used space
fun calculateUsedSpace(owner: String): Long

// Cleanup orphaned files
fun cleanupOrphaned(daysOld: Int = 30): CleanupResult

// Format size for display
fun formatSize(bytes: Long): String
```

---

## Storage Directory Structure

```
phone_storage/
├── calcvault/
│   ├── storage/
│   │   ├── Zain/
│   │   │   ├── large_media/          # Files > 1GB
│   │   │   │   ├── audio/
│   │   │   │   ├── video/
│   │   │   │   └── image/
│   │   │   └── [other types]/
│   │   ├── Sanu/
│   │   │   ├── media/                # Files <= 1GB
│   │   │   │   ├── audio/
│   │   │   │   ├── video/
│   │   │   │   ├── image/
│   │   │   │   └── document/
│   │   │   └── [other types]/
│   │   ├── shared/
│   │   │   ├── audio/
│   │   │   ├── video/
│   │   │   ├── image/
│   │   │   ├── document/
│   │   │   └── archive/
│   │   ├── thumbnails/               # All thumbnails
│   │   │   └── {fileId}.jpg
│   │   └── remote_refs.json          # Remote file metadata

PC_backup/
├── CalcvaultBackup/
│   ├── index.json                     # Indexed file list
│   ├── thumbnails/
│   │   └── {fileId}.jpg
│   └── files/
│       ├── audio/
│       ├── video/
│       ├── image/
│       ├── document/
│       └── archive/
```

---

## Constants & Configuration

```kotlin
// Size threshold
const val LARGE_FILE_THRESHOLD_BYTES = 1_000_000_000L  // 1 GB

// User identifiers
const val USER_ZAIN = "Zain"
const val USER_SANU = "Sanu"

// Supported file types
val SUPPORTED_TYPES = setOf(
    "audio", "video", "image", "document", "archive"
)

// Backup settings
const val BACKUP_CHUNK_SIZE = 10_000_000L     // 10 MB
const val CONCURRENT_TRANSFERS = 3

// Thumbnail sizes
const val THUMBNAIL_WIDTH = 200
const val THUMBNAIL_HEIGHT = 200
```

---

## Error Handling

### Common Errors

```kotlin
// Storage quota exceeded
"Insufficient quota: 2.0 GB > 0.5 GB available"

// File not found
"Source file does not exist: /path/to/file"

// Access denied
"Ask Zain to connect their phone to your PC"

// Backup error
"Failed to backup file.mp4: permission denied"

// Index corruption
"Backup index corrupted, regenerating..."
```

### Exception Handling

```kotlin
try {
    val result = storageSystem.storeFile(file, "Sanu", "audio")
    if (!result.success) {
        Log.e("Error", result.reason)
    }
} catch (e: IOException) {
    Log.e("Error", "Storage error: ${e.message}")
} catch (e: SecurityException) {
    Log.e("Error", "Permission denied: ${e.message}")
}
```

---

## Quick API Summary

| Operation | Method | Returns |
|-----------|--------|---------|
| Store file | `storeFile()` | `FileStorageResult` |
| Check access | `canAccessFile()` | `AccessCheckResult` |
| Backup system | `performPCBackup()` | `BackupResult` |
| Get status | `getSystemStatus()` | `SystemStatus` |
| Get summary | `getSystemSummary()` | `String` |
| Load index | `loadIndex()` | `BackupIndex?` |
| Search index | `searchIndex()` | `List<IndexedFile>` |
| Get quota | `getQuotaInfo()` | `QuotaInfo` |
| Add remote ref | `addRef()` | `String` (ref ID) |
| Generate thumbnail | `generateThumbnail()` | `Bitmap?` |

---

**Status**: ✅ COMPLETE - Full API reference ready
