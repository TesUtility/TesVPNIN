package com.example.parser

import android.net.Uri
import android.util.Base64
import com.example.model.ProxyConfig
import com.example.model.ProxyProtocol
import org.json.JSONObject
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID

object ProxyParser {

    fun parse(uriString: String): ProxyConfig? {
        val trimmed = uriString.trim().trim('\uFEFF', '\u200B')
        return when {
            trimmed.startsWith("vless://", ignoreCase = true) -> parseVless(trimmed)
            trimmed.startsWith("vmess://", ignoreCase = true) -> parseVmess(trimmed)
            trimmed.startsWith("trojan://", ignoreCase = true) -> parseTrojan(trimmed)
            trimmed.startsWith("ss://", ignoreCase = true) -> parseShadowsocks(trimmed)
            trimmed.startsWith("hysteria2://", ignoreCase = true) || trimmed.startsWith("hy2://", ignoreCase = true) -> parseHysteria2(trimmed)
            trimmed.startsWith("wireguard://", ignoreCase = true) || trimmed.startsWith("wg://", ignoreCase = true) -> parseWireguard(trimmed)
            trimmed.startsWith("socks5://", ignoreCase = true) || trimmed.startsWith("socks://", ignoreCase = true) -> parseSocks(trimmed)
            else -> null
        }
    }

    fun parseMultiple(content: String): List<ProxyConfig> {
        val cleaned = content.trim().trim('\uFEFF', '\u200B')
        if (cleaned.isEmpty()) return emptyList()

        val configs = mutableListOf<ProxyConfig>()

        // 1. First attempt: Line by line
        for (line in cleaned.lines()) {
            val trimmedLine = line.trim()
            if (trimmedLine.isEmpty() || trimmedLine.startsWith("#") || trimmedLine.startsWith("//")) continue
            val parsed = parse(trimmedLine)
            if (parsed != null) {
                configs.add(parsed)
            }
        }

        // 2. If nothing found or if entire content might be base64 (common in subscriptions)
        if (configs.isEmpty()) {
            val base64Candidates = listOf(
                cleaned,
                cleaned.replace("\n", "").replace("\r", "").replace(" ", "")
            )

            for (candidate in base64Candidates) {
                val decoded = tryDecodeBase64(candidate)
                if (!decoded.isNullOrEmpty() && decoded != candidate) {
                    for (line in decoded.lines()) {
                        val trimmedLine = line.trim()
                        if (trimmedLine.isEmpty() || trimmedLine.startsWith("#") || trimmedLine.startsWith("//")) continue
                        val parsed = parse(trimmedLine)
                        if (parsed != null) {
                            configs.add(parsed)
                        }
                    }
                    if (configs.isNotEmpty()) break
                }
            }
        }

        // 3. Fallback: Check for Clash YAML proxies block if present
        if (configs.isEmpty() && (cleaned.contains("proxies:") || cleaned.contains("- name:"))) {
            configs.addAll(parseClashProxies(cleaned))
        }

        // 4. Fallback: Check for Sing-box JSON format
        if (configs.isEmpty() && (cleaned.startsWith("{") && cleaned.contains("\"outbounds\""))) {
            configs.addAll(parseSingBoxProxies(cleaned))
        }

        return configs
    }

    private fun tryDecodeBase64(input: String): String? {
        val flags = intArrayOf(
            Base64.DEFAULT,
            Base64.URL_SAFE or Base64.NO_PADDING,
            Base64.NO_WRAP,
            Base64.URL_SAFE
        )
        for (flag in flags) {
            try {
                val bytes = Base64.decode(input, flag)
                if (bytes.isNotEmpty()) {
                    val str = String(bytes, StandardCharsets.UTF_8)
                    if (str.any { it in "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789" }) {
                        return str
                    }
                }
            } catch (_: Exception) {}
        }
        return null
    }

    private fun parseVless(rawUri: String): ProxyConfig? {
        return try {
            val uri = Uri.parse(rawUri)
            val userInfo = uri.userInfo ?: uri.authority?.substringBefore("@") ?: ""
            val host = uri.host ?: ""
            val port = if (uri.port > 0) uri.port else 443
            val name = decodeFragment(uri.fragment, "VLESS Node")
            val security = uri.getQueryParameter("security") ?: "tls"
            val network = uri.getQueryParameter("type") ?: "tcp"
            val sni = uri.getQueryParameter("sni") ?: ""
            val fp = uri.getQueryParameter("fp") ?: "chrome"
            val pbk = uri.getQueryParameter("pbk") ?: ""
            val sid = uri.getQueryParameter("sid") ?: ""
            val spx = uri.getQueryParameter("spx") ?: ""
            val flow = uri.getQueryParameter("flow") ?: "xtls-rprx-vision"
            val alpn = uri.getQueryParameter("alpn") ?: ""
            val path = uri.getQueryParameter("path") ?: ""
            val wsHost = uri.getQueryParameter("host") ?: ""

            ProxyConfig(
                id = UUID.randomUUID().toString(),
                name = name,
                protocol = ProxyProtocol.VLESS,
                server = host,
                port = port,
                uuidOrPassword = userInfo,
                security = security,
                network = network,
                sni = sni,
                fingerprint = fp,
                publicKey = pbk,
                shortId = sid,
                spiderX = spx,
                flow = flow,
                alpn = alpn,
                wsPath = path,
                wsHost = wsHost,
                countryCode = detectCountryCode(name, host)
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun parseVmess(rawUri: String): ProxyConfig? {
        return try {
            val base64Data = rawUri.removePrefix("vmess://").trim()
            val decodedJson = tryDecodeBase64(base64Data) ?: String(Base64.decode(base64Data, Base64.DEFAULT or Base64.URL_SAFE), StandardCharsets.UTF_8)
            val json = JSONObject(decodedJson)

            val name = json.optString("ps", "VMess Node")
            val server = json.optString("add", "")
            val port = json.optInt("port", 443)
            val uuid = json.optString("id", "")
            val alterId = json.optInt("aid", 0)
            val net = json.optString("net", "tcp")
            val security = if (json.optString("tls", "").isNotEmpty() && json.optString("tls") != "none") "tls" else "none"
            val path = json.optString("path", "")
            val host = json.optString("host", "")
            val sni = json.optString("sni", "")

            ProxyConfig(
                id = UUID.randomUUID().toString(),
                name = name,
                protocol = ProxyProtocol.VMESS,
                server = server,
                port = port,
                uuidOrPassword = uuid,
                alterId = alterId,
                security = security,
                network = net,
                wsPath = path,
                wsHost = host,
                sni = sni,
                countryCode = detectCountryCode(name, server)
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun parseTrojan(rawUri: String): ProxyConfig? {
        return try {
            val uri = Uri.parse(rawUri)
            val password = uri.userInfo ?: uri.authority?.substringBefore("@") ?: ""
            val host = uri.host ?: ""
            val port = if (uri.port > 0) uri.port else 443
            val name = decodeFragment(uri.fragment, "Trojan Node")
            val security = uri.getQueryParameter("security") ?: "tls"
            val network = uri.getQueryParameter("type") ?: "tcp"
            val sni = uri.getQueryParameter("sni") ?: host
            val path = uri.getQueryParameter("path") ?: ""
            val alpn = uri.getQueryParameter("alpn") ?: ""

            ProxyConfig(
                id = UUID.randomUUID().toString(),
                name = name,
                protocol = ProxyProtocol.TROJAN,
                server = host,
                port = port,
                uuidOrPassword = password,
                security = security,
                network = network,
                sni = sni,
                alpn = alpn,
                wsPath = path,
                countryCode = detectCountryCode(name, host)
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun parseShadowsocks(rawUri: String): ProxyConfig? {
        return try {
            val clean = rawUri.removePrefix("ss://")
            val fragment = clean.substringAfter("#", "")
            val name = decodeFragment(fragment, "Shadowsocks Node")
            val content = clean.substringBefore("#")

            if (content.contains("@")) {
                val parts = content.split("@")
                val decodedUserInfo = tryDecodeBase64(parts[0]) ?: parts[0]
                val (method, password) = if (decodedUserInfo.contains(":")) {
                    decodedUserInfo.substringBefore(":") to decodedUserInfo.substringAfter(":")
                } else {
                    "aes-256-gcm" to decodedUserInfo
                }
                val hostPort = parts[1].split(":")
                ProxyConfig(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    protocol = ProxyProtocol.SHADOWSOCKS,
                    server = hostPort[0],
                    port = hostPort.getOrNull(1)?.substringBefore("?")?.toIntOrNull() ?: 8388,
                    uuidOrPassword = password,
                    security = method,
                    countryCode = detectCountryCode(name, hostPort[0])
                )
            } else {
                val decoded = tryDecodeBase64(content) ?: String(Base64.decode(content, Base64.DEFAULT), StandardCharsets.UTF_8)
                val userInfo = decoded.substringBefore("@")
                val (method, password) = if (userInfo.contains(":")) {
                    userInfo.substringBefore(":") to userInfo.substringAfter(":")
                } else {
                    "aes-256-gcm" to userInfo
                }
                val hostPort = decoded.substringAfter("@").split(":")
                ProxyConfig(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    protocol = ProxyProtocol.SHADOWSOCKS,
                    server = hostPort[0],
                    port = hostPort.getOrNull(1)?.substringBefore("?")?.toIntOrNull() ?: 8388,
                    uuidOrPassword = password,
                    security = method,
                    countryCode = detectCountryCode(name, hostPort[0])
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseHysteria2(rawUri: String): ProxyConfig? {
        return try {
            val uri = Uri.parse(rawUri)
            val auth = uri.userInfo ?: uri.authority?.substringBefore("@") ?: ""
            val host = uri.host ?: ""
            val port = if (uri.port > 0) uri.port else 443
            val name = decodeFragment(uri.fragment, "Hysteria2 Node")
            val sni = uri.getQueryParameter("sni") ?: host
            val alpn = uri.getQueryParameter("alpn") ?: "h3"

            ProxyConfig(
                id = UUID.randomUUID().toString(),
                name = name,
                protocol = ProxyProtocol.HYSTERIA2,
                server = host,
                port = port,
                uuidOrPassword = auth,
                security = "tls",
                network = "udp",
                sni = sni,
                alpn = alpn,
                countryCode = detectCountryCode(name, host)
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun parseWireguard(rawUri: String): ProxyConfig? {
        return try {
            val uri = Uri.parse(rawUri)
            val privKey = uri.userInfo ?: uri.authority?.substringBefore("@") ?: ""
            val host = uri.host ?: ""
            val port = if (uri.port > 0) uri.port else 51820
            val name = decodeFragment(uri.fragment, "WireGuard Node")
            val pubKey = uri.getQueryParameter("public_key") ?: uri.getQueryParameter("pubkey") ?: ""

            ProxyConfig(
                id = UUID.randomUUID().toString(),
                name = name,
                protocol = ProxyProtocol.WIREGUARD,
                server = host,
                port = port,
                uuidOrPassword = privKey,
                publicKey = pubKey,
                security = "none",
                network = "udp",
                countryCode = detectCountryCode(name, host)
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun parseSocks(rawUri: String): ProxyConfig? {
        return try {
            val uri = Uri.parse(rawUri)
            val userInfo = uri.userInfo ?: uri.authority?.substringBefore("@") ?: ""
            val host = uri.host ?: ""
            val port = if (uri.port > 0) uri.port else 1080
            val name = decodeFragment(uri.fragment, "SOCKS5 Node")

            ProxyConfig(
                id = UUID.randomUUID().toString(),
                name = name,
                protocol = ProxyProtocol.SOCKS5,
                server = host,
                port = port,
                uuidOrPassword = userInfo,
                security = "none",
                network = "tcp",
                countryCode = detectCountryCode(name, host)
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun parseClashProxies(yamlText: String): List<ProxyConfig> {
        val list = mutableListOf<ProxyConfig>()
        val lines = yamlText.lines()
        var inProxies = false

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("proxies:")) {
                inProxies = true
                continue
            }
            if (inProxies && trimmed.startsWith("- ") && (trimmed.contains("name:") || trimmed.contains("server:"))) {
                try {
                    val name = extractYamlField(trimmed, "name")
                    val server = extractYamlField(trimmed, "server")
                    val port = extractYamlField(trimmed, "port").toIntOrNull() ?: 443
                    val type = extractYamlField(trimmed, "type").lowercase()
                    val uuid = extractYamlField(trimmed, "uuid").ifEmpty { extractYamlField(trimmed, "password") }
                    val sni = extractYamlField(trimmed, "sni")

                    val protocol = when {
                        type.contains("vless") -> ProxyProtocol.VLESS
                        type.contains("vmess") -> ProxyProtocol.VMESS
                        type.contains("trojan") -> ProxyProtocol.TROJAN
                        type.contains("ss") || type.contains("shadowsocks") -> ProxyProtocol.SHADOWSOCKS
                        type.contains("hy2") || type.contains("hysteria") -> ProxyProtocol.HYSTERIA2
                        else -> null
                    }

                    if (protocol != null && server.isNotEmpty()) {
                        list.add(
                            ProxyConfig(
                                id = UUID.randomUUID().toString(),
                                name = if (name.isNotEmpty()) name else "${protocol.displayName} Server",
                                protocol = protocol,
                                server = server,
                                port = port,
                                uuidOrPassword = uuid,
                                sni = sni,
                                countryCode = detectCountryCode(name, server)
                            )
                        )
                    }
                } catch (_: Exception) {}
            }
        }
        return list
    }

    private fun extractYamlField(line: String, field: String): String {
        val pattern = Regex("$field\\s*:\\s*[\"']?([^,\"'}\\]]+)[\"']?")
        return pattern.find(line)?.groupValues?.getOrNull(1)?.trim() ?: ""
    }

    private fun parseSingBoxProxies(jsonText: String): List<ProxyConfig> {
        val list = mutableListOf<ProxyConfig>()
        try {
            val root = JSONObject(jsonText)
            val outbounds = root.optJSONArray("outbounds") ?: return emptyList()
            for (i in 0 until outbounds.length()) {
                val obj = outbounds.getJSONObject(i)
                val type = obj.optString("type", "").lowercase()
                val tag = obj.optString("tag", "")
                val server = obj.optString("server", "")
                val port = obj.optInt("server_port", 443)
                val uuid = obj.optString("uuid", "").ifEmpty { obj.optString("password", "") }

                val protocol = when {
                    type == "vless" -> ProxyProtocol.VLESS
                    type == "vmess" -> ProxyProtocol.VMESS
                    type == "trojan" -> ProxyProtocol.TROJAN
                    type == "shadowsocks" -> ProxyProtocol.SHADOWSOCKS
                    type == "hysteria2" -> ProxyProtocol.HYSTERIA2
                    type == "wireguard" -> ProxyProtocol.WIREGUARD
                    type == "socks" -> ProxyProtocol.SOCKS5
                    else -> null
                }

                if (protocol != null && server.isNotEmpty()) {
                    val tlsObj = obj.optJSONObject("tls")
                    val sni = tlsObj?.optString("server_name", "") ?: ""
                    val realityObj = tlsObj?.optJSONObject("reality")
                    val pbk = realityObj?.optString("public_key", "") ?: ""
                    val sid = realityObj?.optString("short_id", "") ?: ""

                    list.add(
                        ProxyConfig(
                            id = UUID.randomUUID().toString(),
                            name = if (tag.isNotEmpty()) tag else "${protocol.displayName} Server",
                            protocol = protocol,
                            server = server,
                            port = port,
                            uuidOrPassword = uuid,
                            security = if (realityObj != null) "reality" else if (tlsObj?.optBoolean("enabled") == true) "tls" else "none",
                            sni = sni,
                            publicKey = pbk,
                            shortId = sid,
                            countryCode = detectCountryCode(tag, server)
                        )
                    )
                }
            }
        } catch (_: Exception) {}
        return list
    }

    fun toUri(config: ProxyConfig): String {
        return when (config.protocol) {
            ProxyProtocol.VLESS -> {
                val params = mutableListOf<String>()
                params.add("type=${config.network}")
                params.add("security=${config.security}")
                if (config.sni.isNotEmpty()) params.add("sni=${config.sni}")
                if (config.fingerprint.isNotEmpty()) params.add("fp=${config.fingerprint}")
                if (config.security.equals("reality", ignoreCase = true)) {
                    if (config.publicKey.isNotEmpty()) params.add("pbk=${config.publicKey}")
                    if (config.shortId.isNotEmpty()) params.add("sid=${config.shortId}")
                    if (config.spiderX.isNotEmpty()) params.add("spx=${URLEncoder.encode(config.spiderX, "UTF-8")}")
                    if (config.flow.isNotEmpty()) params.add("flow=${config.flow}")
                }
                if (config.wsPath.isNotEmpty()) params.add("path=${URLEncoder.encode(config.wsPath, "UTF-8")}")
                if (config.wsHost.isNotEmpty()) params.add("host=${config.wsHost}")
                val query = if (params.isNotEmpty()) "?${params.joinToString("&")}" else ""
                val fragment = "#${URLEncoder.encode(config.name, "UTF-8")}"
                "vless://${config.uuidOrPassword}@${config.server}:${config.port}$query$fragment"
            }
            ProxyProtocol.VMESS -> {
                val json = JSONObject().apply {
                    put("v", "2")
                    put("ps", config.name)
                    put("add", config.server)
                    put("port", config.port)
                    put("id", config.uuidOrPassword)
                    put("aid", config.alterId)
                    put("net", config.network)
                    put("type", "none")
                    put("host", config.wsHost)
                    put("path", config.wsPath)
                    put("tls", if (config.security == "tls") "tls" else "")
                    put("sni", config.sni)
                }
                val base64 = Base64.encodeToString(json.toString().toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
                "vmess://$base64"
            }
            ProxyProtocol.TROJAN -> {
                val params = mutableListOf<String>()
                params.add("type=${config.network}")
                params.add("security=${config.security}")
                if (config.sni.isNotEmpty()) params.add("sni=${config.sni}")
                if (config.wsPath.isNotEmpty()) params.add("path=${URLEncoder.encode(config.wsPath, "UTF-8")}")
                val query = if (params.isNotEmpty()) "?${params.joinToString("&")}" else ""
                val fragment = "#${URLEncoder.encode(config.name, "UTF-8")}"
                "trojan://${config.uuidOrPassword}@${config.server}:${config.port}$query$fragment"
            }
            ProxyProtocol.SHADOWSOCKS -> {
                val authString = if (config.security.isNotEmpty() && !config.uuidOrPassword.contains(":")) {
                    "${config.security}:${config.uuidOrPassword}"
                } else {
                    config.uuidOrPassword
                }
                val encodedAuth = Base64.encodeToString(authString.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
                val fragment = "#${URLEncoder.encode(config.name, "UTF-8")}"
                "ss://$encodedAuth@${config.server}:${config.port}$fragment"
            }
            ProxyProtocol.HYSTERIA2 -> {
                val fragment = "#${URLEncoder.encode(config.name, "UTF-8")}"
                val sniParam = if (config.sni.isNotEmpty()) "?sni=${config.sni}" else ""
                "hy2://${config.uuidOrPassword}@${config.server}:${config.port}$sniParam$fragment"
            }
            ProxyProtocol.WIREGUARD -> {
                val fragment = "#${URLEncoder.encode(config.name, "UTF-8")}"
                val pub = if (config.publicKey.isNotEmpty()) "?public_key=${config.publicKey}" else ""
                "wg://${config.uuidOrPassword}@${config.server}:${config.port}$pub$fragment"
            }
            ProxyProtocol.SOCKS5 -> {
                val fragment = "#${URLEncoder.encode(config.name, "UTF-8")}"
                val auth = if (config.uuidOrPassword.isNotEmpty()) "${config.uuidOrPassword}@" else ""
                "socks5://$auth${config.server}:${config.port}$fragment"
            }
        }
    }

    private fun decodeFragment(fragment: String?, fallback: String): String {
        if (fragment.isNullOrBlank()) return fallback
        return try {
            URLDecoder.decode(fragment, "UTF-8")
        } catch (_: Exception) {
            fragment
        }
    }

    fun detectCountryCode(name: String, host: String): String {
        val lower = "$name $host".lowercase()
        return when {
            lower.contains("germany") || lower.contains("frankfurt") || lower.contains("de.") || lower.contains("🇩🇪") || lower.contains("deutsch") -> "DE"
            lower.contains("netherlands") || lower.contains("amsterdam") || lower.contains("nl.") || lower.contains("🇳🇱") -> "NL"
            lower.contains("finland") || lower.contains("helsinki") || lower.contains("fi.") || lower.contains("🇫🇮") -> "FI"
            lower.contains("singapore") || lower.contains("sg.") || lower.contains("🇸🇬") -> "SG"
            lower.contains("united states") || lower.contains("usa") || lower.contains("us.") || lower.contains("🇺🇸") || lower.contains("america") -> "US"
            lower.contains("turkey") || lower.contains("istanbul") || lower.contains("tr.") || lower.contains("🇹🇷") -> "TR"
            lower.contains("kazakhstan") || lower.contains("almaty") || lower.contains("astana") || lower.contains("kz.") || lower.contains("🇰🇿") -> "KZ"
            lower.contains("france") || lower.contains("paris") || lower.contains("fr.") || lower.contains("🇫🇷") -> "FR"
            lower.contains("japan") || lower.contains("tokyo") || lower.contains("jp.") || lower.contains("🇯🇵") -> "JP"
            lower.contains("sweden") || lower.contains("stockholm") || lower.contains("se.") || lower.contains("🇸🇪") -> "SE"
            lower.contains("russia") || lower.contains("moscow") || lower.contains("ru.") || lower.contains("🇷🇺") -> "RU"
            lower.contains("ukraine") || lower.contains("kyiv") || lower.contains("ua.") || lower.contains("🇺🇦") -> "UA"
            lower.contains("poland") || lower.contains("warsaw") || lower.contains("pl.") || lower.contains("🇵🇱") -> "PL"
            lower.contains("united kingdom") || lower.contains("london") || lower.contains("gb.") || lower.contains("uk.") || lower.contains("🇬🇧") -> "GB"
            else -> "DE"
        }
    }
}
