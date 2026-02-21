package com.carstensen.parcelcam.upload

import android.content.Context
import com.carstensen.parcelcam.data.AppSettings
import jcifs.CIFSContext
import jcifs.context.SingletonContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbFile
import java.io.File

class SmbUploader(
    private val context: Context,
    private val s: AppSettings
) : Uploader {

    private fun cifs(): CIFSContext {
        // Support: DOMAIN\user, user@domain.tld and (domain field + user field).
        val rawUser = s.username.trim()
        val rawDomain = s.domain.trim().takeIf { it.isNotBlank() }

        val (domain, user) = when {
            // UPN style: keep as-is, domain is not used.
            rawUser.contains("@") -> null to rawUser

            // Windows style: DOMAIN\user (or DOMAIN/user)
            rawUser.contains("\\") -> {
                val parts = rawUser.split("\\", limit = 2)
                parts.getOrNull(0)?.takeIf { it.isNotBlank() } to (parts.getOrNull(1) ?: rawUser)
            }
            rawUser.contains("/") -> {
                val parts = rawUser.split("/", limit = 2)
                parts.getOrNull(0)?.takeIf { it.isNotBlank() } to (parts.getOrNull(1) ?: rawUser)
            }

            else -> rawDomain to rawUser
        }

        val auth = NtlmPasswordAuthenticator(domain, user, s.password)
        val base = SingletonContext.getInstance()
        return base.withCredentials(auth)
    }

    private fun rootUrl(): String {
        // smb://server/share/path/
        // We accept either:
        //  - Server="srv" + Share="ShareName"
        //  - Server=""  + Share="\\\\srv\\ShareName" (UNC style)
        // so users don't get stuck on formatting.

        val server0 = s.server.trim().removeSuffix("/")
        val share0 = s.share.trim()

        val (server, share) = if (share0.startsWith("\\\\")) {
            val t = share0.removePrefix("\\\\").trim('\u005c').split('\\')
            val host = t.getOrNull(0).orEmpty()
            val sh = t.getOrNull(1).orEmpty()
            val resolvedServer = if (server0.isBlank()) host else server0
            resolvedServer to sh
        } else if (share0.startsWith("smb://", ignoreCase = true)) {
            // smb://srv/share/...  -> take first 2 segments
            val noScheme = share0.removePrefix("smb://")
            val parts = noScheme.split('/').filter { it.isNotBlank() }
            val host = parts.getOrNull(0).orEmpty()
            val sh = parts.getOrNull(1).orEmpty()
            val resolvedServer = if (server0.isBlank()) host else server0
            resolvedServer to sh
        } else {
            server0 to share0.trim('/').trim()
        }

        val path = s.remotePath.trim().trim('/')
        return if (path.isBlank()) {
            "smb://$server/$share/"
        } else {
            "smb://$server/$share/$path/"
        }
    }

    override suspend fun testConnection(): Result<Unit> = runCatching {
        val ctx = cifs()
        val root = SmbFile(rootUrl(), ctx)
        if (!root.exists()) {
            // Try to create folder path if possible
            root.mkdirs()
        }
        Unit
    }

    override suspend fun uploadFiles(files: List<File>, remoteBaseName: String): Result<Unit> = runCatching {
        val ctx = cifs()
        val root = SmbFile(rootUrl(), ctx)
        if (!root.exists()) root.mkdirs()

        for (f in files) {
    val dest = SmbFile(root, f.name)
    val ins = f.inputStream()
    val outs = dest.outputStream
    try {
        ins.copyTo(outs)
    } finally {
        runCatching { ins.close() }
        runCatching { outs.close() }
    }
}
        Unit
    }
}
