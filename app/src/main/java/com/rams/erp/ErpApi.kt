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
    val ok: Boolean,
    val project_name: String = "",
    val uploaded: Int = 0,
    val files: List<Map<String, Any>> = emptyList(),
    val error: String = ""
)

object ErpApi {
    const val BASE_URL   = "http://192.168.0.101:3000"
    const val API_TOKEN  = "rams-mobile-2026"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    /** 프로젝트 목록 조회 */
    fun getProjects(): List<Project> {
        val request = Request.Builder()
            .url("$BASE_URL/api/mobile/projects")
            .addHeader("X-Mobile-Token", API_TOKEN)
            .build()

        return try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return emptyList()
            val body = response.body?.string() ?: return emptyList()
            val type = object : TypeToken<List<Project>>() {}.type
            gson.fromJson(body, type) ?: emptyList()
        } catch (e: IOException) {
            Log.e("ErpApi", "getProjects 오류: ${e.message}")
            emptyList()
        }
    }

    /** 파일 업로드 (멀티파트) */
    fun uploadFiles(
        projectId: Int,
        files: List<File>,
        uploaderName: String,
        onProgress: (Int) -> Unit
    ): UploadResult {
        val builder = MultipartBody.Builder().setType(MultipartBody.FORM)
        builder.addFormDataPart("uploader_name", uploaderName)

        for (file in files) {
            val mimeType = when {
                file.name.matches(Regex(".*\\.(mp4|mov|avi|mkv|3gp)", RegexOption.IGNORE_CASE)) -> "video/*"
                else -> "image/*"
            }
            builder.addFormDataPart(
                "files",
                file.name,
                file.asRequestBody(mimeType.toMediaType())
            )
        }

        val requestBody = builder.build()

        // 진행률 추적 래퍼
        val countingBody = object : RequestBody() {
            override fun contentType() = requestBody.contentType()
            override fun contentLength() = requestBody.contentLength()
            override fun writeTo(sink: okio.BufferedSink) {
                val total = contentLength()
                var uploaded = 0L
                val bufferedSink = okio.Buffer()
                requestBody.writeTo(bufferedSink)
                val bytes = bufferedSink.readByteArray()
                val chunkSize = 8192
                var offset = 0
                while (offset < bytes.size) {
                    val end = minOf(offset + chunkSize, bytes.size)
                    sink.write(bytes, offset, end - offset)
                    uploaded += (end - offset)
                    if (total > 0) {
                        val progress = ((uploaded * 100) / total).toInt()
                        onProgress(progress)
                    }
                    offset = end
                }
            }
        }

        val request = Request.Builder()
            .url("$BASE_URL/api/mobile/upload/$projectId")
            .addHeader("X-Mobile-Token", API_TOKEN)
            .post(countingBody)
            .build()

        return try {
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: "{\"ok\":false,\"error\":\"응답 없음\"}"
            gson.fromJson(body, UploadResult::class.java)
        } catch (e: IOException) {
            Log.e("ErpApi", "uploadFiles 오류: ${e.message}")
            UploadResult(ok = false, error = "네트워크 오류: ${e.message}")
        }
    }

    /** 서버 연결 확인 */
    fun ping(): Boolean {
        return try {
            val request = Request.Builder()
                .url("$BASE_URL/api/mobile/projects")
                .addHeader("X-Mobile-Token", API_TOKEN)
                .head()
                .build()
            val response = client.newCall(request).execute()
            response.code != 0
        } catch (e: Exception) {
            false
        }
    }
}
