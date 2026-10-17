package com.example.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ConfigRepository
import com.example.model.ConnectionStatus
import com.example.model.LogEntry
import com.example.model.ProxyConfig
import com.example.model.SubscriptionInfo
import com.example.model.TrafficStats
import com.example.network.PingService
import com.example.parser.ProxyParser
import com.example.service.SubscriptionFetcher
import com.example.service.TesVpnService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ConfigRepository(application)

    val currentConfig: StateFlow<ProxyConfig?> = repository.currentConfig
    val allConfigs: StateFlow<List<ProxyConfig>> = repository.allConfigs
    val subscriptions: StateFlow<List<SubscriptionInfo>> = repository.subscriptions
    val routingMode: StateFlow<String> = repository.routingMode
    val dnsServer: StateFlow<String> = repository.dnsServer
    val killSwitch: StateFlow<Boolean> = repository.killSwitch

    val connectionStatus: StateFlow<ConnectionStatus> = TesVpnService.connectionStatus
    val trafficStats: StateFlow<TrafficStats> = TesVpnService.trafficStats
    val logs: StateFlow<List<LogEntry>> = TesVpnService.logs

    private val _isTestingPing = MutableStateFlow(false)
    val isTestingPing: StateFlow<Boolean> = _isTestingPing.asStateFlow()

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    // Dialog states
    private val _showAddConfigSheet = MutableStateFlow(false)
    val showAddConfigSheet: StateFlow<Boolean> = _showAddConfigSheet.asStateFlow()

    private val _showQrScanner = MutableStateFlow(false)
    val showQrScanner: StateFlow<Boolean> = _showQrScanner.asStateFlow()

    private val _qrShareConfig = MutableStateFlow<ProxyConfig?>(null)
    val qrShareConfig: StateFlow<ProxyConfig?> = _qrShareConfig.asStateFlow()

    private val _editingConfig = MutableStateFlow<ProxyConfig?>(null)
    val editingConfig: StateFlow<ProxyConfig?> = _editingConfig.asStateFlow()

    fun clearUserMessage() {
        _userMessage.value = null
    }

    fun showMessage(msg: String) {
        _userMessage.value = msg
    }

    fun setShowAddConfigSheet(show: Boolean) {
        _showAddConfigSheet.value = show
    }

    fun setShowQrScanner(show: Boolean) {
        _showQrScanner.value = show
    }

    fun setQrShareConfig(config: ProxyConfig?) {
        _qrShareConfig.value = config
    }

    fun setEditingConfig(config: ProxyConfig?) {
        _editingConfig.value = config
    }

    fun toggleConnection(hasVpnPermission: Boolean, requestVpnPermission: () -> Unit) {
        if (connectionStatus.value == ConnectionStatus.CONNECTED || connectionStatus.value == ConnectionStatus.CONNECTING) {
            TesVpnService.stopVpn(getApplication())
            return
        }

        val config = currentConfig.value
        if (config == null) {
            showMessage("Добавьте конфигурацию для подключения")
            setShowAddConfigSheet(true)
            return
        }

        if (!hasVpnPermission) {
            requestVpnPermission()
            return
        }

        TesVpnService.startVpn(getApplication(), config, dnsServer.value, routingMode.value)
    }

    fun pingConfig(targetConfig: ProxyConfig? = null) {
        val config = targetConfig ?: currentConfig.value ?: return
        viewModelScope.launch {
            _isTestingPing.value = true
            val ms = PingService.ping(config.server, config.port)
            repository.updatePingForConfig(config.id, ms)
            _isTestingPing.value = false
            TesVpnService.log("INFO", "Пинг ${config.name}: ${if (ms > 0) "${ms}ms" else "Таймаут"}")
        }
    }

    fun pingAllConfigs() {
        val list = allConfigs.value
        if (list.isEmpty()) return
        viewModelScope.launch {
            _isTestingPing.value = true
            for (cfg in list) {
                val ms = PingService.ping(cfg.server, cfg.port)
                repository.updatePingForConfig(cfg.id, ms)
            }
            _isTestingPing.value = false
            showMessage("Проверка пинга всех серверов завершена")
        }
    }

    fun importFromClipboard(): Boolean {
        val clipboard = getApplication<Application>().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val item = clipboard.primaryClip?.getItemAt(0)?.text?.toString()?.trim()
        if (item.isNullOrEmpty()) {
            showMessage("Буфер обмена пуст")
            return false
        }
        return importConfigString(item)
    }

    fun importConfigString(raw: String): Boolean {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            showMessage("Строка конфигурации пуста")
            return false
        }

        // Case 1: Subscription URL (http/https/sub) or containing URL
        val hasUrl = trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true) ||
            trimmed.startsWith("sub://", ignoreCase = true) ||
            ((trimmed.contains("http://") || trimmed.contains("https://")) &&
                !trimmed.startsWith("vless://", ignoreCase = true) &&
                !trimmed.startsWith("vmess://", ignoreCase = true) &&
                !trimmed.startsWith("trojan://", ignoreCase = true) &&
                !trimmed.startsWith("ss://", ignoreCase = true))

        if (hasUrl) {
            val url = com.example.service.SubscriptionFetcher.cleanUrl(trimmed)
            fetchSubscriptionUrl(url)
            return true
        }

        // Case 2: Direct URIs or Base64 content
        val parsedList = ProxyParser.parseMultiple(trimmed)
        return if (parsedList.isNotEmpty()) {
            repository.saveConfigs(parsedList, selectFirstIfNone = true)
            TesVpnService.log("INFO", "Импортировано серверов: ${parsedList.size}")
            showMessage(
                if (parsedList.size == 1) {
                    "Добавлен сервер: ${parsedList.first().name} (${parsedList.first().protocol.displayName})"
                } else {
                    "Успешно импортировано серверов: ${parsedList.size}"
                }
            )
            pingConfig()
            true
        } else {
            showMessage("Не удалось распознать формат. Поддерживаются: vless, vmess, trojan, ss, hy2, wg, socks5 или ссылка подписки (http/https)")
            false
        }
    }

    private fun fetchSubscriptionUrl(url: String) {
        viewModelScope.launch {
            _isImporting.value = true
            TesVpnService.log("INFO", "Загрузка подписки: $url")
            showMessage("Загрузка подписки...")

            val result = SubscriptionFetcher.fetchSubscription(url)
            result.onSuccess { subResult ->
                repository.saveConfigs(subResult.configs, selectFirstIfNone = true)
                repository.addOrUpdateSubscription(
                    SubscriptionInfo(
                        id = UUID.randomUUID().toString(),
                        url = subResult.url,
                        name = subResult.name,
                        lastUpdated = System.currentTimeMillis(),
                        serverCount = subResult.configs.size
                    )
                )
                _isImporting.value = false
                TesVpnService.log("INFO", "Подписка «${subResult.name}» загружена: ${subResult.configs.size} серверов")
                showMessage("Успешно загружено ${subResult.configs.size} серверов из подписки «${subResult.name}»")
                pingConfig()
            }.onFailure { err ->
                _isImporting.value = false
                TesVpnService.log("ERROR", "Ошибка загрузки подписки: ${err.message}")
                showMessage("Ошибка загрузки подписки: ${err.localizedMessage ?: "Таймаут или неверный URL"}")
            }
        }
    }

    fun refreshSubscription(sub: SubscriptionInfo) {
        viewModelScope.launch {
            _isImporting.value = true
            showMessage("Обновление подписки «${sub.name}»...")
            val result = SubscriptionFetcher.fetchSubscription(sub.url)
            result.onSuccess { subResult ->
                repository.saveConfigs(subResult.configs, selectFirstIfNone = false)
                repository.addOrUpdateSubscription(
                    sub.copy(
                        name = subResult.name,
                        lastUpdated = System.currentTimeMillis(),
                        serverCount = subResult.configs.size
                    )
                )
                _isImporting.value = false
                showMessage("Подписка обновлена (${subResult.configs.size} серверов)")
            }.onFailure { err ->
                _isImporting.value = false
                showMessage("Не удалось обновить: ${err.localizedMessage}")
            }
        }
    }

    fun deleteSubscription(subId: String, deleteServers: Boolean) {
        repository.deleteSubscription(subId, deleteServers)
        showMessage("Подписка удалена")
    }

    fun selectConfig(configId: String) {
        val wasConnected = connectionStatus.value == ConnectionStatus.CONNECTED || connectionStatus.value == ConnectionStatus.CONNECTING
        repository.selectConfig(configId)
        val selected = allConfigs.value.find { it.id == configId }
        if (selected != null) {
            showMessage("Выбран сервер: ${selected.name}")
            if (wasConnected) {
                TesVpnService.stopVpn(getApplication())
                TesVpnService.startVpn(getApplication(), selected, dnsServer.value, routingMode.value)
            } else {
                pingConfig(selected)
            }
        }
    }

    fun saveConfig(config: ProxyConfig) {
        repository.saveConfig(config)
        showMessage("Конфигурация сохранена")
        pingConfig(config)
    }

    fun deleteConfig(configId: String? = null) {
        val targetId = configId ?: currentConfig.value?.id ?: return
        if (currentConfig.value?.id == targetId &&
            (connectionStatus.value == ConnectionStatus.CONNECTED || connectionStatus.value == ConnectionStatus.CONNECTING)
        ) {
            TesVpnService.stopVpn(getApplication())
        }
        repository.deleteConfig(targetId)
        showMessage("Конфигурация удалена")
    }

    fun deleteAllConfigs() {
        if (connectionStatus.value == ConnectionStatus.CONNECTED || connectionStatus.value == ConnectionStatus.CONNECTING) {
            TesVpnService.stopVpn(getApplication())
        }
        repository.deleteAllConfigs()
        showMessage("Все серверы удалены")
    }

    fun copyConfigToClipboard(config: ProxyConfig) {
        val uri = ProxyParser.toUri(config)
        val clipboard = getApplication<Application>().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("TESVPN Config", uri)
        clipboard.setPrimaryClip(clip)
        showMessage("Ссылка скопирована в буфер обмена")
    }

    fun setRoutingMode(mode: String) {
        repository.setRoutingMode(mode)
        TesVpnService.log("ROUTE", "Режим маршрутизации изменен: $mode")
    }

    fun setDnsServer(dns: String) {
        repository.setDnsServer(dns)
        TesVpnService.log("INFO", "DNS изменен: $dns")
    }

    fun setKillSwitch(enabled: Boolean) {
        repository.setKillSwitch(enabled)
        TesVpnService.log("WARN", "Kill Switch ${if (enabled) "активирован" else "отключен"}")
    }

    fun clearLogs() {
        TesVpnService.clearLogs()
    }
}
