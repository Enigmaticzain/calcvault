# Bulk Data Ingestion - Quick Reference

## 🚀 5-Minute Setup

### Step 1: Add to Manifest
```xml
<activity android:name="com.calcvault.ui.ingestion.BulkImportActivity" />
```

### Step 2: Initialize in App
```kotlin
val index = VaultFileIndex(this)
val engine = BulkDataIngestionEngine(this, storageEngine, index, thumbnailGen)
val monitor = IngestionMonitor(this, engine)
monitor.startMonitoring()
```

### Step 3: Add UI Button
```kotlin
findViewById<Button>(R.id.btn_import).setOnClickListener {
    startActivity(Intent(this, BulkImportActivity::class.java))
}
```

## 📂 File Locations

| Component | Path |
|-----------|------|
| Index DB | `com.calcvault.storage.ingestion.VaultFileIndex` |
| Engine | `com.calcvault.storage.ingestion.BulkDataIngestionEngine` |
| Monitor | `com.calcvault.storage.ingestion.IngestionMonitor` |
| Deleter | `com.calcvault.storage.ingestion.SecureFileDeleter` |
| Activity | `com.calcvault.ui.ingestion.BulkImportActivity` |
| CLI | `com.calcvault.cli.IngestionCLI` |

## 🔑 Core APIs

### VaultFileIndex
```kotlin
val index = VaultFileIndex(context)
index.addFile(vaultFile)           // Add file
index.getFile(id)                  // Get by ID
index.getAllFiles()                // Get all
index.getFilesByType("VIDEO")      // Filter by type
index.fileExists(hash)             // Check duplicate
index.getTotalSize()               // Total size
index.deleteFile(id)               // Remove entry
```

### BulkDataIngestionEngine
```kotlin
val engine = BulkDataIngestionEngine(context, storage, index, thumbnails)
engine.ingestFile(file)            // Single file
engine.ingestDirectory(dir)        // Batch import
engine.decryptFile(vaultFile, out) // Decrypt
engine.onProgress = { }            // Progress callback
engine.onError = { }               // Error callback
```

### IngestionMonitor
```kotlin
val monitor = IngestionMonitor(context, engine)
monitor.startMonitoring()          // Start watching
monitor.triggerImport()            // Manual import
monitor.getImportDirectory()       // Get folder
monitor.clearImportFolder()        // Clear files
monitor.stopMonitoring()           // Stop watching
```

## 📊 Data Models

### VaultFile
```kotlin
data class VaultFile(
    val id: String,                // UUID
    val name: String,              // Original filename
    val size: Long,                // File size in bytes
    val type: String,              // AUDIO, VIDEO, IMAGE, etc.
    val encryptedPath: String,     // Path to encrypted file
    val thumbnailPath: String?,    // Path to thumbnail
    val createdAt: Long,           // Creation timestamp
    val modifiedAt: Long,          // Modification timestamp
    val hash: String?,             // SHA-256 hash
    val mimeType: String?          // MIME type
)
```

### IngestionProgress
```kotlin
data class IngestionProgress(
    val totalFiles: Int,           // Total files to process
    val processedFiles: Int,       // Files processed
    val currentFile: String,       // Current filename
    val currentFileProgress: Float,// 0.0 to 1.0
    val totalBytesProcessed: Long, // Bytes processed
    val totalBytes: Long,          // Total bytes
    val estimatedTimeRemaining: Long // Milliseconds
)
```

## 🔐 Encryption

### Algorithm
- **Cipher**: AES-256-GCM
- **Key**: 256-bit random
- **IV**: 12-byte random per file
- **Tag**: 128-bit authentication

### File Format
```
[IV: 12 bytes] [Encrypted Data] [Auth Tag: 16 bytes]
```

## 📁 Directory Structure

```
/data/data/com.calcvault/files/
├── import/          ← User drops files here
├── encrypted/       ← Encrypted files
├── thumbnails/      ← Generated previews
└── vault_index.db   ← SQLite database
```

## 🎯 Common Tasks

### Import Single File
```kotlin
lifecycleScope.launch {
    val success = engine.ingestFile(File("/path/to/file"))
    if (success) {
        Toast.makeText(context, "Imported!", Toast.LENGTH_SHORT).show()
    }
}
```

### Import Directory
```kotlin
lifecycleScope.launch {
    val success = engine.ingestDirectory(File("/path/to/dir"))
    if (success) {
        loadVaultFiles()
    }
}
```

### List Vault Files
```kotlin
val files = index.getAllFiles()
files.forEach { file ->
    println("${file.name} (${file.type}) - ${file.size} bytes")
}
```

### Get File by Type
```kotlin
val videos = index.getFilesByType("VIDEO")
val images = index.getFilesByType("IMAGE")
val audio = index.getFilesByType("AUDIO")
```

### Check for Duplicates
```kotlin
val hash = computeHash(file)
if (index.fileExists(hash)) {
    println("File already in vault")
} else {
    engine.ingestFile(file)
}
```

### Decrypt File
```kotlin
val vaultFile = index.getFile(fileId)
val outputFile = File(context.cacheDir, "decrypted")
val success = engine.decryptFile(vaultFile, outputFile)
```

### Get Vault Statistics
```kotlin
val totalSize = index.getTotalSize()
val fileCount = index.getAllFiles().size
println("Vault: $fileCount files, ${formatBytes(totalSize)}")
```

## 🔄 Progress Tracking

```kotlin
engine.onProgress = { progress ->
    val percent = (progress.currentFileProgress * 100).toInt()
    val status = "${progress.processedFiles}/${progress.totalFiles}"
    val speed = progress.totalBytesProcessed / progress.estimatedTimeRemaining
    
    progressBar.progress = percent
    statusText.text = status
    speedText.text = "${formatBytes(speed)}/s"
}
```

## ⚠️ Error Handling

```kotlin
engine.onError = { file, error ->
    when (error) {
        is IOException -> println("I/O error: ${error.message}")
        is SecurityException -> println("Security error: ${error.message}")
        else -> println("Unknown error: ${error.message}")
    }
}
```

## 🧪 Testing

### Test Ingestion
```kotlin
@Test
fun testFileIngestion() = runBlocking {
    val testFile = File(context.cacheDir, "test.txt")
    testFile.writeText("test")
    
    val success = engine.ingestFile(testFile)
    assertTrue(success)
    assertFalse(testFile.exists())
}
```

### Test Duplicate Detection
```kotlin
@Test
fun testDuplicateDetection() = runBlocking {
    val file1 = File(context.cacheDir, "file1.txt")
    file1.writeText("same content")
    
    engine.ingestFile(file1)
    
    val file2 = File(context.cacheDir, "file2.txt")
    file2.writeText("same content")
    
    val success = engine.ingestFile(file2)
    assertTrue(success) // Duplicate detected and skipped
}
```

## 📈 Performance Tips

1. **Batch Import**: Import multiple files at once
2. **Background Thread**: Use coroutines for UI responsiveness
3. **Chunk Size**: Adjust based on device RAM
4. **Indexing**: Query by indexed fields (name, type, date)
5. **Thumbnails**: Generate in parallel

## 🔒 Security Tips

1. Verify encryption session active before import
2. Check file permissions before ingestion
3. Verify secure deletion completed
4. Monitor for unauthorized access
5. Audit all import operations

## 🐛 Troubleshooting

| Issue | Solution |
|-------|----------|
| Import folder not found | Create manually: `File(context.filesDir, "import").mkdirs()` |
| No encryption session | Call `keyManager.startEphemeralExchange()` first |
| Out of storage | Check `context.filesDir.freeSpace` |
| Slow ingestion | Increase `CHUNK_SIZE` or reduce thumbnail quality |
| Duplicate not detected | Verify hash computation is correct |

## 📞 Support Resources

- **INGESTION_SYSTEM.md**: Full documentation
- **INTEGRATION_GUIDE.md**: Integration instructions
- **Code comments**: Inline documentation
- **Logcat**: Debug logs with tag "BulkDataIngestion"

## 🎓 Key Concepts

| Concept | Explanation |
|---------|------------|
| Streaming | Process files in chunks, not all at once |
| Encryption | AES-256-GCM with unique IV per file |
| Duplicate | SHA-256 hash comparison |
| Secure Delete | 3-pass overwrite with random data |
| Thumbnail | Generated preview for media files |
| Index | SQLite database for fast lookup |

## ✅ Checklist

- [ ] Add files to project
- [ ] Update AndroidManifest.xml
- [ ] Initialize in Application class
- [ ] Add import button to UI
- [ ] Test with sample files
- [ ] Verify encryption working
- [ ] Check secure deletion
- [ ] Monitor performance
- [ ] Deploy to production

## 🚀 Next Steps

1. Copy files to project
2. Update manifest
3. Initialize components
4. Add UI button
5. Test with files
6. Deploy and monitor

---

**Quick Links**
- [Full Documentation](INGESTION_SYSTEM.md)
- [Integration Guide](INTEGRATION_GUIDE.md)
- [Implementation Summary](BULK_INGESTION_SUMMARY.md)
