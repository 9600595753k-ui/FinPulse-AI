package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.domain.model.ThemePreference
import com.example.security.configureAppCheck
import com.example.ui.screens.AiAssistantScreen
import com.example.ui.screens.BudgetsScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.ReportsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SmsInboxScreen
import com.example.ui.screens.SubscriptionsScreen
import com.example.ui.screens.TransactionsScreen
import com.example.ui.theme.FinPulseTheme
import com.example.ui.viewmodel.FinanceViewModel
import kotlinx.coroutines.launch

enum class FinPulseDestination(
    val route: String,
    val title: String,
    val shortLabel: String,
    val icon: ImageVector,
    val isPrimaryMobileTab: Boolean
) {
    DASHBOARD("dashboard", "Dashboard", "Home", Icons.Default.Dashboard, true),
    SMS_INBOX("sms_inbox", "SMS Inbox", "SMS", Icons.Default.Sms, true),
    TRANSACTIONS("transactions", "Transactions", "Txns", Icons.AutoMirrored.Filled.ReceiptLong, true),
    BUDGETS("budgets", "Budgets", "Budgets", Icons.Default.AccountBalanceWallet, true),
    SUBSCRIPTIONS("subscriptions", "Subscriptions", "Subs", Icons.Default.Autorenew, false),
    REPORTS("reports", "Reports", "Reports", Icons.Default.Description, false),
    AI_ASSISTANT("ai_assistant", "AI Assistant", "AI", Icons.Default.AutoAwesome, true),
    SETTINGS("settings", "Settings", "Settings", Icons.Default.Settings, false)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Mandatory Firebase App Check wiring with launch intent BEFORE UI/ViewModels
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            val financeViewModel: FinanceViewModel = viewModel()
            val settings by financeViewModel.settings.collectAsStateWithLifecycle()

            val useDarkTheme = when (settings.themePreference) {
                ThemePreference.SYSTEM -> isSystemInDarkTheme()
                ThemePreference.LIGHT -> false
                ThemePreference.DARK -> true
            }

            FinPulseTheme(darkTheme = useDarkTheme) {
                FinPulseAppShell(viewModel = financeViewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinPulseAppShell(viewModel: FinanceViewModel) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: FinPulseDestination.DASHBOARD.route
    val currentDest = FinPulseDestination.entries.firstOrNull { it.route == currentRoute }
        ?: FinPulseDestination.DASHBOARD

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val dashboardState by viewModel.dashboardState.collectAsStateWithLifecycle()
    val smsMessages by viewModel.smsMessages.collectAsStateWithLifecycle()
    val customFolders by viewModel.customFolders.collectAsStateWithLifecycle()
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val subscriptions by viewModel.subscriptions.collectAsStateWithLifecycle()
    val alerts by viewModel.alerts.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val aiMessages by viewModel.aiMessages.collectAsStateWithLifecycle()
    val isAiGenerating by viewModel.isAiGenerating.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()

    LaunchedEffect(statusBanner) {
        statusBanner?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusBanner()
        }
    }

    fun navigateTo(dest: FinPulseDestination) {
        navController.navigate(dest.route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isExpandedWidth = maxWidth >= 600.dp

        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = !isExpandedWidth,
            drawerContent = {
                ModalDrawerSheet(modifier = Modifier.width(280.dp)) {
                    Text(
                        text = "FinPulse Finance",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)
                    )
                    HorizontalDivider()
                    FinPulseDestination.entries.forEach { dest ->
                        NavigationDrawerItem(
                            label = { Text(dest.title) },
                            selected = currentDest == dest,
                            onClick = {
                                scope.launch { drawerState.close() }
                                navigateTo(dest)
                            },
                            icon = { Icon(dest.icon, contentDescription = dest.title) },
                            modifier = Modifier
                                .padding(horizontal = 12.dp, vertical = 2.dp)
                                .testTag("drawer_nav_${dest.route}")
                        )
                    }
                }
            }
        ) {
            Scaffold(
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                topBar = {
                    if (!isExpandedWidth) {
                        TopAppBar(
                            title = {
                                Text(
                                    text = currentDest.title,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            navigationIcon = {
                                IconButton(
                                    onClick = { scope.launch { drawerState.open() } },
                                    modifier = Modifier.testTag("open_drawer_button")
                                ) {
                                    Icon(Icons.Default.Menu, contentDescription = "Open navigation menu")
                                }
                            },
                            actions = {
                                IconButton(
                                    onClick = { navigateTo(FinPulseDestination.SUBSCRIPTIONS) },
                                    modifier = Modifier.testTag("topbar_subscriptions_button")
                                ) {
                                    Icon(
                                        Icons.Default.Autorenew,
                                        contentDescription = "Subscriptions",
                                        tint = if (currentDest == FinPulseDestination.SUBSCRIPTIONS) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                                IconButton(
                                    onClick = { navigateTo(FinPulseDestination.REPORTS) },
                                    modifier = Modifier.testTag("topbar_reports_button")
                                ) {
                                    Icon(
                                        Icons.Default.Description,
                                        contentDescription = "Reports & CSV",
                                        tint = if (currentDest == FinPulseDestination.REPORTS) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                                IconButton(
                                    onClick = { navigateTo(FinPulseDestination.SETTINGS) },
                                    modifier = Modifier.testTag("topbar_settings_button")
                                ) {
                                    Icon(
                                        Icons.Default.Settings,
                                        contentDescription = "Settings",
                                        tint = if (currentDest == FinPulseDestination.SETTINGS) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                            }
                        )
                    }
                },
                bottomBar = {
                    if (!isExpandedWidth) {
                        NavigationBar {
                            FinPulseDestination.entries
                                .filter { it.isPrimaryMobileTab }
                                .forEach { dest ->
                                    NavigationBarItem(
                                        selected = currentDest == dest,
                                        onClick = { navigateTo(dest) },
                                        icon = { Icon(dest.icon, contentDescription = dest.title) },
                                        label = { Text(dest.shortLabel) },
                                        modifier = Modifier.testTag("bottom_nav_${dest.route}")
                                    )
                                }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (isExpandedWidth) {
                        NavigationRail(
                            modifier = Modifier
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState())
                        ) {
                            Spacer(modifier = Modifier.width(4.dp))
                            FinPulseDestination.entries.forEach { dest ->
                                NavigationRailItem(
                                    selected = currentDest == dest,
                                    onClick = { navigateTo(dest) },
                                    icon = { Icon(dest.icon, contentDescription = dest.title) },
                                    label = { Text(dest.shortLabel) },
                                    modifier = Modifier.testTag("rail_nav_${dest.route}")
                                )
                            }
                        }
                    }

                    NavHost(
                        navController = navController,
                        startDestination = FinPulseDestination.DASHBOARD.route,
                        modifier = Modifier.weight(1f)
                    ) {
                        composable(FinPulseDestination.DASHBOARD.route) {
                            DashboardScreen(
                                state = dashboardState,
                                allTransactions = transactions,
                                alerts = alerts,
                                onSelectMonth = viewModel::selectMonth,
                                onSelectTimeframe = viewModel::selectTimeframe,
                                onMarkAlertsRead = viewModel::markAllAlertsRead,
                                onNavigateToBudgets = { navigateTo(FinPulseDestination.BUDGETS) },
                                onNavigateToAssistant = { navigateTo(FinPulseDestination.AI_ASSISTANT) }
                            )
                        }
                        composable(FinPulseDestination.SMS_INBOX.route) {
                            SmsInboxScreen(
                                messages = smsMessages,
                                customFolders = customFolders,
                                hasRequestedSmsPermission = settings.hasRequestedSmsPermission,
                                onMarkSmsPermissionRequested = viewModel::markSmsPermissionRequested,
                                onPermissionDeniedMessage = viewModel::postStatusBanner,
                                onScanDeviceSms = viewModel::scanDeviceSms,
                                onImportManualSms = viewModel::importManualSms,
                                onCorrectFolder = viewModel::correctSmsFolder,
                                onMarkRead = viewModel::markSmsRead,
                                onDeleteSms = viewModel::deleteSms,
                                onCreateCustomFolder = viewModel::createCustomFolder
                            )
                        }
                        composable(FinPulseDestination.TRANSACTIONS.route) {
                            TransactionsScreen(
                                transactions = transactions,
                                availableMonths = dashboardState.availableMonths,
                                onAddTransaction = viewModel::addManualTransaction,
                                onUpdateTransaction = viewModel::updateTransaction,
                                onDeleteTransaction = viewModel::deleteTransaction
                            )
                        }
                        composable(FinPulseDestination.BUDGETS.route) {
                            BudgetsScreen(
                                budgets = dashboardState.budgetStatuses,
                                overallLimit = dashboardState.overallBudgetLimit,
                                overallSpent = dashboardState.overallBudgetSpent,
                                settings = settings,
                                onSaveBudget = viewModel::saveBudget,
                                onDeleteBudget = viewModel::deleteBudget,
                                onToggleCarryForward = viewModel::setCarryForwardBudgets
                            )
                        }
                        composable(FinPulseDestination.SUBSCRIPTIONS.route) {
                            SubscriptionsScreen(
                                subscriptions = subscriptions,
                                monthlyEquivalent = viewModel.getMonthlySubscriptionCost(),
                                annualCost = viewModel.getAnnualSubscriptionCost(),
                                onSaveSubscription = viewModel::saveSubscription,
                                onTogglePause = viewModel::toggleSubscriptionPause,
                                onDeleteSubscription = viewModel::deleteSubscription
                            )
                        }
                        composable(FinPulseDestination.REPORTS.route) {
                            ReportsScreen(
                                dataRevision = transactions.size + subscriptions.size + dashboardState.budgetStatuses.size,
                                onGenerateCsv = viewModel::generateCsvReport
                            )
                        }
                        composable(FinPulseDestination.AI_ASSISTANT.route) {
                            AiAssistantScreen(
                                messages = aiMessages,
                                isGenerating = isAiGenerating,
                                onAskQuestion = viewModel::askAssistant
                            )
                        }
                        composable(FinPulseDestination.SETTINGS.route) {
                            SettingsScreen(
                                settings = settings,
                                onMarkSmsPermissionRequested = viewModel::markSmsPermissionRequested,
                                onScanDeviceSms = viewModel::scanDeviceSms,
                                onSetTheme = viewModel::setThemePreference,
                                onSetLargeTxnThreshold = viewModel::setLargeTransactionThreshold,
                                onSetDeviceNotifications = viewModel::setDeviceNotifications,
                                onSetAlertFrequency = viewModel::setAlertFrequency,
                                onSetThresholdAlerts = viewModel::setThresholdAlerts,
                                onSetAllowAiInsights = viewModel::setAllowAiAggregatedInsights,
                                onImportCsvText = viewModel::importCsvTransactions,
                                onResetDemoData = viewModel::resetDemoData,
                                onDeleteAllSms = viewModel::deleteAllSmsData,
                                onDeleteAllTransactions = viewModel::deleteAllTransactionData
                            )
                        }
                    }
                }
            }
        }
    }
}
