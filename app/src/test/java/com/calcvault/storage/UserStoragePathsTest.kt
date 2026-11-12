package com.calcvault.storage

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UserStoragePathsTest {
    @Test
    fun pairedUserIdentifiersResolveToStablePrivateStorageDirectories() {
        val root = Files.createTempDirectory("calcvault-storage-test").toFile()
        try {
            val zain = UserStoragePaths.forFile(root, "USER_A", "video", "clip.mp4", "file_abc")
            val sanu = UserStoragePaths.forFile(root, "Sanu", "image", "photo.jpg", "file_def")
            val shared = UserStoragePaths.forFile(root, "USER_B", "chat", "chat.json")

            assertTrue(zain.path.startsWith(File(root, "Zain").canonicalPath + File.separator))
            assertTrue(sanu.path.startsWith(File(root, "Sanu").canonicalPath + File.separator))
            assertTrue(shared.path.startsWith(File(root, "shared").canonicalPath + File.separator))
            assertTrue(zain.path.startsWith(root.canonicalPath + File.separator))
            assertEquals("file_abc_clip.mp4", zain.name)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun fileNamesCannotEscapeTheStorageRootAndSameNamesCanBeUnique() {
        val root = Files.createTempDirectory("calcvault-storage-safety").toFile()
        try {
            val first = UserStoragePaths.forFile(root, "shared", "image", "../photo.jpg", "file_one")
            val second = UserStoragePaths.forFile(root, "shared", "image", "../photo.jpg", "file_two")
            assertTrue(first.canonicalPath.startsWith(root.canonicalPath + File.separator))
            assertTrue(second.canonicalPath.startsWith(root.canonicalPath + File.separator))
            assertTrue(first.name != second.name)
        } finally {
            root.deleteRecursively()
        }
    }
}
