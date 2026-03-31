package com.example.oto1720.dojo2026

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.oto1720.dojo2026.navigation.AppNavHost
import com.example.oto1720.dojo2026.navigation.Screen
import com.example.oto1720.dojo2026.ui.theme.TechDigestTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TechDigestTheme {
                val navController = rememberNavController()
                val currentEntry by navController.currentBackStackEntryAsState()
                val currentRoute = currentEntry?.destination?.route

                val showBottomBar = currentRoute in listOf(
                    Screen.Portal.route,
                    Screen.Tsundoku.route,
                    Screen.Done.route,
                )

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        if (showBottomBar) {
                            AppBottomNavigationBar(
                                currentRoute = currentRoute,
                                navController = navController,
                            )
                        }
                    },
                ) { innerPadding ->
                    AppNavHost(
                        navController = navController,
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }
}

@Composable
private fun AppBottomNavigationBar(
    currentRoute: String?,
    navController: NavHostController,
) {
    NavigationBar {
        NavigationBarItem(
            selected = currentRoute == Screen.Portal.route,
            onClick = {
                navController.navigate(Screen.Portal.route) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            },
            icon = { Icon(Icons.Default.Home, contentDescription = "ポータル") },
            label = { Text("ポータル") },
        )
        NavigationBarItem(
            selected = currentRoute == Screen.Tsundoku.route,
            onClick = {
                navController.navigate(Screen.Tsundoku.route) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            },
            icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "積読") },
            label = { Text("積読") },
        )
        NavigationBarItem(
            selected = currentRoute == Screen.Done.route,
            onClick = {
                navController.navigate(Screen.Done.route) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            },
            icon = { Icon(Icons.Default.Done, contentDescription = "完了") },
            label = { Text("完了") },
        )
    }
}
