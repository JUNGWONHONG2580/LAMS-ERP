package com.rams.erp

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*

class SplashActivity : AppCompatActivity() {

    companion object {
        // 사내 와이파이 설정 — 연결 우선순위: LAMS_501 → LAMS(503)
        val WIFI_CONFIGS = listOf(
            WifiConfig("LAMS_501",  "abridge2580"),
            WifiConfig("LAMS(503)", "abridge2580")
        )
        const val ERP_HOST = "192.168.0.101"
        const val ERP_PORT = 3000
    }

    data class WifiConfig(val ssid: String, val password: String)

    private lateinit var tvStatus: TextView
    private lateinit var tvSsid: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnRetry: Button

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private val PERMISSION_REQUEST_CODE = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        tvStatus    = findViewById(R.id.tvStatus)
        tvSsid      = findViewById(R.id.tvSsid)
        progressBar = findViewById(R.id.progressBar)
        btnRetry    = findViewById(R.id.btnRetry)

        btnRetry.setOnClickListener { startWifiConnection() }

        // 권한 요청 후 시작
        requestPermissionsAndStart()
    }

    private fun requestPermissionsAndStart() {
        val permsNeeded = mutableListOf<String>()

        // 위치 권한 (Wi-Fi SSID 접근에 필요)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            permsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        // 미디어 권한
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                != PackageManager.PERMISSION_GRANTED) {
                permsNeeded.add(Manifest.permission.READ_MEDIA_IMAGES)
                permsNeeded.add(Manifest.permission.READ_MEDIA_VIDEO)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                permsNeeded.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (permsNeeded.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permsNeeded.toTypedArray(), PERMISSION_REQUEST_CODE)
        } else {
            startWifiConnection()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        startWifiConnection()
    }

    private fun startWifiConnection() {
        btnRetry.visibility = View.GONE
        progressBar.visibility = View.VISIBLE
        setStatus("사내 Wi-Fi 연결 중...")

        // 현재 연결된 Wi-Fi 확인
        if (isAlreadyOnCompanyWifi()) {
            setStatus("✅ 사내 네트워크 연결됨")
            checkErpServer()
            return
        }

        // Android 10+ : WifiNetworkSpecifier 사용
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            connectWifiModern(WIFI_CONFIGS[0]) { success ->
                if (success) {
                    checkErpServer()
                } else {
                    // 두 번째 SSID 시도
                    connectWifiModern(WIFI_CONFIGS[1]) { success2 ->
                        if (success2) checkErpServer()
                        else showRetry("Wi-Fi 연결 실패.\n수동으로 연결 후 재시도하세요.")
                    }
                }
            }
        } else {
            // Android 9 이하: WifiManager (deprecated)
            connectWifiLegacy()
        }
    }

    private fun isAlreadyOnCompanyWifi(): Boolean {
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val connMgr = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connMgr.activeNetwork ?: return false
        val caps = connMgr.getNetworkCapabilities(network) ?: return false

        if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return false

        val info = wifiManager.connectionInfo
        val ssid = info.ssid.trim('"')
        return WIFI_CONFIGS.any { it.ssid == ssid }
    }

    @Suppress("DEPRECATION")
    private fun connectWifiLegacy() {
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        if (!wifiManager.isWifiEnabled) wifiManager.isWifiEnabled = true

        for (config in WIFI_CONFIGS) {
            val wifiConfig = android.net.wifi.WifiConfiguration().apply {
                SSID = "\"${config.ssid}\""
                preSharedKey = "\"${config.password}\""
            }
            val netId = wifiManager.addNetwork(wifiConfig)
            if (netId != -1) {
                wifiManager.disconnect()
                wifiManager.enableNetwork(netId, true)
                wifiManager.reconnect()
                setStatus("연결 중: ${config.ssid}")
                tvSsid.text = config.ssid
                Handler(Looper.getMainLooper()).postDelayed({ checkErpServer() }, 3000)
                return
            }
        }
        showRetry("Wi-Fi 설정 실패")
    }

    private fun connectWifiModern(config: WifiConfig, callback: (Boolean) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) { callback(false); return }

        setStatus("연결 시도: ${config.ssid}")
        tvSsid.text = config.ssid

        val specifier = WifiNetworkSpecifier.Builder()
            .setSsid(config.ssid)
            .setWpa2Passphrase(config.password)
            .build()

        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .setNetworkSpecifier(specifier)
            .build()

        val connMgr = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        // 이전 콜백 해제
        networkCallback?.let {
            try { connMgr.unregisterNetworkCallback(it) } catch (e: Exception) {}
        }

        var responded = false
        val timeout = Handler(Looper.getMainLooper())

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (responded) return
                responded = true
                timeout.removeCallbacksAndMessages(null)
                runOnUiThread {
                    setStatus("✅ ${config.ssid} 연결됨")
                    callback(true)
                }
            }
            override fun onUnavailable() {
                if (responded) return
                responded = true
                timeout.removeCallbacksAndMessages(null)
                runOnUiThread { callback(false) }
            }
        }

        connMgr.requestNetwork(request, networkCallback!!)

        // 10초 타임아웃
        timeout.postDelayed({
            if (!responded) {
                responded = true
                callback(false)
            }
        }, 10_000)
    }

    private fun checkErpServer() {
        setStatus("ERP 서버 연결 확인 중...")
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    val socket = java.net.Socket()
                    socket.connect(java.net.InetSocketAddress(ERP_HOST, ERP_PORT), 5000)
                    socket.close()
                    true
                } catch (e: Exception) { false }
            }
            if (ok) {
                setStatus("✅ ERP 서버 연결 완료")
                delay(800)
                goToMain()
            } else {
                showRetry("ERP 서버($ERP_HOST:$ERP_PORT)에\n연결할 수 없습니다.\n\nWi-Fi 연결 확인 후 재시도하세요.")
            }
        }
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun setStatus(msg: String) {
        runOnUiThread { tvStatus.text = msg }
    }

    private fun showRetry(msg: String) {
        runOnUiThread {
            progressBar.visibility = View.GONE
            btnRetry.visibility = View.VISIBLE
            tvStatus.text = msg
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        networkCallback?.let {
            try {
                (getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager)
                    .unregisterNetworkCallback(it)
            } catch (e: Exception) {}
        }
    }
}
