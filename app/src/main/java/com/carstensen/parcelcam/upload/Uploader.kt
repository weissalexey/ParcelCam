package com.carstensen.parcelcam.upload

import java.io.File

interface Uploader {
    suspend fun testConnection(): Result<Unit>
    suspend fun uploadFiles(files: List<File>, remoteBaseName: String): Result<Unit>
}
