package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.model.ProxyConfig
import com.example.model.ProxyProtocol
import com.example.model.SubscriptionInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class ConfigRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("tesvpn_prefs", Context.MODE_PRIVATE)

    private val _allConfigs = MutableStateFlow<List<ProxyConfig>>(emptyList())
    val allConfigs: StateFlow<List<ProxyConfig>> = _allConfigs.asStateFlow()

    private val _currentConfig = MutableStateFlow<ProxyConfig?>(null)
    val currentConfig: StateFlow<ProxyConfig?> = _currentConfig.asStateFlow()

    private val _subscriptions = MutableStateFlow<List<SubscriptionInfo>>(emptyList())
    val subscriptions: StateFlow<List<SubscriptionInfo>> = _subscriptions.asStateFlow()

    private val _routingMode = MutableStateFlow("BYPASS_LAN") // PROXY_ALL, BYPASS_LAN, DIRECT
    val routingMode: StateFlow<String> = _routingMode.asStateFlow()

    private val _dnsServer = MutableStateFlow("1.1.1.1")
    val dnsServer: StateFlow<String> = _dnsServer.asStateFlow()

    private val _killSwitch = MutableStateFlow(false)
    val killSwitch: StateFlow<Boolean> = _killSwitch.asStateFlow()

    init {
        loadSettings()
        loadConfigsFromDisk()
        loadSubscriptionsFromDisk()
    }

    private fun loadSettings() {
        _routingMode.value = prefs.getString("routing_mode", "BYPASS_LAN") ?: "BYPASS_LAN"
        _dnsServer.value = prefs.getString("dns_server", "1.1.1.1") ?: "1.1.1.1"
        _killSwitch.value = prefs.getBoolean("kill_switch", false)
    }

    fun setRoutingMode(mode: String) {
        _routingMode.value = mode
        prefs.edit().putString("routing_mode", mode).apply()
    }

    fun setDnsServer(dns: String) {
        _dnsServer.value = dns
        prefs.edit().putString("dns_server", dns).apply()
    }

    fun setKillSwitch(enabled: Boolean) {
        _killSwitch.value = enabled
        prefs.edit().putBoolean("kill_switch", enabled).apply()
    }

    private fun loadConfigsFromDisk() {
        val jsonArrayStr = prefs.getString("saved_all_configs", null)
        val list = mutableListOf<ProxyConfig>()
        if (!jsonArrayStr.isNullOrEmpty()) {
            try {
                val array = JSONArray(jsonArrayStr)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val cfg = parseConfigFromJson(obj)
                    if (cfg != null) list.add(cfg)
                }
            } catch (_: Exception) {}
        }

        // Backward compatibility: load legacy single active config if list is empty
        if (list.isEmpty()) {
            val singleJson = prefs.getString("saved_active_config", null)
            if (!singleJson.isNullOrEmpty()) {
                try {
                    val cfg = parseConfigFromJson(JSONObject(singleJson))
                    if (cfg != null) list.add(cfg)
                } catch (_: Exception) {}
            }
        }

        _allConfigs.value = list

        val selectedId = prefs.getString("selected_config_id", null)
        val selected = list.find { it.id == selectedId } ?: list.firstOrNull()
        _currentConfig.value = selected?.copy(isSelected = true)
    }

    private fun loadSubscriptionsFromDisk() {
        val json = prefs.getString("saved_subscriptions", null)
        val list = mutableListOf<SubscriptionInfo>()
        if (!json.isNullOrEmpty()) {
            try {
                val array = JSONArray(json)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        SubscriptionInfo(
                            id = obj.getString("id"),
                            url = obj.getString("url"),
                            name = obj.getString("name"),
                            lastUpdated = obj.optLong("lastUpdated", System.currentTimeMillis()),
                            serverCount = obj.optInt("serverCount", 0)
                        )
                    )
                }
            } catch (_: Exception) {}
        }
        _subscriptions.value = list
    }

    fun saveConfig(config: ProxyConfig) {
        val currentList = _allConfigs.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == config.id }
        val updatedConfig = config.copy(isSelected = true)
        if (index >= 0) {
            currentList[index] = updatedConfig
        } else {
            currentList.add(0, updatedConfig)
        }
        _allConfigs.value = currentList
        _currentConfig.value = updatedConfig
        prefs.edit().putString("selected_config_id", updatedConfig.id).apply()
        saveConfigsToDisk(currentList)
    }

    fun saveConfigs(newConfigs: List<ProxyConfig>, selectFirstIfNone: Boolean = true) {
        if (newConfigs.isEmpty()) return
        val currentList = _allConfigs.value.toMutableList()

        for (cfg in newConfigs) {
            val existingIndex = currentList.indexOfFirst {
                it.id == cfg.id || (it.server == cfg.server && it.port == cfg.port && it.protocol == cfg.protocol)
            }
            if (existingIndex >= 0) {
                currentList[existingIndex] = cfg.copy(id = currentList[existingIndex].id)
            } else {
                currentList.add(cfg)
            }
        }

        _allConfigs.value = currentList

        if (_currentConfig.value == null && selectFirstIfNone && currentList.isNotEmpty()) {
            selectConfig(currentList.first().id)
        } else {
            // refresh currentConfig if it was in the updated list
            val activeId = _currentConfig.value?.id
            if (activeId != null) {
                val updated = currentList.find { it.id == activeId }
                if (updated != null) {
                    _currentConfig.value = updated.copy(isSelected = true)
                }
            }
        }

        saveConfigsToDisk(currentList)
    }

    fun selectConfig(id: String) {
        val list = _allConfigs.value
        val config = list.find { it.id == id } ?: return
        val updatedList = list.map { it.copy(isSelected = (it.id == id)) }
        _allConfigs.value = updatedList
        _currentConfig.value = config.copy(isSelected = true)
        prefs.edit().putString("selected_config_id", id).apply()
        saveConfigsToDisk(updatedList)
    }

    fun deleteConfig(id: String) {
        val currentList = _allConfigs.value.toMutableList()
        val wasSelected = _currentConfig.value?.id == id
        currentList.removeAll { it.id == id }
        _allConfigs.value = currentList

        if (wasSelected) {
            val next = currentList.firstOrNull()
            _currentConfig.value = next?.copy(isSelected = true)
            prefs.edit().putString("selected_config_id", next?.id).apply()
        }

        saveConfigsToDisk(currentList)
    }

    fun deleteAllConfigs() {
        _allConfigs.value = emptyList()
        _currentConfig.value = null
        prefs.edit().remove("saved_all_configs").remove("saved_active_config").remove("selected_config_id").apply()
    }

    fun updatePing(pingMs: Long) {
        _currentConfig.value?.let { current ->
            updatePingForConfig(current.id, pingMs)
        }
    }

    fun updatePingForConfig(configId: String, pingMs: Long) {
        val list = _allConfigs.value.toMutableList()
        val index = list.indexOfFirst { it.id == configId }
        if (index >= 0) {
            val updated = list[index].copy(pingMs = pingMs)
            list[index] = updated
            _allConfigs.value = list
            if (_currentConfig.value?.id == configId) {
                _currentConfig.value = updated.copy(isSelected = true)
            }
            saveConfigsToDisk(list)
        }
    }

    fun addOrUpdateSubscription(sub: SubscriptionInfo) {
        val list = _subscriptions.value.toMutableList()
        val index = list.indexOfFirst { it.id == sub.id || it.url == sub.url }
        if (index >= 0) {
            list[index] = sub
        } else {
            list.add(0, sub)
        }
        _subscriptions.value = list
        saveSubscriptionsToDisk(list)
    }

    fun deleteSubscription(subId: String, deleteServers: Boolean = false) {
        val sub = _subscriptions.value.find { it.id == subId }
        val list = _subscriptions.value.filterNot { it.id == subId }
        _subscriptions.value = list
        saveSubscriptionsToDisk(list)

        if (deleteServers && sub != null) {
            val remainingConfigs = _allConfigs.value.filterNot { it.subscriptionUrl == sub.url }
            _allConfigs.value = remainingConfigs
            if (_currentConfig.value?.subscriptionUrl == sub.url) {
                val next = remainingConfigs.firstOrNull()
                _currentConfig.value = next?.copy(isSelected = true)
                prefs.edit().putString("selected_config_id", next?.id).apply()
            }
            saveConfigsToDisk(remainingConfigs)
        }
    }

    private fun saveConfigsToDisk(list: List<ProxyConfig>) {
        try {
            val array = JSONArray()
            for (c in list) {
                val obj = JSONObject().apply {
                    put("id", c.id)
                    put("name", c.name)
                    put("protocol", c.protocol.name)
                    put("server", c.server)
                    put("port", c.port)
                    put("uuidOrPassword", c.uuidOrPassword)
                    put("alterId", c.alterId)
                    put("security", c.security)
                    put("network", c.network)
                    put("wsPath", c.wsPath)
                    put("wsHost", c.wsHost)
                    put("sni", c.sni)
                    put("fingerprint", c.fingerprint)
                    put("publicKey", c.publicKey)
                    put("shortId", c.shortId)
                    put("spiderX", c.spiderX)
                    put("flow", c.flow)
                    put("alpn", c.alpn)
                    put("countryCode", c.countryCode)
                    put("pingMs", c.pingMs)
                    put("subscriptionUrl", c.subscriptionUrl)
                    put("subscriptionName", c.subscriptionName)
                    put("createdAt", c.createdAt)
                }
                array.put(obj)
            }
            prefs.edit().putString("saved_all_configs", array.toString()).apply()

            // Also save legacy single config for fallback
            _currentConfig.value?.let { current ->
                val legacyObj = JSONObject().apply {
                    put("id", current.id)
                    put("name", current.name)
                    put("protocol", current.protocol.name)
                    put("server", current.server)
                    put("port", current.port)
                    put("uuidOrPassword", current.uuidOrPassword)
                    put("alterId", current.alterId)
                    put("security", current.security)
                    put("network", current.network)
                    put("wsPath", current.wsPath)
                    put("wsHost", current.wsHost)
                    put("sni", current.sni)
                    put("fingerprint", current.fingerprint)
                    put("publicKey", current.publicKey)
                    put("shortId", current.shortId)
                    put("spiderX", current.spiderX)
                    put("flow", current.flow)
                    put("alpn", current.alpn)
                    put("countryCode", current.countryCode)
                    put("pingMs", current.pingMs)
                    put("createdAt", current.createdAt)
                }
                prefs.edit().putString("saved_active_config", legacyObj.toString()).apply()
            } ?: prefs.edit().remove("saved_active_config").apply()
        } catch (_: Exception) {}
    }

    private fun saveSubscriptionsToDisk(list: List<SubscriptionInfo>) {
        try {
            val array = JSONArray()
            for (s in list) {
                array.put(
                    JSONObject().apply {
                        put("id", s.id)
                        put("url", s.url)
                        put("name", s.name)
                        put("lastUpdated", s.lastUpdated)
                        put("serverCount", s.serverCount)
                    }
                )
            }
            prefs.edit().putString("saved_subscriptions", array.toString()).apply()
        } catch (_: Exception) {}
    }

    private fun parseConfigFromJson(obj: JSONObject): ProxyConfig? {
        return try {
            val proto = try {
                ProxyProtocol.valueOf(obj.optString("protocol", "VLESS"))
            } catch (_: Exception) {
                ProxyProtocol.VLESS
            }
            ProxyConfig(
                id = obj.optString("id", "tesvpn-node"),
                name = obj.optString("name", "Мой Xray сервер"),
                protocol = proto,
                server = obj.optString("server", ""),
                port = obj.optInt("port", 443),
                uuidOrPassword = obj.optString("uuidOrPassword", ""),
                alterId = obj.optInt("alterId", 0),
                security = obj.optString("security", "reality"),
                network = obj.optString("network", "tcp"),
                wsPath = obj.optString("wsPath", ""),
                wsHost = obj.optString("wsHost", ""),
                sni = obj.optString("sni", ""),
                fingerprint = obj.optString("fingerprint", "chrome"),
                publicKey = obj.optString("publicKey", ""),
                shortId = obj.optString("shortId", ""),
                spiderX = obj.optString("spiderX", ""),
                flow = obj.optString("flow", "xtls-rprx-vision"),
                alpn = obj.optString("alpn", ""),
                countryCode = obj.optString("countryCode", "DE"),
                pingMs = obj.optLong("pingMs", -1),
                isSelected = false,
                subscriptionUrl = obj.optString("subscriptionUrl", ""),
                subscriptionName = obj.optString("subscriptionName", ""),
                createdAt = obj.optLong("createdAt", System.currentTimeMillis())
            )
        } catch (e: Exception) {
            null
        }
    }
}
