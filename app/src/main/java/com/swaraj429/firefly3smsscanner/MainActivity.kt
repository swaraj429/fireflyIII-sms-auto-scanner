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
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
import com.swaraj429.firefly3smsscanner.ui.DebugScreen
import com.swaraj429.firefly3smsscanner.ui.screens.*
import com.swaraj429.firefly3smsscanner.ui.theme.*
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
            FireflyTheme {
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
            TransactionType.WITHDRAWAL
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

sealed class Screen(
    val route: String,
    val title: String,
    val selectedIcon: @Composable () -> Unit,
    val unselectedIcon: @Composable () -> Unit
) {
    data object Home : Screen(
        "home", "Home",
        { Icon(Icons.Filled.Home, "Home") },
        { Icon(Icons.Outlined.Home, "Home") }
    )
    data object SmsList : Screen(
        "sms", "SMS",
        { Icon(Icons.Filled.Sms, "SMS") },
        { Icon(Icons.Outlined.Sms, "SMS") }
    )
    data object Rules : Screen(
        "rules", "Rules",
        { Icon(Icons.Filled.AutoAwesome, "Rules") },
        { Icon(Icons.Outlined.AutoAwesome, "Rules") }
    )
    data object Settings : Screen(
        "settings", "Settings",
        { Icon(Icons.Filled.Settings, "Settings") },
        { Icon(Icons.Outlined.Settings, "Settings") }
    )
    data object Debug : Screen(
        "debug", "Debug",
        { Icon(Icons.Filled.BugReport, "Debug") },
        { Icon(Icons.Outlined.BugReport, "Debug") }
    )
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
    LaunchedEffect(pendingNotificationTransaction.value, fireflyDataViewModel.isCacheLoaded) {
        val tx = pendingNotificationTransaction.value ?: return@LaunchedEffect
        
        // Wait for cache to load so we have accounts for matching
        if (!fireflyDataViewModel.isCacheLoaded) return@LaunchedEffect
        
        pendingNotificationTransaction.value = null  // consume it

        smsViewModel.addTransactionFromNotification(
            transaction = tx,
            accounts = fireflyDataViewModel.assetAccounts
        )

        // Navigate to Home (Transactions) tab
        navController.navigate(Screen.Home.route) {
            popUpTo(navController.graph.startDestinationId) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    // ─────────────────────────────────────────────────────────────────────────

    val screens = listOf(Screen.Home, Screen.SmsList, Screen.Rules, Screen.Settings)
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp
            ) {
                screens.forEach { screen ->
                    val selected = currentRoute == screen.route
                    NavigationBarItem(
                        icon = { if (selected) screen.selectedIcon() else screen.unselectedIcon() },
                        label = {
                            Text(
                                screen.title,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        },
                        selected = selected,
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
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Primary,
                            selectedTextColor = Primary,
                            indicatorColor = Primary.copy(alpha = 0.1f),
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    smsViewModel = smsViewModel,
                    transactionViewModel = transactionViewModel,
                    fireflyDataViewModel = fireflyDataViewModel
                )
            }

            composable(Screen.SmsList.route) {
                SmsScreen(
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
                        navController.navigate(Screen.Home.route) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }

            composable(Screen.Rules.route) {
                RulesScreen()
            }

            composable(Screen.Settings.route) {
                SettingsScreen(viewModel = setupViewModel)
            }

            composable(Screen.Debug.route) {
                DebugScreen()
            }
        }
    }
}
