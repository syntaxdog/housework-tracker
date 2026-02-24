package com.housework.tracker.ui.main

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.housework.tracker.ui.calendar.CalendarScreen
import com.housework.tracker.ui.chat.ChatScreen
import com.housework.tracker.ui.checklist.ChecklistScreen
import com.housework.tracker.ui.settings.SettingsScreen

enum class MainTab(val route: String, val label: String, val icon: ImageVector) {
    CHECKLIST("tab_checklist", "체크리스트", Icons.Default.Checklist),
    CALENDAR("tab_calendar", "달력", Icons.Default.CalendarMonth),
    CHAT("tab_chat", "채팅", Icons.Default.Chat),
    SETTINGS("tab_settings", "설정", Icons.Default.Settings),
}

@Composable
fun MainScreen(onLogout: () -> Unit = {}) {
    val tabNavController: NavHostController = rememberNavController()
    val navBackStackEntry by tabNavController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val isChatTab = currentDestination?.hierarchy?.any { it.route == MainTab.CHAT.route } == true

    Scaffold(
        bottomBar = {
            NavigationBar {
                MainTab.entries.forEach { tab ->
                    NavigationBarItem(
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                        selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true,
                        onClick = {
                            tabNavController.navigate(tab.route) {
                                popUpTo(tabNavController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = tabNavController,
            startDestination = MainTab.CHECKLIST.route,
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .then(
                    if (isChatTab) Modifier.imePadding() else Modifier
                )
        ) {
            composable(MainTab.CHECKLIST.route) { ChecklistScreen() }
            composable(MainTab.CALENDAR.route) { CalendarScreen() }
            composable(MainTab.CHAT.route) { ChatScreen() }
            composable(MainTab.SETTINGS.route) { SettingsScreen(onLogout = onLogout) }
        }
    }
}
