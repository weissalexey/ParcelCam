package com.carstensen.parcelcam.upload

import android.content.Context
import com.carstensen.parcelcam.data.AppSettings
import com.carstensen.parcelcam.data.UploadMethod

object UploaderFactory {

    /**
     * Returns an uploader implementation based on current app settings.
     *
     * Notes:
     * - FTP_ONLY: uses plain FTP on port 21 (no TLS).
     * - SHARE:   uses SMB share uploader.
     * - LIS_CAMERA: upload is handled outside of ParcelCam, so we keep uploader disabled.
     */
    fun create(context: Context, s: AppSettings): Uploader {
        return when (s.method) {
            UploadMethod.FTP_ONLY -> FtpUploader(s, ftps = false)
            UploadMethod.SHARE -> SmbUploader(context, s)
            UploadMethod.LIS_CAMERA -> DisabledUploader(context)
        }
    }
}
