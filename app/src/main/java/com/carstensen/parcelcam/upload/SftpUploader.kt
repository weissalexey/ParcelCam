package com.carstensen.parcelcam.upload

import android.content.Context
import com.carstensen.parcelcam.data.AppSettings
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import java.io.File

class SftpUploader(
    private val context: Context,
    private val s: AppSettings
) : Uploader {

    private fun port(): Int = if (s.port > 0) s.port else 22

    private fun withClient(block: (SSHClient) -> Unit) {
        val ssh = SSHClient()
        // NOTE: PromiscuousVerifier accepts all host keys.
        // For production, pin host keys. Kept simple for MVP.
        ssh.addHostKeyVerifier(PromiscuousVerifier())
        ssh.connect(s.server, port())
        ssh.authPassword(s.username, s.password)
        try {
            block(ssh)
        } finally {
            runCatching { ssh.disconnect() }
            runCatching { ssh.close() }
        }
    }

    override suspend fun testConnection(): Result<Unit> = runCatching {
        withClient { ssh ->
            ssh.newSFTPClient().use { sftp ->
                val path = s.remotePath.trim()
                if (path.isNotBlank()) {
                    runCatching { sftp.mkdir(path) }
                }
            }
        }
        Unit
    }

    override suspend fun uploadFiles(files: List<File>, remoteBaseName: String): Result<Unit> = runCatching {
        withClient { ssh ->
            ssh.newSFTPClient().use { sftp ->
                val path = s.remotePath.trim().trimEnd('/')
                if (path.isNotBlank()) runCatching { sftp.mkdir(path) }
                for (f in files) {
                    val remote = if (path.isBlank()) f.name else "$path/${f.name}"
                    sftp.put(f.absolutePath, remote)
                }
            }
        }
        Unit
    }
}
