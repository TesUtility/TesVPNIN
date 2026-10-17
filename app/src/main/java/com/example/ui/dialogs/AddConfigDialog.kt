package com.example.ui.dialogs

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.model.ProxyConfig
import com.example.model.ProxyProtocol
import com.example.ui.theme.CyberBlue
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.MetallicDark
import com.example.ui.theme.MetallicMuted
import com.example.ui.theme.MetallicSilver
import java.util.UUID

@Composable
fun AddConfigDialog(
    onDismiss: () -> Unit,
    onImportString: (String) -> Unit,
    onSaveManualConfig: (ProxyConfig) -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var importText by remember { mutableStateOf("") }

    // Manual form fields
    var protocol by remember { mutableStateOf(ProxyProtocol.VLESS) }
    var name by remember { mutableStateOf("") }
    var server by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("443") }
    var uuid by remember { mutableStateOf("") }
    var security by remember { mutableStateOf("reality") }
    var network by remember { mutableStateOf("tcp") }
    var sni by remember { mutableStateOf("") }
    var publicKey by remember { mutableStateOf("") }
    var shortId by remember { mutableStateOf("") }
    var flow by remember { mutableStateOf("xtls-rprx-vision") }
    var wsPath by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
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
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ДОБАВИТЬ СЕРВЕР",
                        color = CyberCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = MetallicDark)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Tabs: Импорт / Вручную
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = DarkSurfaceVariant,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = CyberCyan
                        )
                    },
                    modifier = Modifier.clip(RoundedCornerShape(10.dp))
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Импорт ссылки / QR", fontSize = 12.sp, color = if (selectedTab == 0) CyberCyan else MetallicMuted) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Вручную", fontSize = 12.sp, color = if (selectedTab == 1) CyberCyan else MetallicMuted) }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (selectedTab == 0) {
                    // Import Mode
                    Text(
                        text = "Вставьте ссылку конфигурации (VLESS, VMess, Trojan, SS, Hy2, WireGuard) или подписку (http/https):",
                        color = MetallicMuted,
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = importText,
                        onValueChange = { importText = it },
                        placeholder = { Text("vless://, vmess://, trojan://, ss://, hy2://...\nили ссылка подписки https://...", color = MetallicDark, fontSize = 12.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = DarkSurfaceVariant,
                            unfocusedContainerColor = DarkSurfaceVariant,
                            focusedBorderColor = CyberCyan,
                            unfocusedBorderColor = DarkBorder,
                            focusedTextColor = MetallicSilver,
                            unfocusedTextColor = MetallicSilver
                        ),
                        shape = RoundedCornerShape(10.dp),
                        minLines = 4,
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clipText = clipboard.primaryClip?.getItemAt(0)?.text?.toString()?.trim()
                            if (!clipText.isNullOrEmpty()) {
                                importText = clipText
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant, contentColor = CyberCyan),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Вставить из буфера обмена", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            if (importText.isNotBlank()) {
                                onImportString(importText.trim())
                                onDismiss()
                            }
                        },
                        enabled = importText.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color(0xFF001426)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Импортировать", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                } else {
                    // Manual Mode
                    Text(text = "Протокол", color = MetallicMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(ProxyProtocol.VLESS, ProxyProtocol.VMESS, ProxyProtocol.TROJAN, ProxyProtocol.SHADOWSOCKS, ProxyProtocol.HYSTERIA2).forEach { proto ->
                            val isChosen = protocol == proto
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isChosen) CyberBlue else DarkSurfaceVariant)
                                    .clickable {
                                        protocol = proto
                                        if (proto == ProxyProtocol.VLESS) security = "reality"
                                        if (proto == ProxyProtocol.TROJAN) security = "tls"
                                    }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = proto.displayName,
                                    color = if (isChosen) Color.White else MetallicSilver,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    CustomInputField(value = name, onValueChange = { name = it }, label = "Название сервера", placeholder = "Мой VLESS сервер")
                    CustomInputField(value = server, onValueChange = { server = it }, label = "Адрес сервера (IP или домен)", placeholder = "example.com")
                    CustomInputField(value = port, onValueChange = { port = it }, label = "Порт", placeholder = "443")
                    CustomInputField(value = uuid, onValueChange = { uuid = it }, label = if (protocol == ProxyProtocol.TROJAN) "Пароль" else "UUID", placeholder = "e68f3a38-51f4-4a49...")

                    if (protocol == ProxyProtocol.VLESS) {
                        CustomInputField(value = sni, onValueChange = { sni = it }, label = "SNI (Server Name)", placeholder = "dl.google.com")
                        CustomInputField(value = publicKey, onValueChange = { publicKey = it }, label = "Reality Public Key (pbk)", placeholder = "i3hV8G_L8rYn9p...")
                        CustomInputField(value = shortId, onValueChange = { shortId = it }, label = "Short ID (sid)", placeholder = "8a2f1c9e")
                    } else if (protocol == ProxyProtocol.VMESS) {
                        CustomInputField(value = sni, onValueChange = { sni = it }, label = "SNI / Host", placeholder = "server.com")
                        CustomInputField(value = wsPath, onValueChange = { wsPath = it }, label = "WS Path", placeholder = "/ws")
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            val newConfig = ProxyConfig(
                                id = UUID.randomUUID().toString(),
                                name = if (name.isNotBlank()) name else "${protocol.displayName} Server",
                                protocol = protocol,
                                server = if (server.isNotBlank()) server else "127.0.0.1",
                                port = port.toIntOrNull() ?: 443,
                                uuidOrPassword = uuid,
                                security = security,
                                network = network,
                                sni = sni,
                                publicKey = publicKey,
                                shortId = shortId,
                                flow = flow,
                                wsPath = wsPath
                            )
                            onSaveManualConfig(newConfig)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color(0xFF001426)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Сохранить конфигурацию", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun CustomInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(text = label, color = MetallicMuted, fontSize = 11.sp)
        Spacer(modifier = Modifier.height(2.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, color = MetallicDark, fontSize = 12.sp) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = DarkSurfaceVariant,
                unfocusedContainerColor = DarkSurfaceVariant,
                focusedBorderColor = CyberCyan,
                unfocusedBorderColor = DarkBorder,
                focusedTextColor = MetallicSilver,
                unfocusedTextColor = MetallicSilver
            ),
            shape = RoundedCornerShape(8.dp),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
