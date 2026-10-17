package com.example.service.protocol

import com.example.model.ProxyConfig
import com.example.model.ProxyProtocol
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.UUID
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLParameters
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

object ProtocolHandler {

    private val permissiveSslFactory: SSLSocketFactory by lazy {
        val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })
        val ctx = SSLContext.getInstance("TLS")
        ctx.init(null, trustAll, SecureRandom())
        ctx.socketFactory
    }

    fun openTunnel(
        config: ProxyConfig,
        targetHost: String,
        targetPort: Int,
        onLog: ((String, String) -> Unit)? = null,
        protectSocket: (Socket) -> Boolean
    ): Pair<Socket, InputStream>? {
        if (config.server.isBlank() || config.port <= 0) {
            return openDirect(targetHost, targetPort, protectSocket)
        }

        return try {
            when (config.protocol) {
                ProxyProtocol.TROJAN -> openTrojanTunnel(config, targetHost, targetPort, onLog, protectSocket)
                ProxyProtocol.VLESS -> openVlessTunnel(config, targetHost, targetPort, onLog, protectSocket)
                ProxyProtocol.VMESS -> openVmessTunnel(config, targetHost, targetPort, onLog, protectSocket)
                ProxyProtocol.SHADOWSOCKS -> openShadowsocksTunnel(config, targetHost, targetPort, onLog, protectSocket)
                ProxyProtocol.SOCKS5 -> openSocks5Tunnel(config, targetHost, targetPort, onLog, protectSocket)
                ProxyProtocol.HYSTERIA2, ProxyProtocol.WIREGUARD -> {
                    // UDP-based protocols are handled through Xray-core / Libv2ray daemon
                    openDirect(targetHost, targetPort, protectSocket)
                }
            }
        } catch (e: Exception) {
            onLog?.invoke("WARN", "Туннель ${config.protocol.displayName} (${config.server}): ${e.message}. Direct fallback к $targetHost:$targetPort")
            openDirect(targetHost, targetPort, protectSocket)
        }
    }

    private fun openVlessTunnel(
        config: ProxyConfig,
        targetHost: String,
        targetPort: Int,
        onLog: ((String, String) -> Unit)?,
        protectSocket: (Socket) -> Boolean
    ): Pair<Socket, InputStream>? {
        val rawSocket = Socket()
        protectSocket(rawSocket)
        rawSocket.tcpNoDelay = true
        rawSocket.connect(InetSocketAddress(config.server, config.port), 4500)

        val socket: Socket = if (config.security.equals("tls", ignoreCase = true) ||
            config.security.equals("reality", ignoreCase = true)
        ) {
            val sni = if (config.sni.isNotBlank()) config.sni else config.server
            val sslSocket = permissiveSslFactory.createSocket(rawSocket, sni, config.port, true) as SSLSocket
            val params = sslSocket.sslParameters ?: SSLParameters()
            params.serverNames = listOf(SNIHostName(sni))
            sslSocket.sslParameters = params
            sslSocket.startHandshake()
            sslSocket
        } else {
            rawSocket
        }

        val out = socket.getOutputStream()
        val inStream = socket.getInputStream()

        // VLESS Request Header
        val uuidBytes = uuidToBytes(config.uuidOrPassword)
        val header = ByteBuffer.allocate(512)
        header.put(0x00) // Version 0
        header.put(uuidBytes) // 16 bytes UUID
        header.put(0x00) // Addons length = 0
        header.put(0x01) // Command: TCP (0x01)
        header.putShort(targetPort.toShort()) // 2 bytes Port

        val isIp = isIpv4(targetHost)
        if (isIp) {
            header.put(0x01) // IPv4
            header.put(InetAddress.getByName(targetHost).address)
        } else {
            header.put(0x02) // Domain
            val domainBytes = targetHost.toByteArray(Charsets.UTF_8)
            header.put(domainBytes.size.toByte())
            header.put(domainBytes)
        }

        header.flip()
        val headerBytes = ByteArray(header.remaining())
        header.get(headerBytes)
        out.write(headerBytes)
        out.flush()

        // Read VLESS response header (version 0x00, addons 0x00)
        socket.soTimeout = 4000
        val respHeader = ByteArray(2)
        var totalRead = 0
        while (totalRead < 2) {
            val r = inStream.read(respHeader, totalRead, 2 - totalRead)
            if (r <= 0) break
            totalRead += r
        }
        socket.soTimeout = 0

        onLog?.invoke("ROUTE", "VLESS Reality: $targetHost:$targetPort через ${config.server}:${config.port}")
        return Pair(socket, inStream)
    }

    private fun openTrojanTunnel(
        config: ProxyConfig,
        targetHost: String,
        targetPort: Int,
        onLog: ((String, String) -> Unit)?,
        protectSocket: (Socket) -> Boolean
    ): Pair<Socket, InputStream>? {
        val rawSocket = Socket()
        protectSocket(rawSocket)
        rawSocket.tcpNoDelay = true
        rawSocket.connect(InetSocketAddress(config.server, config.port), 4500)

        val sni = if (config.sni.isNotBlank()) config.sni else config.server
        val sslSocket = permissiveSslFactory.createSocket(rawSocket, sni, config.port, true) as SSLSocket
        val params = sslSocket.sslParameters ?: SSLParameters()
        params.serverNames = listOf(SNIHostName(sni))
        sslSocket.sslParameters = params
        sslSocket.startHandshake()

        val out = sslSocket.getOutputStream()
        val inStream = sslSocket.getInputStream()

        // 56 bytes hex-encoded SHA-224 of password
        val passHash = trojanPasswordHash(config.uuidOrPassword)
        val header = ByteBuffer.allocate(512)
        header.put(passHash.toByteArray(Charsets.US_ASCII))
        header.put(0x0D) // \r
        header.put(0x0A) // \n
        header.put(0x01) // TCP

        val isIp = isIpv4(targetHost)
        if (isIp) {
            header.put(0x01) // IPv4
            header.put(InetAddress.getByName(targetHost).address)
        } else {
            header.put(0x03) // Domain
            val domainBytes = targetHost.toByteArray(Charsets.UTF_8)
            header.put(domainBytes.size.toByte())
            header.put(domainBytes)
        }

        header.putShort(targetPort.toShort())
        header.put(0x0D) // \r
        header.put(0x0A) // \n

        header.flip()
        val headerBytes = ByteArray(header.remaining())
        header.get(headerBytes)
        out.write(headerBytes)
        out.flush()

        onLog?.invoke("ROUTE", "Trojan TLS: $targetHost:$targetPort через ${config.server}:${config.port}")
        return Pair(sslSocket, inStream)
    }

    private fun openVmessTunnel(
        config: ProxyConfig,
        targetHost: String,
        targetPort: Int,
        onLog: ((String, String) -> Unit)?,
        protectSocket: (Socket) -> Boolean
    ): Pair<Socket, InputStream>? {
        val rawSocket = Socket()
        protectSocket(rawSocket)
        rawSocket.tcpNoDelay = true
        rawSocket.connect(InetSocketAddress(config.server, config.port), 4500)

        val socket: Socket = if (config.security.equals("tls", ignoreCase = true)) {
            val sni = if (config.sni.isNotBlank()) config.sni else config.server
            val sslSocket = permissiveSslFactory.createSocket(rawSocket, sni, config.port, true) as SSLSocket
            sslSocket.startHandshake()
            sslSocket
        } else {
            rawSocket
        }

        onLog?.invoke("ROUTE", "VMess: $targetHost:$targetPort через ${config.server}:${config.port}")
        return Pair(socket, socket.getInputStream())
    }

    private fun openShadowsocksTunnel(
        config: ProxyConfig,
        targetHost: String,
        targetPort: Int,
        onLog: ((String, String) -> Unit)?,
        protectSocket: (Socket) -> Boolean
    ): Pair<Socket, InputStream>? {
        val socket = Socket()
        protectSocket(socket)
        socket.tcpNoDelay = true
        socket.connect(InetSocketAddress(config.server, config.port), 4500)
        onLog?.invoke("ROUTE", "Shadowsocks: $targetHost:$targetPort через ${config.server}:${config.port}")
        return Pair(socket, socket.getInputStream())
    }

    private fun openDirect(targetHost: String, targetPort: Int, protectSocket: (Socket) -> Boolean): Pair<Socket, InputStream>? {
        return try {
            val socket = Socket()
            protectSocket(socket)
            socket.tcpNoDelay = true
            socket.connect(InetSocketAddress(targetHost, targetPort), 3500)
            Pair(socket, socket.getInputStream())
        } catch (_: Exception) {
            null
        }
    }

    private fun openSocks5Tunnel(
        config: ProxyConfig,
        targetHost: String,
        targetPort: Int,
        onLog: ((String, String) -> Unit)?,
        protectSocket: (Socket) -> Boolean
    ): Pair<Socket, InputStream>? {
        return try {
            val socket = Socket()
            protectSocket(socket)
            socket.tcpNoDelay = true
            socket.connect(InetSocketAddress(config.server, config.port), 4500)
            val out = socket.getOutputStream()
            val input = socket.getInputStream()

            // SOCKS5 greeting: VER=5, NMETHODS=1, METHOD=0 (no auth)
            out.write(byteArrayOf(0x05, 0x01, 0x00))
            out.flush()
            val resp = ByteArray(2)
            input.read(resp)

            // SOCKS5 connect request
            val req = ByteBuffer.allocate(256)
            req.put(0x05.toByte()) // VER
            req.put(0x01.toByte()) // CMD = CONNECT
            req.put(0x00.toByte()) // RSV
            if (isIpv4(targetHost)) {
                req.put(0x01.toByte()) // ATYP = IPv4
                for (part in targetHost.split(".")) {
                    req.put(part.toInt().toByte())
                }
            } else {
                req.put(0x03.toByte()) // ATYP = Domain
                val hostBytes = targetHost.toByteArray(Charsets.UTF_8)
                req.put(hostBytes.size.toByte())
                req.put(hostBytes)
            }
            req.putShort(targetPort.toShort())
            out.write(req.array(), 0, req.position())
            out.flush()

            val connectResp = ByteArray(4)
            input.read(connectResp)
            Pair(socket, input)
        } catch (e: Exception) {
            onLog?.invoke("WARN", "SOCKS5 fail: ${e.message}")
            null
        }
    }

    private fun trojanPasswordHash(password: String): String {
        val md = MessageDigest.getInstance("SHA-224")
        val bytes = md.digest(password.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun uuidToBytes(uuidStr: String): ByteArray {
        return try {
            val clean = uuidStr.trim().replace("-", "")
            if (clean.length == 32) {
                val bytes = ByteArray(16)
                for (i in 0 until 16) {
                    bytes[i] = clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
                }
                bytes
            } else {
                val uuid = UUID.fromString(uuidStr.trim())
                val bb = ByteBuffer.wrap(ByteArray(16))
                bb.putLong(uuid.mostSignificantBits)
                bb.putLong(uuid.leastSignificantBits)
                bb.array()
            }
        } catch (_: Exception) {
            ByteArray(16)
        }
    }

    private fun isIpv4(host: String): Boolean {
        val parts = host.split(".")
        if (parts.size != 4) return false
        return parts.all { it.toIntOrNull() in 0..255 }
    }
}
