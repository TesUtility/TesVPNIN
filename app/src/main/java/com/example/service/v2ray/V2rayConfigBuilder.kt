package com.example.service.v2ray

import com.example.model.ProxyConfig
import com.example.model.ProxyProtocol
import org.json.JSONArray
import org.json.JSONObject

object V2rayConfigBuilder {

    fun buildConfig(
        config: ProxyConfig,
        routingMode: String = "BYPASS_LAN",
        dnsServer: String = "1.1.1.1",
        socksPort: Int = 10808,
        httpPort: Int = 10809,
        dnsPort: Int = 10853
    ): String {
        val root = JSONObject()

        // 1. Log configuration
        val logObj = JSONObject().apply {
            put("loglevel", "warning")
        }
        root.put("log", logObj)

        // 2. Inbounds (Native TUN, SOCKS5, HTTP, and Dokodemo-door for DNS)
        val inbounds = JSONArray()

        // 2a. Native TUN inbound (attached to Android VpnService tunFd)
        val tunInbound = JSONObject().apply {
            put("tag", "tun")
            put("protocol", "tun")
            put("settings", JSONObject().apply {
                put("name", "xray0")
                put("MTU", 1500)
                put("userLevel", 8)
            })
            put("sniffing", JSONObject().apply {
                put("enabled", true)
                put("destOverride", JSONArray().apply {
                    put("http")
                    put("tls")
                    put("quic")
                })
            })
        }
        inbounds.put(tunInbound)

        // 2b. Local SOCKS5 inbound
        val socksInbound = JSONObject().apply {
            put("tag", "socks-in")
            put("port", socksPort)
            put("listen", "127.0.0.1")
            put("protocol", "socks")
            put("sniffing", JSONObject().apply {
                put("enabled", true)
                put("destOverride", JSONArray().apply {
                    put("http")
                    put("tls")
                    put("quic")
                })
            })
            put("settings", JSONObject().apply {
                put("auth", "noauth")
                put("udp", true)
                put("userLevel", 8)
            })
        }
        inbounds.put(socksInbound)

        // 2c. Local HTTP inbound
        val httpInbound = JSONObject().apply {
            put("tag", "http-in")
            put("port", httpPort)
            put("listen", "127.0.0.1")
            put("protocol", "http")
            put("settings", JSONObject().apply {
                put("allowTransparent", false)
            })
        }
        inbounds.put(httpInbound)

        val targetDns = if (dnsServer.isNotBlank()) dnsServer else "1.1.1.1"
        val dnsInbound = JSONObject().apply {
            put("tag", "dns-in")
            put("port", dnsPort)
            put("listen", "127.0.0.1")
            put("protocol", "dokodemo-door")
            put("settings", JSONObject().apply {
                put("address", targetDns)
                put("port", 53)
                put("network", "tcp,udp")
            })
        }
        inbounds.put(dnsInbound)

        root.put("inbounds", inbounds)

        // 3. Outbounds (Proxy outbound & Direct & Block)
        val outbounds = JSONArray()
        val proxyOutbound = buildProxyOutbound(config)
        outbounds.put(proxyOutbound)

        val directOutbound = JSONObject().apply {
            put("tag", "direct")
            put("protocol", "freedom")
            put("settings", JSONObject())
            put("streamSettings", JSONObject().apply {
                put("sockopt", JSONObject().apply {
                    put("domainStrategy", "UseIP")
                })
            })
        }
        outbounds.put(directOutbound)

        val blockOutbound = JSONObject().apply {
            put("tag", "block")
            put("protocol", "blackhole")
            put("settings", JSONObject())
        }
        outbounds.put(blockOutbound)

        // 3d. DNS outbound (intercepts port 53 queries and feeds them to Xray's DNS engine)
        val dnsOutbound = JSONObject().apply {
            put("tag", "dns-out")
            put("protocol", "dns")
        }
        outbounds.put(dnsOutbound)

        root.put("outbounds", outbounds)

        // 4. DNS Configuration in Xray (DoH over proxy + fast IP resolvers)
        val dnsObj = JSONObject().apply {
            val servers = JSONArray().apply {
                put("https://1.1.1.1/dns-query")
                put("https://8.8.8.8/dns-query")
                put(targetDns)
                put("8.8.8.8")
                put("1.1.1.1")
            }
            put("servers", servers)
        }
        root.put("dns", dnsObj)

        // 5. Routing
        val routing = JSONObject().apply {
            put("domainStrategy", "IPIfNonMatch")
            val rules = JSONArray()

            // 5a. Intercept port 53 DNS queries arriving from TUN into the dns-out engine
            val dnsPortRule = JSONObject().apply {
                put("type", "field")
                put("inboundTag", JSONArray().apply { put("tun") })
                put("port", "53")
                put("outboundTag", "dns-out")
            }
            rules.put(dnsPortRule)

            val dnsInboundRule = JSONObject().apply {
                put("type", "field")
                put("inboundTag", JSONArray().apply { put("dns-in") })
                put("port", "53")
                put("outboundTag", "dns-out")
            }
            rules.put(dnsInboundRule)

            // 5b. Block UDP port 443 (QUIC) so YouTube and browsers immediately use TLS/TCP (HTTP/2)
            val blockQuicRule = JSONObject().apply {
                put("type", "field")
                put("port", "443")
                put("network", "udp")
                put("outboundTag", "block")
            }
            rules.put(blockQuicRule)

            // 5c. Route Xray internal DNS queries through proxy so DoH is encrypted and bypasses censorship
            val dnsThroughProxyRule = JSONObject().apply {
                put("type", "field")
                put("inboundTag", JSONArray().apply { put("dns") })
                put("outboundTag", "proxy")
            }
            rules.put(dnsThroughProxyRule)

            val dnsServerIpRule = JSONObject().apply {
                put("type", "field")
                put("ip", JSONArray().apply {
                    put("1.1.1.1")
                    put("8.8.8.8")
                    put("1.0.0.1")
                    put("8.8.4.4")
                })
                put("outboundTag", "proxy")
            }
            rules.put(dnsServerIpRule)

            if (routingMode == "BYPASS_LAN") {
                val lanIpRule = JSONObject().apply {
                    put("type", "field")
                    put("outboundTag", "direct")
                    put("ip", JSONArray().apply {
                        put("10.0.0.0/8")
                        put("172.16.0.0/12")
                        put("192.168.0.0/16")
                        put("127.0.0.0/8")
                        put("fc00::/7")
                        put("fe80::/10")
                        put("geoip:ru")
                    })
                }
                rules.put(lanIpRule)

                val directDomainRule = JSONObject().apply {
                    put("type", "field")
                    put("outboundTag", "direct")
                    put("domain", JSONArray().apply {
                        put("domain:ru")
                        put("domain:su")
                        put("domain:xn--p1ai")
                        put("domain:local")
                        put("domain:localhost")
                        put("domain:lan")
                    })
                }
                rules.put(directDomainRule)
            } else if (routingMode == "DIRECT") {
                val defaultDirect = JSONObject().apply {
                    put("type", "field")
                    put("outboundTag", "direct")
                    put("network", "tcp,udp")
                }
                rules.put(defaultDirect)
            }

            // Default proxy rule for all remaining internet traffic
            val defaultProxy = JSONObject().apply {
                put("type", "field")
                put("outboundTag", "proxy")
                put("network", "tcp,udp")
            }
            rules.put(defaultProxy)

            put("rules", rules)
        }
        root.put("routing", routing)

        // 6. Stats & Policy
        root.put("stats", JSONObject())
        root.put("policy", JSONObject().apply {
            put("system", JSONObject().apply {
                put("statsInboundUplink", true)
                put("statsInboundDownlink", true)
                put("statsOutboundUplink", true)
                put("statsOutboundDownlink", true)
            })
        })

        return root.toString(2)
    }

    private fun buildProxyOutbound(config: ProxyConfig): JSONObject {
        val outbound = JSONObject()
        outbound.put("tag", "proxy")

        when (config.protocol) {
            ProxyProtocol.VLESS -> {
                outbound.put("protocol", "vless")
                val userObj = JSONObject().apply {
                    put("id", config.uuidOrPassword)
                    put("encryption", "none")
                    if (config.flow.isNotBlank() && (config.security.lowercase() in listOf("reality", "tls")) && (config.network.isBlank() || config.network.lowercase() == "tcp")) {
                        put("flow", config.flow)
                    }
                }
                val vnext = JSONArray().apply {
                    put(JSONObject().apply {
                        put("address", config.server)
                        put("port", config.port)
                        put("users", JSONArray().apply { put(userObj) })
                    })
                }
                outbound.put("settings", JSONObject().apply { put("vnext", vnext) })
                outbound.put("streamSettings", buildStreamSettings(config))
            }
            ProxyProtocol.TROJAN -> {
                outbound.put("protocol", "trojan")
                val serverObj = JSONObject().apply {
                    put("address", config.server)
                    put("port", config.port)
                    put("password", config.uuidOrPassword)
                }
                outbound.put("settings", JSONObject().apply {
                    put("servers", JSONArray().apply { put(serverObj) })
                })
                outbound.put("streamSettings", buildStreamSettings(config))
            }
            ProxyProtocol.VMESS -> {
                outbound.put("protocol", "vmess")
                val userObj = JSONObject().apply {
                    put("id", config.uuidOrPassword)
                    put("alterId", config.alterId)
                    put("security", "auto")
                }
                val vnext = JSONArray().apply {
                    put(JSONObject().apply {
                        put("address", config.server)
                        put("port", config.port)
                        put("users", JSONArray().apply { put(userObj) })
                    })
                }
                outbound.put("settings", JSONObject().apply { put("vnext", vnext) })
                outbound.put("streamSettings", buildStreamSettings(config))
            }
            ProxyProtocol.SHADOWSOCKS -> {
                outbound.put("protocol", "shadowsocks")
                val serverObj = JSONObject().apply {
                    put("address", config.server)
                    put("port", config.port)
                    put("password", config.uuidOrPassword)
                    put("method", if (config.security.isNotBlank()) config.security else "aes-256-gcm")
                }
                outbound.put("settings", JSONObject().apply {
                    put("servers", JSONArray().apply { put(serverObj) })
                })
            }
            ProxyProtocol.SOCKS5 -> {
                outbound.put("protocol", "socks")
                val serverObj = JSONObject().apply {
                    put("address", config.server)
                    put("port", config.port)
                    if (config.uuidOrPassword.contains(":")) {
                        val parts = config.uuidOrPassword.split(":")
                        put("users", JSONArray().apply {
                            put(JSONObject().apply {
                                put("user", parts[0])
                                put("pass", parts.getOrElse(1) { "" })
                            })
                        })
                    }
                }
                outbound.put("settings", JSONObject().apply {
                    put("servers", JSONArray().apply { put(serverObj) })
                })
            }
            ProxyProtocol.HYSTERIA2 -> {
                outbound.put("protocol", "hysteria2")
                val serverObj = JSONObject().apply {
                    put("address", config.server)
                    put("port", config.port)
                    put("password", config.uuidOrPassword)
                }
                outbound.put("settings", JSONObject().apply {
                    put("servers", JSONArray().apply { put(serverObj) })
                })
                outbound.put("streamSettings", buildStreamSettings(config))
            }
            ProxyProtocol.WIREGUARD -> {
                outbound.put("protocol", "wireguard")
                outbound.put("settings", JSONObject().apply {
                    put("secretKey", config.uuidOrPassword)
                    put("peers", JSONArray().apply {
                        put(JSONObject().apply {
                            put("publicKey", config.publicKey)
                            put("endpoint", "${config.server}:${config.port}")
                        })
                    })
                })
            }
        }

        return outbound
    }

    private fun buildStreamSettings(config: ProxyConfig): JSONObject {
        val stream = JSONObject()
        val net = if (config.network.isNotBlank()) config.network.lowercase() else "tcp"
        stream.put("network", net)

        val sec = config.security.lowercase()
        if (sec == "reality") {
            stream.put("security", "reality")
            val reality = JSONObject().apply {
                put("serverName", if (config.sni.isNotBlank()) config.sni else config.server)
                put("fingerprint", if (config.fingerprint.isNotBlank()) config.fingerprint else "chrome")
                put("show", false)
                put("publicKey", config.publicKey)
                put("shortId", config.shortId)
                put("spiderX", if (config.spiderX.isNotBlank()) config.spiderX else "/")
            }
            stream.put("realitySettings", reality)
        } else if (sec == "tls") {
            stream.put("security", "tls")
            val tls = JSONObject().apply {
                put("serverName", if (config.sni.isNotBlank()) config.sni else config.server)
                put("allowInsecure", true)
                if (config.fingerprint.isNotBlank()) {
                    put("fingerprint", config.fingerprint)
                }
                if (config.alpn.isNotBlank()) {
                    val alpnArray = JSONArray()
                    config.alpn.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach {
                        alpnArray.put(it)
                    }
                    if (alpnArray.length() > 0) {
                        put("alpn", alpnArray)
                    }
                }
            }
            stream.put("tlsSettings", tls)
        }

        if (net == "ws") {
            val ws = JSONObject().apply {
                if (config.wsPath.isNotBlank()) put("path", config.wsPath)
                if (config.wsHost.isNotBlank()) {
                    put("headers", JSONObject().apply { put("Host", config.wsHost) })
                }
            }
            stream.put("wsSettings", ws)
        } else if (net == "grpc") {
            val grpc = JSONObject().apply {
                if (config.wsPath.isNotBlank()) {
                    put("serviceName", config.wsPath)
                }
                put("multiMode", false)
            }
            stream.put("grpcSettings", grpc)
        }

        return stream
    }
}
