package com.carstensen.parcelcam.ui

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.carstensen.parcelcam.R
import com.carstensen.parcelcam.data.*
import com.carstensen.parcelcam.databinding.ActivitySettingsBinding
import com.carstensen.parcelcam.upload.UploaderFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : AppCompatActivity() {

    private lateinit var vb: ActivitySettingsBinding
    private lateinit var store: SettingsStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vb = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(vb.root)

        store = SettingsStore(this)

        val methodLabels = listOf(
            getString(R.string.method_smb),
            getString(R.string.method_ftp),
            getString(R.string.method_ftps),
            getString(R.string.method_sftp),
            getString(R.string.method_lis)
        )
        vb.ddMethod.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, methodLabels))

        val maxResLabels = listOf("Original", "2048", "1600", "1280")
        vb.ddMaxRes.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, maxResLabels))


        vb.ddMethod.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val m = methodFromLabel(vb.ddMethod.text.toString())
                updateVisibility(m)
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        lifecycleScope.launch {
            val s = store.settingsFlow.first()
            bind(s)
        }

        vb.sbJpegQuality.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                val q = progress.coerceIn(1, 100)
                vb.tvJpegQuality.text = q.toString()
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
        })

        vb.btnTest.setOnClickListener { testConnection() }
        vb.btnSave.setOnClickListener { save() }
    }

    private fun bind(s: AppSettings) {
        vb.etRequiredPhotos.setText(s.requiredPhotos.toString())
        vb.swSaveToGallery.isChecked = s.saveToGallery
        vb.swDeleteLocal.isChecked = s.deleteLocalAfterUpload

        vb.sbJpegQuality.progress = s.jpegQuality
        vb.tvJpegQuality.text = s.jpegQuality.toString()
        vb.ddMaxRes.setText(MaxResolution.toLabel(s.maxResolution), false)

        vb.ddMethod.setText(methodLabel(s.method), false)

        vb.etServer.setText(s.server)
        vb.etShare.setText(s.share)
        vb.etRemotePath.setText(s.remotePath)
        vb.etDomain.setText(s.domain)
        vb.etUser.setText(s.username)
        vb.etPass.setText(s.password)
        vb.etLisIntentTemplate.setText(s.lisIntentUriTemplate)

        updateVisibility(s.method)
    }

    private fun methodLabel(m: UploadMethod): String = when (m) {
        UploadMethod.SMB -> getString(R.string.method_smb)
        UploadMethod.FTP -> getString(R.string.method_ftp)
        UploadMethod.LIS_CAMERA -> getString(R.string.method_lis)
    }

    private fun methodFromLabel(label: String): UploadMethod = when (label) {
        getString(R.string.method_ftp) -> UploadMethod.FTP
        getString(R.string.method_lis) -> UploadMethod.LIS_CAMERA
        else -> UploadMethod.SMB
    }



    private fun updateVisibility(m: UploadMethod) {
        val isLis = m == UploadMethod.LIS_CAMERA

        vb.groupLisSettings.visibility = if (isLis) android.view.View.VISIBLE else android.view.View.GONE

        // Hide upload credential fields when LIS is selected
        val v = if (isLis) android.view.View.GONE else android.view.View.VISIBLE
        vb.etServer.visibility = v
        vb.etShare.visibility = v
        vb.etRemotePath.visibility = v
        vb.etDomain.visibility = v
        vb.etUser.visibility = v
        vb.etPass.visibility = v
    }
    private fun readUi(): AppSettings {
        val required = vb.etRequiredPhotos.text.toString().toIntOrNull()?.coerceAtLeast(1) ?: 5
        val saveToGallery = vb.swSaveToGallery.isChecked
        val deleteLocal = vb.swDeleteLocal.isChecked
        val jpegQ = vb.tvJpegQuality.text.toString().toIntOrNull()?.coerceIn(1, 100) ?: 80
        val maxRes = MaxResolution.fromLabel(vb.ddMaxRes.text.toString().ifBlank { "2048" })

        val method = methodFromLabel(vb.ddMethod.text.toString())
        val server = vb.etServer.text.toString().trim()
        val share = vb.etShare.text.toString().trim()
        val path = vb.etRemotePath.text.toString().trim()
        val domain = vb.etDomain.text.toString().trim()
        val user = vb.etUser.text.toString().trim()
        val pass = vb.etPass.text.toString()
        val lisTemplate = vb.etLisIntentTemplate.text.toString().trim()

        return AppSettings(
            requiredPhotos = required,
            saveToGallery = saveToGallery,
            deleteLocalAfterUpload = deleteLocal,
            jpegQuality = jpegQ,
            maxResolution = maxRes,
            method = method,
            server = server,
            share = share,
            remotePath = path,
            domain = domain,
            username = user,
            password = pass
        )
    }

    private fun save() {
        lifecycleScope.launch {
            val s = readUi()
            store.save(s)
            Toast.makeText(this@SettingsActivity, "Saved", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun testConnection() {
        lifecycleScope.launch {
            val s = readUi()
            val uploader = UploaderFactory.create(this@SettingsActivity, s)
            val r = withContext(Dispatchers.IO) { uploader.testConnection() }
            if (r.isSuccess) {
                Toast.makeText(this@SettingsActivity, "Connection OK", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@SettingsActivity, "Connection failed: ${r.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
