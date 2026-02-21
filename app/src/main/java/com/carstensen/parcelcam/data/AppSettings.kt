package com.carstensen.parcelcam.data

/**
 * Default intent:// template for LIS Kamera app.
 *
 * You can change it in Settings -> LIS template. Placeholders supported:
 *  - {baseName} : sanitized baseName (A-Z a-z 0-9 _ - .)
 *  - {timestamp} : yyyyMMdd_HHmmss
 */
const val DEFAULT_LIS_INTENT_URI_TEMPLATE: String = "intent:#Intent;component=eu.lis.tssphoto/.MainActivity;S.baseName={baseName};S.posturl=;end"

data class AppSettings(
    val requiredPhotos: Int = 5,
    val saveToGallery: Boolean = false,
    val deleteLocalAfterUpload: Boolean = true,

    val jpegQuality: Int = 80,
    val maxResolution: MaxResolution = MaxResolution.PX_2048,

    val method: UploadMethod = UploadMethod.SMB,

    // SMB/FTP common
    val server: String = "",
    val share: String = "",      // for SMB
    val remotePath: String = "", // subfolder
    val domain: String = "",
    val username: String = "",
    val password: String = "",

    // optional ports for FTP/FTPS/SFTP
    val port: Int = 0,

    // LIS Kamera (external app bridge)
    val lisIntentUriTemplate: String = DEFAULT_LIS_INTENT_URI_TEMPLATE
)
