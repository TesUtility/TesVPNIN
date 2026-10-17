package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.ConnectionStatus
import com.example.ui.theme.CyberBlue
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.MetallicSilver
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusGreen

@Composable
fun TesVpnShieldButton(
    status: ConnectionStatus,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 220.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (status == ConnectionStatus.CONNECTED || status == ConnectionStatus.CONNECTING) 1.08f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val outerRingColor = when (status) {
        ConnectionStatus.CONNECTED -> StatusGreen.copy(alpha = 0.25f)
        ConnectionStatus.CONNECTING -> StatusAmber.copy(alpha = 0.3f)
        ConnectionStatus.DISCONNECTING -> StatusAmber.copy(alpha = 0.2f)
        ConnectionStatus.DISCONNECTED -> CyberCyan.copy(alpha = 0.12f)
    }

    val rimGradient = when (status) {
        ConnectionStatus.CONNECTED -> listOf(StatusGreen, CyberCyan, StatusGreen)
        ConnectionStatus.CONNECTING -> listOf(StatusAmber, Color(0xFFFBBF24), StatusAmber)
        ConnectionStatus.DISCONNECTING -> listOf(StatusAmber, Color(0xFFEF4444))
        ConnectionStatus.DISCONNECTED -> listOf(CyberCyan, CyberBlue, DarkBorder)
    }

    val pulseSize = size * 0.95f
    val innerSize = size * 0.80f
    val logoSize = size * 0.57f

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                )
                .testTag("shield_connect_button")
        ) {
            // Concentric Glowing Pulse Ring
            Box(
                modifier = Modifier
                    .size(pulseSize)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(outerRingColor)
            )

            // Inner Metallic Ring
            Box(
                modifier = Modifier
                    .size(innerSize)
                    .shadow(16.dp, CircleShape, spotColor = rimGradient.first())
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF1E283A),
                                DarkSurface,
                                Color(0xFF0C101A)
                            )
                        )
                    )
                    .border(
                        width = 3.dp,
                        brush = Brush.sweepGradient(rimGradient),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Center Shield T Logo image
                Image(
                    painter = painterResource(id = R.drawable.ic_tesvpn_logo),
                    contentDescription = "TESVPN Shield Logo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(logoSize)
                        .clip(CircleShape)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Status Label Pill
        val statusText = when (status) {
            ConnectionStatus.CONNECTED -> "ЗАЩИЩЕНО"
            ConnectionStatus.CONNECTING -> "ПОДКЛЮЧЕНИЕ..."
            ConnectionStatus.DISCONNECTING -> "ОТКЛЮЧЕНИЕ..."
            ConnectionStatus.DISCONNECTED -> "НАЖМИТЕ ДЛЯ СТАРТА"
        }

        val statusColor = when (status) {
            ConnectionStatus.CONNECTED -> StatusGreen
            ConnectionStatus.CONNECTING -> StatusAmber
            ConnectionStatus.DISCONNECTING -> StatusAmber
            ConnectionStatus.DISCONNECTED -> MetallicSilver
        }

        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(
                    when (status) {
                        ConnectionStatus.CONNECTED -> StatusGreen.copy(alpha = 0.15f)
                        ConnectionStatus.CONNECTING -> StatusAmber.copy(alpha = 0.15f)
                        else -> Color(0xFF151D2D)
                    }
                )
                .border(
                    width = 1.dp,
                    color = when (status) {
                        ConnectionStatus.CONNECTED -> StatusGreen.copy(alpha = 0.4f)
                        ConnectionStatus.CONNECTING -> StatusAmber.copy(alpha = 0.4f)
                        else -> DarkBorder
                    },
                    shape = CircleShape
                )
                .padding(horizontal = 18.dp, vertical = 7.dp)
        ) {
            Text(
                text = statusText,
                color = statusColor,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                letterSpacing = 1.sp
            )
        }
    }
}
