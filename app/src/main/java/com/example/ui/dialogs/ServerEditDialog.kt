package com.example.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.model.ProxyConfig
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.MetallicDark

@Composable
fun ServerEditDialog(
    config: ProxyConfig,
    onDismiss: () -> Unit,
    onSave: (ProxyConfig) -> Unit
) {
    var name by remember { mutableStateOf(config.name) }
    var server by remember { mutableStateOf(config.server) }
    var port by remember { mutableStateOf(config.port.toString()) }
    var uuid by remember { mutableStateOf(config.uuidOrPassword) }
    var security by remember { mutableStateOf(config.security) }
    var network by remember { mutableStateOf(config.network) }
    var sni by remember { mutableStateOf(config.sni) }
    var publicKey by remember { mutableStateOf(config.publicKey) }
    var shortId by remember { mutableStateOf(config.shortId) }
    var flow by remember { mutableStateOf(config.flow) }
    var wsPath by remember { mutableStateOf(config.wsPath) }

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
                        text = "РЕДАКТИРОВАНИЕ КОНФИГА",
                        color = CyberCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = MetallicDark)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                CustomInputField(value = name, onValueChange = { name = it }, label = "Название", placeholder = "Имя сервера")
                CustomInputField(value = server, onValueChange = { server = it }, label = "Адрес хоста", placeholder = "example.com")
                CustomInputField(value = port, onValueChange = { port = it }, label = "Порт", placeholder = "443")
                CustomInputField(value = uuid, onValueChange = { uuid = it }, label = "Пароль / UUID", placeholder = "uuid")
                CustomInputField(value = security, onValueChange = { security = it }, label = "Безопасность (tls / reality / none)", placeholder = "reality")
                CustomInputField(value = network, onValueChange = { network = it }, label = "Сеть (tcp / ws / grpc)", placeholder = "tcp")
                CustomInputField(value = sni, onValueChange = { sni = it }, label = "SNI", placeholder = "dl.google.com")

                if (security.equals("reality", ignoreCase = true)) {
                    CustomInputField(value = publicKey, onValueChange = { publicKey = it }, label = "Reality Public Key (pbk)", placeholder = "pbk")
                    CustomInputField(value = shortId, onValueChange = { shortId = it }, label = "Short ID (sid)", placeholder = "sid")
                    CustomInputField(value = flow, onValueChange = { flow = it }, label = "Flow", placeholder = "xtls-rprx-vision")
                }

                if (network.equals("ws", ignoreCase = true)) {
                    CustomInputField(value = wsPath, onValueChange = { wsPath = it }, label = "WebSocket Path", placeholder = "/ws")
                }

                Spacer(modifier = Modifier.height(18.dp))

                Button(
                    onClick = {
                        val updated = config.copy(
                            name = name,
                            server = server,
                            port = port.toIntOrNull() ?: config.port,
                            uuidOrPassword = uuid,
                            security = security,
                            network = network,
                            sni = sni,
                            publicKey = publicKey,
                            shortId = shortId,
                            flow = flow,
                            wsPath = wsPath
                        )
                        onSave(updated)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color(0xFF001426)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Сохранить изменения", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}
