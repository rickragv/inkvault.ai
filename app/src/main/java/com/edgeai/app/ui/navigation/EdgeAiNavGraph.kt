package com.edgeai.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.edgeai.app.ui.chat.ChatScreen
import com.edgeai.app.ui.documents.DocumentDetailScreen
import com.edgeai.app.ui.documents.DocumentsScreen
import com.edgeai.app.ui.graph.GraphScreen
import com.edgeai.app.ui.timeline.TimelineScreen

object Routes {
    const val CHAT = "chat"
    const val DOCUMENTS = "documents"
    const val DOCUMENT_DETAIL = "documents/{docId}"
    const val GRAPH = "graph"
    const val TIMELINE = "timeline"
}

enum class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    CHAT(Routes.CHAT, "Chat", Icons.Default.Chat),
    DOCUMENTS(Routes.DOCUMENTS, "Docs", Icons.Default.Description),
    GRAPH(Routes.GRAPH, "Graph", Icons.Default.Hub),
    TIMELINE(Routes.TIMELINE, "Timeline", Icons.Default.Timeline),
}

@Composable
fun EdgeAiNavGraph() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                BottomNavItem.entries.forEach { item ->
                    NavigationBarItem(
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                        selected = currentRoute == item.route,
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.CHAT,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Routes.CHAT) {
                ChatScreen()
            }
            composable(Routes.DOCUMENTS) {
                DocumentsScreen(
                    onDocumentClick = { docId ->
                        navController.navigate("documents/$docId")
                    },
                )
            }
            composable(
                Routes.DOCUMENT_DETAIL,
                arguments = listOf(navArgument("docId") { type = NavType.LongType }),
            ) { backStackEntry ->
                val docId = backStackEntry.arguments?.getLong("docId") ?: 0L
                DocumentDetailScreen(
                    documentId = docId,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.GRAPH) {
                GraphScreen()
            }
            composable(Routes.TIMELINE) {
                TimelineScreen()
            }
        }
    }
}
