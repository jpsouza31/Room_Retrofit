package com.app.room_retrofit.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.app.room_retrofit.presentation.ui.PokedexScreen
import com.app.room_retrofit.presentation.ui.PokemonDetailScreen

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "pokedex") {
        composable("pokedex") {
            PokedexScreen(
                onPokemonClick = { id ->
                    navController.navigate("pokemon/$id")
                }
            )
        }
        composable(
            route = "pokemon/{id}",
            arguments = listOf(navArgument("id") { type = NavType.IntType })
        ) { backStackEntry ->
            PokemonDetailScreen(
                pokemonId = backStackEntry.arguments?.getInt("id") ?: 0,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
