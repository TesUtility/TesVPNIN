package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.ProxyProtocol
import com.example.parser.ProxyParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("TESVPN", appName)
    }

    @Test
    fun `test vless parsing and serialization`() {
        val vlessUri = "vless://e68f3a38-51f4-4a49-92bb-18753d0bc261@de.tesvpn.net:443?type=tcp&security=reality&sni=dl.google.com&fp=chrome&pbk=i3hV8G_L8rYn9p5mX1k_W7QvZaB2CdEfGhIjKlMnOpQ&sid=8a2f1c9e&flow=xtls-rprx-vision#GermanyNode"
        val parsed = ProxyParser.parse(vlessUri)

        assertNotNull(parsed)
        assertEquals(ProxyProtocol.VLESS, parsed?.protocol)
        assertEquals("de.tesvpn.net", parsed?.server)
        assertEquals(443, parsed?.port)
        assertEquals("e68f3a38-51f4-4a49-92bb-18753d0bc261", parsed?.uuidOrPassword)
        assertEquals("reality", parsed?.security)
        assertEquals("dl.google.com", parsed?.sni)
        assertEquals("GermanyNode", parsed?.name)

        val exported = ProxyParser.toUri(parsed!!)
        assertTrue(exported.startsWith("vless://"))
        assertTrue(exported.contains("security=reality"))
    }

    @Test
    fun `test trojan parsing`() {
        val trojanUri = "trojan://mypassword123@fi.tesvpn.net:443?security=tls&sni=fi.tesvpn.net#FinlandTrojan"
        val parsed = ProxyParser.parse(trojanUri)

        assertNotNull(parsed)
        assertEquals(ProxyProtocol.TROJAN, parsed?.protocol)
        assertEquals("fi.tesvpn.net", parsed?.server)
        assertEquals(443, parsed?.port)
        assertEquals("mypassword123", parsed?.uuidOrPassword)
        assertEquals("FinlandTrojan", parsed?.name)
    }

    @Test
    fun `test shadowsocks sip002 parsing and export`() {
        // ss://YWVzLTI1Ni1nY206cGFzc3dvcmQ=@ss.tesvpn.net:8388#ShadowsocksServer
        // Base64("aes-256-gcm:password") = YWVzLTI1Ni1nY206cGFzc3dvcmQ=
        val ssUri = "ss://YWVzLTI1Ni1nY206cGFzc3dvcmQ=@ss.tesvpn.net:8388#ShadowsocksServer"
        val parsed = ProxyParser.parse(ssUri)

        assertNotNull(parsed)
        assertEquals(ProxyProtocol.SHADOWSOCKS, parsed?.protocol)
        assertEquals("ss.tesvpn.net", parsed?.server)
        assertEquals(8388, parsed?.port)
        assertEquals("password", parsed?.uuidOrPassword)
        assertEquals("aes-256-gcm", parsed?.security)
        assertEquals("ShadowsocksServer", parsed?.name)

        val exported = ProxyParser.toUri(parsed!!)
        assertTrue(exported.startsWith("ss://"))
        assertTrue(exported.contains("ss.tesvpn.net:8388"))
    }

    @Test
    fun `test v2ray config builder produces valid json`() {
        val vlessUri = "vless://e68f3a38-51f4-4a49-92bb-18753d0bc261@de.tesvpn.net:443?type=tcp&security=reality&sni=dl.google.com&fp=chrome&pbk=i3hV8G_L8rYn9p5mX1k_W7QvZaB2CdEfGhIjKlMnOpQ&sid=8a2f1c9e&flow=xtls-rprx-vision#GermanyNode"
        val config = ProxyParser.parse(vlessUri)!!
        val json = com.example.service.v2ray.V2rayConfigBuilder.buildConfig(config)
        assertTrue(json.contains("\"protocol\": \"vless\""))
        assertTrue(json.contains("\"tag\": \"proxy\""))
        assertTrue(json.contains("10808"))
    }

    @Test
    fun `test subscription cleanUrl`() {
        val raw1 = "   <https://example.com/sub/token123>  "
        assertEquals("https://example.com/sub/token123", com.example.service.SubscriptionFetcher.cleanUrl(raw1))

        val raw2 = "\"sub://domain.org:8443/subscribe\""
        assertEquals("https://domain.org:8443/subscribe", com.example.service.SubscriptionFetcher.cleanUrl(raw2))

        val raw3 = "Вот ссылка: https://mypanel.net/api/v1?key=abc "
        assertEquals("https://mypanel.net/api/v1?key=abc", com.example.service.SubscriptionFetcher.cleanUrl(raw3))
    }

    @Test
    fun `test sing-box json outbounds parsing`() {
        val singBoxJson = """
            {
              "outbounds": [
                {
                  "type": "vless",
                  "tag": "SingBox VLESS",
                  "server": "sb.tesvpn.net",
                  "server_port": 443,
                  "uuid": "11111111-2222-3333-4444-555555555555",
                  "tls": {
                    "enabled": true,
                    "server_name": "sb.tesvpn.net",
                    "reality": {
                      "enabled": true,
                      "public_key": "pbk123",
                      "short_id": "sid123"
                    }
                  }
                }
              ]
            }
        """.trimIndent()
        val parsed = ProxyParser.parseMultiple(singBoxJson)
        assertEquals(1, parsed.size)
        assertEquals("SingBox VLESS", parsed[0].name)
        assertEquals(ProxyProtocol.VLESS, parsed[0].protocol)
        assertEquals("sb.tesvpn.net", parsed[0].server)
        assertEquals("reality", parsed[0].security)
        assertEquals("pbk123", parsed[0].publicKey)
    }

    @Test
    fun `test QR code encoding and decoding without exceptions`() {
        val testContent = "vless://test-uuid@example.com:443?security=reality#TestServer"
        val bitMatrix = com.google.zxing.qrcode.QRCodeWriter().encode(
            testContent,
            com.google.zxing.BarcodeFormat.QR_CODE,
            256,
            256
        )
        val width = bitMatrix.width
        val height = bitMatrix.height
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            val offset = y * width
            for (x in 0 until width) {
                pixels[offset + x] = if (bitMatrix.get(x, y)) -0x1000000 else -0x1
            }
        }
        val source = com.google.zxing.RGBLuminanceSource(width, height, pixels)
        val binaryBitmap = com.google.zxing.BinaryBitmap(com.google.zxing.common.HybridBinarizer(source))
        val reader = com.google.zxing.MultiFormatReader().apply {
            setHints(
                mapOf(
                    com.google.zxing.DecodeHintType.POSSIBLE_FORMATS to listOf(com.google.zxing.BarcodeFormat.QR_CODE),
                    com.google.zxing.DecodeHintType.TRY_HARDER to true
                )
            )
        }
        val result = reader.decode(binaryBitmap)
        assertEquals(testContent, result.text)
    }

    @Test
    fun `test v2ray config builder generates valid json and no illegal geosite rules`() {
        val config = com.example.model.ProxyConfig(
            id = "cfg-1",
            name = "TestNode",
            protocol = ProxyProtocol.VLESS,
            server = "vpn.example.com",
            port = 443,
            uuidOrPassword = "e68f3a38-51f4-4a49-92bb-18753d0bc261",
            security = "reality",
            sni = "gateway.example.com",
            publicKey = "pbk123"
        )

        for (mode in listOf("BYPASS_LAN", "PROXY_ALL", "DIRECT")) {
            val jsonStr = com.example.service.v2ray.V2rayConfigBuilder.buildConfig(
                config = config,
                routingMode = mode,
                dnsServer = "1.1.1.1"
            )
            assertNotNull(jsonStr)
            val json = org.json.JSONObject(jsonStr)
            assertTrue(json.has("inbounds"))
            assertTrue(json.has("outbounds"))
            assertTrue(json.has("routing"))

            // Verify no invalid geosite rules exist
            val rawString = jsonStr.lowercase()
            org.junit.Assert.assertFalse("Config should not contain geosite:ru", rawString.contains("geosite:ru"))
            org.junit.Assert.assertFalse("Config should not contain geosite:private", rawString.contains("geosite:private"))

            // Verify native TUN inbound is present
            val inbounds = json.getJSONArray("inbounds")
            var hasTun = false
            for (i in 0 until inbounds.length()) {
                val inb = inbounds.getJSONObject(i)
                if (inb.optString("protocol") == "tun" && inb.optString("tag") == "tun") {
                    hasTun = true
                }
            }
            assertTrue("Should contain native tun inbound", hasTun)

            // Verify dns-out outbound is present
            val outbounds = json.getJSONArray("outbounds")
            var hasDnsOut = false
            for (i in 0 until outbounds.length()) {
                val out = outbounds.getJSONObject(i)
                if (out.optString("protocol") == "dns" && out.optString("tag") == "dns-out") {
                    hasDnsOut = true
                }
            }
            assertTrue("Should contain dns-out outbound", hasDnsOut)
        }
    }
}
