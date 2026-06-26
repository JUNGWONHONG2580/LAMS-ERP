package com.rams.erp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.text.TextUtils
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
            try {
                val detail = withContext(Dispatchers.IO) { ErpApi.getPcbDetail(pcbId) }
                val files  = withContext(Dispatchers.IO) { ErpApi.getPcbFiles(pcbId) }
                pb.visibility = View.GONE

                if (detail == null) {
                    toast("데이터를 불러올 수 없습니다.")
                    return@launch
                }

                // 기본 정보
                setTv(R.id.tvPcbManageNo,      detail.manage_no)
                setTv(R.id.tvPcbProjectName,   detail.project_name ?: "-")
                setTv(R.id.tvPcbModelName,     detail.model_name ?: "-")
                setTv(R.id.tvPcbCompany,       detail.company ?: "-")
                setTv(R.id.tvPcbDesigner,      detail.designer ?: "-")
                setTv(R.id.tvPcbLayers,        "${detail.layers}층")
                setTv(R.id.tvPcbThickness,     "${detail.thickness}mm")
                setTv(R.id.tvPcbLinkedProject,
                    if (!detail.linked_project_code.isNullOrBlank())
                        "[${detail.linked_project_code}] ${detail.linked_project_name ?: ""}"
                    else "-"
                )

                // 이슈/메모
                val layoutNote = findViewById<LinearLayout>(R.id.layoutPcbNote)
                if (!detail.note.isNullOrBlank()) {
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
                    return@launch
                }

                tvNoFiles.visibility = View.GONE

                val catLabels = linkedMapOf(
                    "design"     to "디자인",
                    "gerber"     to "거버",
                    "gerber_pdf" to "거버PDF",
                    "schematic"  to "회로도",
                    "bom"        to "BOM",
                    "assy"       to "자삽"
                )
                val grouped = files.groupBy { it.category ?: "etc" }

                catLabels.forEach { (key, label) ->
                    val catFiles = grouped[key]
                    if (catFiles.isNullOrEmpty()) return@forEach

                    // 헤더
                    val header = TextView(this@PcbDetailActivity)
                    header.text = "$label (${catFiles.size})"
                    header.setTextColor(Color.parseColor("#185FA5"))
                    header.textSize = 13f
                    header.setTypeface(null, Typeface.BOLD)
                    header.setPadding(0, px(16), 0, px(6))
                    container.addView(header, lp())

                    // 파일 행
                    catFiles.forEach { f ->
                        try {
                            val row = makeRow(f)
                            container.addView(row, lp())
                        } catch (e: Exception) {
                            // 개별 행 오류는 무시하고 계속
                        }
                    }
                }

            } catch (e: Exception) {
                pb.visibility = View.GONE
                toast("오류: ${e.message}")
            }
        }
    }

    private fun makeRow(f: PcbFile): LinearLayout {
        val row = LinearLayout(this@PcbDetailActivity)
        row.orientation = LinearLayout.HORIZONTAL
        row.setPadding(px(8), px(12), px(8), px(12))
        row.setBackgroundColor(Color.parseColor("#F7FAFC"))

        // 파일명 (null 안전)
        val originalName = f.original_name ?: f.filename ?: "파일"
        val tvName = TextView(this@PcbDetailActivity)
        tvName.text = originalName
        tvName.textSize = 13f
        tvName.setTextColor(Color.parseColor("#1A202C"))
        tvName.maxLines = 1
        tvName.ellipsize = TextUtils.TruncateAt.END
        val nameParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        row.addView(tvName, nameParams)

        // 크기
        val sizeStr = if (f.file_size > 1024 * 1024)
            "${"%.1f".format(f.file_size / 1024.0 / 1024.0)}MB"
        else "${f.file_size / 1024}KB"
        val tvSize = TextView(this@PcbDetailActivity)
        tvSize.text = sizeStr
        tvSize.textSize = 11f
        tvSize.setTextColor(Color.parseColor("#A0AEC0"))
        tvSize.setPadding(px(8), 0, 0, 0)
        row.addView(tvSize, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        val filename = f.filename ?: ""
        if (filename.isNotEmpty()) {
            row.setOnClickListener { openFile(filename) }
        }
        return row
    }

    private fun openFile(filename: String) {
        try {
            val url = "${ErpApi.BASE_URL}/api/mobile/download/$filename?token=${ErpApi.API_TOKEN}"
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            toast("파일을 열 수 있는 앱이 없습니다.")
        }
    }

    private fun lp() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    )

    private fun px(dp: Int) = (dp * resources.displayMetrics.density + 0.5f).toInt()

    private fun setTv(id: Int, text: String) { findViewById<TextView>(id)?.text = text }

    private fun toast(msg: String) =
        Toast.makeText(this@PcbDetailActivity, msg, Toast.LENGTH_SHORT).show()

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
    override fun onDestroy() { super.onDestroy(); scope.cancel() }
}
