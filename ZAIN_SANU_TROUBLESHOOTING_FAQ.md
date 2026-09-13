# ZAIN-SANU STORAGE - Troubleshooting & FAQ

## ❓ Frequently Asked Questions

### Q1: How does the 1GB threshold work?

**A:** Files are routed based on size:
- **≤ 999 MB**: Stored in Sanu's directory (`/calcvault/storage/Sanu/media/`)
- **≥ 1 GB**: Stored in Zain's directory (`/calcvault/storage/Zain/large_media/`)
- **Exactly 1 GB**: Treated as large file → Zain

The threshold is hardcoded as `1_000_000_000 bytes` in `UserOwnershipManager.LARGE_FILE_THRESHOLD_BYTES`.

### Q2: Can Sanu store large files (>1GB)?

**A:** No. By design:
- Sanu can only store files ≤ 1GB directly
- If Sanu records a file > 1GB, it's stored in Zain's storage
- A `RemoteFileRef` is created for Sanu's access
- Sanu can access it only if the PC has a backup

### Q3: What happens when storage quota is full?

**A:** The system refuses to store the file with error:
```
"Insufficient quota: 2.0 GB > 0.5 GB available"
```

Options:
1. Delete old files to free space
2. Run cleanup: `quotaManager.cleanupOrphaned(daysOld=30)`
3. Increase quota in `StorageQuotaManager` (requires code change)

### Q4: Why is Sanu seeing a locked file?

**A:** Large files are stored only on Zain's device. Sanu sees a `RemoteFileRef` with:
```
accessState = AccessState.LOCKED
status = "Stored on Zain's device"
```

To unlock:
1. Zain connects phone to PC
2. PC backup completes
3. File becomes `AccessState.AVAILABLE`

### Q5: Can we change the quota limits?

**A:** Yes, edit `StorageQuotaManager.kt`:
```kotlin
companion object {
    const val DEFAULT_QUOTA_ZAIN = 500_000_000_000L    // Change this
    const val DEFAULT_QUOTA_SANU = 100_000_000_000L   // Change this
    const val DEFAULT_QUOTA_SHARED = 50_000_000_000L  // Change this
}
```

Then recompile the app.

### Q6: What if the backup index corrupts?

**A:** The system can regenerate it:
```kotlin
val result = storageSystem.performPCBackup { }
if (result.success) {
    // New index.json created
    Log.d("Backup", "Index regenerated at ${result.indexPath}")
}
```

### Q7: How do I export files to share with someone else?

**A:** Use the backup system:
1. `performPCBackup()` → copies all files to `/CalcvaultBackup`
2. Transfer `/CalcvaultBackup` folder to external device
3. Share `index.json` + thumbnail folder for indexed access

### Q8: Can we have more than 2 users?

**A:** The current system is designed for Zain and Sanu only. To add more users:
1. Extend `FileOwner` enum in `UserOwnershipManager`
2. Add new quotas in `StorageQuotaManager`
3. Create new storage paths in `getStoragePath()`
4. Update access control logic in `canAccessDirectly()`

### Q9: What's the overhead of the system?

**A:** 
- **Small**: Index JSON (~2-5% of backup size)
- **Thumbnails**: ~500KB per 100 files
- **Metadata**: `remote_refs.json` (~1-2% of backup size)

Total overhead: ~5-10% of actual file size.

### Q10: Can we backup incrementally?

**A:** Currently, backups are full copies. For incremental:
1. Modify `PCBackupManager.scanDirectory()` to track last backup time
2. Skip files with `lastModified < lastBackupTime`
3. Update index with new files only
4. Merge with existing index

---

## 🐛 Troubleshooting Guide

### Issue: "Storage system initialization failed"

**Causes:**
- Missing storage directory permissions
- Invalid storage path
- Corrupted config files

**Solutions:**
1. Check Android permissions:
   ```xml
   <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
   <uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE" />
   ```

2. Verify initialization in logcat:
   ```bash
   adb logcat | grep "CalcVault"
   ```

3. Check storage directories exist:
   ```kotlin
   val storageRoot = File("/calcvault/storage")
   Log.d("Debug", "Storage root exists: ${storageRoot.exists()}")
   ```

4. Clear app cache:
   ```bash
   adb shell pm clear com.your.app
   ```

---

### Issue: File stored but not found later

**Causes:**
- Wrong file path used
- File moved/deleted after storage
- Ownership not tracked correctly

**Solutions:**
1. Verify file exists immediately after storage:
   ```kotlin
   val result = storageSystem.storeFile(file, "Sanu", "audio")
   val storedFile = File(result.filePath!!)
   assert(storedFile.exists())  // Debug assertion
   ```

2. Check database record:
   ```kotlin
   val message = messageDB.getMessage(messageId)
   Log.d("Debug", "Stored path: ${message.attachmentPath}")
   ```

3. Verify ownership tracking:
   ```kotlin
   val owner = ownershipManager.determineOwner(file)
   Log.d("Debug", "File owner: $owner")
   ```

---

### Issue: PC backup not creating index.json

**Causes:**
- PC not detected as connected
- Permission denied writing to backup path
- Index generation failed

**Solutions:**
1. Check PC connection:
   ```kotlin
   val isConnected = storageSystem.pcBackupManager.isConnectedToPC()
   Log.d("Debug", "PC connected: $isConnected")
   ```

2. Verify backup path:
   ```kotlin
   val backupRoot = storageSystem.getSystemStatus().backupRoot
   Log.d("Debug", "Backup path: $backupRoot")
   ```

3. Check file permissions (on PC):
   ```bash
   ls -la /CalcvaultBackup/
   chmod 777 /CalcvaultBackup/
   ```

4. Check backup logs:
   ```kotlin
   val result = storageSystem.performPCBackup { }
   if (!result.success) {
       result.errors.forEach { Log.e("Backup", it) }
   }
   ```

---

### Issue: Sanu sees "Access Denied" for all Zain files

**Causes:**
- PC backup never completed
- Remote file references not synced
- Access state not updated

**Solutions:**
1. Trigger PC backup:
   ```kotlin
   val result = storageSystem.performPCBackup { progress ->
       Log.d("Backup", "${progress.getProgressPercentage()}% complete")
   }
   ```

2. Verify sync:
   ```kotlin
   val refs = storageSystem.remoteFileRefManager.getAllRefs()
   Log.d("Debug", "Remote refs count: ${refs.size}")
   ```

3. Check access states:
   ```kotlin
   val lockedRefs = storageSystem.remoteFileRefManager.getLockedRefs()
   val availableRefs = storageSystem.remoteFileRefManager.getAvailableRefs()
   Log.d("Debug", "Locked: ${lockedRefs.size}, Available: ${availableRefs.size}")
   ```

---

### Issue: Quota exceeded even after deleting files

**Causes:**
- Orphaned files not cleaned up
- Symlinks not removed
- Database not updated

**Solutions:**
1. Cleanup orphaned files:
   ```kotlin
   val result = storageSystem.quotaManager.cleanupOrphaned(daysOld = 7)
   Log.d("Debug", "Removed ${result.filesRemoved} orphaned files")
   ```

2. Recalculate usage:
   ```kotlin
   val usedSpace = storageSystem.quotaManager.calculateUsedSpace("Sanu")
   Log.d("Debug", "Used space: ${quotaManager.formatSize(usedSpace)}")
   ```

3. Clear cache:
   ```kotlin
   val cacheDir = context.cacheDir
   cacheDir.deleteRecursively()
   ```

---

### Issue: Thumbnails not generating

**Causes:**
- Unsupported file format
- Media file corrupted
- Insufficient memory for large files

**Solutions:**
1. Check file type support:
   ```kotlin
   val supported = thumbnailGenerator.supportsThumbnail(file)
   Log.d("Debug", "Thumbnail supported: $supported")
   ```

2. Verify file integrity:
   ```bash
   ffprobe /path/to/file.mp4  # Check if valid media file
   ```

3. Check available memory:
   ```kotlin
   val runtime = Runtime.getRuntime()
   val usedMemory = runtime.totalMemory() - runtime.freeMemory()
   Log.d("Debug", "Used memory: $usedMemory bytes")
   ```

4. Generate with error handling:
   ```kotlin
   try {
       val thumbnail = thumbnailGenerator.generateThumbnail(file)
       if (thumbnail != null) {
           // Save and use
       } else {
           Log.w("Thumbnail", "Returned null bitmap")
       }
   } catch (e: Exception) {
       Log.e("Thumbnail", "Generation failed", e)
   }
   ```

---

### Issue: Large file takes forever to store

**Causes:**
- Slow storage (microSD card)
- Background processes competing
- File very large (> 10GB)

**Solutions:**
1. Use async/coroutines:
   ```kotlin
   lifecycleScope.launch(Dispatchers.IO) {
       val result = storageSystem.storeFile(file, "Sanu", "audio")
   }
   ```

2. Monitor progress:
   ```kotlin
   Thread {
       val startTime = System.currentTimeMillis()
       val result = storageSystem.storeFile(file, "Sanu", "video")
       val duration = System.currentTimeMillis() - startTime
       Log.d("Perf", "Storage took ${duration}ms")
   }.start()
   ```

3. Check storage speed:
   ```bash
   adb shell dd if=/dev/zero bs=1M count=100 of=/sdcard/test.bin
   # Check time and calculate MB/s
   ```

---

### Issue: Index.json malformed or corrupted

**Causes:**
- Disk error during write
- Process killed mid-backup
- Encoding issue

**Solutions:**
1. Validate JSON:
   ```kotlin
   val json = File("/CalcvaultBackup/index.json").readText()
   try {
       val parsed = Gson().fromJson(json, JsonObject::class.java)
       Log.d("Debug", "JSON valid")
   } catch (e: JsonSyntaxException) {
       Log.e("Debug", "JSON invalid", e)
   }
   ```

2. Regenerate index:
   ```kotlin
   val result = storageSystem.performPCBackup { }
   // Creates new index.json
   ```

3. Restore from backup:
   ```bash
   cp /CalcvaultBackup/index.json.backup /CalcvaultBackup/index.json
   ```

---

### Issue: Remote file reference not syncing

**Causes:**
- Sync process not triggered
- Index not loaded
- Reference database corrupted

**Solutions:**
1. Manually sync:
   ```kotlin
   val index = storageSystem.backupIndexer.loadIndex("/CalcvaultBackup")
   if (index != null) {
       val updated = storageSystem.remoteFileRefManager.syncWithBackupIndex(index)
       Log.d("Debug", "Updated $updated references")
   }
   ```

2. Check reference database:
   ```kotlin
   val refsDb = File("/calcvault/storage/remote_refs.json")
   val json = refsDb.readText()
   Log.d("Debug", "Refs JSON: $json")
   ```

3. Reload references:
   ```kotlin
   storageSystem.remoteFileRefManager.clear()
   storageSystem.remoteFileRefManager.load()
   ```

---

## 🔧 Debug Logging

### Enable Detailed Logging

```kotlin
// In ZainSanuStorageSystem
companion object {
    var DEBUG = true  // Set to true for verbose logging
}

// Then logs appear as:
// D/CalcVault: ✓ Storage initialized
// D/CalcVault: [storeFile] Processing recording.m4a (45MB)
// D/CalcVault: [determineOwner] File owner: SANU
// D/CalcVault: [storeFile] Saved to /calcvault/storage/Sanu/media/audio/
```

### Check System Status

```kotlin
val status = storageSystem.getSystemStatus()
Log.d("System", "Storage initialized: ${status.isInitialized}")
Log.d("System", "Backup exists: ${status.backupExists}")
Log.d("System", "Storage paths: ${status.storagePaths}")

val summary = storageSystem.getSystemSummary()
Log.d("Summary", summary)
```

### Monitor Quota Usage

```kotlin
val quotas = storageSystem.quotaManager.getAllQuotas()
quotas.forEach { (owner, quota) ->
    Log.d("Quota", "$owner: ${quota.usedSpace} / ${quota.totalQuota} bytes")
}
```

---

## ✅ Verification Checklist

- [ ] All directories created successfully
- [ ] Permissions granted
- [ ] First file stored without errors
- [ ] Thumbnail generated
- [ ] Index.json valid JSON
- [ ] PC backup completes
- [ ] Remote file references created
- [ ] Access control working
- [ ] Quota enforcement active
- [ ] No memory leaks detected

---

## 📞 Support Information

### Debug Info to Collect

When reporting issues, provide:
1. Logcat output (errors & warnings)
2. System status from `getSystemSummary()`
3. File size and type
4. User attempting operation
5. Android version and device
6. Available storage space

### Get Debug Package

```kotlin
fun getDebugInfo(): String {
    val status = storageSystem.getSystemStatus()
    val quotas = storageSystem.quotaManager.getAllQuotas()
    val summary = storageSystem.getSystemSummary()
    
    return """
        System Status:
        ${status}
        
        Quotas:
        ${quotas}
        
        Summary:
        ${summary}
    """.trimIndent()
}
```

---

**Status**: ✅ COMPLETE - Comprehensive troubleshooting guide ready
