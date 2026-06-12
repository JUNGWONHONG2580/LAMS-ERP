package com.rams.erp

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import kotlinx.coroutines.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class UploadActivity : AppCompatActivity() {

    private var projectId   = 0
    private var projectCode = ""
    private var projectName = ""

    private lateinit var tvProjectTitle: TextView
    private lateinit var tvProjectCode: TextView
    private lateinit var rvPreview: RecyclerView
    private lateinit var btnCamera: Button
    private lateinit var btnGallery: Button
    private lateinit var btnUpload: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvProgress: TextView
    private lateinit var tvCount: TextView
    private lateinit var etUploaderName: EditText

    private val selectedUris = mutableListOf<Uri>()
    private var cameraImageUri: Uri? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // 갤러리 선택
    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            selectedUris.addAll(uris)
            updatePreview()
        }
    }

    // 카메라 촬영
    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && cameraImageUri != null) {
            selectedUris.add(cameraImageUri!!)
            updatePreview()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_upload)

        projectId   = intent.getIntExtra("project_id", 0)
        projectCode = intent.getStringExtra("project_code") ?: ""
        projectName = intent.getStringExtra("project_name") ?: ""

        supportActionBar?.title = "파일 첨부"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        tvProjectTitle = findViewById(R.id.tvProjectTitle)
        tvProjectCode  = findViewById(R.id.tvProjectCode)
        rvPreview      = findViewById(R.id.rvPreview)
        btnCamera      = findViewById(R.id.btnCamera)
        btnGallery     = findViewById(R.id.btnGallery)
        btnUpload      = findViewById(R.id.btnUpload)
        progressBar    = findViewById(R.id.progressBar)
        tvProgress     = findViewById(R.id.tvProgress)
        tvCount        = findViewById(R.id.tvCount)
        etUploaderName = findViewById(R.id.etUploaderName)

        tvProjectTitle.text = projectName
        tvProjectCode.text  = projectCode

        rvPreview.layoutManager = GridLayoutManager(this, 3)

        btnCamera.setOnClickListener  { openCamera() }
        btnGallery.setOnClickListener { galleryLauncher.launch("image/*") }
        btnUpload.setOnClickListener  { confirmAndUpload() }

        updatePreview()
    }

    private fun openCamera() {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName  = "RAMS_${timestamp}.jpg"

        // Android 10+는 MediaStore, 그 이하는 FileProvider
        cameraImageUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/RamsERP")
            }
            contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        } else {
            val file = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), fileName)
            FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        }

        cameraImageUri?.let { cameraLauncher.launch(it) }
    }

    private fun confirmAndUpload() {
        if (selectedUris.isEmpty()) {
            Toast.makeText(this, "파일을 선택하세요.", Toast.LENGTH_SHORT).show()
            return
        }

        val uploaderName = etUploaderName.text.toString().trim().ifBlank { "모바일" }

        AlertDialog.Builder(this)
            .setTitle("업로드 확인")
            .setMessage("${projectName}\n\n${selectedUris.size}개 파일을 업로드하시겠습니까?\n\n업로더: $uploaderName")
            .setPositiveButton("업로드") { _, _ -> startUpload(uploaderName) }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun startUpload(uploaderName: String) {
        btnCamera.isEnabled  = false
        btnGallery.isEnabled = false
        btnUpload.isEnabled  = false
        progressBar.visibility = View.VISIBLE
        tvProgress.visibility  = View.VISIBLE
        tvProgress.text = "준비 중..."

        scope.launch {
            // URI → 임시 파일로 복사
            val tempFiles = withContext(Dispatchers.IO) {
                selectedUris.mapIndexedNotNull { idx, uri ->
                    try {
                        copyUriToTemp(uri, idx)
                    } catch (e: Exception) { null }
                }
            }

            if (tempFiles.isEmpty()) {
                showError("파일 준비 실패")
                resetButtons()
                return@launch
            }

            tvProgress.text = "업로드 중 (0%)..."

            val result = withContext(Dispatchers.IO) {
                ErpApi.uploadFiles(projectId, tempFiles, uploaderName) { progress ->
                    runOnUiThread { tvProgress.text = "업로드 중 ($progress%)..." }
                }
            }

            // 임시 파일 삭제
            withContext(Dispatchers.IO) { tempFiles.forEach { it.delete() } }

            progressBar.visibility = View.GONE

            if (result.ok) {
                tvProgress.text = "✅ ${result.uploaded}개 파일 업로드 완료!"
                selectedUris.clear()
                updatePreview()
                Toast.makeText(this@UploadActivity,
                    "✅ ${result.project_name}에 ${result.uploaded}개 파일 첨부 완료",
                    Toast.LENGTH_LONG).show()
                resetButtons()
            } else {
                showError(result.error.ifBlank { "업로드 실패" })
                resetButtons()
            }
        }
    }

    private fun copyUriToTemp(uri: Uri, index: Int): File {
        val ext = contentResolver.getType(uri)?.let {
            when {
                it.contains("mp4") || it.contains("video") -> ".mp4"
                it.contains("png")  -> ".png"
                else -> ".jpg"
            }
        } ?: ".jpg"

        val cursor = contentResolver.query(uri, arrayOf(MediaStore.Images.Media.DISPLAY_NAME), null, null, null)
        val originalName = cursor?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: "file_${index}${ext}"
        cursor?.close()

        val tempFile = File(cacheDir, "upload_${System.currentTimeMillis()}_${index}_${originalName}")
        contentResolver.openInputStream(uri)?.use { input ->
            tempFile.outputStream().use { output -> input.copyTo(output) }
        }
        return tempFile
    }

    private fun updatePreview() {
        tvCount.text = "선택된 파일: ${selectedUris.size}개"
        btnUpload.isEnabled = selectedUris.isNotEmpty()
        btnUpload.text = if (selectedUris.isNotEmpty()) "📤 ${selectedUris.size}개 업로드" else "📤 업로드"

        rvPreview.adapter = PreviewAdapter(selectedUris) { position ->
            selectedUris.removeAt(position)
            updatePreview()
        }
    }

    private fun showError(msg: String) {
        runOnUiThread {
            tvProgress.text = "❌ $msg"
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        }
    }

    private fun resetButtons() {
        runOnUiThread {
            btnCamera.isEnabled  = true
            btnGallery.isEnabled = true
            btnUpload.isEnabled  = selectedUris.isNotEmpty()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}

// ── 미리보기 그리드 어댑터 ──
class PreviewAdapter(
    private val uris: List<Uri>,
    private val onRemove: (Int) -> Unit
) : RecyclerView.Adapter<PreviewAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.ivPreview)
        val btnRemove: ImageButton = view.findViewById(R.id.btnRemove)
        val tvType: TextView = view.findViewById(R.id.tvType)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_preview, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(h: VH, pos: Int) {
        val uri = uris[pos]
        val mimeType = h.itemView.context.contentResolver.getType(uri) ?: ""
        val isVideo  = mimeType.startsWith("video")

        h.tvType.text = if (isVideo) "🎬" else "🖼"

        Glide.with(h.itemView.context)
            .load(uri)
            .centerCrop()
            .placeholder(android.R.drawable.ic_menu_gallery)
            .into(h.imageView)

        h.btnRemove.setOnClickListener { onRemove(pos) }
    }

    override fun getItemCount() = uris.size
}
