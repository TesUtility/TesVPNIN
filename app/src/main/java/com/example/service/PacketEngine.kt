package com.example.service

import android.net.VpnService
import com.example.model.ProxyConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

class PacketEngine(
    private val vpnService: VpnService,
    private val tunFd: FileDescriptor,
    private val config: ProxyConfig,
    private val dnsServerIp: String,
    private val onTraffic: (upBytes: Long, downBytes: Long) -> Unit,
    private val onLog: (level: String, msg: String) -> Unit
) {
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val inStream = FileInputStream(tunFd)
    private val outStream = FileOutputStream(tunFd)
    private val writeLock = Any()

    val totalUpBytes = AtomicLong(0L)
    val totalDownBytes = AtomicLong(0L)

    private val tcpSessions = ConcurrentHashMap<String, TcpSession>()

    private class TcpSession(
        val srcIp: ByteArray,
        val dstIp: ByteArray,
        val srcPort: Int,
        val dstPort: Int,
        var clientSeq: Long,
        var serverSeq: Long,
        var remoteSocket: Socket? = null,
        var remoteOutStream: OutputStream? = null,
        var remoteInStream: InputStream? = null,
        @Volatile var isConnected: Boolean = false,
        @Volatile var isClosed: Boolean = false
    ) {
        val outLock = Any()
    }

    fun start() {
        scope.launch {
            val buffer = ByteArray(32768)
            onLog("INFO", "Сетевой шлюз tun0 запущен. Маршрутизация трафика в Xray-core активна.")

            while (isActive) {
                val readBytes = try {
                    inStream.read(buffer, 0, buffer.size)
                } catch (e: Exception) {
                    if (!isActive) break
                    delay(10)
                    continue
                }

                if (readBytes > 0) {
                    processIpPacket(buffer, readBytes)
                } else {
                    delay(5)
                }
            }
        }
    }

    fun stop() {
        try { scope.cancel() } catch (_: Exception) {}
        tcpSessions.values.forEach { session ->
            session.isClosed = true
            try { session.remoteSocket?.close() } catch (_: Exception) {}
        }
        tcpSessions.clear()
        try { inStream.close() } catch (_: Exception) {}
        try { outStream.close() } catch (_: Exception) {}
    }

    private fun processIpPacket(packet: ByteArray, length: Int) {
        if (length < 20) return
        val versionAndIhl = packet[0].toInt() and 0xFF
        if (versionAndIhl shr 4 != 4) return // IPv4 only

        val ihl = (versionAndIhl and 0x0F) * 4
        if (length < ihl) return

        val protocol = packet[9].toInt() and 0xFF
        val srcIp = packet.copyOfRange(12, 16)
        val dstIp = packet.copyOfRange(16, 20)

        when (protocol) {
            1 -> handleIcmp(packet, length, ihl, srcIp, dstIp)
            17 -> handleUdp(packet, length, ihl, srcIp, dstIp)
            6 -> handleTcp(packet, length, ihl, srcIp, dstIp)
        }
    }

    private fun handleIcmp(packet: ByteArray, length: Int, ihl: Int, srcIp: ByteArray, dstIp: ByteArray) {
        if (length < ihl + 8) return
        val type = packet[ihl].toInt() and 0xFF
        if (type == 8) { // Echo request -> Echo reply
            packet[ihl] = 0
            System.arraycopy(dstIp, 0, packet, 12, 4)
            System.arraycopy(srcIp, 0, packet, 16, 4)

            packet[ihl + 2] = 0
            packet[ihl + 3] = 0
            val icmpChecksum = calculateChecksum(packet, ihl, length - ihl)
            packet[ihl + 2] = (icmpChecksum shr 8).toByte()
            packet[ihl + 3] = (icmpChecksum and 0xFF).toByte()

            packet[10] = 0
            packet[11] = 0
            val ipChecksum = calculateChecksum(packet, 0, ihl)
            packet[10] = (ipChecksum shr 8).toByte()
            packet[11] = (ipChecksum and 0xFF).toByte()

            sendToTun(packet, length)
        }
    }

    private fun handleUdp(packet: ByteArray, length: Int, ihl: Int, srcIp: ByteArray, dstIp: ByteArray) {
        if (length < ihl + 8) return
        val srcPort = ((packet[ihl].toInt() and 0xFF) shl 8) or (packet[ihl + 1].toInt() and 0xFF)
        val dstPort = ((packet[ihl + 2].toInt() and 0xFF) shl 8) or (packet[ihl + 3].toInt() and 0xFF)
        val udpLen = ((packet[ihl + 4].toInt() and 0xFF) shl 8) or (packet[ihl + 5].toInt() and 0xFF)

        if (dstPort == 53) {
            val payloadOffset = ihl + 8
            val payloadLen = udpLen - 8
            if (payloadLen <= 0 || payloadOffset + payloadLen > length) return

            val dnsQuery = packet.copyOfRange(payloadOffset, payloadOffset + payloadLen)
            totalUpBytes.addAndGet(dnsQuery.size.toLong())
            onTraffic(dnsQuery.size.toLong(), 0L)

            scope.launch {
                var dnsResp: ByteArray? = null

                // 1. Send DNS query through Xray dokodemo-door (127.0.0.1:10853)
                try {
                    val socket = DatagramSocket()
                    vpnService.protect(socket)
                    socket.soTimeout = 2500
                    val outPacket = DatagramPacket(
                        dnsQuery, dnsQuery.size,
                        InetAddress.getByName("127.0.0.1"), 10853
                    )
                    socket.send(outPacket)
                    val respBuf = ByteArray(2048)
                    val inPacket = DatagramPacket(respBuf, respBuf.size)
                    socket.receive(inPacket)
                    socket.close()
                    if (inPacket.length > 0) {
                        dnsResp = inPacket.data.copyOf(inPacket.length)
                    }
                } catch (_: Exception) {}

                // 2. Fallback to direct protected socket if Xray DNS did not reply
                if (dnsResp == null) {
                    try {
                        val socket = DatagramSocket()
                        vpnService.protect(socket)
                        socket.soTimeout = 2500
                        val target = if (dnsServerIp.isNotBlank()) dnsServerIp else "1.1.1.1"
                        val outPacket = DatagramPacket(
                            dnsQuery, dnsQuery.size,
                            InetAddress.getByName(target), 53
                        )
                        socket.send(outPacket)
                        val respBuf = ByteArray(2048)
                        val inPacket = DatagramPacket(respBuf, respBuf.size)
                        socket.receive(inPacket)
                        socket.close()
                        if (inPacket.length > 0) {
                            dnsResp = inPacket.data.copyOf(inPacket.length)
                        }
                    } catch (e: Exception) {
                        onLog("WARN", "DNS запрос: ${e.message}")
                    }
                }

                if (dnsResp != null) {
                    totalDownBytes.addAndGet(dnsResp.size.toLong())
                    onTraffic(0L, dnsResp.size.toLong())
                    buildAndSendUdpPacket(
                        srcIp = dstIp,
                        dstIp = srcIp,
                        srcPort = dstPort,
                        dstPort = srcPort,
                        payload = dnsResp
                    )
                }
            }
        }
    }

    private fun handleTcp(packet: ByteArray, length: Int, ihl: Int, srcIp: ByteArray, dstIp: ByteArray) {
        if (length < ihl + 20) return
        val srcPort = ((packet[ihl].toInt() and 0xFF) shl 8) or (packet[ihl + 1].toInt() and 0xFF)
        val dstPort = ((packet[ihl + 2].toInt() and 0xFF) shl 8) or (packet[ihl + 3].toInt() and 0xFF)

        val seqNum = ((packet[ihl + 4].toLong() and 0xFF) shl 24) or
                ((packet[ihl + 5].toLong() and 0xFF) shl 16) or
                ((packet[ihl + 6].toLong() and 0xFF) shl 8) or
                (packet[ihl + 7].toLong() and 0xFF)

        val dataOffset = ((packet[ihl + 12].toInt() and 0xF0) shr 4) * 4
        val flags = packet[ihl + 13].toInt() and 0xFF

        val isSyn = (flags and 0x02) != 0
        val isAck = (flags and 0x10) != 0
        val isFin = (flags and 0x01) != 0
        val isRst = (flags and 0x04) != 0

        val sessionKey = "${formatIp(srcIp)}:$srcPort->${formatIp(dstIp)}:$dstPort"
        val payloadOffset = ihl + dataOffset
        val payloadLen = length - payloadOffset

        if (isRst) {
            val session = tcpSessions.remove(sessionKey)
            session?.isClosed = true
            try { session?.remoteSocket?.close() } catch (_: Exception) {}
            return
        }

        if (isSyn && !isAck) {
            val initialServerSeq = Random.nextLong(10000, 1000000)
            val session = TcpSession(
                srcIp = srcIp,
                dstIp = dstIp,
                srcPort = srcPort,
                dstPort = dstPort,
                clientSeq = seqNum + 1,
                serverSeq = initialServerSeq
            )
            tcpSessions[sessionKey] = session

            scope.launch {
                val targetIp = formatIp(dstIp)
                val socket = connectSocks5(targetIp, dstPort)
                if (socket != null && !session.isClosed) {
                    session.remoteSocket = socket
                    session.remoteOutStream = socket.getOutputStream()
                    session.remoteInStream = socket.getInputStream()
                    session.isConnected = true

                    // SOCKS5 tunnel is ready: send SYN-ACK to tun0
                    sendTcpPacket(
                        srcIp = dstIp,
                        dstIp = srcIp,
                        srcPort = dstPort,
                        dstPort = srcPort,
                        seq = session.serverSeq,
                        ack = session.clientSeq,
                        flags = 0x12, // SYN + ACK
                        payload = ByteArray(0)
                    )
                    session.serverSeq++

                    // Start background reader for this TCP session
                    startRemoteReader(session, sessionKey)
                } else {
                    // Send RST if Xray or proxy rejects connection
                    sendTcpPacket(
                        srcIp = dstIp,
                        dstIp = srcIp,
                        srcPort = dstPort,
                        dstPort = srcPort,
                        seq = initialServerSeq,
                        ack = seqNum + 1,
                        flags = 0x04, // RST
                        payload = ByteArray(0)
                    )
                    tcpSessions.remove(sessionKey)
                }
            }
            return
        }

        val session = tcpSessions[sessionKey] ?: return

        if (isFin) {
            session.clientSeq = seqNum + 1
            sendTcpPacket(
                srcIp = dstIp,
                dstIp = srcIp,
                srcPort = dstPort,
                dstPort = srcPort,
                seq = session.serverSeq,
                ack = session.clientSeq,
                flags = 0x11, // FIN + ACK
                payload = ByteArray(0)
            )
            session.serverSeq++
            session.isClosed = true
            try { session.remoteSocket?.close() } catch (_: Exception) {}
            tcpSessions.remove(sessionKey)
            return
        }

        if (payloadLen > 0) {
            session.clientSeq = seqNum + payloadLen
            // Acknowledge receipt of data immediately
            sendTcpPacket(
                srcIp = dstIp,
                dstIp = srcIp,
                srcPort = dstPort,
                dstPort = srcPort,
                seq = session.serverSeq,
                ack = session.clientSeq,
                flags = 0x10, // ACK
                payload = ByteArray(0)
            )

            val data = packet.copyOfRange(payloadOffset, payloadOffset + payloadLen)
            totalUpBytes.addAndGet(data.size.toLong())
            onTraffic(data.size.toLong(), 0L)

            // Write payload synchronously under session.outLock to preserve packet order
            scope.launch {
                try {
                    synchronized(session.outLock) {
                        session.remoteOutStream?.write(data)
                        session.remoteOutStream?.flush()
                    }
                } catch (e: Exception) {
                    onLog("WARN", "Передача в сокет ($sessionKey): ${e.message}")
                }
            }
        }
    }

    private fun startRemoteReader(session: TcpSession, sessionKey: String) {
        scope.launch {
            val inStream = session.remoteInStream ?: return@launch
            val socket = session.remoteSocket ?: return@launch
            val chunk = ByteArray(1380)

            try {
                while (session.isConnected && !session.isClosed && !socket.isClosed) {
                    val read = inStream.read(chunk)
                    if (read <= 0) break

                    val payload = chunk.copyOf(read)
                    totalDownBytes.addAndGet(read.toLong())
                    onTraffic(0L, read.toLong())

                    sendTcpPacket(
                        srcIp = session.dstIp,
                        dstIp = session.srcIp,
                        srcPort = session.dstPort,
                        dstPort = session.srcPort,
                        seq = session.serverSeq,
                        ack = session.clientSeq,
                        flags = 0x18, // PSH + ACK
                        payload = payload
                    )
                    session.serverSeq += read
                }
            } catch (_: Exception) {
            } finally {
                session.isClosed = true
                try {
                    sendTcpPacket(
                        srcIp = session.dstIp,
                        dstIp = session.srcIp,
                        srcPort = session.dstPort,
                        dstPort = session.srcPort,
                        seq = session.serverSeq,
                        ack = session.clientSeq,
                        flags = 0x11, // FIN + ACK
                        payload = ByteArray(0)
                    )
                } catch (_: Exception) {}
                try { session.remoteSocket?.close() } catch (_: Exception) {}
                tcpSessions.remove(sessionKey)
            }
        }
    }

    private fun connectSocks5(targetIp: String, targetPort: Int): Socket? {
        return try {
            val s = Socket()
            vpnService.protect(s)
            s.tcpNoDelay = true
            s.soTimeout = 4000
            s.connect(InetSocketAddress("127.0.0.1", 10808), 2500)
            val out = s.getOutputStream()
            val input = s.getInputStream()

            // 1. SOCKS5 Method selection: No Auth (0x05, 0x01, 0x00)
            out.write(byteArrayOf(0x05, 0x01, 0x00))
            out.flush()

            val mResp = ByteArray(2)
            var readM = 0
            while (readM < 2) {
                val r = input.read(mResp, readM, 2 - readM)
                if (r <= 0) { s.close(); return null }
                readM += r
            }
            if (mResp[0].toInt() != 5 || mResp[1].toInt() != 0) {
                s.close()
                return null
            }

            // 2. SOCKS5 CONNECT command: IPv4 (0x05, 0x01, 0x00, 0x01)
            val ipParts = targetIp.split(".").map { it.toInt() }
            if (ipParts.size != 4) { s.close(); return null }

            val req = ByteArray(10)
            req[0] = 0x05
            req[1] = 0x01
            req[2] = 0x00
            req[3] = 0x01
            req[4] = ipParts[0].toByte()
            req[5] = ipParts[1].toByte()
            req[6] = ipParts[2].toByte()
            req[7] = ipParts[3].toByte()
            req[8] = (targetPort shr 8).toByte()
            req[9] = (targetPort and 0xFF).toByte()

            out.write(req)
            out.flush()

            // 3. Response: VER=5, REP=0, RSV=0, ATYP=...
            val header = ByteArray(4)
            var readH = 0
            while (readH < 4) {
                val r = input.read(header, readH, 4 - readH)
                if (r <= 0) { s.close(); return null }
                readH += r
            }
            if (header[1].toInt() != 0) {
                s.close()
                return null
            }

            // Skip bound address & port
            val atyp = header[3].toInt() and 0xFF
            val remainingToSkip = when (atyp) {
                1 -> 4 + 2 // IPv4 + port
                4 -> 16 + 2 // IPv6 + port
                3 -> {
                    val len = input.read()
                    if (len <= 0) { s.close(); return null }
                    len + 2
                }
                else -> 4 + 2
            }
            var skipped = 0
            val skipBuf = ByteArray(remainingToSkip)
            while (skipped < remainingToSkip) {
                val r = input.read(skipBuf, skipped, remainingToSkip - skipped)
                if (r <= 0) { s.close(); return null }
                skipped += r
            }

            s.soTimeout = 0
            s
        } catch (_: Exception) {
            null
        }
    }

    private fun sendTcpPacket(
        srcIp: ByteArray,
        dstIp: ByteArray,
        srcPort: Int,
        dstPort: Int,
        seq: Long,
        ack: Long,
        flags: Int,
        payload: ByteArray
    ) {
        val totalLength = 20 + 20 + payload.size
        val packet = ByteArray(totalLength)

        packet[0] = 0x45.toByte()
        packet[1] = 0x00
        packet[2] = (totalLength shr 8).toByte()
        packet[3] = (totalLength and 0xFF).toByte()
        val id = Random.nextInt(0, 65535)
        packet[4] = (id shr 8).toByte()
        packet[5] = (id and 0xFF).toByte()
        packet[6] = 0x40.toByte()
        packet[7] = 0x00
        packet[8] = 64.toByte()
        packet[9] = 6.toByte()

        System.arraycopy(srcIp, 0, packet, 12, 4)
        System.arraycopy(dstIp, 0, packet, 16, 4)

        val ipChecksum = calculateChecksum(packet, 0, 20)
        packet[10] = (ipChecksum shr 8).toByte()
        packet[11] = (ipChecksum and 0xFF).toByte()

        val tcpOffset = 20
        packet[tcpOffset] = (srcPort shr 8).toByte()
        packet[tcpOffset + 1] = (srcPort and 0xFF).toByte()
        packet[tcpOffset + 2] = (dstPort shr 8).toByte()
        packet[tcpOffset + 3] = (dstPort and 0xFF).toByte()

        packet[tcpOffset + 4] = (seq shr 24).toByte()
        packet[tcpOffset + 5] = (seq shr 16).toByte()
        packet[tcpOffset + 6] = (seq shr 8).toByte()
        packet[tcpOffset + 7] = (seq and 0xFF).toByte()

        packet[tcpOffset + 8] = (ack shr 24).toByte()
        packet[tcpOffset + 9] = (ack shr 16).toByte()
        packet[tcpOffset + 10] = (ack shr 8).toByte()
        packet[tcpOffset + 11] = (ack and 0xFF).toByte()

        packet[tcpOffset + 12] = 0x50.toByte()
        packet[tcpOffset + 13] = flags.toByte()

        packet[tcpOffset + 14] = 0x7F.toByte()
        packet[tcpOffset + 15] = 0xFF.toByte()

        if (payload.isNotEmpty()) {
            System.arraycopy(payload, 0, packet, tcpOffset + 20, payload.size)
        }

        val tcpChecksum = calculateTcpChecksum(srcIp, dstIp, packet, tcpOffset, 20 + payload.size)
        packet[tcpOffset + 16] = (tcpChecksum shr 8).toByte()
        packet[tcpOffset + 17] = (tcpChecksum and 0xFF).toByte()

        sendToTun(packet, totalLength)
    }

    private fun buildAndSendUdpPacket(
        srcIp: ByteArray,
        dstIp: ByteArray,
        srcPort: Int,
        dstPort: Int,
        payload: ByteArray
    ) {
        val totalLength = 20 + 8 + payload.size
        val packet = ByteArray(totalLength)

        packet[0] = 0x45.toByte()
        packet[1] = 0x00
        packet[2] = (totalLength shr 8).toByte()
        packet[3] = (totalLength and 0xFF).toByte()
        val id = Random.nextInt(0, 65535)
        packet[4] = (id shr 8).toByte()
        packet[5] = (id and 0xFF).toByte()
        packet[6] = 0x40.toByte()
        packet[7] = 0x00
        packet[8] = 64.toByte()
        packet[9] = 17.toByte()

        System.arraycopy(srcIp, 0, packet, 12, 4)
        System.arraycopy(dstIp, 0, packet, 16, 4)

        val ipChecksum = calculateChecksum(packet, 0, 20)
        packet[10] = (ipChecksum shr 8).toByte()
        packet[11] = (ipChecksum and 0xFF).toByte()

        val udpOffset = 20
        packet[udpOffset] = (srcPort shr 8).toByte()
        packet[udpOffset + 1] = (srcPort and 0xFF).toByte()
        packet[udpOffset + 2] = (dstPort shr 8).toByte()
        packet[udpOffset + 3] = (dstPort and 0xFF).toByte()
        val udpLen = 8 + payload.size
        packet[udpOffset + 4] = (udpLen shr 8).toByte()
        packet[udpOffset + 5] = (udpLen and 0xFF).toByte()
        packet[udpOffset + 6] = 0
        packet[udpOffset + 7] = 0

        System.arraycopy(payload, 0, packet, udpOffset + 8, payload.size)
        sendToTun(packet, totalLength)
    }

    private fun sendToTun(packet: ByteArray, length: Int) {
        synchronized(writeLock) {
            try {
                outStream.write(packet, 0, length)
            } catch (_: Exception) {}
        }
    }

    private fun calculateTcpChecksum(srcIp: ByteArray, dstIp: ByteArray, packet: ByteArray, tcpOffset: Int, tcpLength: Int): Int {
        var sum = 0
        for (i in 0 until 4 step 2) {
            sum += ((srcIp[i].toInt() and 0xFF) shl 8) or (srcIp[i + 1].toInt() and 0xFF)
            sum += ((dstIp[i].toInt() and 0xFF) shl 8) or (dstIp[i + 1].toInt() and 0xFF)
        }
        sum += 6
        sum += tcpLength

        var i = tcpOffset
        val end = tcpOffset + tcpLength
        while (i < end - 1) {
            val word = ((packet[i].toInt() and 0xFF) shl 8) or (packet[i + 1].toInt() and 0xFF)
            sum += word
            i += 2
        }
        if (i < end) {
            sum += (packet[i].toInt() and 0xFF) shl 8
        }

        while (sum shr 16 != 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        return sum.inv() and 0xFFFF
    }

    private fun calculateChecksum(data: ByteArray, offset: Int, length: Int): Int {
        var sum = 0
        var i = offset
        val end = offset + length
        while (i < end - 1) {
            val word = ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            sum += word
            i += 2
        }
        if (i < end) {
            sum += (data[i].toInt() and 0xFF) shl 8
        }
        while (sum shr 16 != 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        return sum.inv() and 0xFFFF
    }

    private fun formatIp(ip: ByteArray): String {
        return "${ip[0].toInt() and 0xFF}.${ip[1].toInt() and 0xFF}.${ip[2].toInt() and 0xFF}.${ip[3].toInt() and 0xFF}"
    }
}
