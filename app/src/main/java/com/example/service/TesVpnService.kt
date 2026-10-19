package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.model.ConnectionStatus
import com.example.model.LogEntry
import com.example.model.ProxyConfig
import com.example.model.ProxyProtocol
import com.example.model.TrafficStats
import com.example.service.v2ray.V2rayCoreManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class TesVpnService : VpnService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var vpnInterface: ParcelFileDescriptor? = null
    private var v2rayCore: V2rayCoreManager? = null
    private var packetEngine: PacketEngine? = null
    private var statsJob: Job? = null
    private var durationSeconds: Long = 0

    companion object {
        const val ACTION_CONNECT = "com.example.tesvpn.CONNECT"
        const val ACTION_DISCONNECT = "com.example.tesvpn.DISCONNECT"
        const val EXTRA_CONFIG_NAME = "config_name"
        const val EXTRA_SERVER = "server"
        const val EXTRA_PORT = "port"
        const val EXTRA_PROTOCOL = "protocol"
        const val EXTRA_SECURITY = "security"
        const val EXTRA_DNS = "dns"
        const val EXTRA_ROUTING_MODE = "routing_mode"
        const val NOTIFICATION_ID = 9001
        const val CHANNEL_ID = "tesvpn_channel"

        private var activeProxyConfig: ProxyConfig? = null

        private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
        val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

        private val _trafficStats = MutableStateFlow(TrafficStats())
        val trafficStats: StateFlow<TrafficStats> = _trafficStats.asStateFlow()

        private val _connectedConfig = MutableStateFlow<String?>(null)
        val connectedConfig: StateFlow<String?> = _connectedConfig.asStateFlow()

        private val _logs = MutableStateFlow<List<LogEntry>>(
            listOf(
                LogEntry(message = "TESVPN Core (Xray-core / libv2ray.aar) инициализирован.")
            )
        )
        val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

        fun log(level: String = "INFO", message: String) {
            val entry = LogEntry(level = level, message = message)
            _logs.value = (_logs.value + entry).takeLast(250)
        }

        fun clearLogs() {
            _logs.value = listOf(LogEntry(message = "Логи очищены."))
        }

        fun startVpn(context: Context, config: ProxyConfig, dns: String, routingMode: String = "BYPASS_LAN") {
            activeProxyConfig = config
            val intent = Intent(context, TesVpnService::class.java).apply {
                action = ACTION_CONNECT
                putExtra(EXTRA_CONFIG_NAME, config.name)
                putExtra(EXTRA_SERVER, config.server)
                putExtra(EXTRA_PORT, config.port)
                putExtra(EXTRA_PROTOCOL, config.protocol.displayName)
                putExtra(EXTRA_SECURITY, config.securityBadge)
                putExtra(EXTRA_DNS, dns)
                putExtra(EXTRA_ROUTING_MODE, routingMode)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                log("ERROR", "Ошибка запуска VPN сервиса: ${e.message}")
            }
        }

        fun stopVpn(context: Context) {
            val intent = Intent(context, TesVpnService::class.java).apply {
                action = ACTION_DISCONNECT
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                log("ERROR", "Ошибка остановки VPN: ${e.message}")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> {
                val configName = intent.getStringExtra(EXTRA_CONFIG_NAME) ?: "TESVPN Профиль"
                val server = intent.getStringExtra(EXTRA_SERVER) ?: "unknown"
                val port = intent.getIntExtra(EXTRA_PORT, 443)
                val protocol = intent.getStringExtra(EXTRA_PROTOCOL) ?: "VLESS"
                val security = intent.getStringExtra(EXTRA_SECURITY) ?: "REALITY"
                val dns = intent.getStringExtra(EXTRA_DNS) ?: "1.1.1.1"
                val routingMode = intent.getStringExtra(EXTRA_ROUTING_MODE) ?: "BYPASS_LAN"

                handleConnect(configName, server, port, protocol, security, dns, routingMode)
            }
            ACTION_DISCONNECT -> {
                handleDisconnect()
            }
        }
        return START_NOT_STICKY
    }

    private fun safeStartForeground(notification: Notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (_: Exception) {
            try {
                startForeground(NOTIFICATION_ID, notification)
            } catch (e2: Exception) {
                log("WARN", "Foreground уведомление: ${e2.message}")
            }
        }
    }

    private fun handleConnect(
        configName: String,
        server: String,
        port: Int,
        protocol: String,
        security: String,
        dns: String,
        routingMode: String
    ) {
        _connectionStatus.value = ConnectionStatus.CONNECTING
        _connectedConfig.value = configName

        createNotificationChannel()
        safeStartForeground(createNotification("Подключение к $configName..."))

        log("INFO", "Инициализация защищенного шлюза TESVPN...")
        log("INFO", "Конфигурация: $configName [$protocol / $security]")
        log("INFO", "Целевой сервер: $server:$port")

        serviceScope.launch {
            delay(150)

            val currentCfg = activeProxyConfig ?: ProxyConfig(
                id = "default",
                name = configName,
                protocol = ProxyProtocol.VLESS,
                server = server,
                port = port,
                uuidOrPassword = ""
            )

            // 1. Establish Android VpnService TUN interface first
            val pfd: ParcelFileDescriptor? = try {
                val builder = Builder()
                    .setMtu(1500)
                    .addAddress("10.10.14.1", 24)
                    .addRoute("0.0.0.0", 0)
                    .addDnsServer(dns)
                    .addDnsServer("8.8.8.8")
                    .setSession("TESVPN - $configName")

                // Route IPv6 into VPN tunnel to prevent IPv6 traffic from leaking to ISP
                try {
                    builder.addAddress("fc00::10:10:14:1", 126)
                    builder.addRoute("::", 0)
                } catch (e: Exception) {
                    log("WARN", "IPv6 route: ${e.message}")
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    try {
                        builder.setMetered(false)
                    } catch (_: Exception) {}
                }

                // Prevent routing loops: exclude app's own process from VPN tunnel
                try {
                    builder.addDisallowedApplication(packageName)
                } catch (e: Exception) {
                    log("WARN", "addDisallowedApplication: ${e.message}")
                }

                val pendingIntent = PendingIntent.getActivity(
                    this@TesVpnService,
                    0,
                    Intent(this@TesVpnService, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE
                )
                builder.setConfigureIntent(pendingIntent)

                builder.establish()
            } catch (e: Exception) {
                log("ERROR", "Ошибка создания tun интерфейса: ${e.message}")
                null
            }

            if (pfd == null) {
                log("ERROR", "tun интерфейс не выделился системой. Проверьте VPN разрешение.")
                _connectionStatus.value = ConnectionStatus.DISCONNECTED
                _connectedConfig.value = null
                stopSelf()
                return@launch
            }

            vpnInterface = pfd
            val tunFd = pfd.fd
            log("INFO", "Интерфейс tun0 поднят (fd=$tunFd). Перенаправление трафика через Xray-core...")

            // 2. Start Xray-core / libv2ray.aar with native TUN attached
            v2rayCore?.stop()
            val core = V2rayCoreManager(this@TesVpnService) { level, msg ->
                log(level, msg)
            }
            v2rayCore = core

            val coreStarted = core.start(currentCfg, routingMode, dns, tunFd)
            if (!coreStarted) {
                log("ERROR", "libv2ray ядро не смогло запуститься с конфигурацией")
                try { pfd.close() } catch (_: Exception) {}
                vpnInterface = null
                _connectionStatus.value = ConnectionStatus.DISCONNECTED
                _connectedConfig.value = null
                stopSelf()
                return@launch
            }

            _connectionStatus.value = ConnectionStatus.CONNECTED
            log("INFO", "TESVPN успешно подключен! Защита активна.")
            startHeartbeat(configName)
        }
    }

    private fun startHeartbeat(configName: String) {
        statsJob?.cancel()
        durationSeconds = 0

        statsJob = serviceScope.launch {
            var totalDownBytes = 0L
            var totalUpBytes = 0L

            while (isActive) {
                delay(1000)
                durationSeconds++

                // Query traffic from libv2ray core stats
                val coreDown = v2rayCore?.queryDownlinkBytes() ?: 0L
                val coreUp = v2rayCore?.queryUplinkBytes() ?: 0L

                val downSpeed: Long
                val upSpeed: Long

                if (coreDown >= totalDownBytes && totalDownBytes > 0L) {
                    downSpeed = coreDown - totalDownBytes
                    totalDownBytes = coreDown
                } else {
                    downSpeed = coreDown
                    totalDownBytes += coreDown
                }

                if (coreUp >= totalUpBytes && totalUpBytes > 0L) {
                    upSpeed = coreUp - totalUpBytes
                    totalUpBytes = coreUp
                } else {
                    upSpeed = coreUp
                    totalUpBytes += coreUp
                }

                _trafficStats.value = TrafficStats(
                    uploadSpeedBps = upSpeed,
                    downloadSpeedBps = downSpeed,
                    totalUploadedBytes = totalUpBytes,
                    totalDownloadedBytes = totalDownBytes,
                    connectedDurationSeconds = durationSeconds
                )

                if (durationSeconds % 2 == 0L) {
                    val notifText = "${_trafficStats.value.formattedDuration()} | ↓ ${_trafficStats.value.formattedDownloadSpeed()}  ↑ ${_trafficStats.value.formattedUploadSpeed()}"
                    val notificationManager = getSystemService(NOTIFICATION_SERVICE) as? NotificationManager
                    try {
                        notificationManager?.notify(NOTIFICATION_ID, createNotification(notifText, configName))
                    } catch (_: Exception) {}
                }
            }
        }
    }

    private fun handleDisconnect() {
        _connectionStatus.value = ConnectionStatus.DISCONNECTING
        log("INFO", "Остановка TESVPN и ядра libv2ray...")

        serviceScope.launch {
            statsJob?.cancel()
            statsJob = null

            packetEngine?.stop()
            packetEngine = null

            v2rayCore?.stop()
            v2rayCore = null

            try {
                vpnInterface?.close()
                vpnInterface = null
            } catch (_: Exception) {}

            delay(150)
            log("INFO", "Соединение закрыто. Трафик возвращен в прямой режим.")
            _connectionStatus.value = ConnectionStatus.DISCONNECTED
            _connectedConfig.value = null
            _trafficStats.value = TrafficStats()

            try {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } catch (_: Exception) {}
            stopSelf()
        }
    }

    private fun createNotification(statusText: String, title: String = "TESVPN Подключен"): Notification {
        val launchIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val disconnectIntent = Intent(this, TesVpnService::class.java).apply {
            action = ACTION_DISCONNECT
        }
        val disconnectPendingIntent = PendingIntent.getService(
            this, 1, disconnectIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Отключить", disconnectPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "TESVPN Connection",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Состояние VPN соединения TESVPN"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        statsJob?.cancel()
        packetEngine?.stop()
        packetEngine = null
        v2rayCore?.stop()
        v2rayCore = null
        try {
            vpnInterface?.close()
        } catch (_: Exception) {}
        _connectionStatus.value = ConnectionStatus.DISCONNECTED
        _connectedConfig.value = null
        super.onDestroy()
    }
}
