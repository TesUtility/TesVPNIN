package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NetworkPing
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ConnectionStatus
import com.example.model.ProxyConfig
import com.example.model.TrafficStats
import com.example.ui.components.TesVpnShieldButton
import com.example.ui.theme.CyberBlue
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.DeepNavyBg
import com.example.ui.theme.MetallicDark
import com.example.ui.theme.MetallicMuted
import com.example.ui.theme.MetallicSilver
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusRed

@Composable
fun HomeScreen(
    status: ConnectionStatus,
    config: ProxyConfig?,
    trafficStats: TrafficStats,
    isTestingPing: Boolean,
    allConfigs: List<ProxyConfig> = emptyList(),
    onSelectConfig: (String) -> Unit = {},
    onConnectClick: () -> Unit,
    onAddConfigClick: () -> Unit,
    onScanQrClick: () -> Unit,
    onPasteClipboard: () -> Unit,
    onPingClick: () -> Unit,
    onShareQrClick: () -> Unit,
    onEditConfigClick: () -> Unit,
    onDeleteConfigClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showServerPicker by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(DeepNavyBg)
    ) {
        val isWideScreen = maxWidth >= 600.dp
        val isCompactHeight = maxHeight < 520.dp
        val scrollState = rememberScrollState()

        if (isWideScreen) {
            // Tablet / Foldable landscape / Two-Pane layout
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Column: Shield Button, Status, Timer
                Column(
                    modifier = Modifier
                        .weight(0.9f)
                        .widthIn(max = 380.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    TesVpnShieldButton(
                        status = status,
                        onClick = onConnectClick,
                        size = if (isCompactHeight) 180.dp else 220.dp
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    ConnectionTimerBadge(status = status, trafficStats = trafficStats)
                }

                // Right Column: Speed cards, Config card, Security badge
                Column(
                    modifier = Modifier
                        .weight(1.1f)
                        .widthIn(max = 600.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    TelemetrySpeedCards(status = status, trafficStats = trafficStats)

                    ConfigCardOrEmpty(
                        config = config,
                        serverCount = allConfigs.size,
                        onOpenServerPicker = { showServerPicker = true },
                        isTestingPing = isTestingPing,
                        onAddConfigClick = onAddConfigClick,
                        onScanQrClick = onScanQrClick,
                        onPasteClipboard = onPasteClipboard,
                        onPingClick = onPingClick,
                        onShareQrClick = onShareQrClick,
                        onEditConfigClick = onEditConfigClick,
                        onDeleteConfigClick = onDeleteConfigClick
                    )

                    SecuritySummaryCard(trafficStats = trafficStats)
                }
            }
        } else {
            // Standard Phone vertical layout (centered with horizontal padding)
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 520.dp)
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(8.dp))

                    TesVpnShieldButton(
                        status = status,
                        onClick = onConnectClick,
                        size = if (isCompactHeight) 180.dp else 220.dp
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    ConnectionTimerBadge(status = status, trafficStats = trafficStats)

                    Spacer(modifier = Modifier.height(14.dp))

                    TelemetrySpeedCards(status = status, trafficStats = trafficStats)

                    Spacer(modifier = Modifier.height(16.dp))

                    ConfigCardOrEmpty(
                        config = config,
                        serverCount = allConfigs.size,
                        onOpenServerPicker = { showServerPicker = true },
                        isTestingPing = isTestingPing,
                        onAddConfigClick = onAddConfigClick,
                        onScanQrClick = onScanQrClick,
                        onPasteClipboard = onPasteClipboard,
                        onPingClick = onPingClick,
                        onShareQrClick = onShareQrClick,
                        onEditConfigClick = onEditConfigClick,
                        onDeleteConfigClick = onDeleteConfigClick
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    SecuritySummaryCard(trafficStats = trafficStats)

                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }
    }

    if (showServerPicker && allConfigs.isNotEmpty()) {
        ServerPickerBottomSheet(
            configs = allConfigs,
            activeConfigId = config?.id,
            onSelect = { serverId ->
                onSelectConfig(serverId)
                showServerPicker = false
            },
            onDismiss = { showServerPicker = false }
        )
    }
}

@Composable
private fun ConnectionTimerBadge(
    status: ConnectionStatus,
    trafficStats: TrafficStats
) {
    AnimatedVisibility(
        visible = status == ConnectionStatus.CONNECTED || status == ConnectionStatus.CONNECTING,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(DarkSurfaceVariant)
                .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Timer,
                contentDescription = null,
                tint = CyberCyan,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = trafficStats.formattedDuration(),
                color = MetallicSilver,
                fontSize = 15.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun TelemetrySpeedCards(
    status: ConnectionStatus,
    trafficStats: TrafficStats
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Download Speed
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = CardDefaults.outlinedCardBorder().copy(brush = Brush.verticalGradient(listOf(DarkBorder, Color(0xFF182234)))),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.weight(1f)
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(StatusGreen.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowDownward,
                        contentDescription = "Download",
                        tint = StatusGreen,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "СКАЧИВАНИЕ",
                        color = MetallicDark,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = if (status == ConnectionStatus.CONNECTED) trafficStats.formattedDownloadSpeed() else "0 B/s",
                        color = MetallicSilver,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Upload Speed
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = CardDefaults.outlinedCardBorder().copy(brush = Brush.verticalGradient(listOf(DarkBorder, Color(0xFF182234)))),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.weight(1f)
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(CyberCyan.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowUpward,
                        contentDescription = "Upload",
                        tint = CyberCyan,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "ОТДАЧА",
                        color = MetallicDark,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = if (status == ConnectionStatus.CONNECTED) trafficStats.formattedUploadSpeed() else "0 B/s",
                        color = MetallicSilver,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun ConfigCardOrEmpty(
    config: ProxyConfig?,
    serverCount: Int,
    onOpenServerPicker: () -> Unit,
    isTestingPing: Boolean,
    onAddConfigClick: () -> Unit,
    onScanQrClick: () -> Unit,
    onPasteClipboard: () -> Unit,
    onPingClick: () -> Unit,
    onShareQrClick: () -> Unit,
    onEditConfigClick: () -> Unit,
    onDeleteConfigClick: () -> Unit
) {
    if (config == null) {
        // Empty State: Prompt to add configuration
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = CardDefaults.outlinedCardBorder().copy(brush = Brush.horizontalGradient(listOf(CyberCyan.copy(alpha = 0.4f), DarkBorder))),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("empty_config_card")
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(CyberBlue.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = null,
                        tint = CyberCyan,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Конфигурация не добавлена",
                    color = MetallicSilver,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Отсканируйте QR-код или вставьте ссылку Xray (VLESS, VMess, Trojan)",
                    color = MetallicMuted,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onScanQrClick,
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color(0xFF001426)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("QR-код", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    OutlinedButton(
                        onClick = onPasteClipboard,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CyberCyan),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Вставить", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "или ввести вручную",
                    color = CyberCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clickable { onAddConfigClick() }
                        .padding(vertical = 4.dp, horizontal = 8.dp)
                )
            }
        }
    } else {
        // Active Configuration Card
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = CardDefaults.outlinedCardBorder().copy(brush = Brush.horizontalGradient(listOf(CyberBlue.copy(alpha = 0.5f), DarkBorder))),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("current_config_card")
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(text = config.countryFlag, fontSize = 26.sp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = config.name,
                                color = MetallicSilver,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = config.displayAddress,
                                color = MetallicMuted,
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        }
                    }

                    // Ping Button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(DarkSurfaceVariant)
                            .border(1.dp, DarkBorder, RoundedCornerShape(8.dp))
                            .clickable { onPingClick() }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        if (isTestingPing) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                color = CyberCyan,
                                modifier = Modifier.size(16.dp)
                            )
                        } else {
                            val pingVal = config.pingMs
                            val pingColor = when {
                                pingVal in 1..149 -> StatusGreen
                                pingVal in 150..399 -> StatusAmber
                                pingVal >= 400 -> StatusRed
                                else -> MetallicDark
                            }
                            val pingText = when {
                                pingVal > 0 -> "${pingVal}ms"
                                pingVal == -2L -> "Таймаут"
                                else -> "Пинг"
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(pingColor)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = pingText,
                                    color = pingColor,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                if (serverCount > 1) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(CyberBlue.copy(alpha = 0.2f))
                            .border(1.dp, CyberCyan.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .clickable { onOpenServerPicker() }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Серверов в списке: $serverCount",
                            color = CyberCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Сменить сервер ▾",
                            color = MetallicSilver,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Badges row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(CyberBlue.copy(alpha = 0.25f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = config.protocol.displayName,
                                color = CyberCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(DarkSurfaceVariant)
                                .border(1.dp, DarkBorder, RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = config.securityBadge,
                                color = MetallicSilver,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(DarkSurfaceVariant)
                                .border(1.dp, DarkBorder, RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = config.network.uppercase(),
                                color = MetallicMuted,
                                fontSize = 11.sp
                            )
                        }
                    }

                    // Action icons for current config
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(onClick = onShareQrClick, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.QrCode, contentDescription = "QR", tint = CyberCyan, modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = onEditConfigClick, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MetallicSilver, modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = onDeleteConfigClick, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = StatusRed, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Change / Replace Config button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(DarkSurfaceVariant)
                        .clickable { onAddConfigClick() }
                        .padding(vertical = 8.dp, horizontal = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Заменить конфигурацию (QR / буфер)",
                        color = CyberCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun SecuritySummaryCard(trafficStats: TrafficStats) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DarkSurface)
            .border(1.dp, DarkBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                tint = CyberCyan,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Xray Core • VLESS Reality Vision",
                color = MetallicMuted,
                fontSize = 12.sp
            )
        }
        Text(
            text = "Всего: ${trafficStats.formattedTotalDownloaded()}",
            color = MetallicSilver,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun ServerPickerBottomSheet(
    configs: List<ProxyConfig>,
    activeConfigId: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(DarkSurface)
                .border(1.dp, DarkBorder, RoundedCornerShape(20.dp))
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ВЫБОР СЕРВЕРА (${configs.size})",
                        color = CyberCyan,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Delete.let { Icons.Default.Add }, contentDescription = "Close", tint = MetallicDark)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    configs.forEach { item ->
                        val isSelected = item.id == activeConfigId
                        val bg = if (isSelected) DarkSurfaceVariant else Color(0xFF090D14)
                        val border = if (isSelected) CyberCyan else DarkBorder

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(bg)
                                .border(1.dp, border, RoundedCornerShape(12.dp))
                                .clickable { onSelect(item.id) }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(text = item.countryFlag, fontSize = 22.sp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = item.name,
                                        color = if (isSelected) CyberCyan else MetallicSilver,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 14.sp,
                                        maxLines = 1
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = item.displayAddress,
                                            color = MetallicMuted,
                                            fontSize = 11.sp
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "• ${item.protocol.displayName}",
                                            color = Color(item.protocol.badgeColorHex),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            if (item.pingMs > 0) {
                                Text(
                                    text = "${item.pingMs}ms",
                                    color = StatusGreen,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant, contentColor = MetallicSilver),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Закрыть", fontSize = 12.sp)
                }
            }
        }
    }
}
