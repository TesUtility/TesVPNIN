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

    companion object {
        private val initialized = java.util.concurrent.atomic.AtomicBoolean(false)

        private fun getDeviceIdForXUDPBaseKey(context: Context): String {
            return try {
                val androidId = (android.provider.Settings.Secure.getString(
                    context.contentResolver,
                    android.provider.Settings.Secure.ANDROID_ID
                ) ?: "tesvpn-android-default-device-id").toByteArray(Charsets.UTF_8)
                android.util.Base64.encodeToString(
                    androidId.copyOf(32),
                    android.util.Base64.NO_PADDING or android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP
                ).trim()
            } catch (_: Exception) {
                ""
            }
        }
    }

    init {
        if (initialized.compareAndSet(false, true)) {
            try {
                Seq.setContext(context.applicationContext)
                ensureDataAssets()
                val assetPath = context.filesDir.absolutePath
                val deviceId = getDeviceIdForXUDPBaseKey(context)
                Libv2ray.initCoreEnv(assetPath, deviceId)
                val ver = try { Libv2ray.checkVersionX() } catch (_: Throwable) { "v26.10.17" }
                onLog("INFO", "libv2ray.aar подключен. Версия Xray-core: $ver")
            } catch (e: Throwable) {
                initialized.set(false)
                val err = e.message ?: e.cause?.message ?: e.javaClass.simpleName
                onLog("WARN", "libv2ray инициализация: $err")
            }
        }
    }

    private fun ensureDataAssets() {
        val files = listOf("geoip.dat", "geosite.dat", "geoip-only-cn-private.dat")
        for (fName in files) {
            val target = File(context.filesDir, fName)
            var needCopy = !target.exists() || target.length() == 0L
            if (!needCopy) {
                try {
                    val fd = context.assets.openFd(fName)
                    val expectedLen = fd.length
                    fd.close()
                    if (target.length() != expectedLen) {
                        needCopy = true
                    }
                } catch (_: Exception) {}
            }
            if (needCopy) {
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

    fun start(config: ProxyConfig, routingMode: String, dnsServer: String, tunFd: Int = 0): Boolean {
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
            ctrl.startLoop(configJson, tunFd)
            controller = ctrl
            isRunning = true
            onLog("INFO", "Xray-core активен: TUN(fd=$tunFd), SOCKS:10808, HTTP:10809")
            true
        } catch (e: Throwable) {
            val msg = e.message ?: e.cause?.message ?: e.javaClass.simpleName
            onLog("ERROR", "Ошибка запуска libv2ray: $msg")
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
            var sum = 0L
            // Handle semicolon-delimited stats: tag,direction,value;tag,direction,value;
            stats.split(';').forEach { entry ->
                if (entry.isBlank()) return@forEach
                val parts = entry.split(',', limit = 3)
                if (parts.size == 3 && parts[1].equals(direction, ignoreCase = true)) {
                    sum += parts[2].trim().toLongOrNull() ?: 0L
                }
            }
            if (sum > 0L) return sum

            // Fallback for line-separated stats: proxy>>>downlink>>>12345
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
