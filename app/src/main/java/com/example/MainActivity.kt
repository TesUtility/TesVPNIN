package com.example

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.filled.NetworkPing
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.model.ConnectionStatus
import com.example.ui.MainViewModel
import com.example.ui.dialogs.AddConfigDialog
import com.example.ui.dialogs.QrScannerDialog
import com.example.ui.dialogs.QrShareDialog
import com.example.ui.dialogs.ServerEditDialog
import com.example.ui.screens.ConfigScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LogsScreen
import com.example.ui.screens.RoutingScreen
import com.example.ui.theme.CyberBlue
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.DeepNavyBg
import com.example.ui.theme.MetallicDark
import com.example.ui.theme.MetallicSilver
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.TESVPNTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            TESVPNTheme {
                MainApp(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApp(viewModel: MainViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedTab by remember { mutableIntStateOf(0) }

    // States from ViewModel
    val activeConfig by viewModel.currentConfig.collectAsState()
    val allConfigs by viewModel.allConfigs.collectAsState()
    val subscriptions by viewModel.subscriptions.collectAsState()
    val isImporting by viewModel.isImporting.collectAsState()
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val trafficStats by viewModel.trafficStats.collectAsState()
    val routingMode by viewModel.routingMode.collectAsState()
    val dnsServer by viewModel.dnsServer.collectAsState()
    val killSwitch by viewModel.killSwitch.collectAsState()
    val isTestingPing by viewModel.isTestingPing.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val userMessage by viewModel.userMessage.collectAsState()

    // Dialog states
    val showAddConfigSheet by viewModel.showAddConfigSheet.collectAsState()
    val showQrScanner by viewModel.showQrScanner.collectAsState()
    val qrShareConfig by viewModel.qrShareConfig.collectAsState()
    val editingConfig by viewModel.editingConfig.collectAsState()

    // Show Snackbars
    LaunchedEffect(userMessage) {
        userMessage?.let {
            scope.launch {
                snackbarHostState.showSnackbar(it)
                viewModel.clearUserMessage()
            }
        }
    }

    // Notification Permission (Android 13+)
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* handled */ }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            } catch (_: Exception) {}
        }
    }

    // VPN Permission Launcher
    val vpnLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.toggleConnection(hasVpnPermission = true) { /* no-op */ }
        } else {
            viewModel.showMessage("Разрешение VPN необходимо для работы туннеля")
        }
    }

    val handleConnectToggle = {
        if (activeConfig == null) {
            viewModel.showMessage("Сначала добавьте конфигурацию (QR-код или ссылка)")
            viewModel.setShowAddConfigSheet(true)
        } else {
            try {
                val prepareIntent = VpnService.prepare(context)
                if (prepareIntent != null) {
                    vpnLauncher.launch(prepareIntent)
                } else {
                    viewModel.toggleConnection(hasVpnPermission = true) { /* no-op */ }
                }
            } catch (e: Exception) {
                viewModel.toggleConnection(hasVpnPermission = true) { /* no-op */ }
            }
        }
    }

    // Shared TopAppBar Composable
    val topAppBarComposable: @Composable () -> Unit = {
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_tesvpn_logo),
                        contentDescription = "TESVPN Shield Logo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "TESVPN",
                        color = MetallicSilver,
                        fontWeight = FontWeight.Black,
                        fontSize = 19.sp,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(CyberBlue.copy(alpha = 0.25f))
                            .border(1.dp, CyberCyan.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "XRAY CLIENT",
                            color = CyberCyan,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            actions = {
                // Quick QR scan button in TopAppBar
                IconButton(onClick = { viewModel.setShowQrScanner(true) }) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = "Scan QR",
                        tint = CyberCyan,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Ping button if config exists
                if (activeConfig != null) {
                    Box(
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(DarkSurfaceVariant)
                            .border(1.dp, DarkBorder, RoundedCornerShape(8.dp))
                            .clickable { viewModel.pingConfig() }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag("top_ping_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isTestingPing) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                color = CyberCyan,
                                modifier = Modifier.size(16.dp)
                            )
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.NetworkPing,
                                    contentDescription = "Ping",
                                    tint = CyberCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                val p = activeConfig?.pingMs ?: -1
                                Text(
                                    text = if (p > 0) "${p}ms" else "Пинг",
                                    color = if (p > 0) StatusGreen else MetallicSilver,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = DeepNavyBg,
                titleContentColor = MetallicSilver
            )
        )
    }

    // Shared Screen Content Composable
    val screenContentComposable: @Composable () -> Unit = {
        when (selectedTab) {
            0 -> HomeScreen(
                status = connectionStatus,
                config = activeConfig,
                trafficStats = trafficStats,
                isTestingPing = isTestingPing,
                allConfigs = allConfigs,
                onSelectConfig = { viewModel.selectConfig(it) },
                onConnectClick = handleConnectToggle,
                onAddConfigClick = { viewModel.setShowAddConfigSheet(true) },
                onScanQrClick = { viewModel.setShowQrScanner(true) },
                onPasteClipboard = { viewModel.importFromClipboard() },
                onPingClick = { viewModel.pingConfig() },
                onShareQrClick = { viewModel.setQrShareConfig(activeConfig) },
                onEditConfigClick = { viewModel.setEditingConfig(activeConfig) },
                onDeleteConfigClick = { activeConfig?.let { viewModel.deleteConfig(it.id) } }
            )
            1 -> ConfigScreen(
                config = activeConfig,
                allConfigs = allConfigs,
                subscriptions = subscriptions,
                isTestingPing = isTestingPing,
                isImporting = isImporting,
                onAddConfigClick = { viewModel.setShowAddConfigSheet(true) },
                onScanQrClick = { viewModel.setShowQrScanner(true) },
                onPasteClipboard = { viewModel.importFromClipboard() },
                onPingClick = { viewModel.pingConfig() },
                onPingAllClick = { viewModel.pingAllConfigs() },
                onPingSpecificConfig = { viewModel.pingConfig(it) },
                onSelectConfig = { viewModel.selectConfig(it) },
                onDeleteConfigId = { viewModel.deleteConfig(it) },
                onDeleteAllConfigs = { viewModel.deleteAllConfigs() },
                onRefreshSubscription = { viewModel.refreshSubscription(it) },
                onDeleteSubscription = { id, delServers -> viewModel.deleteSubscription(id, delServers) },
                onShareQrClick = { viewModel.setQrShareConfig(activeConfig) },
                onEditConfigClick = { viewModel.setEditingConfig(activeConfig) },
                onCopyLinkClick = { activeConfig?.let { viewModel.copyConfigToClipboard(it) } },
                onDeleteConfigClick = { activeConfig?.let { viewModel.deleteConfig(it.id) } }
            )
            2 -> RoutingScreen(
                routingMode = routingMode,
                dnsServer = dnsServer,
                killSwitch = killSwitch,
                onRoutingModeChange = { viewModel.setRoutingMode(it) },
                onDnsChange = { viewModel.setDnsServer(it) },
                onKillSwitchChange = { viewModel.setKillSwitch(it) }
            )
            3 -> LogsScreen(
                logs = logs,
                onClearLogs = { viewModel.clearLogs() },
                onShowMessage = { viewModel.showMessage(it) }
            )
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(DeepNavyBg)) {
        val isTablet = maxWidth >= 600.dp

        if (isTablet) {
            // Adaptive Tablet Layout: Side NavigationRail + Main Content
            Row(modifier = Modifier.fillMaxSize()) {
                NavigationRail(
                    containerColor = DarkSurface,
                    header = {
                        Image(
                            painter = painterResource(id = R.drawable.ic_tesvpn_logo),
                            contentDescription = "TESVPN Shield Logo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .padding(top = 16.dp, bottom = 12.dp)
                                .size(40.dp)
                                .clip(CircleShape)
                        )
                    }
                ) {
                    Spacer(modifier = Modifier.weight(1f))

                    NavigationRailItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { Icon(Icons.Default.Shield, contentDescription = "Подключение") },
                        label = { Text("Главная", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = Color(0xFF001426),
                            selectedTextColor = CyberCyan,
                            indicatorColor = CyberCyan,
                            unselectedIconColor = MetallicDark,
                            unselectedTextColor = MetallicDark
                        ),
                        modifier = Modifier.testTag("rail_tab_home")
                    )

                    NavigationRailItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Icon(Icons.Default.QrCode, contentDescription = "Конфиг") },
                        label = { Text("Конфиг", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = Color(0xFF001426),
                            selectedTextColor = CyberCyan,
                            indicatorColor = CyberCyan,
                            unselectedIconColor = MetallicDark,
                            unselectedTextColor = MetallicDark
                        ),
                        modifier = Modifier.testTag("rail_tab_config")
                    )

                    NavigationRailItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = { Icon(Icons.AutoMirrored.Filled.AltRoute, contentDescription = "Маршруты") },
                        label = { Text("Маршруты", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = Color(0xFF001426),
                            selectedTextColor = CyberCyan,
                            indicatorColor = CyberCyan,
                            unselectedIconColor = MetallicDark,
                            unselectedTextColor = MetallicDark
                        ),
                        modifier = Modifier.testTag("rail_tab_routing")
                    )

                    NavigationRailItem(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        icon = { Icon(Icons.Default.Terminal, contentDescription = "Логи") },
                        label = { Text("Логи", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = Color(0xFF001426),
                            selectedTextColor = CyberCyan,
                            indicatorColor = CyberCyan,
                            unselectedIconColor = MetallicDark,
                            unselectedTextColor = MetallicDark
                        ),
                        modifier = Modifier.testTag("rail_tab_logs")
                    )

                    Spacer(modifier = Modifier.weight(1f))
                }

                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    topBar = topAppBarComposable,
                    containerColor = DeepNavyBg
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        screenContentComposable()
                    }
                }
            }
        } else {
            // Adaptive Phone Layout: Bottom NavigationBar + Main Content
            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = topAppBarComposable,
                bottomBar = {
                    NavigationBar(
                        containerColor = DarkSurface,
                        tonalElevation = 8.dp
                    ) {
                        NavigationBarItem(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            icon = { Icon(Icons.Default.Shield, contentDescription = "Подключение") },
                            label = { Text("Главная", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color(0xFF001426),
                                selectedTextColor = CyberCyan,
                                indicatorColor = CyberCyan,
                                unselectedIconColor = MetallicDark,
                                unselectedTextColor = MetallicDark
                            ),
                            modifier = Modifier.testTag("tab_home")
                        )

                        NavigationBarItem(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            icon = { Icon(Icons.Default.QrCode, contentDescription = "Конфиг") },
                            label = { Text("Конфиг", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color(0xFF001426),
                                selectedTextColor = CyberCyan,
                                indicatorColor = CyberCyan,
                                unselectedIconColor = MetallicDark,
                                unselectedTextColor = MetallicDark
                            ),
                            modifier = Modifier.testTag("tab_config")
                        )

                        NavigationBarItem(
                            selected = selectedTab == 2,
                            onClick = { selectedTab = 2 },
                            icon = { Icon(Icons.AutoMirrored.Filled.AltRoute, contentDescription = "Маршруты") },
                            label = { Text("Маршруты", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color(0xFF001426),
                                selectedTextColor = CyberCyan,
                                indicatorColor = CyberCyan,
                                unselectedIconColor = MetallicDark,
                                unselectedTextColor = MetallicDark
                            ),
                            modifier = Modifier.testTag("tab_routing")
                        )

                        NavigationBarItem(
                            selected = selectedTab == 3,
                            onClick = { selectedTab = 3 },
                            icon = { Icon(Icons.Default.Terminal, contentDescription = "Логи") },
                            label = { Text("Логи", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color(0xFF001426),
                                selectedTextColor = CyberCyan,
                                indicatorColor = CyberCyan,
                                unselectedIconColor = MetallicDark,
                                unselectedTextColor = MetallicDark
                            ),
                            modifier = Modifier.testTag("tab_logs")
                        )
                    }
                },
                containerColor = DeepNavyBg
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    screenContentComposable()
                }
            }
        }

        // Shared Dialogs
        if (showAddConfigSheet) {
            AddConfigDialog(
                onDismiss = { viewModel.setShowAddConfigSheet(false) },
                onImportString = { viewModel.importConfigString(it) },
                onSaveManualConfig = { viewModel.saveConfig(it) }
            )
        }

        if (showQrScanner) {
            QrScannerDialog(
                onDismiss = { viewModel.setShowQrScanner(false) },
                onScannedResult = { scannedText ->
                    viewModel.importConfigString(scannedText)
                }
            )
        }

        qrShareConfig?.let { cfg ->
            QrShareDialog(
                config = cfg,
                onDismiss = { viewModel.setQrShareConfig(null) },
                onCopyLink = { viewModel.copyConfigToClipboard(it) }
            )
        }

        editingConfig?.let { cfg ->
            ServerEditDialog(
                config = cfg,
                onDismiss = { viewModel.setEditingConfig(null) },
                onSave = { updated -> viewModel.saveConfig(updated) }
            )
        }
    }
}
