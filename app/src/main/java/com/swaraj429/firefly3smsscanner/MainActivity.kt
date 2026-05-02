package com.swaraj429.firefly3smsscanner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.swaraj429.firefly3smsscanner.debug.DebugLog
import com.swaraj429.firefly3smsscanner.model.ParsedTransaction
import com.swaraj429.firefly3smsscanner.model.TransactionType
import com.swaraj429.firefly3smsscanner.notification.NotificationHelper
import com.swaraj429.firefly3smsscanner.ui.*
import com.swaraj429.firefly3smsscanner.viewmodel.*

class MainActivity : ComponentActivity() {

    // Holds a pending transaction from a notification tap.
    // Written on the main thread from onCreate/onNewIntent, read in Compose.
    private val pendingNotificationTransaction = mutableStateOf<ParsedTransaction?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DebugLog.log("MainActivity", "onCreate")

        // Handle notification tap intent that launched the activity
        handleNotificationIntent(intent)

        setContent {
            MaterialTheme(
                colorScheme = dynamicColorSchemeOrDefault()
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainApp(
                        pendingNotificationTransaction = pendingNotificationTransaction
                    )
                }
            }
        }
    }

    /** Called when the activity is already running and a new intent arrives (e.g. second notification tap). */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        if (intent?.action != NotificationHelper.ACTION_REVIEW_TRANSACTION) return

        val amount = intent.getDoubleExtra(NotificationHelper.EXTRA_AMOUNT, 0.0)
        val typeStr = intent.getStringExtra(NotificationHelper.EXTRA_TYPE) ?: "UNKNOWN"
        val sender = intent.getStringExtra(NotificationHelper.EXTRA_SENDER) ?: ""
        val rawMessage = intent.getStringExtra(NotificationHelper.EXTRA_RAW_MESSAGE) ?: ""
        val timestamp = intent.getLongExtra(NotificationHelper.EXTRA_TIMESTAMP, System.currentTimeMillis())

        val type = try {
            TransactionType.valueOf(typeStr)
        } catch (e: Exception) {
            TransactionType.UNKNOWN
        }

        pendingNotificationTransaction.value = ParsedTransaction(
            amount = amount,
            type = type,
            rawMessage = rawMessage,
            sender = sender,
            timestamp = timestamp,
            description = "SMS: ${rawMessage.take(60)}"
        )

        DebugLog.log("MainActivity", "Notification tap → ₹$amount $type from $sender")
    }
}

@Composable
private fun dynamicColorSchemeOrDefault(): ColorScheme {
    return MaterialTheme.colorScheme
}

sealed class Screen(val route: String, val title: String, val icon: @Composable () -> Unit) {
    data object Setup : Screen("setup", "Setup", { Icon(Icons.Default.Settings, "Setup") })
    data object SmsList : Screen("sms", "SMS", { Icon(Icons.Default.Sms, "SMS") })
    data object Transactions : Screen("transactions", "Txns", { Icon(Icons.Default.Receipt, "Transactions") })
    data object Debug : Screen("debug", "Debug", { Icon(Icons.Default.BugReport, "Debug") })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApp(
    pendingNotificationTransaction: MutableState<ParsedTransaction?> = mutableStateOf(null)
) {
    val navController = rememberNavController()
    val context = LocalContext.current

    // Shared ViewModels
    val setupViewModel: SetupViewModel = viewModel()
    val smsViewModel: SmsViewModel = viewModel()
    val transactionViewModel: TransactionViewModel = viewModel()
    val fireflyDataViewModel: FireflyDataViewModel = viewModel()

    // SMS permissions (READ + RECEIVE)
    var hasSmsPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }
    var hasReceiveSmsPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }

    // Request READ_SMS + RECEIVE_SMS together
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasSmsPermission = results[Manifest.permission.READ_SMS] == true
        hasReceiveSmsPermission = results[Manifest.permission.RECEIVE_SMS] == true
        if (hasSmsPermission) {
            DebugLog.log("Permission", "READ_SMS granted ✓")
            Toast.makeText(context, "SMS permission granted!", Toast.LENGTH_SHORT).show()
            smsViewModel.loadSms()
        } else {
            DebugLog.log("Permission", "READ_SMS denied ✗")
            Toast.makeText(context, "SMS permission denied. Use sample data.", Toast.LENGTH_LONG).show()
        }
        if (hasReceiveSmsPermission) {
            DebugLog.log("Permission", "RECEIVE_SMS granted ✓ — live SMS detection active")
        }
    }

    // Request POST_NOTIFICATIONS on Android 13+
    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        DebugLog.log("Permission", "POST_NOTIFICATIONS: ${if (granted) "granted ✓" else "denied ✗"}")
    }
    LaunchedEffect(Unit) {
        // Fetch Firefly data on app start so account lists are ready for SMS matching
        fireflyDataViewModel.refreshAll()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // ── Handle notification tap ───────────────────────────────────────────────
    // When a pending transaction arrives (from tapping a notification),
    // add it to the list and navigate to the Transactions tab.
    LaunchedEffect(pendingNotificationTransaction.value) {
        val tx = pendingNotificationTransaction.value ?: return@LaunchedEffect
        pendingNotificationTransaction.value = null  // consume it

        smsViewModel.addTransactionFromNotification(tx)

        // Navigate to Transactions tab
        navController.navigate(Screen.Transactions.route) {
            popUpTo(navController.graph.startDestinationId) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    // ─────────────────────────────────────────────────────────────────────────

    val screens = listOf(Screen.Setup, Screen.SmsList, Screen.Transactions, Screen.Debug)
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("🔥 Firefly SMS Scanner")
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        bottomBar = {
            NavigationBar {
                screens.forEach { screen ->
                    NavigationBarItem(
                        icon = screen.icon,
                        label = { Text(screen.title) },
                        selected = currentRoute == screen.route,
                        onClick = {
                            if (currentRoute != screen.route) {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.startDestinationId) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Setup.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Setup.route) {
                SetupScreen(viewModel = setupViewModel)
            }

            composable(Screen.SmsList.route) {
                SmsListScreen(
                    viewModel = smsViewModel,
                    fireflyDataViewModel = fireflyDataViewModel,
                    hasPermission = hasSmsPermission,
                    onRequestPermission = {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.READ_SMS,
                                Manifest.permission.RECEIVE_SMS
                            )
                        )
                    },
                    onNavigateToParsed = {
                        navController.navigate(Screen.Transactions.route)
                    }
                )
            }

            composable(Screen.Transactions.route) {
                TransactionScreen(
                    smsViewModel = smsViewModel,
                    transactionViewModel = transactionViewModel,
                    fireflyDataViewModel = fireflyDataViewModel
                )
            }

            composable(Screen.Debug.route) {
                DebugScreen()
            }
        }
    }
}
