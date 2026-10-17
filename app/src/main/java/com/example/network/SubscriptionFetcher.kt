package com.example.network

import com.example.model.ProxyConfig
import com.example.parser.ProxyParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object SubscriptionFetcher {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun fetchSubscription(url: String): Result<List<ProxyConfig>> = withContext(Dispatchers.IO) {
        try {
            val cleanUrl = url.trim()
            if (!cleanUrl.startsWith("http://", ignoreCase = true) && !cleanUrl.startsWith("https://", ignoreCase = true)) {
                return@withContext Result.failure(IllegalArgumentException("Ссылка должна начинаться с http:// или https://"))
            }

            val request = Request.Builder()
                .url(cleanUrl)
                .addHeader("User-Agent", "v2rayN/6.23 (Windows NT 10.0; Win64; x64) ClashforWindows/0.20.39 okhttp/4.10.0")
                .addHeader("Accept", "*/*")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ошибка сервера подписки: ${response.code} ${response.message}"))
            }

            val body = response.body?.string()
            if (body.isNullOrBlank()) {
                return@withContext Result.failure(Exception("Сервер вернул пустой ответ"))
            }

            val configs = ProxyParser.parseMultiple(body)
            if (configs.isNotEmpty()) {
                Result.success(configs)
            } else {
                Result.failure(Exception("Не удалось извлечь серверы из ответа подписки. Проверьте правильность ссылки."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
