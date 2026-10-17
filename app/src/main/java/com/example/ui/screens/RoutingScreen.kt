package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.CyberBlue
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.DeepNavyBg
import com.example.ui.theme.MetallicDark
import com.example.ui.theme.MetallicMuted
import com.example.ui.theme.MetallicSilver

@Composable
fun RoutingScreen(
    routingMode: String,
    dnsServer: String,
    killSwitch: Boolean,
    onRoutingModeChange: (String) -> Unit,
    onDnsChange: (String) -> Unit,
    onKillSwitchChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    var sniffingEnabled by remember { mutableStateOf(true) }
    var fakeDnsEnabled by remember { mutableStateOf(true) }
    var muxEnabled by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DeepNavyBg),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section: Routing Mode
            Text(
                text = "РЕЖИМ МАРШРУТИЗАЦИИ",
                color = MetallicDark,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.Brush.linearGradient(listOf(DarkBorder, DarkBorder))),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    RoutingModeOption(
                        title = "Обход локальной сети и RU сайтов",
                        subtitle = "Прямой доступ к Госуслугам, банкам и локальной сети; весь остальной трафик через VPN",
                        isSelected = routingMode == "BYPASS_LAN",
                        onClick = { onRoutingModeChange("BYPASS_LAN") }
                    )
                    RoutingModeOption(
                        title = "Глобальный (Proxy All)",
                        subtitle = "Абсолютно весь интернет-трафик направляется через выбранный сервер TESVPN",
                        isSelected = routingMode == "PROXY_ALL",
                        onClick = { onRoutingModeChange("PROXY_ALL") }
                    )
                    RoutingModeOption(
                        title = "Только заблокированные ресурсы",
                        subtitle = "Проксирование только адресов из встроенных списков антицензуры",
                        isSelected = routingMode == "DIRECT",
                        onClick = { onRoutingModeChange("DIRECT") }
                    )
                }
            }

            // Section: DNS
            Text(
                text = "DNS СЕРВЕР (ЗАЩИТА ОТ УТЕЧЕК)",
                color = MetallicDark,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.Brush.linearGradient(listOf(DarkBorder, DarkBorder))),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val dnsOptions = listOf(
                        Triple("1.1.1.1", "Cloudflare DNS (Рекомендуется)", "Ультрабыстрый, DoH шифрование"),
                        Triple("8.8.8.8", "Google Public DNS", "Высокая надежность и глобальный кэш"),
                        Triple("9.9.9.9", "Quad9 Secure DNS", "Автоматическая блокировка вредоносных фишинговых сайтов")
                    )
                    dnsOptions.forEach { (ip, title, desc) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onDnsChange(ip) }
                                .padding(vertical = 8.dp, horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (dnsServer == ip) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (dnsServer == ip) CyberCyan else MetallicDark,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = title, color = MetallicSilver, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text(text = "$ip • $desc", color = MetallicMuted, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            // Section: Security Toggles
            Text(
                text = "БЕЗОПАСНОСТЬ И XRAY CORE",
                color = MetallicDark,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.Brush.linearGradient(listOf(DarkBorder, DarkBorder))),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    ToggleRow(
                        title = "Kill Switch (Аварийная блокировка)",
                        desc = "Блокировать весь интернет-трафик при внезапном обрыве VPN",
                        checked = killSwitch,
                        onCheckedChange = onKillSwitchChange
                    )
                    ToggleRow(
                        title = "Sniffing (Анализ доменов)",
                        desc = "Перехват TLS SNI и HTTP хостов для точной маршрутизации",
                        checked = sniffingEnabled,
                        onCheckedChange = { sniffingEnabled = it }
                    )
                    ToggleRow(
                        title = "FakeDNS",
                        desc = "Устранение задержки DNS-запросов за счет локального кэша пула",
                        checked = fakeDnsEnabled,
                        onCheckedChange = { fakeDnsEnabled = it }
                    )
                    ToggleRow(
                        title = "Mux (Мультиплексирование TCP)",
                        desc = "Объединение множества TCP потоков в одно соединение",
                        checked = muxEnabled,
                        onCheckedChange = { muxEnabled = it }
                    )
                }
            }

            // App Branding Card
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.Brush.linearGradient(listOf(DarkBorder, DarkBorder))),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_tesvpn_logo),
                        contentDescription = "TESVPN Logo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "TESVPN Client v2.4.0",
                            color = MetallicSilver,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Поддержка Xray/V2Ray • VLESS Reality • VMess • Trojan",
                            color = MetallicMuted,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun RoutingModeOption(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .background(if (isSelected) CyberBlue.copy(alpha = 0.15f) else Color.Transparent)
            .border(
                1.dp,
                if (isSelected) CyberCyan.copy(alpha = 0.5f) else Color.Transparent,
                RoundedCornerShape(12.dp)
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isSelected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (isSelected) CyberCyan else MetallicDark,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = if (isSelected) CyberCyan else MetallicSilver,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = MetallicMuted,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    desc: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = MetallicSilver, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = desc, color = MetallicMuted, fontSize = 11.sp)
        }
        Spacer(modifier = Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = CyberCyan,
                uncheckedThumbColor = MetallicDark,
                uncheckedTrackColor = DarkSurfaceVariant
            )
        )
    }
}
