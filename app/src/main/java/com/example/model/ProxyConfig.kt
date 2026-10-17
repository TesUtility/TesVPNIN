package com.example.model

enum class ProxyProtocol(val displayName: String, val badgeColorHex: Long) {
    VLESS("VLESS", 0xFF00E5FF),
    VMESS("VMess", 0xFF38BDF8),
    TROJAN("Trojan", 0xFFA855F7),
    SHADOWSOCKS("Shadowsocks", 0xFFF59E0B),
    HYSTERIA2("Hysteria2", 0xFFFF4081),
    WIREGUARD("WireGuard", 0xFF10B981),
    SOCKS5("SOCKS5", 0xFF8B5CF6)
}

enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DISCONNECTING
}

data class SubscriptionInfo(
    val id: String,
    val url: String,
    val name: String,
    val lastUpdated: Long = System.currentTimeMillis(),
    val serverCount: Int = 0
)

data class ProxyConfig(
    val id: String,
    val name: String,
    val protocol: ProxyProtocol,
    val server: String,
    val port: Int,
    val uuidOrPassword: String,
    val alterId: Int = 0,
    val security: String = "tls", // none, tls, reality
    val network: String = "tcp",  // tcp, ws, grpc, h2
    val wsPath: String = "",
    val wsHost: String = "",
    val sni: String = "",
    val fingerprint: String = "chrome",
    val publicKey: String = "",   // Reality PBK
    val shortId: String = "",     // Reality SID
    val spiderX: String = "",
    val flow: String = "xtls-rprx-vision",
    val alpn: String = "",
    val countryCode: String = "DE",
    val pingMs: Long = -1,        // -1: not tested, -2: timeout/error, >0: latency in ms
    val isSelected: Boolean = false,
    val subscriptionUrl: String = "",
    val subscriptionName: String = "",
    val createdAt: Long = System.currentTimeMillis()
) {
    val countryFlag: String
        get() = when (countryCode.uppercase()) {
            "DE" -> "🇩🇪"
            "NL" -> "🇳🇱"
            "FI" -> "🇫🇮"
            "SG" -> "🇸🇬"
            "US" -> "🇺🇸"
            "TR" -> "🇹🇷"
            "KZ" -> "🇰🇿"
            "GB", "UK" -> "🇬🇧"
            "FR" -> "🇫🇷"
            "JP" -> "🇯🇵"
            "KR" -> "🇰🇷"
            "CA" -> "🇨🇦"
            "SE" -> "🇸🇪"
            "PL" -> "🇵🇱"
            "UA" -> "🇺🇦"
            "RU" -> "🇷🇺"
            "CH" -> "🇨🇭"
            "AT" -> "🇦🇹"
            "IT" -> "🇮🇹"
            "ES" -> "🇪🇸"
            else -> "🌐"
        }

    val displayAddress: String
        get() = "$server:$port"

    val securityBadge: String
        get() = when {
            security.equals("reality", ignoreCase = true) -> "REALITY"
            security.equals("tls", ignoreCase = true) -> "TLS"
            else -> network.uppercase()
        }
}

data class TrafficStats(
    val uploadSpeedBps: Long = 0,
    val downloadSpeedBps: Long = 0,
    val totalUploadedBytes: Long = 0,
    val totalDownloadedBytes: Long = 0,
    val connectedDurationSeconds: Long = 0
) {
    fun formattedUploadSpeed(): String = formatSpeed(uploadSpeedBps)
    fun formattedDownloadSpeed(): String = formatSpeed(downloadSpeedBps)
    fun formattedTotalUploaded(): String = formatBytes(totalUploadedBytes)
    fun formattedTotalDownloaded(): String = formatBytes(totalDownloadedBytes)

    fun formattedDuration(): String {
        val hours = connectedDurationSeconds / 3600
        val minutes = (connectedDurationSeconds % 3600) / 60
        val seconds = connectedDurationSeconds % 60
        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    private fun formatSpeed(bytesPerSec: Long): String {
        return when {
            bytesPerSec >= 1024 * 1024 -> String.format("%.1f MB/s", bytesPerSec / (1024.0 * 1024.0))
            bytesPerSec >= 1024 -> String.format("%.0f KB/s", bytesPerSec / 1024.0)
            else -> "$bytesPerSec B/s"
        }
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 * 1024 -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
            bytes >= 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
            bytes >= 1024 -> String.format("%.0f KB", bytes / 1024.0)
            else -> "$bytes B"
        }
    }
}

data class LogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val level: String = "INFO", // INFO, WARN, ERROR, ROUTE, STATS
    val message: String
)
