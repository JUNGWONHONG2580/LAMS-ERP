package com.rams.erp

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*

class SplashActivity : AppCompatActivity() {

    private val PERM_CODE = 100
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        supportActionBar?.hide()

        findViewById<Button>(R.id.btnRetry).setOnClickListener { checkAndProceed() }

        requestNeededPermissions()
    }

    private fun requestNeededPermissions() {
        val needed = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED)
            needed.add(Manifest.permission.ACCESS_FINE_LOCATION)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.READ_MEDIA_IMAGES)
                needed.add(Manifest.permission.READ_MEDIA_VIDEO)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED)
                needed.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        if (needed.isNotEmpty())
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), PERM_CODE)
        else
            checkAndProceed()
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        checkAndProceed()
    }

    private fun checkAndProceed() {
        setStatus("사내 Wi-Fi 확인 중...")
        showProgress(true)
        hideRetry()

        scope.launch {
            // 1. Wi-Fi 연결 여부 확인
            val wifiOk = isOnWifi()
            if (!wifiOk) {
                // Wi-Fi 아닌 경우 안내 (강제 연결은 Android 10+에서 시스템 다이얼로그 필요)
                setStatus("사내 Wi-Fi(LAMS_501 또는 LAMS(503))에\n먼저 연결해 주세요.\n\n연결 후 재시도를 누르세요.")
                showProgress(false)
                showRetry()
                return@launch
            }

            // 2. 현재 SSID 확인
            val ssid = getConnectedSsid()
            if (ssid != null) setStatusSub("연결됨: $ssid")

            // 3. ERP 서버 연결 확인
            setStatus("ERP 서버 연결 확인 중...")
            val serverOk = withContext(Dispatchers.IO) { ErpApi.isServerReachable() }

            if (serverOk) {
                setStatus("✅ 연결 완료")
                delay(600)
                startActivity(Intent(this@SplashActivity, MainActivity::class.java))
                finish()
            } else {
                setStatus("❌ ERP 서버(192.168.0.101:3000)에\n연결할 수 없습니다.\n\nWi-Fi 연결 상태를 확인 후 재시도하세요.")
                showProgress(false)
                showRetry()
            }
        }
    }

    private fun isOnWifi(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    @Suppress("DEPRECATION")
    private fun getConnectedSsid(): String? {
        return try {
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val info = wm.connectionInfo
            info.ssid?.trim('"')
        } catch (e: Exception) { null }
    }

    private fun setStatus(msg: String) {
        runOnUiThread { findViewById<TextView>(R.id.tvStatus).text = msg }
    }

    private fun setStatusSub(msg: String) {
        runOnUiThread { findViewById<TextView>(R.id.tvSsid).text = msg }
    }

    private fun showProgress(show: Boolean) {
        runOnUiThread {
            findViewById<ProgressBar>(R.id.progressBar).visibility =
                if (show) View.VISIBLE else View.GONE
        }
    }

    private fun showRetry() {
        runOnUiThread { findViewById<Button>(R.id.btnRetry).visibility = View.VISIBLE }
    }

    private fun hideRetry() {
        runOnUiThread { findViewById<Button>(R.id.btnRetry).visibility = View.GONE }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
