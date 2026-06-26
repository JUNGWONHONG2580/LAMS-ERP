package com.rams.erp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.graphics.Typeface
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*

class PcbDetailActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var pcbId = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pcb_detail)
        pcbId = intent.getIntExtra("pcb_id", 0)
        val manageNo = intent.getStringExtra("manage_no") ?: ""
        supportActionBar?.apply {
            title = "PCB $manageNo"
            setDisplayHomeAsUpEnabled(true)
        }
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
                Toast.makeText(
                    this@PcbDetailActivity,
                    "데이터를 불러올 수 없습니다.",
                    Toast.LENGTH_SHORT
                ).show()
                return@launch
            }

            // 기본 정보 표시
            setTv(R.id.tvPcbManageNo,      detail.manage_no)
            setTv(R.id.tvPcbProjectName,   detail.project_name.ifBlank { "-" })
            setTv(R.id.tvPcbModelName,     detail.model_name.ifBlank { "-" })
            setTv(R.id.tvPcbCompany,       detail.company.ifBlank { "-" })
            setTv(R.id.tvPcbDesigner,      detail.designer.ifBlank { "-" })
            setTv(R.id.tvPcbLayers,        "${detail.layers}층")
            setTv(R.id.tvPcbThickness,     "${detail.thickness}mm")
            setTv(R.id.tvPcbLinkedProject,
                if (detail.linked_project_code.isNotBlank())
                    "[${detail.linked_project_code}] ${detail.linked_project_name}"
                else "-"
            )

            // 이슈/기타 메모
            val layoutNote = findViewById<LinearLayout>(R.id.layoutPcbNote)
            if (detail.note.isNotBlank()) {
                layoutNote.visibility = View.VISIBLE
                setTv(R.id.tvPcbNote, detail.note)
            } else {
                layoutNote.visibility = View.GONE
            }

            // 첨부파일
            val container = findViewById<LinearLayout>(R.id.llPcbFiles)
            val tvNoFiles = findViewById<TextView>(R.id.tvPcbNoFiles)
            container.removeAllViews()

            if (files.isEmpty()) {
                tvNoFiles.visibility = View.VISIBLE
                container.visibility = View.GONE
                return@launch
            }

            tvNoFiles.visibility = View.GONE
            container.visibility = View.VISIBLE

            val catLabels = linkedMapOf(
                "design"     to "디자인",
                "gerber"     to "거버",
                "gerber_pdf" to "거버PDF",
                "schematic"  to "회로도",
                "bom"        to "BOM",
                "assy"       to "자삽"
            )
            val grouped = files.groupBy { it.category }

            catLabels.entries.forEach { (key, label) ->
                val catFiles = grouped[key]
                if (catFiles.isNullOrEmpty()) return@forEach

                // 카테고리 헤더
                addHeader(container, "$label (${catFiles.size})")

                // 파일 행들
                catFiles.forEach { f ->
                    addFileRow(container, f)
                }
            }
        }
    }

    private fun setTv(id: Int, text: String) {
        findViewById<TextView>(id)?.text = text
    }

    private fun addHeader(parent: LinearLayout, text: String) {
        val tv = TextView(this)
        tv.text = text
        tv.setTextColor(Color.parseColor("#185FA5"))
        tv.textSize = 13f
        tv.setTypeface(null, Typeface.BOLD)
        tv.setPadding(0, dp(16), 0, dp(6))
        parent.addView(tv, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
    }

    private fun addFileRow(parent: LinearLayout, f: PcbFile) {
        // 바깥 행 컨테이너
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(12), dp(10), dp(12), dp(10))
        row.setBackgroundColor(Color.parseColor("#F7FAFC"))

        // 아이콘
        val ext = f.original_name.substringAfterLast('.', "").lowercase()
        val icon = when (ext) {
            "pdf"                        -> "📄"
            "zip", "rar", "7z"          -> "📦"
            "xls", "xlsx", "csv"        -> "📊"
            "jpg", "jpeg", "png", "gif" -> "🖼️"
            "dsn", "sch", "brd"         -> "🔌"
            else                         -> "📁"
        }
        val tvIcon = TextView(this)
        tvIcon.text = icon
        tvIcon.textSize = 18f
        tvIcon.setPadding(0, 0, dp(10), 0)
        row.addView(tvIcon, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        // 파일명 + 메타정보
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL

        val tvName = TextView(this)
        tvName.text = f.original_name
        tvName.textSize = 13f
        tvName.setTextColor(Color.parseColor("#1A202C"))
        tvName.maxLines = 2
        tvName.ellipsize = TextUtils.TruncateAt.END
        col.addView(tvName, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        val sizeStr = if (f.file_size > 1024 * 1024)
            "${"%.1f".format(f.file_size / 1024.0 / 1024.0)} MB"
        else
            "${f.file_size / 1024} KB"
        val tvMeta = TextView(this)
        tvMeta.text = "$sizeStr · ${f.created_at.take(10)}"
        tvMeta.textSize = 11f
        tvMeta.setTextColor(Color.parseColor("#A0AEC0"))
        col.addView(tvMeta, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        row.addView(col, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        // 화살표
        val tvArrow = TextView(this)
        tvArrow.text = ">"
        tvArrow.textSize = 14f
        tvArrow.setTextColor(Color.parseColor("#CBD5E0"))
        tvArrow.setPadding(dp(8), 0, 0, 0)
        row.addView(tvArrow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        // 클릭 이벤트
        row.setOnClickListener { openFile(f.filename) }

        // 구분선 포함 래퍼
        val wrapper = LinearLayout(this)
        wrapper.orientation = LinearLayout.VERTICAL
        wrapper.addView(row, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        val divider = View(this)
        divider.setBackgroundColor(Color.parseColor("#E2E8F0"))
        wrapper.addView(divider, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(1)
        ))

        parent.addView(wrapper, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
    }

    private fun openFile(filename: String) {
        val url = "${ErpApi.BASE_URL}/api/mobile/download/$filename?token=${ErpApi.API_TOKEN}"
        try {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse(url)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "파일을 열 수 있는 앱이 없습니다: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
    override fun onDestroy() { super.onDestroy(); scope.cancel() }
}
