package com.example.wechat2docx.ui.nav

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.wechat2docx.ui.screens.convert.ConvertScreen
import com.example.wechat2docx.ui.screens.home.HomeScreen
import com.example.wechat2docx.ui.screens.settings.SettingsScreen

object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val CONVERT = "convert/{url}"
    fun convert(url: String): String = "convert/${Uri.encode(url)}"
}

@Composable
fun AppNav(nav: NavHostController) {
    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) { HomeScreen(nav) }
        composable(Routes.SETTINGS) { SettingsScreen(nav) }
        composable(
            route = Routes.CONVERT,
            arguments = listOf(navArgument("url") { type = NavType.StringType }),
        ) { entry ->
            val raw = entry.arguments?.getString("url").orEmpty()
            val url = Uri.decode(raw) ?: raw
            ConvertScreen(nav = nav, url = url)
        }
    }
}
