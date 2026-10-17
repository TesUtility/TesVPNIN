package com.example.service.v2ray

import android.content.Context
import com.example.model.ProxyConfig
import go.Seq
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray
import java.io.File
import java.io.FileOutputStream

class V2rayCoreManager(
    private val context: Context,
    private val onLog: (level: String, message: String) -> Unit
) {
    private var controller: CoreController? = null
    var isRunning: Boolean = false
        private set

    private val callbackHandler = object : CoreCallbackHandler {
        override fun startup(): Long {
            onLog("INFO", "libv2ray.aar: Xray ядро успешно запущено")
            return 0
        }

        override fun shutdown(): Long {
            onLog("INFO", "libv2ray.aar: Xray ядро остановлено")
            return 0
        }

        override fun onEmitStatus(code: Long, msg: String?): Long {
            if (!msg.isNullOrBlank()) {
                onLog("ROUTE", "libv2ray [$code]: $msg")
            }
            return 0
        }
    }

    init {
        try {
            Seq.setContext(context.applicationContext)
            ensureDataAssets()
            val assetPath = context.filesDir.absolutePath
            val cachePath = context.cacheDir.absolutePath
            Libv2ray.initCoreEnv(assetPath, cachePath)
            val ver = try { Libv2ray.checkVersionX() } catch (_: Throwable) { "v26.10.10" }
            onLog("INFO", "libv2ray.aar подключен. Версия Xray-core: $ver")
        } catch (e: Throwable) {
            onLog("WARN", "libv2ray инициализация: ${e.message}")
        }
    }

    private fun ensureDataAssets() {
        val files = listOf("geoip.dat", "geosite.dat", "geoip-only-cn-private.dat")
        for (fName in files) {
            val target = File(context.filesDir, fName)
            if (!target.exists() || target.length() == 0L) {
                try {
                    context.assets.open(fName).use { input ->
                        FileOutputStream(target).use { output ->
                            input.copyTo(output)
                        }
                    }
                } catch (_: Exception) {
                    // Ignore if assets not present
                }
            }
        }
    }

    fun start(config: ProxyConfig, routingMode: String, dnsServer: String): Boolean {
        return try {
            stop()
            val configJson = V2rayConfigBuilder.buildConfig(
                config = config,
                routingMode = routingMode,
                dnsServer = dnsServer,
                socksPort = 10808,
                httpPort = 10809,
                dnsPort = 10853
            )

            val ctrl = Libv2ray.newCoreController(callbackHandler)
            ctrl.startLoop(configJson, -1)
            controller = ctrl
            isRunning = true
            onLog("INFO", "Xray-core активен: SOCKS:10808, HTTP:10809, DNS:10853")
            true
        } catch (e: Throwable) {
            onLog("ERROR", "Ошибка запуска libv2ray: ${e.message}")
            isRunning = false
            false
        }
    }

    fun stop() {
        if (isRunning || controller != null) {
            try {
                controller?.stopLoop()
            } catch (_: Throwable) {}
            controller = null
            isRunning = false
        }
    }

    fun queryDownlinkBytes(): Long {
        return try {
            val stats = controller?.queryAllOutboundTrafficStats() ?: return 0L
            parseTrafficStat(stats, "downlink")
        } catch (_: Throwable) {
            0L
        }
    }

    fun queryUplinkBytes(): Long {
        return try {
            val stats = controller?.queryAllOutboundTrafficStats() ?: return 0L
            parseTrafficStat(stats, "uplink")
        } catch (_: Throwable) {
            0L
        }
    }

    private fun parseTrafficStat(stats: String, direction: String): Long {
        return try {
            for (line in stats.lines()) {
                if (line.contains(direction, ignoreCase = true)) {
                    val num = line.filter { it.isDigit() }
                    if (num.isNotEmpty()) {
                        return num.toLongOrNull() ?: 0L
                    }
                }
            }
            0L
        } catch (_: Throwable) {
            0L
        }
    }

    fun measureDelay(url: String = "https://www.google.com/generate_204"): Long {
        return try {
            controller?.measureDelay(url) ?: -1L
        } catch (_: Throwable) {
            -1L
        }
    }
}
