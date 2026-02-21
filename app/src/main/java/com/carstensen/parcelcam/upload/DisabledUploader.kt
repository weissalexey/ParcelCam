package com.carstensen.parcelcam.upload

import android.content.Context
import java.io.File

/**
 * Uploader stub used when upload is intentionally disabled (e.g. LIS camera bridge mode).
 */
class DisabledUploader(
    @Suppress("UNUSED_PARAMETER") private val context: Context
) : Uploader {

    override suspend fun testConnection(): Result<Unit> {
        return Result.failure(IllegalStateException("Upload disabled"))
    }

    override suspend fun uploadFiles(files: List<File>): Result<Unit> {
        return Result.failure(IllegalStateException("Upload disabled"))
    }
}
