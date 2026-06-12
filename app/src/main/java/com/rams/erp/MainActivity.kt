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

    private var allProjects = listOf<Project>()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        supportActionBar?.title = "📋 레일테크 ERP"

        rv       = findViewById(R.id.recyclerView)
        swipe    = findViewById(R.id.swipeRefresh)
        tvEmpty  = findViewById(R.id.tvEmpty)
        etSearch = findViewById(R.id.etSearch)
        pb       = findViewById(R.id.progressBar)

        rv.layoutManager = LinearLayoutManager(this)

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = filter(s.toString())
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        swipe.setOnRefreshListener { load() }
        load()
    }

    private fun load() {
        pb.visibility = View.VISIBLE
        swipe.isRefreshing = true
        scope.launch {
            val list = withContext(Dispatchers.IO) { ErpApi.getProjects() }
            allProjects = list
            filter(etSearch.text.toString())
            pb.visibility = View.GONE
            swipe.isRefreshing = false
            if (list.isEmpty()) {
                tvEmpty.visibility = View.VISIBLE
                tvEmpty.text = "프로젝트를 불러올 수 없습니다.\n서버 연결을 확인하세요."
            }
        }
    }

    private fun filter(q: String) {
        val list = if (q.isBlank()) allProjects
        else allProjects.filter {
            it.code.contains(q, true) || it.name.contains(q, true) || it.client.contains(q, true)
        }
        tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        if (list.isEmpty() && allProjects.isNotEmpty()) tvEmpty.text = "검색 결과가 없습니다."
        rv.adapter = ProjAdapter(list) { p ->
            startActivity(Intent(this, UploadActivity::class.java).apply {
                putExtra("pid", p.id)
                putExtra("code", p.code)
                putExtra("name", p.name)
            })
        }
    }

    override fun onDestroy() { super.onDestroy(); scope.cancel() }
}

class ProjAdapter(
    private val items: List<Project>,
    private val click: (Project) -> Unit
) : RecyclerView.Adapter<ProjAdapter.VH>() {

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val code:   TextView = v.findViewById(R.id.tvCode)
        val name:   TextView = v.findViewById(R.id.tvName)
        val client: TextView = v.findViewById(R.id.tvClient)
        val status: TextView = v.findViewById(R.id.tvStatus)
    }

    override fun onCreateViewHolder(p: ViewGroup, t: Int) =
        VH(LayoutInflater.from(p.context).inflate(R.layout.item_project, p, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, pos: Int) {
        val p = items[pos]
        h.code.text   = p.code
        h.name.text   = p.name
        h.client.text = if (p.client.isNotBlank()) p.client else "고객사 미지정"
        h.status.text = p.status
        val (bg, fg) = when (p.status) {
            "진행중" -> "#E8F5E9" to "#2E7D32"
            "완료"  -> "#E3F2FD" to "#1565C0"
            "보류"  -> "#FFF8E1" to "#F57F17"
            else    -> "#F5F5F5" to "#616161"
        }
        h.status.setBackgroundColor(android.graphics.Color.parseColor(bg))
        h.status.setTextColor(android.graphics.Color.parseColor(fg))
        h.itemView.setOnClickListener { click(p) }
    }
}
