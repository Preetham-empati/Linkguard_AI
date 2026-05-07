package com.example.trustsphere

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.example.trustsphere.data.local.ScanHistoryEntity
import com.example.trustsphere.data.model.AppRisk
import com.example.trustsphere.service.TrustMonitoringService
import com.example.trustsphere.ui.MainViewModel
import com.example.trustsphere.ui.QrScannerView
import com.example.trustsphere.ui.VerificationStatus
import com.example.trustsphere.ui.theme.TrustsphereTheme
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private var clipboardListener: ClipboardManager.OnPrimaryClipChangedListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        setupClipboardMonitoring()

        setContent {
            TrustsphereTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val status by viewModel.verificationState.collectAsState()
                    val history by viewModel.scanHistory.collectAsState()
                    val riskyApps by viewModel.riskyApps
                    val isUnsafe = intent?.getBooleanExtra("EXTRA_UNSAFE_ENVIRONMENT", false) ?: false
                    val isAlreadyVerified = intent?.getBooleanExtra("EXTRA_ALREADY_VERIFIED", false) ?: false
                    val interceptedUrl = intent?.dataString

                    var currentTab by remember { mutableStateOf(0) }
                    
                    var hasCameraPermission by remember {
                        mutableStateOf(
                            ContextCompat.checkSelfPermission(
                                this@MainActivity,
                                Manifest.permission.CAMERA
                            ) == PackageManager.PERMISSION_GRANTED
                        )
                    }

                    val launcher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestPermission(),
                        onResult = { granted ->
                            hasCameraPermission = granted
                        }
                    )

                    when {
                        isUnsafe -> {
                            UnsafeEnvironmentScreen { finish() }
                        }
                        isAlreadyVerified && interceptedUrl != null -> {
                            SafeLinkScreen(url = interceptedUrl, onContinue = { openInBrowser(interceptedUrl) })
                        }
                        status is VerificationStatus.Loading -> {
                            LoadingScreen()
                        }
                        status is VerificationStatus.Safe -> {
                            val safeStatus = status as VerificationStatus.Safe
                            LaunchedEffect(safeStatus.url) {
                                openInBrowser(safeStatus.url)
                            }
                            SafeLinkScreen(url = safeStatus.url, onContinue = { openInBrowser(safeStatus.url) })
                        }
                        status is VerificationStatus.Unsafe -> {
                            val unsafeStatus = status as VerificationStatus.Unsafe
                            WarningScreen(
                                message = unsafeStatus.message,
                                onBack = { viewModel.resetState() },
                                onContinueAnyway = {
                                    interceptedUrl?.let { openInBrowser(it) }
                                }
                            )
                        }
                        status is VerificationStatus.Error -> {
                            val errorStatus = status as VerificationStatus.Error
                            ErrorScreen(error = errorStatus.message) {
                                viewModel.resetState()
                            }
                        }
                        else -> {
                            Scaffold(
                                bottomBar = {
                                    NavigationBar {
                                        NavigationBarItem(
                                            selected = currentTab == 0,
                                            onClick = { currentTab = 0 },
                                            icon = { Icon(Icons.Default.Dashboard, contentDescription = null) },
                                            label = { Text("Dashboard") }
                                        )
                                        NavigationBarItem(
                                            selected = currentTab == 1,
                                            onClick = { currentTab = 1 },
                                            icon = { Icon(Icons.Default.QrCodeScanner, contentDescription = null) },
                                            label = { Text("Scanner") }
                                        )
                                        NavigationBarItem(
                                            selected = currentTab == 2,
                                            onClick = { 
                                                currentTab = 2
                                                viewModel.auditApps() 
                                            },
                                            icon = { Icon(Icons.Default.SecurityUpdateWarning, contentDescription = null) },
                                            label = { Text("Audit") }
                                        )
                                    }
                                }
                            ) { padding ->
                                Box(modifier = Modifier.padding(padding)) {
                                    when (currentTab) {
                                        0 -> MainDashboard(
                                            isAccessibilityEnabled = isAccessibilityServiceEnabled(this@MainActivity, TrustMonitoringService::class.java),
                                            history = history,
                                            onEnableAccessibility = { openAccessibilitySettings() },
                                            onClearHistory = { viewModel.clearHistory() }
                                        )
                                        1 -> {
                                            if (hasCameraPermission) {
                                                QrScannerView(onUrlDetected = { url ->
                                                    viewModel.verifyLink(url)
                                                })
                                            } else {
                                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                        Text("Camera permission is required for the scanner.")
                                                        Spacer(modifier = Modifier.height(16.dp))
                                                        Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) {
                                                            Text("Grant Permission")
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        2 -> AppAuditScreen(riskyApps = riskyApps)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun setupClipboardMonitoring() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
            val clipData = clipboard.primaryClip
            if (clipData != null && clipData.itemCount > 0) {
                val text = clipData.getItemAt(0).text?.toString() ?: ""
                if (text.startsWith("http://") || text.startsWith("https://")) {
                    Toast.makeText(this, "TrustSphere: Link copied. Open app to scan!", Toast.LENGTH_LONG).show()
                }
            }
        }
        clipboard.addPrimaryClipChangedListener(clipboardListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        clipboardListener?.let {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.removePrimaryClipChangedListener(it)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val interceptedUrl = intent?.dataString
        val isAlreadyVerified = intent?.getBooleanExtra("EXTRA_ALREADY_VERIFIED", false) ?: false
        
        if (interceptedUrl != null && !isAlreadyVerified) {
            viewModel.verifyLink(interceptedUrl)
        }
    }

    private fun openInBrowser(url: String) {
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("EXTRA_ALREADY_VERIFIED", true)
        }
        
        val pkgManager = packageManager
        val dummyIntent = Intent(Intent.ACTION_VIEW, Uri.parse("http://"))
        val activities = pkgManager.queryIntentActivities(dummyIntent, PackageManager.MATCH_ALL)
        
        val otherBrowser = activities.find { it.activityInfo.packageName != packageName }
        
        if (otherBrowser != null) {
            browserIntent.setPackage(otherBrowser.activityInfo.packageName)
        }

        try {
            startActivity(browserIntent)
        } catch (e: Exception) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
    }

    private fun isAccessibilityServiceEnabled(context: Context, service: Class<out AccessibilityService>): Boolean {
        val expectedComponentName = ComponentName(context, service)
        val enabledServicesSetting = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServicesSetting)
        while (colonSplitter.hasNext()) {
            val componentNameString = colonSplitter.next()
            val enabledService = ComponentName.unflattenFromString(componentNameString)
            if (enabledService != null && enabledService == expectedComponentName) return true
        }
        return false
    }
}

@Composable
fun AppAuditScreen(riskyApps: List<AppRisk>) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("App Risk Audit", style = MaterialTheme.typography.headlineMedium)
        Text("Checking for apps with sensitive permissions.", color = Color.Gray, fontSize = 14.sp)
        
        Spacer(modifier = Modifier.height(16.dp))
        
        if (riskyApps.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No risky third-party apps found.")
            }
        } else {
            LazyColumn {
                items(riskyApps) { app ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (app.isHighRisk) Color(0xFFFFEBEE) else Color(0xFFFFFDE7)
                        )
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            app.icon?.let {
                                Image(
                                    bitmap = it.toBitmap().asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(app.appName, fontWeight = FontWeight.Bold)
                                app.riskyPermissions.forEach { perm ->
                                    Text("• $perm", fontSize = 12.sp, color = Color.DarkGray)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MainDashboard(
    isAccessibilityEnabled: Boolean,
    history: List<ScanHistoryEntity>,
    onEnableAccessibility: () -> Unit,
    onClearHistory: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (isAccessibilityEnabled) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isAccessibilityEnabled) Icons.Default.Security else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (isAccessibilityEnabled) Color(0xFF4CAF50) else Color(0xFFF44336)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = if (isAccessibilityEnabled) "Protection is Active" else "Protection is Inactive",
                        fontWeight = FontWeight.Bold,
                        color = if (isAccessibilityEnabled) Color(0xFF2E7D32) else Color(0xFFC62828)
                    )
                    if (!isAccessibilityEnabled) {
                        TextButton(onClick = onEnableAccessibility, contentPadding = PaddingValues(0.dp)) {
                            Text("Enable Now", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // History Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Scan History", style = MaterialTheme.typography.titleLarge)
            if (history.isNotEmpty()) {
                IconButton(onClick = onClearHistory) {
                    Icon(Icons.Default.Delete, contentDescription = "Clear", tint = Color.Gray)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (history.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No recent scans", color = Color.Gray)
            }
        } else {
            LazyColumn {
                items(history) { item ->
                    HistoryItem(item)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), thickness = 0.5.dp, color = Color.LightGray)
                }
            }
        }
    }
}

@Composable
fun HistoryItem(item: ScanHistoryEntity) {
    val dateFormat = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (item.isSafe) Icons.Default.CheckCircle else Icons.Default.Error,
            contentDescription = null,
            tint = if (item.isSafe) Color(0xFF4CAF50) else Color(0xFFF44336),
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.url,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = dateFormat.format(Date(item.timestamp)),
                fontSize = 12.sp,
                color = Color.Gray
            )
        }
    }
}

@Composable
fun SafeLinkScreen(url: String, onContinue: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = "Safe",
            tint = Color(0xFF4CAF50),
            modifier = Modifier.size(100.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text("Link is Safe", style = MaterialTheme.typography.headlineMedium, color = Color(0xFF4CAF50))
        Spacer(modifier = Modifier.height(16.dp))
        Text(url, textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = Color.Gray)
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onContinue, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))) {
            Text("Proceed to Website")
        }
    }
}

@Composable
fun UnsafeEnvironmentScreen(onClose: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = "Unsafe",
            tint = Color.Red,
            modifier = Modifier.size(120.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text("UNSAFE ENVIRONMENT", color = Color.Red, fontWeight = FontWeight.Bold, fontSize = 28.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Security risks detected on this device. Please secure your device before making any payments.", textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onClose, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) {
            Text("I Understand", color = Color.White)
        }
    }
}

@Composable
fun LoadingScreen() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(16.dp))
        Text("Scanning link for phishing risks...")
    }
}

@Composable
fun WarningScreen(message: String, onBack: () -> Unit, onContinueAnyway: () -> Unit) {
    var showConfirmation by remember { mutableStateOf(false) }

    if (showConfirmation) {
        AlertDialog(
            onDismissRequest = { showConfirmation = false },
            title = { Text("Are you sure?") },
            text = { Text("This link is highly suspicious. Continuing could lead to identity theft or financial loss. Do you still want to proceed?") },
            confirmButton = {
                TextButton(onClick = onContinueAnyway) {
                    Text("YES, PROCEED", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmation = false }) {
                    Text("CANCEL")
                }
            }
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(imageVector = Icons.Default.Warning, contentDescription = "Warning", tint = Color.Red, modifier = Modifier.size(100.dp))
        Spacer(modifier = Modifier.height(24.dp))
        Text(text = "DANGER: Malicious Link Detected", color = Color.Red, fontWeight = FontWeight.Bold, fontSize = 22.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = message, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onBack, colors = ButtonDefaults.buttonColors(containerColor = Color.Red), modifier = Modifier.fillMaxWidth()) {
            Text("Back to Safety", color = Color.White)
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(onClick = { showConfirmation = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Continue Anyway", color = Color.Red)
        }
    }
}

@Composable
fun ErrorScreen(error: String, onClose: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Error", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = error)
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onClose) {
            Text("Close")
        }
    }
}