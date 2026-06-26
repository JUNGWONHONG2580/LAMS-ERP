package com.rams.erp

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

// ── 데이터 모델 ──

data class Project(
    val id: Int, val code: String, val name: String,
    val client: String = "", val pm: String = "", val contact: String = "",
    val status: String = "", val tax_invoice: String = "", val issue: String = "",
    val start_date: String = "", val end_date: String = "", val progress: Int = 0
)

data class ProjectFile(
    val id: Int,
    val filename: String?,
    val original_name: String?,
    val file_type: String?,
    val file_size: Long = 0,
    val created_at: String?
)

data class PcbProject(
    val id: Int, val manage_no: String, val project_name: String = "",
    val model_name: String = "", val company: String = "", val designer: String = "",
    val layers: Int = 2, val thickness: Double = 1.6, val note: String = "",
    val linked_project_code: String = "", val linked_project_name: String = ""
)

// 페이지네이션 응답 모델
data class PcbPageResult(
    val data: List<PcbProject> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    val limit: Int = 50,
    val totalPages: Int = 1
)

data class PcbFile(
    val id: Int,
    val filename: String?,
    val original_name: String?,
    val category: String?,
    val file_size: Long = 0,
    val created_at: String?
)

data class UploadResult(
    val ok: Boolean = false, val project_name: String = "",
    val uploaded: Int = 0, val error: String = ""
)

object ErpApi {
    const val BASE_URL  = "http://192.168.0.101:3000"
    const val API_TOKEN = "rams-mobile-2026"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(180, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()

    private fun get(url: String): String? {
        return try {
            val req = Request.Builder().url(url).addHeader("X-Mobile-Token", API_TOKEN).build()
            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) null else resp.body?.string()
        } catch (e: Exception) { Log.e("ErpApi", "GET $url: ${e.message}"); null }
    }

    // 프로젝트 목록
    fun getProjects(): List<Project> {
        val body = get("$BASE_URL/api/mobile/projects") ?: return emptyList()
        return try { val t = object : TypeToken<List<Project>>() {}.type; gson.fromJson(body, t) ?: emptyList() } catch (e: Exception) { emptyList() }
    }

    // 프로젝트 상세
    fun getProjectDetail(id: Int): Project? {
        val body = get("$BASE_URL/api/mobile/project/$id") ?: return null
        return try { gson.fromJson(body, Project::class.java) } catch (e: Exception) { null }
    }

    // 프로젝트 첨부파일
    fun getProjectFiles(id: Int): List<ProjectFile> {
        val body = get("$BASE_URL/api/mobile/project/$id/files") ?: return emptyList()
        return try { val t = object : TypeToken<List<ProjectFile>>() {}.type; gson.fromJson(body, t) ?: emptyList() } catch (e: Exception) { emptyList() }
    }

    // PCB 목록 (페이지네이션 + 검색)
    fun getPcbProjects(page: Int = 1, search: String = ""): PcbPageResult {
        val url = "$BASE_URL/api/mobile/pcb?page=$page&limit=50" +
                  if (search.isNotBlank()) "&search=${java.net.URLEncoder.encode(search, "UTF-8")}" else ""
        val body = get(url) ?: return PcbPageResult()
        return try { gson.fromJson(body, PcbPageResult::class.java) ?: PcbPageResult() } catch (e: Exception) { PcbPageResult() }
    }

    // PCB 상세
    fun getPcbDetail(id: Int): PcbProject? {
        val body = get("$BASE_URL/api/mobile/pcb/$id") ?: return null
        return try { gson.fromJson(body, PcbProject::class.java) } catch (e: Exception) { null }
    }

    // PCB 첨부파일
    fun getPcbFiles(id: Int): List<PcbFile> {
        val body = get("$BASE_URL/api/mobile/pcb/$id/files") ?: return emptyList()
        return try { val t = object : TypeToken<List<PcbFile>>() {}.type; gson.fromJson(body, t) ?: emptyList() } catch (e: Exception) { emptyList() }
    }

    // 파일 업로드
    fun uploadFiles(projectId: Int, files: List<File>, uploaderName: String, onProgress: (Int) -> Unit): UploadResult {
        val bodyBuilder = MultipartBody.Builder().setType(MultipartBody.FORM)
        bodyBuilder.addFormDataPart("uploader_name", uploaderName)
        for (file in files) {
            val mime = if (file.name.matches(Regex(".*\\.(mp4|mov|avi|mkv|3gp)", RegexOption.IGNORE_CASE))) "video/mp4" else "image/jpeg"
            bodyBuilder.addFormDataPart("files", file.name, file.asRequestBody(mime.toMediaType()))
        }
        val req = Request.Builder().url("$BASE_URL/api/mobile/upload/$projectId").addHeader("X-Mobile-Token", API_TOKEN).post(bodyBuilder.build()).build()
        return try {
            onProgress(10); val resp = client.newCall(req).execute(); onProgress(90)
            val body = resp.body?.string() ?: return UploadResult(error = "응답 없음")
            onProgress(100); gson.fromJson(body, UploadResult::class.java) ?: UploadResult(error = "파싱 오류")
        } catch (e: IOException) { Log.e("ErpApi", "upload: ${e.message}"); UploadResult(error = "네트워크 오류: ${e.message}") }
    }

    fun isServerReachable(): Boolean {
        return try { val s = java.net.Socket(); s.connect(java.net.InetSocketAddress("192.168.0.101", 3000), 4000); s.close(); true } catch (e: Exception) { false }
    }
}
