package com.app.room_retrofit.presentation.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.app.room_retrofit.presentation.ui.ArticleDetailScreen
import com.app.room_retrofit.presentation.ui.NewsScreen

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "news") {
        composable("news") {
            NewsScreen(
                onArticleClick = { url ->
                    navController.navigate("detail/${Uri.encode(url)}")
                }
            )
        }
        composable("detail/{url}") { backStackEntry ->
            val url = backStackEntry.arguments?.getString("url") ?: ""
            ArticleDetailScreen(
                url = url,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
