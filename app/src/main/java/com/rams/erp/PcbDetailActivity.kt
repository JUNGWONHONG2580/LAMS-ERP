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
            } else layoutNote.visibility = View.GONE

            val container = findViewById<LinearLayout>(R.id.llPcbFiles)
            val tvNoFiles = findViewById<TextView>(R.id.tvPcbNoFiles)
            if (files.isEmpty()) {
                container.visibility = View.GONE; tvNoFiles.visibility = View.VISIBLE
            } else {
                container.visibility = View.VISIBLE; tvNoFiles.visibility = View.GONE
                val catLabels = linkedMapOf(
                    "design" to "디자인", "gerber" to "거버", "gerber_pdf" to "거버PDF",
                    "schematic" to "회로도", "bom" to "BOM", "assy" to "자삽"
                )
                val grouped = files.groupBy { it.category }
                catLabels.forEach { (key, label) ->
                    val catFiles = grouped[key] ?: return@forEach
                    container.addView(TextView(this@PcbDetailActivity).apply {
                        text = "📂 $label (${catFiles.size})"
                        setTextColor(android.graphics.Color.parseColor("#185FA5"))
                        textSize = 13f; setPadding(0, 16, 0, 6)
                        setTypeface(null, Typeface.BOLD)
                    })
                    val rv = RecyclerView(this@PcbDetailActivity).apply {
                        layoutManager = LinearLayoutManager(this@PcbDetailActivity)
                        isNestedScrollingEnabled = false
                    }
                    rv.adapter = PcbFileAdapter(catFiles) { f -> openFile(f.filename) }
                    container.addView(rv)
                }
            }
        }
    }

    private fun openFile(filename: String) {
        val tokenUrl = "${ErpApi.BASE_URL}/api/mobile/download/$filename?token=${ErpApi.API_TOKEN}"
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(tokenUrl)))
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
    override fun onDestroy() { super.onDestroy(); scope.cancel() }
}

class PcbFileAdapter(
    private val items: List<PcbFile>,
    private val click: (PcbFile) -> Unit
) : RecyclerView.Adapter<PcbFileAdapter.VH>() {
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
        val f = items[pos]; val ext = f.original_name.substringAfterLast('.', "").lowercase()
        h.icon.text = when(ext) {
            "pdf" -> "📄"; "zip","rar","7z" -> "📦"
            "xls","xlsx","csv" -> "📊"; "jpg","jpeg","png","gif" -> "🖼️"
            "dsn","sch","brd" -> "🔌"; else -> "📁"
        }
        h.name.text = f.original_name
        h.size.text = if (f.file_size > 1024*1024) "${"%.1f".format(f.file_size/1024.0/1024.0)} MB" else "${f.file_size/1024} KB"
        h.date.text = f.created_at.take(10)
        h.itemView.setOnClickListener { click(f) }
    }
}
