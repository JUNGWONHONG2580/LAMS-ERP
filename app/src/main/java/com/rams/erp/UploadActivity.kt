package com.rams.erp

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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
    private var projectName = ""
    private var projectCode = ""
    private val uris = mutableListOf<Uri>()
    private var cameraUri: Uri? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // 카메라 권한 요청
    private val requestCameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) launchCamera()
        else toast("카메라 권한이 필요합니다.\n설정 > 앱 > RAMS ERP > 권한에서 허용해 주세요.")
    }

    // 갤러리 선택
    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { list ->
        if (!list.isNullOrEmpty()) { uris.addAll(list); refreshPreview() }
    }

    // 카메라 촬영
    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { ok ->
        if (ok == true) {
            cameraUri?.let { uris.add(it); refreshPreview() }
        }
    }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        setContentView(R.layout.activity_upload)
        supportActionBar?.title = "파일 첨부"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        projectId   = intent.getIntExtra("pid", 0)
        projectCode = intent.getStringExtra("code") ?: ""
        projectName = intent.getStringExtra("name") ?: ""

        findViewById<TextView>(R.id.tvProjectCode).text  = projectCode
        findViewById<TextView>(R.id.tvProjectTitle).text = projectName

        findViewById<Button>(R.id.btnCamera).setOnClickListener  { checkCameraAndLaunch() }
        findViewById<Button>(R.id.btnGallery).setOnClickListener { galleryLauncher.launch("*/*") }
        findViewById<Button>(R.id.btnUpload).setOnClickListener  { confirmUpload() }

        refreshPreview()
    }

    // ── 카메라 권한 확인 후 실행 ──
    private fun checkCameraAndLaunch() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED -> {
                launchCamera()
            }
            else -> {
                requestCameraPermission.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun launchCamera() {
        try {
            val ts   = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val name = "RAMS_$ts.jpg"

            val uri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val cv = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, name)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/RamsERP")
                }
                contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv)
            } else {
                val f = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), name)
                FileProvider.getUriForFile(this, "${packageName}.fileprovider", f)
            }

            if (uri == null) {
                toast("카메라를 준비할 수 없습니다. 갤러리를 이용해 주세요.")
                return
            }
            cameraUri = uri
            cameraLauncher.launch(uri)

        } catch (e: Exception) {
            toast("카메라 오류: ${e.message}")
        }
    }

    private fun confirmUpload() {
        if (uris.isEmpty()) { toast("파일을 선택하세요."); return }
        val uploader = uploaderName()
        AlertDialog.Builder(this)
            .setTitle("업로드 확인")
            .setMessage("프로젝트: $projectName\n\n${uris.size}개 파일을 업로드합니까?\n업로더: $uploader")
            .setPositiveButton("업로드") { _, _ -> doUpload(uploader) }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun uploaderName(): String =
        findViewById<EditText>(R.id.etUploaderName).text.toString().trim().ifBlank { "모바일" }

    private fun doUpload(uploader: String) {
        setButtonsEnabled(false)
        showProgress(true)
        setProgressText("파일 준비 중...")

        scope.launch {
            val tempFiles = withContext(Dispatchers.IO) {
                uris.mapIndexedNotNull { i, uri -> copyToTemp(uri, i) }
            }
            if (tempFiles.isEmpty()) { toast("파일 준비 실패"); resetUI(); return@launch }

            setProgressText("업로드 중...")
            val result = withContext(Dispatchers.IO) {
                ErpApi.uploadFiles(projectId, tempFiles, uploader) { pct ->
                    runOnUiThread { setProgressText("업로드 중 ($pct%)...") }
                }
            }
            withContext(Dispatchers.IO) { tempFiles.forEach { it.delete() } }

            showProgress(false)
            if (result.ok) {
                setProgressText("✅ ${result.uploaded}개 파일 업로드 완료!")
                toast("✅ ${result.project_name}에 ${result.uploaded}개 첨부 완료")
                uris.clear(); refreshPreview()
            } else {
                setProgressText("❌ ${result.error.ifBlank { "업로드 실패" }}")
                toast(result.error.ifBlank { "업로드 실패" })
            }
            setButtonsEnabled(true)
        }
    }

    private fun copyToTemp(uri: Uri, idx: Int): File? {
        return try {
            val mime = contentResolver.getType(uri) ?: "image/jpeg"
            val ext = when {
                mime.contains("mp4") || mime.contains("video") -> ".mp4"
                mime.contains("png") -> ".png"
                else -> ".jpg"
            }
            val tmp = File(cacheDir, "up_${System.currentTimeMillis()}_$idx$ext")
            contentResolver.openInputStream(uri)?.use { it.copyTo(tmp.outputStream()) }
            tmp
        } catch (e: Exception) { null }
    }

    private fun refreshPreview() {
        val count = uris.size
        findViewById<TextView>(R.id.tvCount).text = "선택된 파일: ${count}개"
        val btn = findViewById<Button>(R.id.btnUpload)
        btn.isEnabled = count > 0
        btn.text = if (count > 0) "📤 ${count}개 업로드" else "📤 업로드"
        val rv = findViewById<RecyclerView>(R.id.rvPreview)
        rv.layoutManager = GridLayoutManager(this, 3)
        rv.adapter = PreviewAdapter(uris.toList()) { pos -> uris.removeAt(pos); refreshPreview() }
    }

    private fun setButtonsEnabled(on: Boolean) = runOnUiThread {
        listOf(R.id.btnCamera, R.id.btnGallery).forEach { findViewById<Button>(it).isEnabled = on }
    }
    private fun showProgress(show: Boolean) = runOnUiThread {
        val vis = if (show) View.VISIBLE else View.GONE
        findViewById<ProgressBar>(R.id.progressBar).visibility = vis
        findViewById<TextView>(R.id.tvProgress).visibility = vis
    }
    private fun setProgressText(msg: String) = runOnUiThread {
        findViewById<TextView>(R.id.tvProgress).text = msg
    }
    private fun resetUI() = runOnUiThread { showProgress(false); setButtonsEnabled(true) }
    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
    override fun onDestroy() { super.onDestroy(); scope.cancel() }
}

class PreviewAdapter(
    private val list: List<Uri>,
    private val remove: (Int) -> Unit
) : RecyclerView.Adapter<PreviewAdapter.VH>() {
    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val img: ImageView   = v.findViewById(R.id.ivPreview)
        val del: ImageButton = v.findViewById(R.id.btnRemove)
        val typ: TextView    = v.findViewById(R.id.tvType)
    }
    override fun onCreateViewHolder(p: ViewGroup, t: Int) =
        VH(LayoutInflater.from(p.context).inflate(R.layout.item_preview, p, false))
    override fun getItemCount() = list.size
    override fun onBindViewHolder(h: VH, pos: Int) {
        val uri  = list[pos]
        val mime = h.itemView.context.contentResolver.getType(uri) ?: ""
        h.typ.text = if (mime.startsWith("video")) "🎬" else "🖼"
        Glide.with(h.itemView).load(uri).centerCrop()
            .placeholder(android.R.drawable.ic_menu_gallery).into(h.img)
        h.del.setOnClickListener { remove(pos) }
    }
}
