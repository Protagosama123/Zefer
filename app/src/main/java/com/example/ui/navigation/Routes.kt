package com.example.ui.navigation

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Login : Screen("login")
    object SignUp : Screen("signup")
    object Home : Screen("home")
    object ProductDetail : Screen("detail/{productId}") {
        fun createRoute(productId: String) = "detail/$productId"
    }
    object ArTryOn : Screen("tryon/{productId}/{variantId}") {
        fun createRoute(productId: String, variantId: String) = "tryon/$productId/$variantId"
    }
    object Admin : Screen("admin")
}
