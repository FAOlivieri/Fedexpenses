package io.github.faolivieri.fedexpenses.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import android.net.Uri
import io.github.faolivieri.fedexpenses.data.SellerMatcher
import io.github.faolivieri.fedexpenses.data.TxType
import io.github.faolivieri.fedexpenses.ui.screens.AccountsScreen
import io.github.faolivieri.fedexpenses.ui.screens.CategoriesScreen
import io.github.faolivieri.fedexpenses.ui.screens.EditTransactionScreen
import io.github.faolivieri.fedexpenses.ui.screens.OverviewScreen
import io.github.faolivieri.fedexpenses.ui.screens.PersonScreen
import io.github.faolivieri.fedexpenses.ui.screens.ReceiveScreen
import io.github.faolivieri.fedexpenses.ui.screens.RecurringScreen
import io.github.faolivieri.fedexpenses.ui.screens.ReportsScreen
import io.github.faolivieri.fedexpenses.ui.screens.RuleEditScreen
import io.github.faolivieri.fedexpenses.ui.screens.SellerRuleEditScreen
import io.github.faolivieri.fedexpenses.ui.screens.SettingsScreen
import io.github.faolivieri.fedexpenses.ui.screens.SharingScreen
import io.github.faolivieri.fedexpenses.ui.screens.TransactionsScreen
import kotlinx.coroutines.flow.StateFlow

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Overview("overview", "Overview", Icons.Filled.PieChart),
    Transactions("transactions", "Transactions", Icons.AutoMirrored.Filled.ReceiptLong),
    Reports("reports", "Reports", Icons.AutoMirrored.Filled.ShowChart),
    Categories("categories", "Categories", Icons.Filled.Category),
    Sharing("sharing", "Sharing", Icons.Filled.Group),
}

object Routes {
    fun edit(txId: Long = 0, type: TxType = TxType.EXPENSE, timestamp: Long = 0, accountId: Long = 0) =
        "edit?txId=$txId&type=${type.name}&ts=$timestamp&acc=$accountId"
    fun rule(id: Long = 0) = "rule?id=$id"
    fun sellerRule(id: Long = 0, seller: String = "") = "sellerRule?id=$id&seller=${Uri.encode(seller)}"
    const val SETTINGS = "settings"
    const val RECURRING = "recurring"
    const val ACCOUNTS = "accounts"
    const val RECEIVE = "receive"
    fun person(id: Long) = "person/$id"
}

@Composable
fun ExpensesNavHost(pendingRoute: StateFlow<String?>, onRouteConsumed: () -> Unit) {
    val nav = rememberNavController()
    val vm: MainViewModel = viewModel()
    val settingsVm: SettingsViewModel = viewModel()
    val route by pendingRoute.collectAsState()

    LaunchedEffect(route) {
        route?.let {
            nav.navigate(it)
            onRouteConsumed()
        }
    }

    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBar = Tab.entries.any { it.route == currentRoute }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (showBar) NavigationBar {
                // One shared size: with large text every label shrinks to what the longest one needs.
                var labelScale by remember { mutableFloatStateOf(1f) }
                val style = MaterialTheme.typography.labelSmall
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = currentRoute == tab.route,
                        onClick = { nav.switchTab(tab.route) },
                        icon = { Icon(tab.icon, null) },
                        label = {
                            Text(
                                tab.label, style = style.copy(fontSize = style.fontSize * labelScale),
                                maxLines = 1, softWrap = false,
                                onTextLayout = { if (it.hasVisualOverflow && labelScale > 0.3f) labelScale *= 0.9f },
                            )
                        },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = Tab.Overview.route, modifier = Modifier.padding(padding).consumeWindowInsets(padding)) {
            composable(Tab.Overview.route) {
                OverviewScreen(vm,
                    onAdd = { type -> nav.navigate(Routes.edit(type = type)) },
                    onCategoryClick = { catId ->
                        vm.filter.value = TxFilter(categoryIds = setOf(catId))
                        nav.switchTab(Tab.Transactions.route)
                    },
                    onSettings = { nav.navigate(Routes.SETTINGS) })
            }
            composable(Tab.Transactions.route) {
                TransactionsScreen(vm, onOpen = { nav.navigate(Routes.edit(it)) },
                    onAdd = { type -> nav.navigate(Routes.edit(type = type)) },
                    onRecurring = { nav.navigate(Routes.RECURRING) })
            }
            composable(Routes.RECURRING) {
                RecurringScreen(vm, onOpen = { nav.navigate(Routes.edit(it)) }, onBack = { nav.popBackStack() })
            }
            composable(Tab.Reports.route) { ReportsScreen(vm) }
            composable(Tab.Categories.route) {
                CategoriesScreen(vm, onOpenSellerRule = { nav.navigate(Routes.sellerRule(it)) })
            }
            composable(
                "sellerRule?id={id}&seller={seller}",
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType; defaultValue = 0L },
                    navArgument("seller") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { entry ->
                SellerRuleEditScreen(vm, ruleId = entry.arguments?.getLong("id") ?: 0L,
                    initialSeller = entry.arguments?.getString("seller").orEmpty(), onDone = { nav.popBackStack() })
            }
            composable(Tab.Sharing.route) {
                SharingScreen(vm, onOpenPerson = { nav.navigate(Routes.person(it)) }, onReceive = { nav.navigate(Routes.RECEIVE) })
            }
            composable(Routes.RECEIVE) { ReceiveScreen(vm, onDone = { nav.popBackStack() }) }
            composable("person/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                PersonScreen(vm, personId = entry.arguments?.getLong("id") ?: 0L,
                    onOpenTransaction = { nav.navigate(Routes.edit(it)) }, onBack = { nav.popBackStack() })
            }
            composable(Routes.ACCOUNTS) { AccountsScreen(vm, onBack = { nav.popBackStack() }) }
            composable(
                "edit?txId={txId}&type={type}&ts={ts}&acc={acc}",
                arguments = listOf(
                    navArgument("txId") { type = NavType.LongType; defaultValue = 0L },
                    navArgument("type") { type = NavType.StringType; defaultValue = TxType.EXPENSE.name },
                    navArgument("ts") { type = NavType.LongType; defaultValue = 0L },
                    navArgument("acc") { type = NavType.LongType; defaultValue = 0L },
                ),
            ) { entry ->
                EditTransactionScreen(
                    vm,
                    txId = entry.arguments?.getLong("txId") ?: 0L,
                    initialType = TxType.valueOf(entry.arguments?.getString("type") ?: TxType.EXPENSE.name),
                    initialTimestamp = entry.arguments?.getLong("ts") ?: 0L,
                    initialAccountId = entry.arguments?.getLong("acc") ?: 0L,
                    onSellerRule = { seller ->
                        // Open the rule that currently decides this seller, or start one for it.
                        val rule = SellerMatcher.firstMatch(vm.sellerRules.value, seller)
                        nav.navigate(if (rule != null) Routes.sellerRule(rule.id) else Routes.sellerRule(seller = seller))
                    },
                    onDone = { nav.popBackStack() },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(settingsVm, onBack = { nav.popBackStack() }, onEditRule = { nav.navigate(Routes.rule(it)) },
                    onAccounts = { nav.navigate(Routes.ACCOUNTS) })
            }
            composable("rule?id={id}", arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = 0L })) { entry ->
                RuleEditScreen(settingsVm, vm, ruleId = entry.arguments?.getLong("id") ?: 0L, onDone = { nav.popBackStack() },
                    onOpenTransaction = { nav.navigate(Routes.edit(it)) })
            }
        }
    }
}

private fun NavHostController.switchTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}
