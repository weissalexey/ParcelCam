package com.carstensen.parcelcam.upload

import com.carstensen.parcelcam.data.AppSettings
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPSClient
import java.io.File

class FtpUploader(
    private val s: AppSettings,
    private val ftps: Boolean
) : Uploader {

    private fun port(): Int {
        if (s.port > 0) return s.port
        return if (ftps) 990 else 21
    }

    private fun client(): FTPClient {
        val c: FTPClient = if (ftps) FTPSClient() else FTPClient()

        // compatible across commons-net versions (Int vs Duration)
        setTimeoutCompat(c, "setConnectTimeout", 10_000)
        setTimeoutCompat(c, "setDefaultTimeout", 10_000)
        setTimeoutCompat(c, "setDataTimeout", 20_000)

        return c
    }

    override suspend fun testConnection(): Result<Unit> = runCatching {
        val c = client()
        try {
            c.connect(s.server, port())
            if (!c.login(s.username, s.password)) {
                throw IllegalStateException("FTP login failed")
            }
            c.enterLocalPassiveMode()
            c.setFileType(FTP.BINARY_FILE_TYPE)

            ensurePath(c, s.remotePath)

            c.logout()
            Unit
        } finally {
            if (c.isConnected) runCatching { c.disconnect() }
        }
    }

    override suspend fun uploadFiles(files: List<File>, remoteBaseName: String): Result<Unit> = runCatching {
        val c = client()
        try {
            c.connect(s.server, port())
            if (!c.login(s.username, s.password)) {
                throw IllegalStateException("FTP login failed")
            }

            c.enterLocalPassiveMode()
            c.setFileType(FTP.BINARY_FILE_TYPE)

            ensurePath(c, s.remotePath)

            for (f in files) {
                f.inputStream().use { ins ->
                    if (!c.storeFile(f.name, ins)) {
                        throw IllegalStateException("FTP upload failed for ${'$'}{f.name}")
                    }
                }
            }

            c.logout()
            Unit
        } finally {
            if (c.isConnected) runCatching { c.disconnect() }
        }
    }

    private fun ensurePath(c: FTPClient, path: String) {
        val p = path.trim().trim('/')
        if (p.isBlank()) return

        val parts = p.split("/").filter { it.isNotBlank() }
        for (part in parts) {
            if (!c.changeWorkingDirectory(part)) {
                if (!c.makeDirectory(part)) {
                    throw IllegalStateException("Cannot create FTP directory: ${'$'}part")
                }
                if (!c.changeWorkingDirectory(part)) {
                    throw IllegalStateException("Cannot enter FTP directory: ${'$'}part")
                }
            }
        }
    }

    private fun setTimeoutCompat(client: Any, methodName: String, millis: Int) {
        // old commons-net: method(Int)
        try {
            val m = client.javaClass.getMethod(methodName, Int::class.javaPrimitiveType)
            m.invoke(client, millis)
            return
        } catch (_: Throwable) {
        }

        // new commons-net: method(Duration)
        try {
            val durationClass = Class.forName("java.time.Duration")
            val ofMillis = durationClass.getMethod("ofMillis", Long::class.javaPrimitiveType)
            val duration = ofMillis.invoke(null, millis.toLong())
            val m = client.javaClass.getMethod(methodName, durationClass)
            m.invoke(client, duration)
            return
        } catch (e: Throwable) {
            throw IllegalStateException("Cannot set FTP timeout (${ '$' }methodName)", e)
        }
    }
}
