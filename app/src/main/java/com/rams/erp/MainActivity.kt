package com.rams.erp

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {
    private lateinit var rv: RecyclerView
    private lateinit var swipe: SwipeRefreshLayout
    private lateinit var tvEmpty: TextView
    private lateinit var etSearch: EditText
    private lateinit var pb: ProgressBar
    private lateinit var tabProject: TextView
    private lateinit var tabPcb: TextView
    private var allProjects = listOf<Project>()
    private var allPcbs = listOf<PcbProject>()
    private var currentTab = "project"
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        supportActionBar?.title = "📋 레일테크 ERP"
        rv = findViewById(R.id.recyclerView); swipe = findViewById(R.id.swipeRefresh)
        tvEmpty = findViewById(R.id.tvEmpty); etSearch = findViewById(R.id.etSearch)
        pb = findViewById(R.id.progressBar); tabProject = findViewById(R.id.tabProject); tabPcb = findViewById(R.id.tabPcb)
        rv.layoutManager = LinearLayoutManager(this)
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = filter(s.toString())
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
        tabProject.setOnClickListener { switchTab("project") }
        tabPcb.setOnClickListener { switchTab("pcb") }
        swipe.setOnRefreshListener { load() }
        switchTab("project"); load()
    }

    private fun switchTab(tab: String) {
        currentTab = tab; etSearch.setText("")
        val active = android.graphics.Color.parseColor("#185FA5")
        val inactive = android.graphics.Color.parseColor("#E2E8F0")
        if (tab == "project") {
            tabProject.setBackgroundColor(active); tabProject.setTextColor(android.graphics.Color.WHITE)
            tabPcb.setBackgroundColor(inactive); tabPcb.setTextColor(android.graphics.Color.parseColor("#4A5568"))
            etSearch.hint = "코드 / 프로젝트명 / 고객사 검색"
        } else {
            tabPcb.setBackgroundColor(active); tabPcb.setTextColor(android.graphics.Color.WHITE)
            tabProject.setBackgroundColor(inactive); tabProject.setTextColor(android.graphics.Color.parseColor("#4A5568"))
            etSearch.hint = "관리번호 / 프로젝트명 / 모델명 검색"
        }
        filter(""); load()
    }

    private fun load() {
        pb.visibility = View.VISIBLE; swipe.isRefreshing = true
        scope.launch {
            if (currentTab == "project") allProjects = withContext(Dispatchers.IO) { ErpApi.getProjects() }
            else allPcbs = withContext(Dispatchers.IO) { ErpApi.getPcbProjects() }
            filter(etSearch.text.toString()); pb.visibility = View.GONE; swipe.isRefreshing = false
        }
    }

    private fun filter(q: String) {
        if (currentTab == "project") {
            val list = if (q.isBlank()) allProjects else allProjects.filter { it.code.contains(q, true) || it.name.contains(q, true) || it.client.contains(q, true) }
            tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            rv.adapter = ProjAdapter(list) { p -> startActivity(Intent(this, ProjectDetailActivity::class.java).apply { putExtra("pid", p.id); putExtra("code", p.code); putExtra("name", p.name) }) }
        } else {
            val list = if (q.isBlank()) allPcbs else allPcbs.filter { it.manage_no.contains(q, true) || it.project_name.contains(q, true) || it.model_name.contains(q, true) }
            tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            rv.adapter = PcbAdapter(list) { p -> startActivity(Intent(this, PcbDetailActivity::class.java).apply { putExtra("pcb_id", p.id); putExtra("manage_no", p.manage_no) }) }
        }
    }
    override fun onDestroy() { super.onDestroy(); scope.cancel() }
}

class ProjAdapter(private val items: List<Project>, private val click: (Project) -> Unit) : RecyclerView.Adapter<ProjAdapter.VH>() {
    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val code: TextView = v.findViewById(R.id.tvCode); val name: TextView = v.findViewById(R.id.tvName)
        val client: TextView = v.findViewById(R.id.tvClient); val status: TextView = v.findViewById(R.id.tvStatus)
    }
    override fun onCreateViewHolder(p: ViewGroup, t: Int) = VH(LayoutInflater.from(p.context).inflate(R.layout.item_project, p, false))
    override fun getItemCount() = items.size
    override fun onBindViewHolder(h: VH, pos: Int) {
        val p = items[pos]; h.code.text = p.code; h.name.text = p.name; h.client.text = p.client.ifBlank { "고객사 미지정" }; h.status.text = p.status
        val (bg, fg) = when(p.status) { "진행중" -> "#E8F5E9" to "#2E7D32"; "완료" -> "#E3F2FD" to "#1565C0"; "보류" -> "#FFF8E1" to "#F57F17"; else -> "#F5F5F5" to "#616161" }
        h.status.setBackgroundColor(android.graphics.Color.parseColor(bg)); h.status.setTextColor(android.graphics.Color.parseColor(fg))
        h.itemView.setOnClickListener { click(p) }
    }
}

class PcbAdapter(private val items: List<PcbProject>, private val click: (PcbProject) -> Unit) : RecyclerView.Adapter<PcbAdapter.VH>() {
    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val code: TextView = v.findViewById(R.id.tvCode); val name: TextView = v.findViewById(R.id.tvName)
        val client: TextView = v.findViewById(R.id.tvClient); val status: TextView = v.findViewById(R.id.tvStatus)
    }
    override fun onCreateViewHolder(p: ViewGroup, t: Int) = VH(LayoutInflater.from(p.context).inflate(R.layout.item_project, p, false))
    override fun getItemCount() = items.size
    override fun onBindViewHolder(h: VH, pos: Int) {
        val p = items[pos]; h.code.text = p.manage_no; h.name.text = p.project_name.ifBlank { "-" }
        h.client.text = p.model_name.ifBlank { "모델명 미지정" }; h.status.text = "${p.layers}층 / ${p.thickness}mm"
        h.status.setBackgroundColor(android.graphics.Color.parseColor("#EBF8FF")); h.status.setTextColor(android.graphics.Color.parseColor("#1A237E"))
        h.itemView.setOnClickListener { click(p) }
    }
}
