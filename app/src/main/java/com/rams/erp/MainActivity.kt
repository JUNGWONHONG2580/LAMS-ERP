package com.rams.erp

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var tvEmpty: TextView
    private lateinit var etSearch: EditText
    private lateinit var progressBar: ProgressBar

    private var allProjects = listOf<Project>()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        supportActionBar?.title = "📋 레일테크 ERP"

        recyclerView = findViewById(R.id.recyclerView)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        tvEmpty      = findViewById(R.id.tvEmpty)
        etSearch     = findViewById(R.id.etSearch)
        progressBar  = findViewById(R.id.progressBar)

        recyclerView.layoutManager = LinearLayoutManager(this)

        etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) = filterProjects(s.toString())
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        swipeRefresh.setOnRefreshListener { loadProjects() }

        loadProjects()
    }

    private fun loadProjects() {
        progressBar.visibility = View.VISIBLE
        swipeRefresh.isRefreshing = true

        scope.launch {
            val projects = withContext(Dispatchers.IO) { ErpApi.getProjects() }
            allProjects = projects
            filterProjects(etSearch.text.toString())
            progressBar.visibility = View.GONE
            swipeRefresh.isRefreshing = false

            if (projects.isEmpty()) {
                tvEmpty.visibility = View.VISIBLE
                tvEmpty.text = "프로젝트를 불러올 수 없습니다.\n네트워크를 확인하세요."
            }
        }
    }

    private fun filterProjects(query: String) {
        val filtered = if (query.isBlank()) allProjects
        else allProjects.filter {
            it.code.contains(query, ignoreCase = true) ||
            it.name.contains(query, ignoreCase = true) ||
            it.client.contains(query, ignoreCase = true)
        }

        tvEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        if (filtered.isEmpty()) tvEmpty.text = "검색 결과가 없습니다."

        recyclerView.adapter = ProjectAdapter(filtered) { project ->
            val intent = Intent(this, UploadActivity::class.java).apply {
                putExtra("project_id",   project.id)
                putExtra("project_code", project.code)
                putExtra("project_name", project.name)
            }
            startActivity(intent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}

// ── RecyclerView 어댑터 ──
class ProjectAdapter(
    private val items: List<Project>,
    private val onClick: (Project) -> Unit
) : RecyclerView.Adapter<ProjectAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvCode:   TextView = view.findViewById(R.id.tvCode)
        val tvName:   TextView = view.findViewById(R.id.tvName)
        val tvClient: TextView = view.findViewById(R.id.tvClient)
        val tvStatus: TextView = view.findViewById(R.id.tvStatus)
        val chipIcon: TextView = view.findViewById(R.id.chipIcon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_project, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(h: VH, pos: Int) {
        val p = items[pos]
        h.tvCode.text   = p.code
        h.tvName.text   = p.name
        h.tvClient.text = if (p.client.isNotBlank()) "고객사: ${p.client}" else "고객사 미지정"
        h.tvStatus.text = p.status

        // 상태별 색상
        val (bgColor, textColor) = when (p.status) {
            "진행중"  -> Pair("#E8F5E9", "#2E7D32")
            "완료"   -> Pair("#E3F2FD", "#1565C0")
            "보류"   -> Pair("#FFF8E1", "#F57F17")
            else      -> Pair("#F5F5F5", "#616161")
        }
        h.tvStatus.setBackgroundColor(android.graphics.Color.parseColor(bgColor))
        h.tvStatus.setTextColor(android.graphics.Color.parseColor(textColor))

        h.chipIcon.text = "📁"
        h.itemView.setOnClickListener { onClick(p) }
    }

    override fun getItemCount() = items.size
}
