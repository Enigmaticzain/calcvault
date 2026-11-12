package com.calcvault.storage

import java.io.File

/** Builds owner-scoped paths beneath the app-private storage root. */
object UserStoragePaths {
    private val allowedTypes = setOf("audio", "video", "image", "other", "chat", "recordings", "documents")

    fun forFile(
        storageRoot: File,
        owner: String,
        fileType: String,
        fileName: String,
        uniqueId: String? = null
    ): File {
        val ownerDirectory = when (owner.trim().lowercase()) {
            "zain", "user_a", "a" -> "Zain/large_media"
            "sanu", "user_b", "b" -> "Sanu/media"
            "shared" -> "shared"
            else -> throw IllegalArgumentException("Unknown paired-file owner")
        }
        val type = fileType.trim().lowercase().takeIf { it in allowedTypes } ?: "other"
        val safeName = File(fileName).name
        require(safeName.isNotBlank() && safeName != "." && safeName != "..") { "Invalid file name" }
        val safeId = uniqueId?.trim()?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{1,80}")) }
        val storedName = if (safeId == null) safeName else "${safeId}_$safeName"
        val root = storageRoot.canonicalFile
        val destination = File(root, "$ownerDirectory/$type/$storedName").canonicalFile
        require(destination.path.startsWith(root.path + File.separator)) { "Storage path escapes app data directory" }
        return destination
    }

    fun ownerDirectory(owner: String): String = when (owner.trim().lowercase()) {
        "zain", "user_a", "a" -> "Zain"
        "sanu", "user_b", "b" -> "Sanu"
        "shared" -> "shared"
        else -> throw IllegalArgumentException("Unknown paired-file owner")
    }
}
