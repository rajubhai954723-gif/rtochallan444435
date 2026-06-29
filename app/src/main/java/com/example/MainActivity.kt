package com.example

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.verticalScroll
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.database.AppDatabase
import com.example.data.database.LogEntity
import com.example.data.database.RuleEntity
import com.example.data.repository.SmsRepository
import com.example.ui.SmsViewModel
import com.example.ui.theme.MyApplicationTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val receiveGranted = permissions[Manifest.permission.RECEIVE_SMS] ?: false
        val readGranted = permissions[Manifest.permission.READ_SMS] ?: false
        if (receiveGranted && readGranted) {
            Toast.makeText(this, "SMS Interception permissions granted!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "SMS permissions are required to auto-forward real SMS messages.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Verify/Request permissions on startup
        checkAndRequestSmsPermissions()

        val db = AppDatabase.getInstance(applicationContext)
        val repository = SmsRepository(db.ruleDao(), db.logDao())

        setContent {
            MyApplicationTheme {
                val smsViewModel: SmsViewModel = viewModel(
                    factory = SmsViewModel.Factory(application, repository)
                )
                SmsAppScreen(
                    viewModel = smsViewModel,
                    onRequestPermissions = { checkAndRequestSmsPermissions() }
                )
            }
        }
    }

    private fun checkAndRequestSmsPermissions() {
        val receiveSms = ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS)
        val readSms = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS)
        
        if (receiveSms != PackageManager.PERMISSION_GRANTED || readSms != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.RECEIVE_SMS,
                    Manifest.permission.READ_SMS
                )
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsAppScreen(
    viewModel: SmsViewModel,
    onRequestPermissions: () -> Unit
) {
    val isLocked by viewModel.isLocked.collectAsStateWithLifecycle()
    val isPasscodeSet by viewModel.isPasscodeSet.collectAsStateWithLifecycle()

    if (isLocked && isPasscodeSet) {
        PasscodeLockScreen(viewModel = viewModel)
    } else {
        MainAppContent(
            viewModel = viewModel,
            onRequestPermissions = onRequestPermissions
        )
    }
}

@Composable
fun PasscodeLockScreen(viewModel: SmsViewModel) {
    var codeInput by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.background
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Admin Lock",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(64.dp)
                        .padding(bottom = 12.dp)
                )

                Text(
                    text = "SMS Admin Panel",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "Enter 4-digit passcode",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
                )

                // PIN input dots
                Row(
                    modifier = Modifier.padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    repeat(4) { index ->
                        val active = index < codeInput.length
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(
                                    if (active) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                )
                        )
                    }
                }

                if (errorMsg != null) {
                    Text(
                        text = errorMsg ?: "",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }

                // Grid of numeric buttons
                val numbers = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("Clear", "0", "OK")
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    for (row in numbers) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            for (item in row) {
                                KeypadButton(
                                    text = item,
                                    onClick = {
                                        when (item) {
                                            "Clear" -> {
                                                if (codeInput.isNotEmpty()) {
                                                    codeInput = codeInput.dropLast(1)
                                                    errorMsg = null
                                                }
                                            }
                                            "OK" -> {
                                                if (codeInput.length == 4) {
                                                    val success = viewModel.verifyPasscode(codeInput)
                                                    if (!success) {
                                                        errorMsg = "Incorrect passcode. Please try again."
                                                        codeInput = ""
                                                    }
                                                } else {
                                                    errorMsg = "Enter exactly 4 digits"
                                                }
                                            }
                                            else -> {
                                                if (codeInput.length < 4) {
                                                    codeInput += item
                                                    errorMsg = null
                                                }
                                            }
                                        }
                                    }
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
fun KeypadButton(
    text: String,
    onClick: () -> Unit
) {
    val isAction = text == "Clear" || text == "OK"
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isAction) MaterialTheme.colorScheme.secondaryContainer 
                             else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (isAction) MaterialTheme.colorScheme.onSecondaryContainer 
                           else MaterialTheme.colorScheme.onSurfaceVariant
        ),
        modifier = Modifier
            .size(72.dp)
            .testTag("keypad_${text.lowercase()}"),
        shape = RoundedCornerShape(18.dp),
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(
            text = text,
            fontSize = if (isAction) 13.sp else 22.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppContent(
    viewModel: SmsViewModel,
    onRequestPermissions: () -> Unit
) {
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val isPasscodeSet by viewModel.isPasscodeSet.collectAsStateWithLifecycle()
    val globalEnabled by viewModel.globalForwardingEnabled.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "App Icon",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "SMS Forwarder",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }
                },
                actions = {
                    // Global Status Indicator
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (globalEnabled) Color(0xFF4CAF50) else Color(0xFFF44336))
                        )
                        Text(
                            text = if (globalEnabled) "ACTIVE" else "PAUSED",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (globalEnabled) Color(0xFF4CAF50) else Color(0xFFF44336)
                        )
                    }

                    if (isPasscodeSet) {
                        IconButton(onClick = { viewModel.lockApp() }) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Lock Admin Screen"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                )
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentTab == SmsViewModel.Tab.DASHBOARD,
                    onClick = { viewModel.setTab(SmsViewModel.Tab.DASHBOARD) },
                    icon = { Icon(imageVector = Icons.Default.Home, contentDescription = "Dashboard") },
                    label = { Text("Dashboard") }
                )
                NavigationBarItem(
                    selected = currentTab == SmsViewModel.Tab.RULES,
                    onClick = { viewModel.setTab(SmsViewModel.Tab.RULES) },
                    icon = { Icon(imageVector = Icons.Default.Settings, contentDescription = "Rules") },
                    label = { Text("Rules") }
                )
                NavigationBarItem(
                    selected = currentTab == SmsViewModel.Tab.LOGS,
                    onClick = { viewModel.setTab(SmsViewModel.Tab.LOGS) },
                    icon = { Icon(imageVector = Icons.Default.List, contentDescription = "Logs") },
                    label = { Text("Logs") }
                )
                NavigationBarItem(
                    selected = currentTab == SmsViewModel.Tab.INTEGRATIONS,
                    onClick = { viewModel.setTab(SmsViewModel.Tab.INTEGRATIONS) },
                    icon = { Icon(imageVector = Icons.Default.Info, contentDescription = "Integrations") },
                    label = { Text("Setup") }
                )
            }
        },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (currentTab) {
                SmsViewModel.Tab.DASHBOARD -> DashboardTab(viewModel = viewModel, onRequestPermissions = onRequestPermissions)
                SmsViewModel.Tab.RULES -> RulesTab(viewModel = viewModel)
                SmsViewModel.Tab.LOGS -> LogsTab(viewModel = viewModel)
                SmsViewModel.Tab.INTEGRATIONS -> IntegrationsTab(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun DashboardTab(
    viewModel: SmsViewModel,
    onRequestPermissions: () -> Unit
) {
    val context = LocalContext.current
    val globalEnabled by viewModel.globalForwardingEnabled.collectAsStateWithLifecycle()
    val battery by viewModel.batteryLevel.collectAsStateWithLifecycle()
    val connection by viewModel.connectionType.collectAsStateWithLifecycle()
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()

    // Permission checks
    val permissionGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

    // Launch tester input state
    var mockSender by remember { mutableStateOf("+15551234567") }
    var mockMessage by remember { mutableStateOf("Your code is 482901. Valid for 10 minutes.") }

    LaunchedEffect(Unit) {
        viewModel.refreshDeviceStatus()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Warning if SMS permissions are not granted
        if (!permissionGranted) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Warning",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "SMS Permissions Missing",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "The app cannot capture real incoming SMS messages without permissions. Click below to grant them.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = onRequestPermissions,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Grant Permissions", color = Color.White)
                        }
                    }
                }
            }
        }

        // Global Activation Card
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (globalEnabled) MaterialTheme.colorScheme.primaryContainer 
                                     else MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (globalEnabled) "SMS Interception Active" else "SMS Interception Paused",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = if (globalEnabled) MaterialTheme.colorScheme.onPrimaryContainer 
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (globalEnabled) "Listening and forwarding incoming messages..." 
                                   else "Interception stopped. Messages will not be forwarded.",
                            fontSize = 12.sp,
                            color = if (globalEnabled) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                    Switch(
                        checked = globalEnabled,
                        onCheckedChange = { viewModel.setGlobalForwarding(it) },
                        modifier = Modifier.testTag("global_toggle")
                    )
                }
            }
        }

        // Hardware / Connection Status Grid
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Battery status
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Battery",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Device", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("$battery% Battery", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("SIM: Online", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                    }
                }

                // Network connection status
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Network",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Network", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(connection, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("Ping: Connected", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                    }
                }
            }
        }

        // Summary Statistics Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Forwarding Overview",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("${rules.size}", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                            Text("Total Rules", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                        }
                        Column {
                            Text("${rules.count { it.isActive }}", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text("Active Rules", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                        }
                        Column {
                            Text("${logs.count { it.status == "SUCCESS" }}", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
                            Text("Sent Logs", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                        }
                        Column {
                            Text("${logs.count { it.status == "FAILED" }}", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                            Text("Failures", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                        }
                    }
                }
            }
        }

        // Tester Playground
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "⚡ Incoming SMS Emulator (Test Playground)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Manually simulate receiving an incoming SMS text to test Webhook, Telegram, Slack, or Discord forwarding in real time.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedTextField(
                        value = mockSender,
                        onValueChange = { mockSender = it },
                        label = { Text("Sender Phone Number") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("mock_sender_input"),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedTextField(
                        value = mockMessage,
                        onValueChange = { mockMessage = it },
                        label = { Text("Message Content") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("mock_message_input"),
                        minLines = 2
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Button(
                        onClick = {
                            if (mockSender.isBlank() || mockMessage.isBlank()) {
                                Toast.makeText(context, "Please fill in sender and message", Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.triggerMockSms(mockSender, mockMessage)
                                Toast.makeText(context, "Mock SMS Triggered! Check Logs tab.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("trigger_mock_button")
                    ) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Run")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Trigger Simulated Incoming SMS")
                    }
                }
            }
        }
    }
}

@Composable
fun RulesTab(viewModel: SmsViewModel) {
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<RuleEntity?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (rules.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "No Rules",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No Forwarding Rules Defined",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Forward rules allow you to redirect messages containing specific keywords or sender numbers to Webhooks, Telegram, Slack, etc.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.testTag("add_first_rule_button")
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Add")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Create First Rule")
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = "Forwarding Routing Rules",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                items(rules) { rule ->
                    RuleCard(
                        rule = rule,
                        onEdit = { editingRule = rule },
                        onToggle = { isActive ->
                            viewModel.updateRule(rule.copy(isActive = isActive))
                        },
                        onDelete = {
                            viewModel.deleteRule(rule)
                        }
                    )
                }
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }

            FloatingActionButton(
                onClick = { showAddDialog = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp)
                    .testTag("add_rule_fab"),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Rule")
            }
        }

        if (showAddDialog) {
            RuleEditDialog(
                rule = null,
                onDismiss = { showAddDialog = false },
                onSave = { newRule ->
                    viewModel.addRule(newRule)
                    showAddDialog = false
                }
            )
        }

        if (editingRule != null) {
            RuleEditDialog(
                rule = editingRule,
                onDismiss = { editingRule = null },
                onSave = { updatedRule ->
                    viewModel.updateRule(updatedRule)
                    editingRule = null
                }
            )
        }
    }
}

@Composable
fun RuleCard(
    rule: RuleEntity,
    onEdit: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("rule_card_${rule.id}"),
        colors = CardDefaults.cardColors(
            containerColor = if (rule.isActive) MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                             else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    val destIcon = when (rule.destinationType) {
                        "WEBHOOK" -> Icons.Default.Refresh
                        "TELEGRAM" -> Icons.Default.Send
                        "DISCORD" -> Icons.Default.Share
                        "SLACK" -> Icons.Default.Favorite
                        "EMAIL" -> Icons.Default.Email
                        else -> Icons.Default.Info
                    }
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = destIcon,
                            contentDescription = rule.destinationType,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column {
                        Text(
                            text = rule.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "To ${rule.destinationType}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Switch(
                        checked = rule.isActive,
                        onCheckedChange = onToggle,
                        modifier = Modifier.scale(0.85f).testTag("rule_toggle_${rule.id}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            Spacer(modifier = Modifier.height(12.dp))

            // Filtering info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Sender Filters", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = if (rule.senderPattern.isBlank() || rule.senderPattern == "*") "Any Sender" else rule.senderPattern,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Keyword Match", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = if (rule.keywordPattern.isBlank() || rule.keywordPattern == "*") "Any Message" else rule.keywordPattern,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Actions row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onEdit,
                    modifier = Modifier.testTag("rule_edit_btn_${rule.id}")
                ) {
                    Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Edit", fontSize = 13.sp)
                }
                Spacer(modifier = Modifier.width(4.dp))
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("rule_delete_btn_${rule.id}")
                ) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Delete", fontSize = 13.sp)
                }
            }
        }
    }
}

// Helper to handle layout divisions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleEditDialog(
    rule: RuleEntity?,
    onDismiss: () -> Unit,
    onSave: (RuleEntity) -> Unit
) {
    val isEdit = rule != null
    var name by remember { mutableStateOf(rule?.name ?: "") }
    var senderPattern by remember { mutableStateOf(rule?.senderPattern ?: "") }
    var keywordPattern by remember { mutableStateOf(rule?.keywordPattern ?: "") }
    var destinationType by remember { mutableStateOf(rule?.destinationType ?: "WEBHOOK") }

    // Destination config states
    var webhookUrl by remember { mutableStateOf(rule?.webhookUrl ?: "") }
    var telegramToken by remember { mutableStateOf(rule?.telegramToken ?: "") }
    var telegramChatId by remember { mutableStateOf(rule?.telegramChatId ?: "") }
    var discordWebhookUrl by remember { mutableStateOf(rule?.discordWebhookUrl ?: "") }
    var slackWebhookUrl by remember { mutableStateOf(rule?.slackWebhookUrl ?: "") }
    var emailHost by remember { mutableStateOf(rule?.emailHost ?: "smtp.gmail.com") }
    var emailPort by remember { mutableStateOf(rule?.emailPort ?: "587") }
    var emailUser by remember { mutableStateOf(rule?.emailUser ?: "") }
    var emailPass by remember { mutableStateOf(rule?.emailPass ?: "") }
    var emailTo by remember { mutableStateOf(rule?.emailTo ?: "") }

    var expandedDropdown by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEdit) "Edit Forwarding Rule" else "New Forwarding Rule") },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Rule Name") },
                        placeholder = { Text("e.g., Forward Verification SMS") },
                        modifier = Modifier.fillMaxWidth().testTag("dialog_rule_name"),
                        singleLine = true
                    )
                }

                item {
                    OutlinedTextField(
                        value = senderPattern,
                        onValueChange = { senderPattern = it },
                        label = { Text("Filter Sender (Optional)") },
                        placeholder = { Text("e.g. +1555 or specific bank") },
                        modifier = Modifier.fillMaxWidth().testTag("dialog_sender_pattern"),
                        singleLine = true
                    )
                }

                item {
                    OutlinedTextField(
                        value = keywordPattern,
                        onValueChange = { keywordPattern = it },
                        label = { Text("Match Keyword (Optional)") },
                        placeholder = { Text("e.g., OTP, Verification, Alert") },
                        modifier = Modifier.fillMaxWidth().testTag("dialog_keyword_pattern"),
                        singleLine = true
                    )
                }

                // Dropdown destination selection
                item {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = destinationType,
                            onValueChange = {},
                            label = { Text("Target Destination") },
                            readOnly = true,
                            trailingIcon = {
                                IconButton(onClick = { expandedDropdown = !expandedDropdown }) {
                                    Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Dropdown", modifier = Modifier.scale(1f)) // Standard arrow
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { expandedDropdown = true }
                                .testTag("dialog_dest_type")
                        )
                        DropdownMenu(
                            expanded = expandedDropdown,
                            onDismissRequest = { expandedDropdown = false },
                            modifier = Modifier.fillMaxWidth(0.8f)
                        ) {
                            listOf("WEBHOOK", "TELEGRAM", "DISCORD", "SLACK", "EMAIL").forEach { type ->
                                DropdownMenuItem(
                                    text = { Text(type) },
                                    onClick = {
                                        destinationType = type
                                        expandedDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Configuration fields depending on Selected Destination Type
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Destination Credentials",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                when (destinationType) {
                    "WEBHOOK" -> {
                        item {
                            OutlinedTextField(
                                value = webhookUrl,
                                onValueChange = { webhookUrl = it },
                                label = { Text("Webhook URL") },
                                placeholder = { Text("https://yourserver.com/api/sms") },
                                modifier = Modifier.fillMaxWidth().testTag("config_webhook_url"),
                                singleLine = true
                            )
                        }
                    }
                    "TELEGRAM" -> {
                        item {
                            OutlinedTextField(
                                value = telegramToken,
                                onValueChange = { telegramToken = it },
                                label = { Text("Bot Token") },
                                placeholder = { Text("123456:ABC-DEF...") },
                                modifier = Modifier.fillMaxWidth().testTag("config_tg_token"),
                                singleLine = true
                            )
                        }
                        item {
                            OutlinedTextField(
                                value = telegramChatId,
                                onValueChange = { telegramChatId = it },
                                label = { Text("Chat ID / Channel ID") },
                                placeholder = { Text("e.g. -10012345678 or @channel") },
                                modifier = Modifier.fillMaxWidth().testTag("config_tg_chat"),
                                singleLine = true
                            )
                        }
                    }
                    "DISCORD" -> {
                        item {
                            OutlinedTextField(
                                value = discordWebhookUrl,
                                onValueChange = { discordWebhookUrl = it },
                                label = { Text("Discord Webhook URL") },
                                placeholder = { Text("https://discord.com/api/webhooks/...") },
                                modifier = Modifier.fillMaxWidth().testTag("config_discord_url"),
                                singleLine = true
                            )
                        }
                    }
                    "SLACK" -> {
                        item {
                            OutlinedTextField(
                                value = slackWebhookUrl,
                                onValueChange = { slackWebhookUrl = it },
                                label = { Text("Slack Webhook URL") },
                                placeholder = { Text("https://hooks.slack.com/services/...") },
                                modifier = Modifier.fillMaxWidth().testTag("config_slack_url"),
                                singleLine = true
                            )
                        }
                    }
                    "EMAIL" -> {
                        item {
                            OutlinedTextField(
                                value = emailTo,
                                onValueChange = { emailTo = it },
                                label = { Text("Recipient Email") },
                                placeholder = { Text("user@example.com") },
                                modifier = Modifier.fillMaxWidth().testTag("config_email_to"),
                                singleLine = true
                            )
                        }
                        item {
                            OutlinedTextField(
                                value = emailHost,
                                onValueChange = { emailHost = it },
                                label = { Text("SMTP Host") },
                                modifier = Modifier.fillMaxWidth().testTag("config_email_host"),
                                singleLine = true
                            )
                        }
                        item {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = emailPort,
                                    onValueChange = { emailPort = it },
                                    label = { Text("SMTP Port") },
                                    modifier = Modifier.weight(1f).testTag("config_email_port"),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = emailUser,
                                    onValueChange = { emailUser = it },
                                    label = { Text("SMTP Username") },
                                    modifier = Modifier.weight(2f).testTag("config_email_user"),
                                    singleLine = true
                                )
                            }
                        }
                        item {
                            OutlinedTextField(
                                value = emailPass,
                                onValueChange = { emailPass = it },
                                label = { Text("SMTP Password (Sender App Password)") },
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth().testTag("config_email_pass"),
                                singleLine = true
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank()) {
                        name = "Forward Rule #${(1000..9999).random()}"
                    }
                    val newRule = RuleEntity(
                        id = rule?.id ?: 0,
                        name = name,
                        senderPattern = senderPattern,
                        keywordPattern = keywordPattern,
                        destinationType = destinationType,
                        isActive = rule?.isActive ?: true,
                        webhookUrl = webhookUrl,
                        telegramToken = telegramToken,
                        telegramChatId = telegramChatId,
                        discordWebhookUrl = discordWebhookUrl,
                        slackWebhookUrl = slackWebhookUrl,
                        emailHost = emailHost,
                        emailPort = emailPort,
                        emailUser = emailUser,
                        emailPass = emailPass,
                        emailTo = emailTo
                    )
                    onSave(newRule)
                },
                modifier = Modifier.testTag("dialog_save_btn")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("dialog_cancel_btn")) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsTab(viewModel: SmsViewModel) {
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    var selectedLogItem by remember { mutableStateOf<LogEntity?>(null) }
    var showClearConfirmation by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Search and Actions Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                label = { Text("Search logs...") },
                leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = "Search") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("log_search_input"),
                singleLine = true
            )

            IconButton(
                onClick = { showClearConfirmation = true },
                modifier = Modifier
                    .size(56.dp)
                    .background(MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(14.dp))
                    .testTag("clear_logs_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Clear Logs",
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (logs.isEmpty()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.List,
                    contentDescription = "Empty",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "No Interception Logs Available",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (searchQuery.isNotEmpty()) "No results matching your query." 
                           else "Trigger some SMS messages using the emulator on Dashboard to test forwarding.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp).padding(top = 4.dp)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(logs) { logItem ->
                    LogCard(
                        logItem = logItem,
                        onClick = { selectedLogItem = logItem },
                        onDelete = { viewModel.deleteLogItem(logItem) }
                    )
                }
            }
        }
    }

    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            title = { Text("Clear All Logs?") },
            text = { Text("This will permanently delete all historic forwarding and filtration logs from this device database. This action is irreversible.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearLogs()
                        showClearConfirmation = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_clear_btn")
                ) {
                    Text("Clear All", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showClearConfirmation = false },
                    modifier = Modifier.testTag("cancel_clear_btn")
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (selectedLogItem != null) {
        LogDetailsDialog(
            logItem = selectedLogItem!!,
            onDismiss = { selectedLogItem = null }
        )
    }
}

@Composable
fun LogCard(
    logItem: LogEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val statusColor = when (logItem.status) {
        "SUCCESS" -> Color(0xFF4CAF50)
        "FAILED" -> MaterialTheme.colorScheme.error
        "FILTERED" -> Color(0xFF9E9E9E)
        else -> MaterialTheme.colorScheme.primary
    }

    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    val dateString = dateFormat.format(Date(logItem.timestamp))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("log_card_${logItem.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status circle indicator
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = logItem.sender,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = dateString,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = logItem.messageBody,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                
                val ruleInfo = if (logItem.matchedRuleName != null) {
                    "Rule: ${logItem.matchedRuleName} (${logItem.destinationType})"
                } else {
                    "No matching routing rules"
                }
                Text(
                    text = ruleInfo,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (logItem.matchedRuleName != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp).testTag("delete_log_btn_${logItem.id}")) {
                Icon(
                    imageVector = Icons.Default.Clear,
                    contentDescription = "Delete Item",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun LogDetailsDialog(
    logItem: LogEntity,
    onDismiss: () -> Unit
) {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    val dateString = dateFormat.format(Date(logItem.timestamp))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log Transaction Details") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(androidx.compose.foundation.rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DetailRow(label = "Sender Address", value = logItem.sender)
                DetailRow(label = "Received Timestamp", value = dateString)
                DetailRow(
                    label = "Interception Status", 
                    value = logItem.status, 
                    valueColor = when (logItem.status) {
                        "SUCCESS" -> Color(0xFF4CAF50)
                        "FAILED" -> MaterialTheme.colorScheme.error
                        "FILTERED" -> Color(0xFF757575)
                        else -> MaterialTheme.colorScheme.primary
                    }
                )
                DetailRow(label = "Matched Rule", value = logItem.matchedRuleName ?: "None (Filtered out)")
                DetailRow(label = "Forward Platform", value = logItem.destinationType ?: "None")
                DetailRow(label = "Forward Endpoint", value = logItem.destinationTarget ?: "None")

                Spacer(modifier = Modifier.height(6.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                Spacer(modifier = Modifier.height(6.dp))

                Text("Intercepted Raw Message Body", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = logItem.messageBody,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(12.dp)
                    )
                }

                Text("Server Response / Transaction Log", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = logItem.statusMessage ?: "No transaction log output available.",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, modifier = Modifier.testTag("close_details_btn")) {
                Text("Close")
            }
        }
    )
}

@Composable
fun DetailRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = valueColor)
    }
}

@Composable
fun IntegrationsTab(viewModel: SmsViewModel) {
    val isPasscodeSet by viewModel.isPasscodeSet.collectAsStateWithLifecycle()
    var showPasscodeSetup by remember { mutableStateOf(false) }
    var setupCode by remember { mutableStateOf("") }
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App Security settings
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "🔐 Panel Security & Encryption",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Forwarded SMS data contains highly sensitive verification codes, OTPs, and private communications. Secure this app with a local lock.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Local Passcode Lock", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(
                                text = if (isPasscodeSet) "Passcode active" else "Unsecured (Anyone can view logs)",
                                fontSize = 11.sp,
                                color = if (isPasscodeSet) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
                            )
                        }
                        if (isPasscodeSet) {
                            Button(
                                onClick = {
                                    viewModel.disablePasscode()
                                    Toast.makeText(context, "Passcode lock disabled!", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.testTag("disable_pass_btn")
                            ) {
                                Text("Disable PIN")
                            }
                        } else {
                            Button(
                                onClick = { showPasscodeSetup = true },
                                modifier = Modifier.testTag("enable_pass_btn")
                            ) {
                                Text("Setup Passcode")
                            }
                        }
                    }
                }
            }
        }

        // Configuration setup instructions
        item {
            Text(
                text = "Forwarding Platform Guides",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        item {
            PlatformGuideCard(
                title = "🌐 Custom Webhook API",
                details = "When forwarding to a Webhook URL, the app sends an HTTP POST request containing JSON data with the sender address, the raw text message, and a timestamp. Make sure your server returns HTTP 200/201 to mark the log as successful."
            )
        }

        item {
            PlatformGuideCard(
                title = "✈️ Telegram Bot Integration",
                details = "Create a Telegram bot using @BotFather, retrieve your Bot Token, and add your chat ID or channel name. Ensure the Bot is added as an administrator to your channel or chat group to forward messages successfully."
            )
        }

        item {
            PlatformGuideCard(
                title = "👾 Discord Webhook",
                details = "In your Discord Server Settings under Integrations, create a new Webhook, copy its URL, and paste it directly into the rule. The app will automatically format incoming SMS notifications as code-blocks with source labels."
            )
        }

        item {
            PlatformGuideCard(
                title = "💬 Slack Incoming Webhooks",
                details = "Set up an Incoming Webhook in your Slack Workspace apps, copy the URL starting with hooks.slack.com/services/..., and paste it into the Slack routing rule. It formats SMS headers and text cleanly."
            )
        }
    }

    if (showPasscodeSetup) {
        AlertDialog(
            onDismissRequest = { showPasscodeSetup = false },
            title = { Text("Configure Security Passcode") },
            text = {
                Column {
                    Text(
                        text = "Enter a secure 4-digit numeric code to unlock the SMS forwarding admin panel upon startup.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    OutlinedTextField(
                        value = setupCode,
                        onValueChange = {
                            if (it.length <= 4 && it.all { char -> char.isDigit() }) {
                                setupCode = it
                            }
                        },
                        label = { Text("4-Digit PIN") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("setup_pin_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (setupCode.length == 4) {
                            viewModel.setPasscode(setupCode)
                            setupCode = ""
                            showPasscodeSetup = false
                            Toast.makeText(context, "Admin passcode locked successfully!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "PIN must be exactly 4 digits", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.testTag("setup_pin_confirm_btn")
                ) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        setupCode = ""
                        showPasscodeSetup = false
                    },
                    modifier = Modifier.testTag("setup_pin_cancel_btn")
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun PlatformGuideCard(
    title: String,
    details: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = details,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(text = "Hello $name!", modifier = modifier)
}
