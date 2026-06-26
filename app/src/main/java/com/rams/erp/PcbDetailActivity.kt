package com.rams.erp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.graphics.Typeface
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*

class PcbDetailActivity : AppCompatActivity() {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var pcbId = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pcb_detail)
        pcbId = intent.getIntExtra("pcb_id", 0)
        val manageNo = intent.getStringExtra("manage_no") ?: ""
        supportActionBar?.apply { title = "🔲 $manageNo"; setDisplayHomeAsUpEnabled(true) }
        load()
    }

    private fun load() {
        val pb = findViewById<ProgressBar>(R.id.pbPcbDetail)
        pb.visibility = View.VISIBLE
        scope.launch {
            val detail = withContext(Dispatchers.IO) { ErpApi.getPcbDetail(pcbId) }
            val files  = withContext(Dispatchers.IO) { ErpApi.getPcbFiles(pcbId) }
            pb.visibility = View.GONE

            if (detail == null) {
                Toast.makeText(this@PcbDetailActivity, "데이터를 불러올 수 없습니다.", Toast.LENGTH_SHORT).show()
                return@launch
            }

            fun tv(id: Int) = findViewById<TextView>(id)
            tv(R.id.tvPcbManageNo).text      = detail.manage_no
            tv(R.id.tvPcbProjectName).text   = detail.project_name.ifBlank { "-" }
            tv(R.id.tvPcbModelName).text     = detail.model_name.ifBlank { "-" }
            tv(R.id.tvPcbCompany).text       = detail.company.ifBlank { "-" }
            tv(R.id.tvPcbDesigner).text      = detail.designer.ifBlank { "-" }
            tv(R.id.tvPcbLayers).text        = "${detail.layers}층"
            tv(R.id.tvPcbThickness).text     = "${detail.thickness}mm"
            tv(R.id.tvPcbLinkedProject).text = if (detail.linked_project_code.isNotBlank())
                "[${detail.linked_project_code}] ${detail.linked_project_name}" else "-"

            val layoutNote = findViewById<LinearLayout>(R.id.layoutPcbNote)
            if (detail.note.isNotBlank()) {
                layoutNote.visibility = View.VISIBLE
                tv(R.id.tvPcbNote).text = detail.note
            } else {
                layoutNote.visibility = View.GONE
            }

            val container = findViewById<LinearLayout>(R.id.llPcbFiles)
            val tvNoFiles = findViewById<TextView>(R.id.tvPcbNoFiles)

            if (files.isEmpty()) {
                container.visibility  = View.GONE
                tvNoFiles.visibility  = View.VISIBLE
            } else {
                container.visibility  = View.VISIBLE
                tvNoFiles.visibility  = View.GONE
                container.removeAllViews()  // 재로드 시 중복 방지

                val catLabels = linkedMapOf(
                    "design"     to "디자인",
                    "gerber"     to "거버",
                    "gerber_pdf" to "거버PDF",
                    "schematic"  to "회로도",
                    "bom"        to "BOM",
                    "assy"       to "자삽"
                )
                val grouped = files.groupBy { it.category }

                catLabels.forEach { (key, label) ->
                    val catFiles = grouped[key]
                    if (catFiles.isNullOrEmpty()) return@forEach

                    // 카테고리 헤더 TextView
                    val header = TextView(this@PcbDetailActivity)
                    header.text = "📂 $label (${catFiles.size})"
                    header.setTextColor(android.graphics.Color.parseColor("#185FA5"))
                    header.textSize = 13f
                    header.setPadding(0, 24, 0, 8)
                    header.setTypeface(null, Typeface.BOLD)
                    val headerParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    container.addView(header, headerParams)

                    // 파일 목록 — RecyclerView 대신 LinearLayout으로 직접 표시
                    // (ScrollView 안 중첩 RecyclerView 크래시 방지)
                    catFiles.forEach { f ->
                        val row = buildFileRow(f)
                        val rowParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        rowParams.bottomMargin = 4
                        container.addView(row, rowParams)
                    }
                }
            }
        }
    }

    private fun buildFileRow(f: PcbFile): LinearLayout {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.setPadding(12, 10, 12, 10)
        row.setBackgroundColor(android.graphics.Color.parseColor("#F7FAFC"))
        row.gravity = android.view.Gravity.CENTER_VERTICAL

        // 아이콘
        val ext = f.original_name.substringAfterLast('.', "").lowercase()
        val iconText = when (ext) {
            "pdf"                    -> "📄"
            "zip", "rar", "7z"      -> "📦"
            "xls", "xlsx", "csv"    -> "📊"
            "jpg", "jpeg", "png", "gif" -> "🖼️"
            "dsn", "sch", "brd"     -> "🔌"
            else                     -> "📁"
        }
        val tvIcon = TextView(this)
        tvIcon.text = iconText
        tvIcon.textSize = 20f
        tvIcon.setPadding(0, 0, 12, 0)
        row.addView(tvIcon, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        // 파일 정보 (이름 + 크기/날짜)
        val info = LinearLayout(this)
        info.orientation = LinearLayout.VERTICAL
        val infoParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        container@ run {
            val tvName = TextView(this)
            tvName.text = f.original_name
            tvName.textSize = 13f
            tvName.setTextColor(android.graphics.Color.parseColor("#1A202C"))
            tvName.maxLines = 2
            tvName.ellipsize = android.text.TextUtils.TruncateAt.END
            info.addView(tvName, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))

            val sizeStr = if (f.file_size > 1024*1024)
                "${"%.1f".format(f.file_size/1024.0/1024.0)} MB"
            else "${f.file_size/1024} KB"
            val tvMeta = TextView(this)
            tvMeta.text = "$sizeStr · ${f.created_at.take(10)}"
            tvMeta.textSize = 11f
            tvMeta.setTextColor(android.graphics.Color.parseColor("#A0AEC0"))
            info.addView(tvMeta, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))
        }
        row.addView(info, infoParams)

        // 화살표
        val tvArrow = TextView(this)
        tvArrow.text = "▶"
        tvArrow.textSize = 12f
        tvArrow.setTextColor(android.graphics.Color.parseColor("#CBD5E0"))
        tvArrow.setPadding(12, 0, 0, 0)
        row.addView(tvArrow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        row.setOnClickListener { openFile(f.filename) }
        row.isClickable = true
        row.isFocusable = true
        row.background = android.graphics.drawable.ColorDrawable(
            android.graphics.Color.parseColor("#F7FAFC")
        )
        return row
    }

    private fun openFile(filename: String) {
        val tokenUrl = "${ErpApi.BASE_URL}/api/mobile/download/$filename?token=${ErpApi.API_TOKEN}"
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(tokenUrl)))
        } catch (e: Exception) {
            Toast.makeText(this, "파일을 열 수 있는 앱이 없습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
    override fun onDestroy() { super.onDestroy(); scope.cancel() }
}
