package com.calcvault.companion

object CompanionBridgeConstants {
    const val BRIDGE_PORT = 37111
    const val SIGNALING_PORT = 37112
    const val BRIDGE_PATH = "/bridge"
    const val PROTOCOL_VERSION = 1

    const val PAIRING_CODE_LENGTH = 8
    const val PAIRING_TTL_MS = 5 * 60 * 1000L
    const val SESSION_TTL_MS = 15 * 60 * 1000L
    const val OP_TIMEOUT_MS = 60 * 1000L
    const val CHUNK_SIZE_BYTES = 256 * 1024

    const val ACTION_START = "com.calcvault.companion.START"
    const val ACTION_STOP = "com.calcvault.companion.STOP"

    const val EXTRA_BIND_LAN = "bind_lan"
    const val EXTRA_AUTO_APPROVE = "auto_approve"

    const val OP_BACKUP_CREATE = "backup.create"
    const val OP_BACKUP_RESTORE = "backup.restore"
    const val OP_FILES_LIST = "files.list"
    const val OP_FILES_UPLOAD = "files.upload"
    const val OP_FILES_DOWNLOAD = "files.download"

    val SUPPORTED_OPS = setOf(
        OP_BACKUP_CREATE,
        OP_BACKUP_RESTORE,
        OP_FILES_LIST,
        OP_FILES_UPLOAD,
        OP_FILES_DOWNLOAD
    )
}
