package com.rams.erp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.graphics.Color
import android.text.TextUtils
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*

class ProjectDetailActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var projectId = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_project_detail)
        projectId = intent.getIntExtra("pid", 0)
        val code = intent.getStringExtra("code") ?: ""
        supportActionBar?.apply {
            title = "프로젝트 $code"
            setDisplayHomeAsUpEnabled(true)
        }
        load()
    }

    private fun load() {
        val pb = findViewById<ProgressBar>(R.id.pbDetail)
        pb.visibility = View.VISIBLE

        scope.launch {
            val detail = withContext(Dispatchers.IO) { ErpApi.getProjectDetail(projectId) }
            val files  = withContext(Dispatchers.IO) { ErpApi.getProjectFiles(projectId) }
            pb.visibility = View.GONE

            if (detail == null) {
                Toast.makeText(
                    this@ProjectDetailActivity,
                    "데이터를 불러올 수 없습니다.",
                    Toast.LENGTH_SHORT
                ).show()
                return@launch
            }

            // 기본 정보
            setTv(R.id.tvDetailCode,      detail.code)
            setTv(R.id.tvDetailName,      detail.name)
            setTv(R.id.tvDetailClient,    detail.client ?: "-")
            setTv(R.id.tvDetailPm,        detail.pm ?: "-")
            setTv(R.id.tvDetailContact,   detail.contact ?: "-")
            setTv(R.id.tvDetailStatus,    detail.status ?: "-")
            setTv(R.id.tvDetailTax,       detail.tax_invoice ?: "미발행")
            setTv(R.id.tvDetailProgress,  "${detail.progress}%")
            setTv(R.id.tvDetailStartDate, detail.start_date ?: "-")
            setTv(R.id.tvDetailEndDate,   detail.end_date ?: "-")

            // 이슈
            val layoutIssue = findViewById<LinearLayout>(R.id.layoutIssue)
            if (!detail.issue.isNullOrBlank()) {
                layoutIssue.visibility = View.VISIBLE
                setTv(R.id.tvDetailIssue, detail.issue)
            } else {
                layoutIssue.visibility = View.GONE
            }

            // 첨부파일 목록
            val rvFiles   = findViewById<RecyclerView>(R.id.rvFiles)
            val tvNoFiles = findViewById<TextView>(R.id.tvNoFiles)
            if (files.isEmpty()) {
                rvFiles.visibility   = View.GONE
                tvNoFiles.visibility = View.VISIBLE
            } else {
                rvFiles.visibility   = View.VISIBLE
                tvNoFiles.visibility = View.GONE
                rvFiles.layoutManager = LinearLayoutManager(this@ProjectDetailActivity)
                rvFiles.isNestedScrollingEnabled = false
                rvFiles.adapter = FileAdapter(files) { f -> openFile(f.filename ?: "") }
            }

            // 업로드 버튼
            findViewById<Button>(R.id.btnUpload).setOnClickListener {
                startActivity(
                    Intent(this@ProjectDetailActivity, UploadActivity::class.java).apply {
                        putExtra("pid",  detail.id)
                        putExtra("code", detail.code)
                        putExtra("name", detail.name)
                    }
                )
            }
        }
    }

    private fun setTv(id: Int, text: String) {
        findViewById<TextView>(id)?.text = text
    }

    private fun openFile(filename: String) {
        val url = "${ErpApi.BASE_URL}/api/mobile/download/$filename?token=${ErpApi.API_TOKEN}"
        try {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse(url)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "파일을 열 수 없습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
    override fun onDestroy() { super.onDestroy(); scope.cancel() }
}

class FileAdapter(
    private val items: List<ProjectFile>,
    private val click: (ProjectFile) -> Unit
) : RecyclerView.Adapter<FileAdapter.VH>() {

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val icon: TextView = itemView.findViewById(R.id.tvFileIcon)
        val name: TextView = itemView.findViewById(R.id.tvFileName)
        val size: TextView = itemView.findViewById(R.id.tvFileSize)
        val date: TextView = itemView.findViewById(R.id.tvFileDate)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_file, parent, false)
        return VH(v)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, pos: Int) {
        val f = items[pos]
        val n = f.original_name ?: f.filename ?: "파일"
        h.icon.text = when {
            n.matches(Regex(".*\\.(jpg|jpeg|png|gif|webp)$", RegexOption.IGNORE_CASE)) -> "🖼️"
            n.matches(Regex(".*\\.(mp4|mov|avi|mkv)$",       RegexOption.IGNORE_CASE)) -> "🎥"
            n.matches(Regex(".*\\.pdf$",                      RegexOption.IGNORE_CASE)) -> "📄"
            n.matches(Regex(".*\\.(bin|hex|elf|fw)$",         RegexOption.IGNORE_CASE)) -> "💾"
            n.matches(Regex(".*\\.(zip|rar|7z)$",             RegexOption.IGNORE_CASE)) -> "📦"
            else -> "📁"
        }
        h.name.text = n
        h.size.text = if (f.file_size > 1024 * 1024)
            "${"%.1f".format(f.file_size / 1024.0 / 1024.0)} MB"
        else
            "${f.file_size / 1024} KB"
        h.date.text = (f.created_at ?: "").take(10)
        val filename = f.filename ?: ""
        if (filename.isNotEmpty()) {
            h.itemView.setOnClickListener { click(f) }
        }
    }
}
