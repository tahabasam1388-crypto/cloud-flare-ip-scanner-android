package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SavedIpEntity
import com.example.scanner.ScanResult
import com.example.ui.theme.*
import com.example.viewmodel.*
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.*

class MainActivity : ComponentActivity() {
    private val viewModel: ScannerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    ScannerMainScreen(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerMainScreen(
    viewModel: ScannerViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val savedIps by viewModel.savedIps.collectAsState()
    val activeScanResults by viewModel.activeScanResults.collectAsState()
    val scanUiState by viewModel.scanUiState.collectAsState()
    val ipInputList by viewModel.ipInputList.collectAsState()
    val timeoutMs by viewModel.timeoutMs.collectAsState()
    val concurrency by viewModel.concurrency.collectAsState()
    val ipPoolSize by viewModel.ipPoolSizeToGenerate.collectAsState()
    val configInput by viewModel.configInput.collectAsState()
    val isCloudflareConfig by viewModel.isCloudflareConfig.collectAsState()
    val customPort by viewModel.customPort.collectAsState()
    val isSpeedTestEnabled by viewModel.isSpeedTestEnabled.collectAsState()
    val speedTestLimit by viewModel.speedTestLimit.collectAsState()

    val parsedConfig = remember(configInput) { viewModel.parseVlessTrojanConfig(configInput) }

    var activeTab by remember { mutableStateOf(0) } // 0 = Scan, 1 = Database History
    var showConfigDialog by remember { mutableStateOf(false) }
    var isPersian by remember { mutableStateOf(false) }

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(
                context,
                if (isPersian) "برای دریافت اعلان زنده پیشرفت اسکن، لطفاً اجازه ارسال اعلان را بدهید" 
                else "Please authorize notifications to see scan progress live", 
                Toast.LENGTH_LONG
            ).show()
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(isPersian) {
        viewModel.isPersianLanguage = isPersian
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            
            // --- TOP HEADER STYLED WITH A PREMIUM NEON GLOW ---
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start
                    ) {
                        // Dynamically load the generated blue cloud logo directly!
                        Image(
                            painter = painterResource(id = R.drawable.img_blue_cloud),
                            contentDescription = "Cloudflare Blue Cloud Logo",
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.5.dp, AccentCyan, RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (isPersian) "یابنده آی‌پی" else "IP Finder",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = TextPrimary,
                                    letterSpacing = 0.5.sp
                                )
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = if (isPersian) "یابنده آی‌پی تمیز کلودفلر" else "CF Clean IP Finder",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = AccentCyan,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                                Text(
                                    text = "•",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = TextSecondary,
                                        fontSize = 11.sp
                                    )
                                )
                                Text(
                                    text = if (isPersian) "برنامه از @COD_LARK" else "app by @COD_LARK",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = TextSecondary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { exportApkToDownloads(context, viewModel) }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "ذخیره APK",
                            tint = AccentBlue,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(
                        onClick = { showConfigDialog = true },
                        modifier = Modifier.testTag("config_dialog_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Config parameters",
                            tint = AccentBlue,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )

            // --- SMOOTH GLASSMORPHIC TAB NAVIGATION ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(SurfaceVariantBg)
                        .padding(4.dp)
                ) {
                    TabButton(
                        text = if (isPersian) "اسکنر زنده" else "Live Scanner",
                        icon = Icons.Default.Search,
                        selected = activeTab == 0,
                        modifier = Modifier.weight(1f),
                        onClick = { activeTab = 0 }
                    )
                    TabButton(
                        text = if (isPersian) "ذخیره شده (${savedIps.size})" else "Saved (${savedIps.size})",
                        icon = Icons.Default.Star,
                        selected = activeTab == 1,
                        modifier = Modifier.weight(1f),
                        onClick = { activeTab = 1 }
                    )
                }
            }



            // --- ANIMATED TAB NAVIGATION TRANSITION VIEW ---
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                AnimatedContent(
                    targetState = activeTab,
                    transitionSpec = {
                        if (targetState > initialState) {
                            (slideInHorizontally { width -> width } + fadeIn()).togetherWith(
                                slideOutHorizontally { width -> -width } + fadeOut())
                        } else {
                            (slideInHorizontally { width -> -width } + fadeIn()).togetherWith(
                                slideOutHorizontally { width -> width } + fadeOut())
                        }
                    },
                    label = "TabContentTransition"
                ) { targetTab ->
                    if (targetTab == 0) {
                        LiveScannerSection(
                            viewModel = viewModel,
                            scanUiState = scanUiState,
                            ipInputList = ipInputList,
                            activeScanResults = activeScanResults,
                            ipPoolSize = ipPoolSize,
                            configInput = configInput,
                            parsedConfig = parsedConfig,
                            customPort = customPort,
                            context = context,
                            isPersian = isPersian
                        )
                    } else {
                        SavedHistorySection(
                            viewModel = viewModel,
                            savedIps = savedIps,
                            parsedConfig = parsedConfig,
                            context = context,
                            isPersian = isPersian
                        )
                    }
                }
            }
        }

        // --- PROGRESS FLOATING CARD (WITH BEAUTIFUL BOUNCING FADE) ---
        AnimatedVisibility(
            visible = scanUiState is ScanUiState.Scanning,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            val sState = scanUiState as? ScanUiState.Scanning
            if (sState != null) {
                Card(
                    modifier = Modifier
                        .padding(bottom = 24.dp, start = 16.dp, end = 16.dp)
                        .fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    elevation = CardDefaults.cardElevation(16.dp),
                    shape = RoundedCornerShape(24.dp),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = Brush.linearGradient(listOf(AccentBlue, AccentCyan))
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = AccentCyan,
                                    strokeWidth = 2.5.dp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = if (isPersian) "در حال اسکن کاندیداها... (${sState.scanned}/${sState.total})" else "Scanning Candidates... (${sState.scanned}/${sState.total})",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                )
                            }
                            Text(
                                text = "${(sState.progressPercent * 100).toInt()}%",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Black,
                                    color = AccentCyan
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = sState.progressPercent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = AccentBlue,
                            trackColor = SurfaceVariantBg
                        )
                    }
                }
            }
        }

        // --- OUTLINED PREMIUM PARAMETERS CONFIG DIALOG ---
        if (showConfigDialog) {
            AlertDialog(
                onDismissRequest = { showConfigDialog = false },
                containerColor = CardBg,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.border(1.5.dp, AccentBlue, RoundedCornerShape(28.dp)),
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isPersian) "تنظیمات پارامترهای اسکن" else "Scan Configuration Parameters",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        )
                        IconButton(
                            onClick = { isPersian = !isPersian }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = "تغییر زبان / Toggle Language",
                                tint = AccentCyan,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column {
                            Text(
                                if (isPersian) "مشخصات کانفیگ VLESS / Trojan" else "VLESS / Trojan Configuration Profile",
                                style = MaterialTheme.typography.labelSmall.copy(color = AccentCyan, fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            TextField(
                                value = configInput,
                                onValueChange = {
                                    viewModel.updateConfigInput(it)
                                },
                                placeholder = {
                                    Text("vless://uuid@host:port?query#remark", style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary))
                                },
                                modifier = Modifier.fillMaxWidth().height(80.dp),
                                shape = RoundedCornerShape(16.dp),
                                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = SurfaceVariantBg,
                                    unfocusedContainerColor = SurfaceVariantBg,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                )
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            if (parsedConfig != null) {
                                Text(
                                    text = if (isPersian) "✓ معتبر: پروتکل ${parsedConfig.scheme.uppercase()} روی پورت ${parsedConfig.port} استخراج شد" else "✓ Valid: ${parsedConfig.scheme.uppercase()} configuration parsed on Port ${parsedConfig.port}",
                                    style = MaterialTheme.typography.bodySmall.copy(color = AccentTeal, fontWeight = FontWeight.Bold)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                when (isCloudflareConfig) {
                                    true -> {
                                        Text(
                                            text = if (isPersian) "☁️ این کانفیگ متعلق به کلودفلر (شبکه هدف) است." else "☁️ This configuration belongs to Cloudflare (target network).",
                                            style = MaterialTheme.typography.bodySmall.copy(color = AccentCyan, fontWeight = FontWeight.Bold)
                                        )
                                    }
                                    false -> {
                                        Text(
                                            text = if (isPersian) "⚠️ هشدار: آدرس کانفیگ متعلق به کلودفلر نیست!" else "⚠️ Warning: Config address does NOT belong to Cloudflare!",
                                            style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFF59E0B), fontWeight = FontWeight.Bold)
                                        )
                                    }
                                    null -> {
                                        Text(
                                            text = if (isPersian) "⏳ در حال بررسی تعلق آدرس کانفیگ به کلودفلر..." else "⏳ Checking address Cloudflare status...",
                                            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                                        )
                                    }
                                }
                            } else if (configInput.isNotEmpty()) {
                                Text(
                                    text = if (isPersian) "⚠️ فرمت نامعتبر! اسکن با پورت پیش‌فرض انجام می‌شود." else "⚠️ Invalid format! Scanning fallback to the main screen port.",
                                    style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFF43F5E), fontWeight = FontWeight.SemiBold)
                                )
                            } else {
                                Text(
                                    text = if (isPersian) "کانفیگ فعال را وارد کنید. در غیر این صورت اسکن روی پورت انتخابی خواهد بود." else "Insert active profile. Scanning fallback to the main screen port.",
                                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                                )
                            }
                        }

                        Column {
                            Text(
                                if (isPersian) "محدودیت زمان انتظار ارزیابی (میلی‌ثانیه)" else "Probe Timeout Limit (milliseconds)",
                                style = MaterialTheme.typography.labelSmall.copy(color = AccentCyan)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            TextField(
                                value = timeoutMs.toString(),
                                onValueChange = {
                                    val checked = it.toIntOrNull() ?: 1500
                                    viewModel.updateTimeout(checked)
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = SurfaceVariantBg,
                                    unfocusedContainerColor = SurfaceVariantBg,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                )
                            )
                        }

                        Column {
                            Text(
                                if (isPersian) "میزان همزمانی (حداکثر سوکت‌های موازی)" else "Concurrency Level (maximum parallel sockets)",
                                style = MaterialTheme.typography.labelSmall.copy(color = AccentCyan)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            TextField(
                                value = concurrency.toString(),
                                onValueChange = {
                                    val checked = it.toIntOrNull() ?: 15
                                    viewModel.updateConcurrency(checked)
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = SurfaceVariantBg,
                                    unfocusedContainerColor = SurfaceVariantBg,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                )
                            )
                        }

                        // Automatic Speed Test controls
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    if (isPersian) "تست سرعت خودکار" else "Automatic Speed Test",
                                    style = MaterialTheme.typography.labelMedium.copy(color = AccentCyan, fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    if (isPersian) "اندازه‌گیری و تخمین سرعت آی‌پی‌ها هنگام اسکن" else "Estimate speed of IPs during scan",
                                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                                )
                            }
                            Switch(
                                checked = isSpeedTestEnabled,
                                onCheckedChange = { viewModel.updateSpeedTestEnabled(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = AccentCyan,
                                    checkedTrackColor = AccentBlue,
                                    uncheckedThumbColor = TextSecondary,
                                    uncheckedTrackColor = SurfaceVariantBg
                                )
                            )
                        }


                    }
                },
                confirmButton = {
                    TextButton(onClick = { showConfigDialog = false }) {
                        Text(if (isPersian) "اعمال تغییرات" else "Apply Settings", color = AccentBlue, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    }
}

@Composable
fun TabButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val backgroundColor = if (selected) AccentBlue else Color.Transparent
    val contentColor = if (selected) Color.Black else TextSecondary

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(backgroundColor)
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = contentColor
                )
            )
        }
    }
}

@Composable
fun LiveScannerSection(
    viewModel: ScannerViewModel,
    scanUiState: ScanUiState,
    ipInputList: String,
    activeScanResults: List<ScanResult>,
    ipPoolSize: Int,
    configInput: String,
    parsedConfig: VlessTrojanConfig?,
    customPort: Int,
    context: Context,
    isPersian: Boolean = false
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // --- INPUT SEGMENT WITH MASSIVE CAPACITY ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CardBg),
            shape = RoundedCornerShape(24.dp),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = Brush.linearGradient(listOf(SurfaceVariantBg, AccentCyan))
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isPersian) "تنظیم استخر آی‌پی کلودفلر" else "Cloudflare IP Pool Setup",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = AccentCyan,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    
                    // Display details of current pools available
                    Text(
                        text = if (isPersian) "اندازه استخر: $ipPoolSize آی‌پی" else "Pool Size: $ipPoolSize IPs",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = AccentTeal,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
                
                Spacer(modifier = Modifier.height(10.dp))

                // --- 50,000+ CLOUDFLARE POOL GENERATOR SIZE PICKER ---
                Text(
                    text = if (isPersian) "اندازه جمعیت کاندیدای اسکن دقیق را پیکربندی کنید (پشتیبانی تا ۵۰ هزار+):" else "Configure exact scan candidate population size (supports up to 50k+):",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 11.sp),
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceVariantBg)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(100, 1000, 5000, 10000, 50000).forEach { size ->
                        val isSelected = ipPoolSize == size
                        val displayStr = if (size >= 1000) "${size / 1000}k" else size.toString()
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) AccentCyan else Color.Transparent)
                                .clickable { viewModel.updateIpPoolSize(size) }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = displayStr,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.Black else TextSecondary
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // --- TARGET PORT SELECTOR & PRIORITY INDICATORS according to User Request ---
                Text(
                    text = if (isPersian) "پورت هدف اسکن سوکت (در صورت تنظیم نشدن کانفیگ استفاده می‌شود):" else "Scanning Target Socket Port (Used if config is unset):",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 11.sp),
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (parsedConfig != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = SurfaceVariantBg.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, AccentTeal.copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Config Active Override",
                                    tint = AccentTeal,
                                    modifier = Modifier.size(16.dp)
                                )
                                Column {
                                    Text(
                                        text = if (isPersian) "تغییر پورت طبق کانفیگ فعال است" else "Config Port Override Active",
                                        style = MaterialTheme.typography.bodySmall.copy(color = AccentTeal, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                                    )
                                    Text(
                                        text = if (isPersian) "پورت اسکن: ${parsedConfig.port} از پروفایل فعلی ${parsedConfig.scheme.uppercase()} استخراج شد" else "Scanning Port: ${parsedConfig.port} extracted from current ${parsedConfig.scheme.uppercase()} profile",
                                        style = MaterialTheme.typography.bodySmall.copy(color = TextPrimary, fontSize = 10.sp)
                                    )
                                }
                            }
                        }
                    } else {
                        // User specifies custom port
                        listOf(443, 80, 8080, 2053).forEach { port ->
                            val isSelected = customPort == port
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) AccentBlue else SurfaceVariantBg)
                                    .clickable { viewModel.updateCustomPort(port) }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = port.toString(),
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color.Black else TextSecondary
                                    )
                                )
                            }
                        }

                        // Manual port dialog
                        var showPortChooserDialog by remember { mutableStateOf(false) }
                        val isDefault = customPort in listOf(443, 80, 8080, 2053)
                        Box(
                            modifier = Modifier
                                .weight(1.2f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (!isDefault) AccentBlue else SurfaceVariantBg)
                                .clickable { showPortChooserDialog = true }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (!isDefault) "$customPort ✎" else if (isPersian) "سایر پورت‌ها ✎" else "Other ✎",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (!isDefault) Color.Black else TextSecondary
                                )
                            )
                        }

                        if (showPortChooserDialog) {
                            AlertDialog(
                                onDismissRequest = { showPortChooserDialog = false },
                                containerColor = CardBg,
                                shape = RoundedCornerShape(20.dp),
                                title = { Text(if (isPersian) "وارد کردن پورت سفارشی" else "Enter Custom Port", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)) },
                                text = {
                                    TextField(
                                        value = customPort.toString(),
                                        onValueChange = {
                                            val p = it.toIntOrNull() ?: 443
                                            viewModel.updateCustomPort(p)
                                        },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = TextFieldDefaults.colors(
                                            focusedContainerColor = SurfaceVariantBg,
                                            unfocusedContainerColor = SurfaceVariantBg,
                                            focusedIndicatorColor = Color.Transparent,
                                            unfocusedIndicatorColor = Color.Transparent
                                        )
                                    )
                                },
                                confirmButton = {
                                    TextButton(onClick = { showPortChooserDialog = false }) {
                                        Text(if (isPersian) "ذخیره" else "Save", color = AccentBlue)
                                    }
                                }
                            )
                        }
                    }
                }

                TextField(
                    value = ipInputList,
                    onValueChange = { viewModel.updateInputList(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(84.dp)
                        .testTag("ip_input_field"),
                    textStyle = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        color = TextPrimary
                    ),
                    placeholder = {
                        Text(
                            text = if (isPersian) "آی‌پی‌ها/رنج‌های سفارشی را اینجا اضافه کنید یا اجازه دهید اسکنر استخر ساب‌نت کلودفلر را پویا گسترش دهد..." else "Add custom IPs/Ranges here or let the scanner expand Cloudflare subnet pool dynamically...",
                            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 11.sp)
                        )
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = SurfaceVariantBg,
                        unfocusedContainerColor = SurfaceVariantBg,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )
                
                Spacer(modifier = Modifier.height(12.dp))

                // Dual Status Play/Cancel Buttons with ultra-curved shape design
                if (scanUiState is ScanUiState.Scanning) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { },
                            modifier = Modifier
                                .weight(1.3f)
                                .height(46.dp)
                                .testTag("scanning_status_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SurfaceVariantBg,
                                contentColor = TextSecondary
                            ),
                            shape = RoundedCornerShape(24.dp),
                            enabled = false
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = AccentCyan,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isPersian) "در حال اسکن..." else "Running Scan...",
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Button(
                            onClick = { viewModel.stopScanning() },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("cancel_scan_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFE11D48),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Cancel Scan", tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isPersian) "لغو" else "Cancel",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                } else {
                    Button(
                        onClick = { viewModel.startScanning() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .testTag("start_scan_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentBlue,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isPersian) "شروع اسکن آی‌پی تمیز" else "Start Clean IP Scan",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- RESULTS TABLE HEADER ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isPersian) "آی‌پی‌های تمیز شناسایی شده (بر اساس تاخیر صعودی)" else "Detected Clean IPs (Latency Asc)",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            )
            if (activeScanResults.isNotEmpty()) {
                Text(
                    text = if (isPersian) "تعداد آی‌پی تمیز: ${activeScanResults.size}" else "Clean Count: ${activeScanResults.size}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = AccentCyan,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // --- IP RESULTS TABLE IN GLASSMORPHIC LIST ---
        if (activeScanResults.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(CardBg)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Large styled blue cloud logo shown nicely as empty state illustration
                Image(
                    painter = painterResource(id = R.drawable.img_blue_cloud),
                    contentDescription = "Cloud Logo Illustration",
                    modifier = Modifier
                        .size(100.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .border(1.dp, AccentCyan.copy(alpha = 0.5f), RoundedCornerShape(20.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = if (isPersian) "آماده برای اسکن استخر کلودفلر" else "Ready to Scan Cloudflare Pool",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isPersian) "برای پایش زیرشبکه‌های کاندیدای آی‌پی دکمه را فشار دهید" else "Press button to probe candidate IP subnets",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                    textAlign = TextAlign.Center
                )
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfaceVariantBg),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp, horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(if (isPersian) "آدرس آی‌پی" else "IP Address", modifier = Modifier.weight(1.5f), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = TextPrimary))
                    Text(if (isPersian) "تاخیر" else "Latency", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = TextPrimary), textAlign = TextAlign.Center)
                    Text(if (isPersian) "سرعت تخمینی" else "Est Speed", modifier = Modifier.weight(1.1f), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = TextPrimary), textAlign = TextAlign.End)
                    Spacer(modifier = Modifier.width(if (parsedConfig != null) 90.dp else 48.dp)) // Extra margin for active direct action buttons
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, SurfaceVariantBg, RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
                    .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
            ) {
                activeScanResults.forEach { result ->
                    ResultRow(
                        ip = result.ip,
                        latency = result.latency,
                        speed = result.speed,
                        config = parsedConfig,
                        isPersian = isPersian,
                        onRowClick = {
                            if (parsedConfig != null) {
                                val resolved = parsedConfig.copyWithIp(result.ip)
                                copyToClipboard(context, resolved, "your config copied with new ip!", viewModel)
                            } else {
                                copyToClipboard(context, result.ip, if (isPersian) "آدرس آی‌پی در حافظه موقت کپی شد" else "IP Address copied to clipboard", viewModel)
                            }
                        },
                        onCopyClick = {
                            copyToClipboard(context, result.ip, if (isPersian) "آدرس آی‌پی در حافظه موقت کپی شد" else "IP Address copied to clipboard", viewModel)
                        },
                        onCopyConfigClick = if (parsedConfig != null) {
                            {
                                val resolved = parsedConfig.copyWithIp(result.ip)
                                copyToClipboard(context, resolved, "your config copied with new ip!", viewModel)
                            }
                        } else null,
                        onManualSpeedTest = {
                            viewModel.runManualSpeedTest(result.ip)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun SavedHistorySection(
    viewModel: ScannerViewModel,
    savedIps: List<SavedIpEntity>,
    parsedConfig: VlessTrojanConfig?,
    context: Context,
    isPersian: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isPersian) "پایگاه داده آی‌پی‌های تمیز ذخیره شده" else "Cached Clean IPs Database",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            )
            if (savedIps.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = {
                            val copyText = savedIps.joinToString(separator = "\n") { it.ip }
                            copyToClipboard(context, copyText, if (isPersian) "همه آی‌پی‌های ذخیره شده در حافظه موقت کپی شدند!" else "All saved IPs copied to clipboard!", viewModel)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy all saved IPs",
                            tint = AccentCyan
                        )
                    }
                    IconButton(onClick = { viewModel.clearSavedHistory() }) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Clear database", tint = Color(0xFFF43F5E))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (savedIps.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(CardBg)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_blue_cloud),
                    contentDescription = "Cloud Logo Illustration",
                    modifier = Modifier
                        .size(100.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .border(1.2.dp, AccentCyan, RoundedCornerShape(20.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = if (isPersian) "پایگاه داده خالی است" else "Database is Empty",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    ),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isPersian) "آی‌پی‌های تمیز اسکن شده به طور خودکار در اینجا ذخیره و ماندگار می‌شوند" else "Scanned clean IPs will populate and persist here automatically",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                    textAlign = TextAlign.Center
                )
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfaceVariantBg),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp, horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(if (isPersian) "آی‌پی" else "IP", modifier = Modifier.weight(1.5f), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = TextPrimary))
                    Text(if (isPersian) "پینگ" else "Ping", modifier = Modifier.weight(1.5f), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = TextPrimary), textAlign = TextAlign.Center)
                    Text(if (isPersian) "سرعت" else "Speed", modifier = Modifier.weight(1.5f), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = TextPrimary), textAlign = TextAlign.End)
                    Spacer(modifier = Modifier.width(if (parsedConfig != null) 130.dp else 90.dp)) // Extra action buttons alignment offset
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .border(1.dp, SurfaceVariantBg, RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
                    .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
            ) {
                items(savedIps) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CardBg)
                            .clickable {
                                if (parsedConfig != null) {
                                    val resolved = parsedConfig.copyWithIp(item.ip)
                                    copyToClipboard(context, resolved, "your config copied with new ip!", viewModel)
                                } else {
                                    copyToClipboard(context, item.ip, if (isPersian) "آدرس آی‌پی در حافظه موقت کپی شد" else "IP Address copied to clipboard", viewModel)
                                }
                            }
                            .padding(vertical = 10.dp, horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = item.ip,
                            modifier = Modifier.weight(1.5f),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                color = TextPrimary,
                                fontWeight = FontWeight.Medium
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${item.latency} ms",
                            modifier = Modifier.weight(1.5f),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (item.latency < 100) AccentTeal else AccentBlue
                            ),
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = String.format(Locale.US, "%.1f MB/s", item.speed),
                            modifier = Modifier.weight(1.5f),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = AccentCyan,
                                fontWeight = FontWeight.SemiBold
                            ),
                            textAlign = TextAlign.End
                        )
                        
                        Row(
                            modifier = Modifier.width(if (parsedConfig != null) 130.dp else 90.dp),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (parsedConfig != null) {
                                IconButton(
                                    onClick = {
                                        val resolved = parsedConfig.copyWithIp(item.ip)
                                        copyToClipboard(context, resolved, "your config copied with new ip!", viewModel)
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clip(CircleShape)
                                            .background(AccentTeal.copy(alpha = 0.15f))
                                            .border(1.dp, AccentTeal, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "c",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = AccentTeal,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                lineHeight = 11.sp
                                            )
                                        )
                                    }
                                }
                            }
                            IconButton(
                                onClick = { copyToClipboard(context, item.ip, if (isPersian) "آدرس آی‌پی در حافظه موقت کپی شد" else "IP Address copied to clipboard", viewModel) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy IP",
                                    tint = AccentCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = { viewModel.deleteSavedIp(item) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ResultRow(
    ip: String,
    latency: Long,
    speed: Double,
    config: VlessTrojanConfig? = null,
    onRowClick: () -> Unit,
    onCopyClick: () -> Unit,
    onCopyConfigClick: (() -> Unit)? = null,
    isPersian: Boolean = false,
    onManualSpeedTest: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardBg)
            .clickable { onRowClick() }
            .padding(vertical = 10.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = ip,
            modifier = Modifier.weight(1.5f),
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                color = TextPrimary
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "${latency} ms",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold,
                color = if (latency < 100) AccentTeal else AccentBlue
            ),
            textAlign = TextAlign.Center
        )
        Box(
            modifier = Modifier.weight(1.1f),
            contentAlignment = Alignment.CenterEnd
        ) {
            if (speed == -1.0) {
                Text(
                    text = if (isPersian) "درحال بررسی..." else "Testing...",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = AccentTeal,
                        fontWeight = FontWeight.Normal,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    ),
                    textAlign = TextAlign.End
                )
            } else if (speed > 0.0) {
                Text(
                    text = String.format(Locale.US, "%.1f MB/s", speed),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = AccentCyan,
                        fontWeight = FontWeight.SemiBold
                    ),
                    textAlign = TextAlign.End
                )
            } else {
                Text(
                    text = if (isPersian) "تست سرعت" else "Test Speed",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = AccentTeal,
                        fontWeight = FontWeight.Bold,
                        textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
                    ),
                    modifier = Modifier
                        .clickable { onManualSpeedTest?.invoke() }
                        .padding(vertical = 4.dp, horizontal = 8.dp),
                    textAlign = TextAlign.End
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Row(
            modifier = Modifier.width(if (config != null) 90.dp else 48.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (config != null && onCopyConfigClick != null) {
                IconButton(
                    onClick = onCopyConfigClick,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(AccentTeal.copy(alpha = 0.15f))
                            .border(1.dp, AccentTeal, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "c",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = AccentTeal,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                lineHeight = 11.sp
                            )
                        )
                    }
                }
            }
            IconButton(
                onClick = onCopyClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy IP",
                    tint = AccentCyan,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun LiveActivityNotificationBar(
    viewModel: ScannerViewModel,
    isPersian: Boolean,
    modifier: Modifier = Modifier
) {
    val liveEvents by viewModel.liveEvents.collectAsState()
    val latestEvent = liveEvents.lastOrNull()
    var showLogDialog by remember { mutableStateOf(false) }

    AnimatedVisibility(
        visible = latestEvent != null,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        modifier = modifier
    ) {
        latestEvent?.let { event ->
            // Pick icon and color scheme based on event type
            val (icon, tint) = when (event.type) {
                LiveEventType.SCAN -> Pair(Icons.Default.Search, AccentCyan)
                LiveEventType.DATABASE -> Pair(Icons.Default.Star, AccentTeal)
                LiveEventType.COPY -> Pair(Icons.Default.ContentCopy, AccentBlue)
                LiveEventType.SUCCESS -> Pair(Icons.Default.CheckCircle, AccentGreen)
                LiveEventType.ERROR -> Pair(Icons.Default.Warning, Color(0xFFF43F5E))
                else -> Pair(Icons.Default.Info, AccentCyan)
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clickable { showLogDialog = true },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = tint.copy(alpha = 0.08f)
                ),
                border = BorderStroke(1.dp, tint.copy(alpha = 0.25f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = "Event type",
                        tint = tint,
                        modifier = Modifier.size(18.dp)
                    )
                    
                    // Display scrolling or sliding text animation
                    AnimatedContent(
                        targetState = event,
                        transitionSpec = {
                            (slideInVertically { height -> height } + fadeIn()).togetherWith(
                                slideOutVertically { height -> -height } + fadeOut()
                            )
                        },
                        modifier = Modifier.weight(1f),
                        label = "ActivityText"
                    ) { activeEvent ->
                        Text(
                            text = if (isPersian) activeEvent.messageFa else activeEvent.messageEn,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Pulse badge for "LIVE"
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(tint.copy(alpha = 0.18f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (isPersian) "زنده" else "LIVE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = tint,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        )
                    }

                    // History log button
                    Icon(
                        imageVector = Icons.Default.List,
                        contentDescription = "Open Logs",
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }

    if (showLogDialog) {
        val reversedLogs = remember(liveEvents) { liveEvents.reversed() }
        AlertDialog(
            onDismissRequest = { showLogDialog = false },
            containerColor = CardBg,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .border(1.5.dp, AccentBlue, RoundedCornerShape(24.dp)),
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isPersian) "گزارش عملیات زنده" else "Live Operations Log",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )
                    IconButton(
                        onClick = { 
                            viewModel.logEvent("Cleared logging session", "جلسه گزارشات پاکسازی شد", LiveEventType.INFO)
                            viewModel.logEvent("Session restarted", "شروع مجدد گزارشات", LiveEventType.INFO)
                            showLogDialog = false 
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Clear logs",
                            tint = Color(0xFFF43F5E),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            },
            text = {
                Box(modifier = Modifier.height(300.dp)) {
                    if (reversedLogs.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isPersian) "هیچ گزارشی ثبت نشده است" else "No operations logged yet",
                                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(reversedLogs, key = { it.id }) { log ->
                                val (icon, tint) = when (log.type) {
                                    LiveEventType.SCAN -> Pair(Icons.Default.Search, AccentCyan)
                                    LiveEventType.DATABASE -> Pair(Icons.Default.Star, AccentTeal)
                                    LiveEventType.COPY -> Pair(Icons.Default.ContentCopy, AccentBlue)
                                    LiveEventType.SUCCESS -> Pair(Icons.Default.CheckCircle, AccentGreen)
                                    LiveEventType.ERROR -> Pair(Icons.Default.Warning, Color(0xFFF43F5E))
                                    else -> Pair(Icons.Default.Info, AccentCyan)
                                }
                                val sdf = remember { java.text.SimpleDateFormat("HH:mm:ss", Locale.US) }
                                val logTime = remember(log.timestamp) { sdf.format(Date(log.timestamp)) }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(tint.copy(alpha = 0.05f))
                                        .border(1.dp, tint.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = tint,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (isPersian) log.messageFa else log.messageEn,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = TextPrimary,
                                                fontWeight = FontWeight.Medium,
                                                fontSize = 11.sp
                                            )
                                        )
                                        Text(
                                            text = logTime,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = TextSecondary,
                                                fontSize = 9.sp
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLogDialog = false }) {
                    Text(if (isPersian) "بستن" else "Close", color = AccentBlue, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

fun copyToClipboard(context: Context, text: String, toastMessage: String = "IP Address copied to clipboard", viewModel: ScannerViewModel? = null) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("IP Address", text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show()
    
    if (viewModel != null) {
        val ipTruncated = if (text.length > 35) text.take(35) + "..." else text
        viewModel.logEvent(
            "Copied to clipboard: $ipTruncated",
            "در حافظه موقت کپی شد: $ipTruncated",
            LiveEventType.COPY
        )
    }
}

fun exportApkToDownloads(context: Context, viewModel: ScannerViewModel? = null) {
    try {
        val srcFile = File(context.applicationInfo.sourceDir)
        if (!srcFile.exists()) {
            Toast.makeText(context, "فایل منبع یافت نشد", Toast.LENGTH_SHORT).show()
            viewModel?.logEvent("Failed to locate application source package", "فایل نصب منبع برنامه یافت نشد", LiveEventType.ERROR)
            return
        }
        val fileName = "IP_Finder_Universal.apk"
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/vnd.android.package-archive")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                resolver.openOutputStream(uri).use { outputStream ->
                    if (outputStream != null) {
                        FileInputStream(srcFile).use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                }
                Toast.makeText(context, "فایل APK یونیورسال با موفقیت در پوشه دانلودها ذخیره شد\n$fileName", Toast.LENGTH_LONG).show()
                viewModel?.logEvent(
                    "Exported Universal APK successfully: $fileName",
                    "فایل نصبی یونیورسال برنامه با موفقیت در پوشه دانلودها ثبت شد: $fileName",
                    LiveEventType.SUCCESS
                )
            } else {
                Toast.makeText(context, "خطا در ساخت فایل در پوشه دانلودها", Toast.LENGTH_SHORT).show()
                viewModel?.logEvent("Failed to create file within public Downloads folder", "خطا در هنگام ایجاد دسترسی فایل درون پوشه دانلودها", LiveEventType.ERROR)
            }
        } else {
            @Suppress("DEPRECATION")
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs()
            }
            val destFile = File(downloadsDir, fileName)
            FileInputStream(srcFile).use { inputStream ->
                FileOutputStream(destFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            Toast.makeText(context, "فایل APK یونیورسال با موفقیت در پوشه دانلودها ذخیره شد\n$fileName", Toast.LENGTH_LONG).show()
            viewModel?.logEvent(
                "Exported Universal APK successfully to Downloads folder: $fileName",
                "فایل نصبی یونیورسال برنامه به عنوان بسته خروجی دانلود شد: $fileName",
                LiveEventType.SUCCESS
            )
        }
    } catch (e: Exception) {
        Toast.makeText(context, "خطا در ذخیره کردن فایل: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        viewModel?.logEvent(
            "Encountered error while preparing installer APK export: ${e.localizedMessage}",
            "خطا در زمان خروجی گرفتن بسته نصبی برنامه: ${e.localizedMessage}",
            LiveEventType.ERROR
        )
    }
}
