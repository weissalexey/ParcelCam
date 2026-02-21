package com.carstensen.parcelcam.ui

import android.Manifest
import android.content.Intent
import android.content.ComponentName
import android.os.Build
import com.carstensen.parcelcam.data.UploadMethod
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.carstensen.parcelcam.AppConstants
import com.carstensen.parcelcam.R
import com.carstensen.parcelcam.data.AppSettings
import com.carstensen.parcelcam.data.SettingsStore
import com.carstensen.parcelcam.databinding.ActivityCameraBinding
import com.carstensen.parcelcam.upload.UploaderFactory
import com.carstensen.parcelcam.util.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.withContext
import java.io.File

class CameraActivity : AppCompatActivity() {

    private lateinit var vb: ActivityCameraBinding
    private lateinit var settingsStore: SettingsStore

    private var imageCapture: ImageCapture? = null
    private var lensFacing: Int = CameraSelector.LENS_FACING_BACK

    private var requiredCount: Int = 5
    private var currentBaseName: String = ""
    private val sessionFiles = mutableListOf<File>()

    private fun timestampForFallback(): String {
        return SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    }

    private fun isLaunchedFromBrowser(): Boolean {
        val i = intent ?: return false

        // When opened from a browser, the intent often has CATEGORY_BROWSABLE.
        val hasBrowsable = i.categories?.contains(Intent.CATEGORY_BROWSABLE) == true
        if (hasBrowsable) return true

        // Some enterprise/MDM setups strip categories; check referrer as fallback.
        return try {
            val ref = if (android.os.Build.VERSION.SDK_INT >= 22) referrer else null
            val refStr = ref?.toString() ?: ""
            refStr.startsWith("http://") || refStr.startsWith("https://") || refStr.startsWith("android-app://")
        } catch (_: Exception) {
            false
        }
    }

    private fun buildLisIntent(template: String, rawBaseName: String): Intent? {
        val ts = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())
        val safe = sanitizeBaseName(rawBaseName)
        val filled = template
            .replace("{baseName}", safe)
            .replace("{rawBaseName}", rawBaseName)
            .replace("{ts}", ts)

        return try {
            // Template can be standard intent://... or just parcelcam://...
            if (filled.startsWith("intent:", ignoreCase = true)) {
                Intent.parseUri(filled, Intent.URI_INTENT_SCHEME)
            } else {
                Intent(Intent.ACTION_VIEW, android.net.Uri.parse(filled))
            }
        } catch (e: Exception) {
            Log.e("ParcelCam", "Failed to parse LIS template intent: ${e.message}")
            null
        }
    }

    private fun launchLisCamera(settings: com.carstensen.parcelcam.data.AppSettings, rawBaseName: String) {
        val tpl = settings.lisIntentUriTemplate.takeIf { it.isNotBlank() }
            ?: com.carstensen.parcelcam.data.DEFAULT_LIS_INTENT_URI_TEMPLATE

        val intent = buildLisIntent(tpl, rawBaseName) ?: run {
            // Safe fallback: explicit component start (from provided LIS sample).
            val fallback = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                component = ComponentName("eu.lis.tssphoto", "eu.lis.tssphoto.MainActivity")
                putExtra("baseName", sanitizeBaseName(rawBaseName))
            }
            fallback
        }

        // Make it more MDM-friendly: explicit package if possible
        if (intent.component == null) {
            intent.`package` = "eu.lis.tssphoto"
        }

        // Ensure we don't crash if not installed
        val resolved = packageManager.resolveActivity(intent, 0)
        if (resolved == null) {
            Toast.makeText(this, "LIS Kamera app not found", Toast.LENGTH_LONG).show()
            return
        }

        Log.d("ParcelCam", "Launching LIS Kamera with baseName=$rawBaseName")
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to launch LIS Kamera", Toast.LENGTH_LONG).show()
            Log.e("ParcelCam", "Failed to launch LIS Kamera", e)
        }
    }
    private fun sanitizeBaseName(raw: String?): String {
        val v = raw?.trim().orEmpty()
        if (v.isBlank()) return "UNKNOWN_${timestampForFallback()}"

        // allowed: A-Z a-z 0-9 _ - .  (everything else -> '_')
        val sanitized = v.replace(Regex("[^A-Za-z0-9_.-]"), "_")
        return if (sanitized.length <= 80) sanitized else sanitized.take(80)
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCamera() else {
                Toast.makeText(this, "Camera permission denied", Toast.LENGTH_LONG).show()
                finish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vb = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(vb.root)

        settingsStore = SettingsStore(this)

        vb.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        vb.btnSwitch.setOnClickListener {
            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK)
                CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
            startCamera()
        }
        vb.btnCapture.setOnClickListener { capture() }

        lifecycleScope.launch {
            // Load settings, plus allow override by Intent extras / deep link
            val s = settingsStore.settingsFlow.first()
            applyLaunchParams(s)

            // MDM-friendly bridge: when launched from a browser, optionally hand off to LIS Kamera.
            if (s.method == UploadMethod.LIS_CAMERA && isLaunchedFromBrowser()) {
                if (currentBaseName.isBlank()) {
                    Toast.makeText(this@CameraActivity, "baseName is missing; LIS Kamera not started", Toast.LENGTH_LONG).show()
                    Log.d("ParcelCam", "LIS launch cancelled: baseName missing")
                    finish()
                    return@launch
                }

                val ok = launchLisCamera(s, currentBaseName)
                Log.d("ParcelCam", "LIS launch result: $ok")
                finish()
                return@launch
            }

            updateCounter()
            ensureCameraPermission()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        lifecycleScope.launch {
            val s = settingsStore.settingsFlow.first()
            applyLaunchParams(s)
            updateCounter()
        }
    }

    private fun applyLaunchParams(s: AppSettings) {
        requiredCount = intent.getIntExtra(AppConstants.EXTRA_REQUIRED_COUNT, s.requiredPhotos)
            .coerceAtLeast(1)

        val fromExtra = intent.getStringExtra(AppConstants.EXTRA_BASENAME)?.trim().orEmpty()
        val fromLink = parseDeepLinkBaseName(intent?.data)

        val raw = when {
            fromExtra.isNotBlank() -> fromExtra
            fromLink.isNotBlank() -> fromLink
            else -> ""
        }

        currentBaseName = sanitizeBaseName(raw)
        Log.d("ParcelCam", "baseName from intent: $raw -> $currentBaseName")
    }

    private fun parseDeepLinkBaseName(uri: Uri?): String {
        if (uri == null) return ""
        if (uri.scheme != AppConstants.DEEPLINK_SCHEME) return ""
        if (uri.host != AppConstants.DEEPLINK_HOST) return ""
        // Primary param: baseName. Keep legacy support for "name".
        return (uri.getQueryParameter("baseName")
            ?: uri.getQueryParameter("name"))
            ?.trim()
            .orEmpty()
    }

    private fun ensureCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(vb.previewView.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build()

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
            } catch (e: Exception) {
                Toast.makeText(this, "Camera start failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun capture() {
        lifecycleScope.launch {
            val s = settingsStore.settingsFlow.first()
            val cap = imageCapture ?: return@launch

            val idx = sessionFiles.size + 1
            val indexStr = idx.toString().padStart(2, '0')
            var fileName = "${currentBaseName}_${indexStr}.jpg"
            var localFile = File(getSessionDir(), fileName)
            if (localFile.exists()) {
                // never overwrite
                fileName = "${currentBaseName}_${indexStr}_${System.currentTimeMillis()}.jpg"
                localFile = File(getSessionDir(), fileName)
            }

            Log.d("ParcelCam", "saving photo: ${localFile.absolutePath}")

            val outputOptions = ImageCapture.OutputFileOptions.Builder(localFile).build()
            cap.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(this@CameraActivity),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        lifecycleScope.launch {
                            val ok = postProcessAndStore(localFile, s)
                            if (!ok) return@launch

                            sessionFiles.add(localFile)
                            updateCounter()

                            if (sessionFiles.size >= requiredCount) {
                                uploadAndFinish(s)
                            }
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        Toast.makeText(this@CameraActivity, "Capture failed: ${exception.message}", Toast.LENGTH_LONG).show()
                    }
                }
            )
        }
    }

    private suspend fun postProcessAndStore(file: File, s: AppSettings): Boolean = withContext(Dispatchers.IO) {
        try {
            // Decode to bitmap, apply resize + watermark, then overwrite with compressed JPEG
            val bmp0 = android.graphics.BitmapFactory.decodeFile(file.absolutePath) ?: return@withContext false
            val resized = com.carstensen.parcelcam.util.ImageUtils.resizeLongSide(bmp0, s.maxResolution.longSidePx)
            val stamped = com.carstensen.parcelcam.util.ImageUtils.addTimestampWatermark(resized, ImageUtils.nowStamp())
            com.carstensen.parcelcam.util.ImageUtils.compressJpegToFile(stamped, s.jpegQuality, file)

            if (s.saveToGallery) {
                com.carstensen.parcelcam.util.ImageUtils.saveToGallery(this@CameraActivity, file)
            }
            true
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(this@CameraActivity, "Post-process failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
            false
        }
    }

    private fun getSessionDir(): File {
        val dir = File(filesDir, "sessions/$currentBaseName")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun updateCounter() {
        vb.tvCounter.text = getString(R.string.photos_count, sessionFiles.size, requiredCount)
    }

    private fun lockExit(): Boolean {
        // Block exit if not enough photos.
        return sessionFiles.size < requiredCount
    }

    override fun onBackPressed() {
        if (lockExit()) {
            Toast.makeText(this, "Need ${requiredCount - sessionFiles.size} more photo(s)", Toast.LENGTH_SHORT).show()
            return
        }
        super.onBackPressed()
    }

    private fun uploadAndFinish(s: AppSettings) {
        lifecycleScope.launch {
            vb.btnCapture.isEnabled = false
            vb.btnSettings.isEnabled = false
            vb.btnSwitch.isEnabled = false

            Toast.makeText(this@CameraActivity, getString(R.string.uploading), Toast.LENGTH_SHORT).show()

            val uploader = UploaderFactory.create(this@CameraActivity, s)
            val res = withContext(Dispatchers.IO) { uploader.uploadFiles(sessionFiles.toList(), currentBaseName) }

            if (res.isSuccess) {
                if (s.deleteLocalAfterUpload) {
                    withContext(Dispatchers.IO) {
                        runCatching { getSessionDir().deleteRecursively() }
                    }
                }
                Toast.makeText(this@CameraActivity, getString(R.string.upload_success), Toast.LENGTH_SHORT).show()
                finishAndRemoveTask()
            } else {
                vb.btnCapture.isEnabled = true
                vb.btnSettings.isEnabled = true
                vb.btnSwitch.isEnabled = true
                Toast.makeText(this@CameraActivity, "${getString(R.string.upload_failed)}: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
