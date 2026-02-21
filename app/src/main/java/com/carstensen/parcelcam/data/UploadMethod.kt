package com.carstensen.parcelcam.data

enum class UploadMethod {
    /** Upload captured images via FTP to a remote server. */
    FTP,

    /** Upload captured images via SMB/CIFS to a Windows share. */
    SMB,

    /**
     * Do not upload from ParcelCam. Instead, when ParcelCam is launched from a browser via deep link,
     * it will forward the request to the external LIS Kamera app using an explicit intent/template.
     */
    LIS_CAMERA,
}
