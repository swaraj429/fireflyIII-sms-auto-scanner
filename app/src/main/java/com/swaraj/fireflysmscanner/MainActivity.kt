package com.swaraj.fireflysmscanner

import android.Manifest
import android.content.pm.PackageManager
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
import com.swaraj.fireflysmscanner.debug.DebugLog
import com.swaraj.fireflysmscanner.ui.*
import com.swaraj.fireflysmscanner.viewmodel.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DebugLog.log("MainActivity", "onCreate")

        setContent {
            MaterialTheme(
                colorScheme = dynamicColorSchemeOrDefault()
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainApp()
                }
            }
        }
    }
}

@Composable
private fun dynamicColorSchemeOrDefault(): ColorScheme {
    // Use default Material3 light scheme — works everywhere
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
fun MainApp() {
    val navController = rememberNavController()
    val context = LocalContext.current

    // Shared ViewModels (scoped to activity via viewModel())
    val setupViewModel: SetupViewModel = viewModel()
    val smsViewModel: SmsViewModel = viewModel()
    val transactionViewModel: TransactionViewModel = viewModel()

    // SMS permission state
    var hasSmsPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasSmsPermission = granted
        if (granted) {
            DebugLog.log("Permission", "READ_SMS granted ✓")
            Toast.makeText(context, "SMS permission granted!", Toast.LENGTH_SHORT).show()
            smsViewModel.loadSms()
        } else {
            DebugLog.log("Permission", "READ_SMS denied ✗")
            Toast.makeText(context, "SMS permission denied. Use sample data.", Toast.LENGTH_LONG).show()
        }
    }

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
                    hasPermission = hasSmsPermission,
                    onRequestPermission = {
                        permissionLauncher.launch(Manifest.permission.READ_SMS)
                    },
                    onNavigateToParsed = {
                        navController.navigate(Screen.Transactions.route)
                    }
                )
            }

            composable(Screen.Transactions.route) {
                TransactionScreen(
                    smsViewModel = smsViewModel,
                    transactionViewModel = transactionViewModel
                )
            }

            composable(Screen.Debug.route) {
                DebugScreen()
            }
        }
    }
}
