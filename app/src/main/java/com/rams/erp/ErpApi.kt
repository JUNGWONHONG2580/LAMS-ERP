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

data class Project(
    val id: Int,
    val code: String,
    val name: String,
    val client: String = "",
    val pm: String = "",
    val status: String = ""
)

data class UploadResult(
    val ok: Boolean = false,
    val project_name: String = "",
    val uploaded: Int = 0,
    val error: String = ""
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

    fun getProjects(): List<Project> {
        val req = Request.Builder()
            .url("$BASE_URL/api/mobile/projects")
            .addHeader("X-Mobile-Token", API_TOKEN)
            .build()
        return try {
            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) return emptyList()
            val body = resp.body?.string() ?: return emptyList()
            val type = object : TypeToken<List<Project>>() {}.type
            gson.fromJson(body, type) ?: emptyList()
        } catch (e: Exception) {
            Log.e("ErpApi", "getProjects: ${e.message}")
            emptyList()
        }
    }

    fun uploadFiles(
        projectId: Int,
        files: List<File>,
        uploaderName: String,
        onProgress: (Int) -> Unit
    ): UploadResult {
        val bodyBuilder = MultipartBody.Builder().setType(MultipartBody.FORM)
        bodyBuilder.addFormDataPart("uploader_name", uploaderName)
        for (file in files) {
            val mime = if (file.name.matches(Regex(".*\\.(mp4|mov|avi|mkv|3gp)", RegexOption.IGNORE_CASE)))
                "video/mp4" else "image/jpeg"
            bodyBuilder.addFormDataPart("files", file.name, file.asRequestBody(mime.toMediaType()))
        }
        val req = Request.Builder()
            .url("$BASE_URL/api/mobile/upload/$projectId")
            .addHeader("X-Mobile-Token", API_TOKEN)
            .post(bodyBuilder.build())
            .build()
        return try {
            onProgress(10)
            val resp = client.newCall(req).execute()
            onProgress(90)
            val body = resp.body?.string() ?: return UploadResult(error = "응답 없음")
            onProgress(100)
            gson.fromJson(body, UploadResult::class.java) ?: UploadResult(error = "파싱 오류")
        } catch (e: IOException) {
            Log.e("ErpApi", "upload: ${e.message}")
            UploadResult(error = "네트워크 오류: ${e.message}")
        }
    }

    fun isServerReachable(): Boolean {
        return try {
            val socket = java.net.Socket()
            socket.connect(java.net.InetSocketAddress("192.168.0.101", 3000), 4000)
            socket.close()
            true
        } catch (e: Exception) { false }
    }
}
