package com.example.ui.navigation

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Login : Screen("login")
    object SignUp : Screen("signup")
    object Home : Screen("home")
    object ProductDetail : Screen("detail/{productId}") {
        fun createRoute(productId: String) = "detail/$productId"
    }
    object PhotoInput : Screen("photo_input/{productId}/{variantId}") {
        fun createRoute(productId: String, variantId: String) = "photo_input/$productId/$variantId"
    }
    object TryOnResult : Screen("tryon_result/{productId}/{variantId}") {
        fun createRoute(productId: String, variantId: String) = "tryon_result/$productId/$variantId"
    }
    object MyTryOns : Screen("my_tryons")
    object Admin : Screen("admin")
}

