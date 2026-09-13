package com.calcvault.storage.ingestion

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONObject

data class VaultFile(
    val id: String,
    val name: String,
    val size: Long,
    val type: String,
    val encryptedPath: String,
    val thumbnailPath: String?,
    val createdAt: Long,
    val modifiedAt: Long,
    val hash: String? = null,
    val mimeType: String? = null
) {
    fun toJson() = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("size", size)
        put("type", type)
        put("encPath", encryptedPath)
        put("thumbPath", thumbnailPath)
        put("created", createdAt)
        put("modified", modifiedAt)
        hash?.let { put("hash", it) }
        mimeType?.let { put("mime", it) }
    }.toString()

    companion object {
        fun fromJson(json: String) = JSONObject(json).let { j ->
            VaultFile(
                id = j.getString("id"),
                name = j.getString("name"),
                size = j.getLong("size"),
                type = j.getString("type"),
                encryptedPath = j.getString("encPath"),
                thumbnailPath = j.optString("thumbPath").takeIf { it.isNotEmpty() },
                createdAt = j.getLong("created"),
                modifiedAt = j.getLong("modified"),
                hash = j.optString("hash").takeIf { it.isNotEmpty() },
                mimeType = j.optString("mime").takeIf { it.isNotEmpty() }
            )
        }
    }
}

class VaultFileIndex(context: Context) : SQLiteOpenHelper(context, "vault_index.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE vault_files (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                size INTEGER NOT NULL,
                type TEXT NOT NULL,
                enc_path TEXT NOT NULL,
                thumb_path TEXT,
                created_at INTEGER NOT NULL,
                modified_at INTEGER NOT NULL,
                hash TEXT,
                mime_type TEXT,
                indexed_at INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL("CREATE INDEX idx_name ON vault_files(name)")
        db.execSQL("CREATE INDEX idx_type ON vault_files(type)")
        db.execSQL("CREATE INDEX idx_created ON vault_files(created_at)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    fun addFile(file: VaultFile): Boolean {
        return try {
            val db = writableDatabase
            val values = android.content.ContentValues().apply {
                put("id", file.id)
                put("name", file.name)
                put("size", file.size)
                put("type", file.type)
                put("enc_path", file.encryptedPath)
                put("thumb_path", file.thumbnailPath)
                put("created_at", file.createdAt)
                put("modified_at", file.modifiedAt)
                put("hash", file.hash)
                put("mime_type", file.mimeType)
                put("indexed_at", System.currentTimeMillis())
            }
            db.insert("vault_files", null, values) > 0
        } catch (e: Exception) {
            false
        }
    }

    fun getFile(id: String): VaultFile? {
        return try {
            val db = readableDatabase
            val cursor = db.query(
                "vault_files",
                null,
                "id = ?",
                arrayOf(id),
                null,
                null,
                null
            )
            cursor.use {
                if (it.moveToFirst()) {
                    VaultFile(
                        id = it.getString(it.getColumnIndexOrThrow("id")),
                        name = it.getString(it.getColumnIndexOrThrow("name")),
                        size = it.getLong(it.getColumnIndexOrThrow("size")),
                        type = it.getString(it.getColumnIndexOrThrow("type")),
                        encryptedPath = it.getString(it.getColumnIndexOrThrow("enc_path")),
                        thumbnailPath = it.getString(it.getColumnIndexOrThrow("thumb_path")).takeIf { s -> s.isNotEmpty() },
                        createdAt = it.getLong(it.getColumnIndexOrThrow("created_at")),
                        modifiedAt = it.getLong(it.getColumnIndexOrThrow("modified_at")),
                        hash = it.getString(it.getColumnIndexOrThrow("hash")).takeIf { s -> s.isNotEmpty() },
                        mimeType = it.getString(it.getColumnIndexOrThrow("mime_type")).takeIf { s -> s.isNotEmpty() }
                    )
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    fun getAllFiles(): List<VaultFile> {
        return try {
            val db = readableDatabase
            val cursor = db.query("vault_files", null, null, null, null, null, "created_at DESC")
            cursor.use {
                val files = mutableListOf<VaultFile>()
                while (it.moveToNext()) {
                    files.add(
                        VaultFile(
                            id = it.getString(it.getColumnIndexOrThrow("id")),
                            name = it.getString(it.getColumnIndexOrThrow("name")),
                            size = it.getLong(it.getColumnIndexOrThrow("size")),
                            type = it.getString(it.getColumnIndexOrThrow("type")),
                            encryptedPath = it.getString(it.getColumnIndexOrThrow("enc_path")),
                            thumbnailPath = it.getString(it.getColumnIndexOrThrow("thumb_path")).takeIf { s -> s.isNotEmpty() },
                            createdAt = it.getLong(it.getColumnIndexOrThrow("created_at")),
                            modifiedAt = it.getLong(it.getColumnIndexOrThrow("modified_at")),
                            hash = it.getString(it.getColumnIndexOrThrow("hash")).takeIf { s -> s.isNotEmpty() },
                            mimeType = it.getString(it.getColumnIndexOrThrow("mime_type")).takeIf { s -> s.isNotEmpty() }
                        )
                    )
                }
                files
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getFilesByType(type: String): List<VaultFile> {
        return try {
            val db = readableDatabase
            val cursor = db.query(
                "vault_files",
                null,
                "type = ?",
                arrayOf(type),
                null,
                null,
                "created_at DESC"
            )
            cursor.use {
                val files = mutableListOf<VaultFile>()
                while (it.moveToNext()) {
                    files.add(
                        VaultFile(
                            id = it.getString(it.getColumnIndexOrThrow("id")),
                            name = it.getString(it.getColumnIndexOrThrow("name")),
                            size = it.getLong(it.getColumnIndexOrThrow("size")),
                            type = it.getString(it.getColumnIndexOrThrow("type")),
                            encryptedPath = it.getString(it.getColumnIndexOrThrow("enc_path")),
                            thumbnailPath = it.getString(it.getColumnIndexOrThrow("thumb_path")).takeIf { s -> s.isNotEmpty() },
                            createdAt = it.getLong(it.getColumnIndexOrThrow("created_at")),
                            modifiedAt = it.getLong(it.getColumnIndexOrThrow("modified_at")),
                            hash = it.getString(it.getColumnIndexOrThrow("hash")).takeIf { s -> s.isNotEmpty() },
                            mimeType = it.getString(it.getColumnIndexOrThrow("mime_type")).takeIf { s -> s.isNotEmpty() }
                        )
                    )
                }
                files
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun fileExists(hash: String): Boolean {
        return try {
            val db = readableDatabase
            val cursor = db.query(
                "vault_files",
                arrayOf("id"),
                "hash = ?",
                arrayOf(hash),
                null,
                null,
                null
            )
            cursor.use { it.count > 0 }
        } catch (e: Exception) {
            false
        }
    }

    fun getTotalSize(): Long {
        return try {
            val db = readableDatabase
            val cursor = db.rawQuery("SELECT SUM(size) FROM vault_files", null)
            cursor.use {
                if (it.moveToFirst()) it.getLong(0) else 0L
            }
        } catch (e: Exception) {
            0L
        }
    }

    fun deleteFile(id: String): Boolean {
        return try {
            val db = writableDatabase
            db.delete("vault_files", "id = ?", arrayOf(id)) > 0
        } catch (e: Exception) {
            false
        }
    }
}
