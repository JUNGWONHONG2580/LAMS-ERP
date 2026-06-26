package com.rams.erp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.*
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
        supportActionBar?.apply { title = "📋 $code"; setDisplayHomeAsUpEnabled(true) }
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
                Toast.makeText(this@ProjectDetailActivity, "데이터를 불러올 수 없습니다.", Toast.LENGTH_SHORT).show()
                return@launch
            }

            fun tv(id: Int) = findViewById<TextView>(id)
            tv(R.id.tvDetailCode).text      = detail.code
            tv(R.id.tvDetailName).text      = detail.name
            tv(R.id.tvDetailClient).text    = detail.client.ifBlank { "-" }
            tv(R.id.tvDetailPm).text        = detail.pm.ifBlank { "-" }
            tv(R.id.tvDetailContact).text   = detail.contact.ifBlank { "-" }
            tv(R.id.tvDetailStatus).text    = detail.status
            tv(R.id.tvDetailTax).text       = detail.tax_invoice.ifBlank { "미발행" }
            tv(R.id.tvDetailProgress).text  = "${detail.progress}%"
            tv(R.id.tvDetailStartDate).text = detail.start_date.ifBlank { "-" }
            tv(R.id.tvDetailEndDate).text   = detail.end_date.ifBlank { "-" }

            val layoutIssue = findViewById<LinearLayout>(R.id.layoutIssue)
            if (detail.issue.isNotBlank()) {
                layoutIssue.visibility = View.VISIBLE
                tv(R.id.tvDetailIssue).text = detail.issue
            } else layoutIssue.visibility = View.GONE

            val rvFiles   = findViewById<RecyclerView>(R.id.rvFiles)
            val tvNoFiles = findViewById<TextView>(R.id.tvNoFiles)
            if (files.isEmpty()) {
                rvFiles.visibility   = View.GONE
                tvNoFiles.visibility = View.VISIBLE
            } else {
                rvFiles.visibility   = View.VISIBLE
                tvNoFiles.visibility = View.GONE
                rvFiles.layoutManager = LinearLayoutManager(this@ProjectDetailActivity)
                rvFiles.adapter = FileAdapter(files) { f -> openFile(f.filename) }
            }

            findViewById<Button>(R.id.btnUpload).setOnClickListener {
                startActivity(Intent(this@ProjectDetailActivity, UploadActivity::class.java).apply {
                    putExtra("pid", detail.id); putExtra("code", detail.code); putExtra("name", detail.name)
                })
            }
        }
    }

    // 모바일 토큰 인증으로 파일 열기
    private fun openFile(filename: String) {
        val url = "${ErpApi.BASE_URL}/api/mobile/download/$filename"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        // 브라우저는 토큰 헤더를 못 붙이므로, 일단 브라우저로 열되
        // 서버에서 /api/mobile/download는 토큰 없이도 파일명 기반으로만 접근 가능하도록
        // 또는 쿼리스트링 토큰 방식 사용
        val tokenUrl = "${ErpApi.BASE_URL}/api/mobile/download/$filename?token=${ErpApi.API_TOKEN}"
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(tokenUrl)))
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
    override fun onDestroy() { super.onDestroy(); scope.cancel() }
}

class FileAdapter(
    private val items: List<ProjectFile>,
    private val click: (ProjectFile) -> Unit
) : RecyclerView.Adapter<FileAdapter.VH>() {
    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val icon: TextView = v.findViewById(R.id.tvFileIcon)
        val name: TextView = v.findViewById(R.id.tvFileName)
        val size: TextView = v.findViewById(R.id.tvFileSize)
        val date: TextView = v.findViewById(R.id.tvFileDate)
    }
    override fun onCreateViewHolder(p: ViewGroup, t: Int) =
        VH(LayoutInflater.from(p.context).inflate(R.layout.item_file, p, false))
    override fun getItemCount() = items.size
    override fun onBindViewHolder(h: VH, pos: Int) {
        val f = items[pos]; val n = f.original_name
        h.icon.text = when {
            n.matches(Regex(".*\\.(jpg|jpeg|png|gif|webp)$", RegexOption.IGNORE_CASE)) -> "🖼️"
            n.matches(Regex(".*\\.(mp4|mov|avi|mkv)$",       RegexOption.IGNORE_CASE)) -> "🎥"
            n.matches(Regex(".*\\.pdf$",                      RegexOption.IGNORE_CASE)) -> "📄"
            n.matches(Regex(".*\\.(bin|hex|elf|fw)$",         RegexOption.IGNORE_CASE)) -> "💾"
            n.matches(Regex(".*\\.(zip|rar|7z)$",             RegexOption.IGNORE_CASE)) -> "📦"
            else -> "📁"
        }
        h.name.text = n
        h.size.text = if (f.file_size > 1024*1024) "${"%.1f".format(f.file_size/1024.0/1024.0)} MB" else "${f.file_size/1024} KB"
        h.date.text = f.created_at.take(10)
        h.itemView.setOnClickListener { click(f) }
    }
}
