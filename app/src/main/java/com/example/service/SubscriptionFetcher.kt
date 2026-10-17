package com.example.service

import com.example.model.ProxyConfig
import com.example.parser.ProxyParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ConnectionSpec
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.URI
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

object SubscriptionFetcher {

    private val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    })

    private val permissiveSslSocketFactory: SSLSocketFactory by lazy {
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, trustAllCerts, SecureRandom())
        sslContext.socketFactory
    }

    private val customDns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            return try {
                Dns.SYSTEM.lookup(hostname)
            } catch (e: Exception) {
                try {
                    listOf(InetAddress.getByName(hostname))
                } catch (_: Exception) {
                    throw e
                }
            }
        }
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .sslSocketFactory(permissiveSslSocketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .connectionSpecs(listOf(
                ConnectionSpec.MODERN_TLS,
                ConnectionSpec.COMPATIBLE_TLS,
                ConnectionSpec.CLEARTEXT
            ))
            .dns(customDns)
            .connectTimeout(45, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(45, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }

    fun cleanUrl(raw: String): String {
        var str = raw.trim().trim('\uFEFF', '\u200B')
        if ((str.startsWith("\"") && str.endsWith("\"")) ||
            (str.startsWith("'") && str.endsWith("'")) ||
            (str.startsWith("<") && str.endsWith(">"))
        ) {
            str = str.substring(1, str.length - 1).trim()
        }
        val match = Regex("(?i)\\b(https?://|sub://)[^\\s\"'<>\\[\\]{}|\\\\^`]+").find(str)
        if (match != null) {
            str = match.value
        }
        if (str.startsWith("sub://", ignoreCase = true)) {
            str = "https://" + str.removePrefix("sub://")
        } else if (!str.startsWith("http://", ignoreCase = true) && !str.startsWith("https://", ignoreCase = true)) {
            str = "https://$str"
        }
        return str
    }

    suspend fun fetchSubscription(url: String): Result<SubscriptionResult> = withContext(Dispatchers.IO) {
        val normalizedUrl = cleanUrl(url)
        val userAgents = listOf(
            "v2rayNG/1.8.19 (Linux; Android 14; Mobile)",
            "ClashMeta/1.18.0 (Linux; Android 14)",
            "sing-box/1.9.0 (Linux; Android 14)",
            "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
        )

        var lastException: Throwable? = null

        // Try primary URL across candidate user agents
        for (ua in userAgents) {
            val res = executeRequest(normalizedUrl, ua)
            if (res.isSuccess) {
                return@withContext res
            }
            lastException = res.exceptionOrNull()
        }

        // If HTTPS had an SSL handshake timeout or SSL exception, attempt HTTP fallback
        if (normalizedUrl.startsWith("https://", ignoreCase = true)) {
            val httpUrl = "http://" + normalizedUrl.removePrefix("https://")
            val httpRes = executeRequest(httpUrl, userAgents.first())
            if (httpRes.isSuccess) {
                return@withContext httpRes
            }
        }

        val errorMsg = when (lastException) {
            is SocketTimeoutException -> "Превышено время ожидания ответа сервера (Timeout). Проверьте доступность ссылки."
            is SSLHandshakeException, is SSLException -> "Ошибка SSL рукопожатия с сервером подписки: ${lastException.message}"
            is IOException -> "Сетевая ошибка: ${lastException.message}"
            else -> lastException?.localizedMessage ?: "Не удалось загрузить подписку"
        }

        Result.failure(Exception(errorMsg, lastException))
    }

    private fun executeRequest(targetUrl: String, userAgent: String): Result<SubscriptionResult> {
        return try {
            val request = Request.Builder()
                .url(targetUrl)
                .addHeader("User-Agent", userAgent)
                .addHeader("Accept", "*/*")
                .addHeader("Accept-Language", "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
                }

                val body = response.body?.string() ?: ""
                if (body.isBlank()) {
                    return Result.failure(Exception("Сервер подписки вернул пустой ответ"))
                }

                // Determine subscription title
                var name = response.header("profile-title")
                if (name.isNullOrBlank()) {
                    val disposition = response.header("Content-Disposition")
                    if (!disposition.isNullOrBlank() && disposition.contains("filename=")) {
                        name = disposition.substringAfter("filename=").trim('"', '\'')
                    }
                }
                if (name.isNullOrBlank()) {
                    try {
                        val host = URI(targetUrl).host
                        name = "Подписка $host"
                    } catch (_: Exception) {
                        name = "VPN Подписка"
                    }
                }

                // Clean potential HTML wrapping
                val cleanBody = if (body.contains("<pre>") && body.contains("</pre>")) {
                    body.substringAfter("<pre>").substringBefore("</pre>").trim()
                } else if (body.contains("<code>") && body.contains("</code>")) {
                    body.substringAfter("<code>").substringBefore("</code>").trim()
                } else {
                    body.trim()
                }

                val parsedConfigs = ProxyParser.parseMultiple(cleanBody)
                if (parsedConfigs.isEmpty()) {
                    return Result.failure(Exception("В подписке не найдено подходящих серверов (VLESS, VMess, Trojan, SS)"))
                }

                val taggedConfigs = parsedConfigs.map {
                    it.copy(
                        subscriptionUrl = targetUrl,
                        subscriptionName = name
                    )
                }

                Result.success(SubscriptionResult(name = name, url = targetUrl, configs = taggedConfigs))
            }
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    data class SubscriptionResult(
        val name: String,
        val url: String,
        val configs: List<ProxyConfig>
    )
}
