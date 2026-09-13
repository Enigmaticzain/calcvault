# Integration Guide: Bulk Data Ingestion System

## Quick Start Integration

### 1. Add to AndroidManifest.xml

```xml
<activity
    android:name="com.calcvault.ui.ingestion.BulkImportActivity"
    android:exported="false"
    android:label="Secure Import" />
```

### 2. Add Menu Item to Main Activity

In your main activity's menu XML:

```xml
<item
    android:id="@+id/menu_bulk_import"
    android:title="Secure Import"
    android:icon="@drawable/ic_import"
    app:showAsAction="never" />
```

Handle in onOptionsItemSelected:

```kotlin
override fun onOptionsItemSelected(item: MenuItem): Boolean {
    return when (item.itemId) {
        R.id.menu_bulk_import -> {
            startActivity(Intent(this, BulkImportActivity::class.java))
            true
        }
        else -> super.onOptionsItemSelected(item)
    }
}
```

### 3. Initialize in Application Class

```kotlin
class CalcVaultApp : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Initialize ingestion system
        val index = VaultFileIndex(this)
        val storageEngine = USBStorageEngine.getInstance(this)
        val thumbnailGen = ThumbnailGenerator(this)
        val engine = BulkDataIngestionEngine(this, storageEngine, index, thumbnailGen)
        val monitor = IngestionMonitor(this, engine)
        
        // Start background monitoring
        monitor.startMonitoring()
    }
}
```

### 4. Add to Gradle Dependencies

```gradle
dependencies {
    // Already included in CalcVault
    implementation 'androidx.recyclerview:recyclerview:1.3.0'
    implementation 'androidx.lifecycle:lifecycle-runtime-ktx:2.6.0'
}
```

## Integration Points

### With USBStorageEngine

The ingestion system integrates seamlessly with existing storage:

```kotlin
// Encrypted files stored in app's internal storage
// Can be synced to USB via existing mechanisms
val encryptedFile = File(context.filesDir, "encrypted/$fileId.enc")

// Use existing encryption session
val keyManager = E2EKeyManager.getInstance()
if (keyManager.hasActiveSession()) {
    // Proceed with ingestion
}
```

### With AppendOnlyMessageDB

Store file references in messaging DB:

```kotlin
val messageDB = AppendOnlyMessageDB(storageEngine)

// Send file reference to partner
messageDB.sendMediaMessage(
    from = "user1",
    to = "user2",
    type = AppendOnlyMessageDB.MSG_FILE,
    mediaRef = vaultFile.id,
    fileName = vaultFile.name,
    mimeType = vaultFile.mimeType
)
```

### With ThumbnailGenerator

Already integrated - generates previews automatically:

```kotlin
val thumbnailGen = ThumbnailGenerator(context)

// Supports: images, videos, audio
if (thumbnailGen.supportsThumbnail(file)) {
    val thumbFile = File(context.filesDir, "thumbnails/$fileId.jpg")
    thumbnailGen.generateThumbnail(file, thumbFile)
}
```

### With SecurityLayer

Ingestion respects security policies:

```kotlin
val security = SecurityLayer(context)
val report = security.runFullScan()

if (report.overallThreat >= SecurityLayer.ThreatLevel.HIGH) {
    // Block ingestion
    return false
}
```

## Usage Examples

### Example 1: Simple Import

```kotlin
class MainActivity : AppCompatActivity() {
    private lateinit var monitor: IngestionMonitor
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val index = VaultFileIndex(this)
        val engine = BulkDataIngestionEngine(
            this,
            USBStorageEngine.getInstance(this),
            index,
            ThumbnailGenerator(this)
        )
        monitor = IngestionMonitor(this, engine)
        
        findViewById<Button>(R.id.btn_import).setOnClickListener {
            lifecycleScope.launch {
                monitor.triggerImport()
            }
        }
    }
}
```

### Example 2: Progress Tracking

```kotlin
engine.onProgress = { progress ->
    runOnUiThread {
        progressBar.progress = (progress.currentFileProgress * 100).toInt()
        statusText.text = "${progress.processedFiles}/${progress.totalFiles}"
        
        val speed = if (progress.estimatedTimeRemaining > 0) {
            (progress.totalBytes - progress.totalBytesProcessed) / progress.estimatedTimeRemaining
        } else 0L
        
        speedText.text = "${formatBytes(speed)}/s"
    }
}
```

### Example 3: Error Handling

```kotlin
engine.onError = { file, error ->
    runOnUiThread {
        AlertDialog.Builder(this)
            .setTitle("Import Error")
            .setMessage("Failed to import $file: ${error.message}")
            .setPositiveButton("Retry") { _, _ ->
                lifecycleScope.launch {
                    engine.ingestFile(File(file))
                }
            }
            .setNegativeButton("Skip", null)
            .show()
    }
}
```

### Example 4: Query Vault

```kotlin
val index = VaultFileIndex(context)

// Get all files
val allFiles = index.getAllFiles()

// Filter by type
val videos = index.getFilesByType("VIDEO")

// Get statistics
val totalSize = index.getTotalSize()
val fileCount = allFiles.size

statusText.text = "Vault: $fileCount files, ${formatBytes(totalSize)}"
```

### Example 5: Decrypt and Share

```kotlin
val vaultFile = index.getFile(fileId)
val outputFile = File(context.cacheDir, "temp_${vaultFile.name}")

lifecycleScope.launch {
    val success = engine.decryptFile(vaultFile, outputFile)
    if (success) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            outputFile
        )
        
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = vaultFile.mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
        }
        startActivity(Intent.createChooser(intent, "Share"))
    }
}
```

## Configuration

### Customize Chunk Size

```kotlin
// In BulkDataIngestionEngine.kt
companion object {
    private const val CHUNK_SIZE = 8 * 1024 * 1024L // 8MB instead of 4MB
}
```

### Customize Encryption

```kotlin
// In BulkDataIngestionEngine.kt
private suspend fun streamEncryptFile(sourceFile: File, encryptedFile: File): Boolean {
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    val key = SecretKeySpec(ByteArray(32).also { SecureRandom().nextBytes(it) }, "AES")
    // Customize key derivation here
}
```

### Customize Thumbnail Size

```kotlin
// In ThumbnailGenerator.kt
companion object {
    const val THUMBNAIL_WIDTH = 300  // Instead of 200
    const val THUMBNAIL_HEIGHT = 300 // Instead of 200
}
```

## Testing

### Unit Test Example

```kotlin
@RunWith(AndroidJUnit4::class)
class IngestionEngineTest {
    
    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()
    
    private lateinit var context: Context
    private lateinit var engine: BulkDataIngestionEngine
    
    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        val index = VaultFileIndex(context)
        engine = BulkDataIngestionEngine(
            context,
            USBStorageEngine.getInstance(context),
            index,
            ThumbnailGenerator(context)
        )
    }
    
    @Test
    fun testFileIngestion() = runBlocking {
        val testFile = File(context.cacheDir, "test.txt")
        testFile.writeText("test content")
        
        val success = engine.ingestFile(testFile)
        assertTrue(success)
        assertFalse(testFile.exists()) // Original deleted
    }
}
```

## Troubleshooting

### Import Folder Not Found

```kotlin
val monitor = IngestionMonitor(context, engine)
val importPath = monitor.getImportDirectoryPath()
Log.d("Import", "Folder: $importPath")

// Manually create if needed
File(importPath).mkdirs()
```

### Encryption Session Not Active

```kotlin
val keyManager = E2EKeyManager.getInstance()
if (!keyManager.hasActiveSession()) {
    // Establish session first
    val ephemeralKey = keyManager.startEphemeralExchange()
    // Exchange with partner...
}
```

### Storage Space Issues

```kotlin
val index = VaultFileIndex(context)
val totalSize = index.getTotalSize()
val availableSpace = context.filesDir.freeSpace

if (totalSize + newFileSize > availableSpace) {
    // Handle insufficient space
}
```

## Performance Tips

1. **Batch Imports**: Import multiple files at once rather than individually
2. **Background Processing**: Run ingestion in background thread
3. **Thumbnail Caching**: Reuse generated thumbnails
4. **Database Indexing**: Query by indexed fields (name, type, date)
5. **Chunk Size**: Adjust based on device RAM

## Security Considerations

1. **Session Key**: Ensure active encryption session before ingestion
2. **File Permissions**: Verify import folder permissions
3. **Secure Deletion**: Verify files are securely deleted
4. **Access Control**: Only allow authenticated users to import
5. **Audit Logging**: Log all import operations

## Next Steps

1. Add import button to main UI
2. Implement progress notifications
3. Add file browser for import folder
4. Integrate with existing sync mechanism
5. Add cloud backup support
6. Implement incremental imports
