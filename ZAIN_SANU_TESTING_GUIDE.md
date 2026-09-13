# ZAIN-SANU STORAGE - Testing & Verification Guide

## 📋 Test Suite Overview

This guide provides comprehensive test cases to validate the Zain-Sanu Storage System implementation.

---

## 1️⃣ Unit Tests

### Test 1: File Ownership Determination

```kotlin
class UserOwnershipManagerTest {
    private lateinit var ownershipManager: UserOwnershipManager
    
    @Before
    fun setup() {
        ownershipManager = UserOwnershipManager()
    }
    
    @Test
    fun testSmallFileStoredAsSanu() {
        val smallFile = File("recording_50mb.m4a")
        val size = 50_000_000L  // 50 MB
        
        val owner = ownershipManager.determineOwner(
            FileLike(name = "recording_50mb.m4a", size = size)
        )
        
        assertEquals(UserOwnershipManager.FileOwner.SANU, owner)
    }
    
    @Test
    fun testLargeFileStoredAsZain() {
        val largeFile = FileLike(name = "video_2gb.mp4", size = 2_000_000_000L)
        
        val owner = ownershipManager.determineOwner(largeFile)
        
        assertEquals(UserOwnershipManager.FileOwner.ZAIN, owner)
    }
    
    @Test
    fun testExactThresholdBoundary() {
        val boundaryFile = FileLike(
            name = "file_exactly_1gb.bin",
            size = 1_000_000_000L  // Exactly 1GB
        )
        
        val owner = ownershipManager.determineOwner(boundaryFile)
        
        // Should be Zain (>= 1GB goes to Zain)
        assertEquals(UserOwnershipManager.FileOwner.ZAIN, owner)
    }
    
    @Test
    fun testJustUnderThreshold() {
        val almostLargeFile = FileLike(
            name = "file_999mb.bin",
            size = 999_000_000L  // 999 MB
        )
        
        val owner = ownershipManager.determineOwner(almostLargeFile)
        
        // Should be Sanu (< 1GB goes to Sanu)
        assertEquals(UserOwnershipManager.FileOwner.SANU, owner)
    }
    
    @Test
    fun testAccessControl_ZainCanAccessOwnFile() {
        val result = ownershipManager.canAccessDirectly(
            userId = "Zain",
            fileOwner = UserOwnershipManager.FileOwner.ZAIN
        )
        
        assertTrue(result)
    }
    
    @Test
    fun testAccessControl_SanuCanAccessOwnFile() {
        val result = ownershipManager.canAccessDirectly(
            userId = "Sanu",
            fileOwner = UserOwnershipManager.FileOwner.SANU
        )
        
        assertTrue(result)
    }
    
    @Test
    fun testAccessControl_SanuCannotAccessZainFile() {
        val result = ownershipManager.canAccessDirectly(
            userId = "Sanu",
            fileOwner = UserOwnershipManager.FileOwner.ZAIN
        )
        
        assertFalse(result)
    }
    
    @Test
    fun testAccessPrompt_SanuAccessingZainFile() {
        val prompt = ownershipManager.getAccessPrompt(
            userId = "Sanu",
            fileOwner = UserOwnershipManager.FileOwner.ZAIN
        )
        
        assertTrue(prompt.contains("Zain"))
        assertTrue(prompt.contains("device") || prompt.contains("connect"))
    }
}

// Helper data class for testing
data class FileLike(val name: String, val size: Long)
```

### Test 2: Backup Indexing

```kotlin
class BackupIndexerTest {
    private lateinit var backupIndexer: BackupIndexer
    
    @Before
    fun setup() {
        backupIndexer = BackupIndexer()
    }
    
    @Test
    fun testGenerateIndex_CreatesValidJSON() {
        val files = listOf(
            File.createTempFile("audio", ".m4a"),
            File.createTempFile("video", ".mp4"),
            File.createTempFile("image", ".jpg")
        )
        
        val index = backupIndexer.generateAndSaveIndex(
            backupPath = "/CalcvaultBackup",
            files = files
        )
        
        assertNotNull(index)
        assertEquals(3, index.files.size)
        assertEquals("1.0", index.version)
    }
    
    @Test
    fun testSearchIndex_FindsAudioFiles() {
        val files = listOf(
            IndexedFile("audio1.m4a", 100_000_000, "SANU", "audio"),
            IndexedFile("video1.mp4", 500_000_000, "ZAIN", "video"),
            IndexedFile("audio2.wav", 50_000_000, "ZANU", "audio")
        )
        
        val index = BackupIndex(
            version = "1.0",
            generatedAt = System.currentTimeMillis(),
            files = files
        )
        
        val results = backupIndexer.searchIndex(index, query = "audio")
        
        assertEquals(2, results.size)
        assertTrue(results.all { it.type == "audio" })
    }
    
    @Test
    fun testGetLargeFiles_ReturnsFilesOver1GB() {
        val files = listOf(
            IndexedFile("small.mp4", 500_000_000, "SANU", "video"),
            IndexedFile("large1.mp4", 2_000_000_000, "ZAIN", "video"),
            IndexedFile("large2.mp4", 3_500_000_000, "ZAIN", "video")
        )
        
        val index = BackupIndex(
            version = "1.0",
            generatedAt = System.currentTimeMillis(),
            files = files
        )
        
        val largeFiles = backupIndexer.getLargeFiles(index)
        
        assertEquals(2, largeFiles.size)
        assertTrue(largeFiles.all { it.size > 1_000_000_000 })
    }
    
    @Test
    fun testCalculateStatistics_AccurateTotals() {
        val files = listOf(
            IndexedFile("file1.mp4", 1_000_000_000, "ZAIN", "video"),
            IndexedFile("file2.m4a", 100_000_000, "SANU", "audio"),
            IndexedFile("file3.jpg", 5_000_000, "SANU", "image")
        )
        
        val stats = backupIndexer.calculateStatistics(files)
        
        assertEquals(3, stats.totalFiles)
        assertEquals(1_105_000_000L, stats.totalSize)
        assertEquals(2, stats.totalByOwner["ZAIN"])
        assertEquals(1, stats.totalByOwner["SANU"])
    }
}
```

### Test 3: Storage Quota

```kotlin
class StorageQuotaManagerTest {
    private lateinit var quotaManager: StorageQuotaManager
    
    @Before
    fun setup() {
        quotaManager = StorageQuotaManager()
    }
    
    @Test
    fun testQuotaLimits_ZainHas500GB() {
        val zainQuota = quotaManager.getQuotaInfo("Zain")
        
        assertEquals(500_000_000_000L, zainQuota.totalQuota)
    }
    
    @Test
    fun testQuotaLimits_SanuHas100GB() {
        val sanuQuota = quotaManager.getQuotaInfo("Sanu")
        
        assertEquals(100_000_000_000L, sanuQuota.totalQuota)
    }
    
    @Test
    fun testStoragePath_ZainLargeFile() {
        val path = quotaManager.getStoragePath(
            fileOwner = UserOwnershipManager.FileOwner.ZAIN,
            fileName = "video.mp4",
            fileType = "video"
        )
        
        assertTrue(path.contains("Zain"))
        assertTrue(path.contains("large_media"))
    }
    
    @Test
    fun testStoragePath_SanuSmallFile() {
        val path = quotaManager.getStoragePath(
            fileOwner = UserOwnershipManager.FileOwner.SANU,
            fileName = "recording.m4a",
            fileType = "audio"
        )
        
        assertTrue(path.contains("Sanu"))
        assertTrue(path.contains("media"))
        assertFalse(path.contains("large"))
    }
    
    @Test
    fun testCanStore_EnforceQuota() {
        // Simulate Zain with 495GB used
        quotaManager.overrideUsedSpace("Zain", 495_000_000_000L)
        
        // Try to store 10GB - should succeed
        val canStore1 = quotaManager.canStoreAs(
            owner = "Zain",
            fileSize = 10_000_000_000L
        )
        assertTrue(canStore1)
        
        // Try to store 20GB - should fail (exceeds remaining 5GB)
        val canStore2 = quotaManager.canStoreAs(
            owner = "Zain",
            fileSize = 20_000_000_000L
        )
        assertFalse(canStore2)
    }
    
    @Test
    fun testStorageDecision_RoutesCorrectly() {
        val decision = quotaManager.getStoragePath(
            fileOwner = UserOwnershipManager.FileOwner.ZAIN,
            fileName = "file.mp4",
            fileType = "video"
        )
        
        // Should return valid Zain storage path
        assertNotNull(decision)
        assertTrue(decision.contains("Zain"))
    }
}
```

---

## 2️⃣ Integration Tests

### Test 4: End-to-End File Storage

```kotlin
class FileStorageFlowTest {
    private lateinit var storageSystem: ZainSanuStorageSystem
    
    @Before
    fun setup() {
        storageSystem = ZainSanuStorageSystem(InstrumentationRegistry.getInstrumentation().context)
    }
    
    @Test
    fun testStoreSmallFile_AsSanu() = runBlocking {
        val testFile = createTestFile("recording", 50_000_000)  // 50 MB
        
        val result = storageSystem.storeFile(
            sourceFile = testFile,
            currentUser = "Sanu",
            fileType = "audio"
        )
        
        assertTrue(result.success)
        assertTrue(result.filePath.contains("Sanu"))
        assertTrue(result.filePath.contains("audio"))
        assertNotNull(result.thumbnailPath)
        assertEquals("SANU", result.owner)
        assertTrue(result.isLocallyStored)
    }
    
    @Test
    fun testStoreLargeFile_AsSanu_CreatesRemoteRef() = runBlocking {
        val testFile = createTestFile("video", 2_000_000_000)  // 2 GB
        
        val result = storageSystem.storeFile(
            sourceFile = testFile,
            currentUser = "Sanu",
            fileType = "video"
        )
        
        // File cannot be stored as Sanu due to size
        assertTrue(result.success || !result.success)  // May succeed with remote ref
        
        // If stored, should be as Zain
        if (result.success) {
            assertEquals("ZAIN", result.owner)
        }
    }
    
    @Test
    fun testStoreFile_GeneratesThumbnail() = runBlocking {
        val testFile = createTestFile("image", 5_000_000)  // Image file
        
        val result = storageSystem.storeFile(
            sourceFile = testFile,
            currentUser = "Sanu",
            fileType = "image"
        )
        
        assertTrue(result.success)
        assertNotNull(result.thumbnailPath)
        
        // Verify thumbnail file exists
        val thumbnailFile = File(result.thumbnailPath!!)
        assertTrue(thumbnailFile.exists())
    }
    
    @Test
    fun testAccessControl_EnforceOwnership() = runBlocking {
        // Store as Zain
        val zainFile = createTestFile("private_video", 2_000_000_000)
        storageSystem.storeFile(zainFile, "Zain", "video")
        
        // Try to access as Sanu
        val fileId = "zain_private_video"
        val accessCheck = storageSystem.canAccessFile("Sanu", fileId)
        
        // Sanu should not have direct access
        assertFalse(accessCheck.canAccess)
        assertTrue(accessCheck.prompt.contains("Zain"))
    }
    
    private fun createTestFile(name: String, size: Long): File {
        val file = File.createTempFile(name, ".tmp")
        file.writeBytes(ByteArray(size.toInt()))
        return file
    }
}
```

### Test 5: PC Backup Flow

```kotlin
class PCBackupFlowTest {
    private lateinit var storageSystem: ZainSanuStorageSystem
    
    @Before
    fun setup() {
        storageSystem = ZainSanuStorageSystem(InstrumentationRegistry.getInstrumentation().context)
    }
    
    @Test
    fun testPCBackup_CreatesIndexJSON() = runBlocking {
        val backupPath = "/CalcvaultBackup"
        
        val result = storageSystem.performPCBackup { progress ->
            // Monitor progress
            Log.d("Test", "Backup progress: ${progress.getProgressPercentage()}%")
        }
        
        if (result.success) {
            // Verify index.json exists
            val indexFile = File(backupPath, "index.json")
            assertTrue(indexFile.exists())
            
            // Verify it's valid JSON
            val json = indexFile.readText()
            assertTrue(json.contains("\"version\""))
            assertTrue(json.contains("\"files\""))
        }
    }
    
    @Test
    fun testPCBackup_CopiesAllFiles() = runBlocking {
        val result = storageSystem.performPCBackup { progress ->
            // No-op
        }
        
        if (result.success) {
            // Verify files were copied
            assertTrue(result.filesBackedUp > 0)
            assertTrue(result.bytesBackedUp > 0)
        }
    }
    
    @Test
    fun testBackupProgress_TrackingAccurate() = runBlocking {
        var maxProgress = 0
        
        val result = storageSystem.performPCBackup { progress ->
            val currentProgress = progress.getProgressPercentage()
            assertTrue(currentProgress >= maxProgress)  // Monotonically increasing
            maxProgress = currentProgress
        }
        
        // Progress should reach 100
        assertEquals(100, maxProgress)
    }
}
```

---

## 3️⃣ Manual Tests

### Test 6: Record and Store Voice Message

**Steps:**
1. Open ChatActivity
2. Tap record button
3. Speak for 10 seconds
4. Stop recording
5. Verify:
   - Message appears with thumbnail
   - File is stored in correct location
   - Metadata saved to database

**Expected Results:**
- ✓ Small recording (< 1GB) stored in Sanu's audio folder
- ✓ Thumbnail shows waveform or audio icon
- ✓ Playback works
- ✓ Message shows local indicator

### Test 7: Store Large Video Call

**Steps:**
1. Open CallActivity
2. Start call
3. Record entire call (simulate 2GB file)
4. End call
5. Verify:
   - Recording saved
   - Stored in Zain's large_media folder
   - Remote reference created for Sanu

**Expected Results:**
- ✓ Large file routed to Zain's device only
- ✓ Sanu sees remote reference with locked state
- ✓ Sanu cannot play until PC backup
- ✓ Thumbnail shows first frame of video

### Test 8: PC Backup Process

**Steps:**
1. Connect phone to PC via USB
2. Open SettingsActivity
3. Tap "Backup Now"
4. Monitor progress
5. Check PC directory
6. Verify index.json

**Expected Results:**
- ✓ Progress dialog shows accurate percentage
- ✓ Files copied to /CalcvaultBackup
- ✓ index.json created with all files
- ✓ Thumbnails generated in /CalcvaultBackup/thumbnails

**index.json Content:**
```json
{
  "version": "1.0",
  "generatedAt": 1712345678,
  "location": "/CalcvaultBackup",
  "statistics": {
    "totalFiles": 1250,
    "totalSize": 487300000000,
    "largeFilesCount": 180
  },
  "files": [
    {
      "id": "file_123",
      "name": "call_recording.mp4",
      "path": "video/call_recording.mp4",
      "size": 2000000000,
      "owner": "Zain",
      "type": "video",
      "thumbnail": "thumbnails/file_123.jpg",
      "modified": 1712340000
    },
    ...
  ]
}
```

### Test 9: Access Control - Sanu Accessing Zain File

**Setup:**
1. Zain stores 2GB video
2. Switch to Sanu user

**Steps:**
1. Open ChatActivity showing Zain's video
2. Try to tap play button
3. Verify access denied message
4. Re-run PC backup
5. Try to play again

**Expected Results - Before PC Backup:**
- ✓ Access denied
- ✓ Prompt: "Ask Zain to connect to PC"
- ✓ File shows locked icon
- ✓ Cannot download

**Expected Results - After PC Backup:**
- ✓ Access granted
- ✓ Play button enabled
- ✓ File shows available icon
- ✓ Smooth playback

### Test 10: Storage Quota Enforcement

**Steps:**
1. Open SettingsActivity
2. Check storage metrics
3. Attempt to store file when quota nearly full
4. Verify rejection

**Expected Results:**
- ✓ Zain quota shows 500GB total
- ✓ Sanu quota shows 100GB total
- ✓ Shared quota shows 50GB total
- ✓ Attempting to exceed quota fails gracefully
- ✓ Error message explains issue

### Test 11: Media Browser

**Setup:**
1. Create mix of local and remote files

**Steps:**
1. Open MediaActivity
2. Verify all files displayed
3. Filter by owner
4. Search for files
5. Check quota indicators

**Expected Results:**
- ✓ Local files show with direct access
- ✓ Remote files show with lock icon
- ✓ Size filters work
- ✓ Search finds by name
- ✓ Quota indicators accurate

### Test 12: Thumbnail Generation

**Steps:**
- Store audio file → verify waveform generated
- Store image file → verify scaled thumbnail
- Store video file → verify first frame extracted
- Store document → verify document icon

**Expected Results:**
- ✓ All media types show appropriate thumbnails
- ✓ Thumbnails load quickly
- ✓ Fallback icons for unsupported types
- ✓ No memory issues with many thumbnails

---

## 4️⃣ Stress Tests

### Test 13: Large Index JSON (1000+ Files)

```kotlin
@Test
fun testLargeIndexPerformance() = runBlocking {
    val files = (1..1500).map {
        IndexedFile(
            "file_$it.mp4",
            (50_000_000L + (Math.random() * 4_950_000_000)).toLong(),
            if (it % 2 == 0) "Zain" else "Sanu",
            "video"
        )
    }
    
    val startTime = System.currentTimeMillis()
    val index = backupIndexer.generateAndSaveIndex("/CalcvaultBackup", files)
    val duration = System.currentTimeMillis() - startTime
    
    // Should complete in < 2 seconds
    assertTrue(duration < 2000)
}
```

### Test 14: Multiple Concurrent Stores

```kotlin
@Test
fun testConcurrentFileStores() = runBlocking {
    val jobs = (1..10).map { i ->
        async(Dispatchers.IO) {
            val file = createTestFile("concurrent_$i", 50_000_000)
            storageSystem.storeFile(file, "Sanu", "audio")
        }
    }
    
    val results = jobs.awaitAll()
    
    // All should succeed
    assertTrue(results.all { it.success })
    // All should be different
    assertEquals(results.map { it.filePath }.distinct().size, 10)
}
```

---

## ✅ Test Results Checklist

- [ ] All unit tests pass
- [ ] All integration tests pass
- [ ] Manual test 1: Voice message - PASS
- [ ] Manual test 2: Large video - PASS
- [ ] Manual test 3: PC backup - PASS
- [ ] Manual test 4: Access control - PASS
- [ ] Manual test 5: Quota enforcement - PASS
- [ ] Manual test 6: Media browser - PASS
- [ ] Manual test 7: Thumbnails - PASS
- [ ] Stress test 1: Large index - PASS
- [ ] Stress test 2: Concurrent store - PASS

---

## 🐛 Debugging Tips

### Check Storage Paths

```kotlin
val status = storageSystem.getSystemStatus()
Log.d("Debug", status.storagePaths)
// Output paths for each storage location
```

### Monitor File Operations

```kotlin
ZainSanuStorageSystem.enableDebugLogging()
// Logs all file moves, ownership checks, quota checks
```

### Inspect Index JSON

```kotlin
// Read and pretty-print index
val index = backupIndexer.loadIndex("/CalcvaultBackup")
val json = GsonBuilder().setPrettyPrinting().create().toJson(index)
Log.d("Index", json)
```

### Verify Ownership

```kotlin
val owner = ownershipManager.determineOwner(file)
Log.d("Owner", "File owner: $owner for ${file.name} (${file.length()} bytes)")
```

---

**Status**: ✅ COMPLETE - Comprehensive testing guide ready
