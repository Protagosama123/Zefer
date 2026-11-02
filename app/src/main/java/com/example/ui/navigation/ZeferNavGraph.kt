package com.example.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.ZeferApp
import com.example.ui.screens.admin.AdminScreen
import com.example.ui.screens.admin.AdminViewModel
import com.example.ui.screens.auth.AuthViewModel
import com.example.ui.screens.auth.LoginScreen
import com.example.ui.screens.auth.SignUpScreen
import com.example.ui.screens.detail.ProductDetailScreen
import com.example.ui.screens.detail.ProductDetailViewModel
import com.example.ui.screens.home.HomeScreen
import com.example.ui.screens.home.HomeViewModel
import com.example.ui.screens.splash.SplashScreen
import com.example.ui.screens.splash.SplashViewModel
import com.example.ui.screens.tryon.MyTryOnsScreen
import com.example.ui.screens.tryon.PhotoInputScreen
import com.example.ui.screens.tryon.TryOnResultScreen

@Composable
fun ZeferNavGraph(
    navController: NavHostController,
    app: ZeferApp,
    modifier: Modifier = Modifier
) {
    val container = app.container

    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route,
        modifier = modifier
    ) {
        // Splash Screen
        composable(Screen.Splash.route) {
            val splashViewModel = viewModel { SplashViewModel(container.userPreferences) }
            SplashScreen(
                viewModel = splashViewModel,
                onNavigateToHome = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                },
                onNavigateToLogin = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
            )
        }

        // Login Screen
        composable(Screen.Login.route) {
            val authViewModel = viewModel { AuthViewModel(container.userPreferences) }
            LoginScreen(
                viewModel = authViewModel,
                onNavigateToSignUp = {
                    navController.navigate(Screen.SignUp.route)
                },
                onLoginSuccess = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        // Sign Up Screen
        composable(Screen.SignUp.route) {
            val authViewModel = viewModel { AuthViewModel(container.userPreferences) }
            SignUpScreen(
                viewModel = authViewModel,
                onNavigateToLogin = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.SignUp.route) { inclusive = true }
                    }
                },
                onSignUpSuccess = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.SignUp.route) { inclusive = true }
                    }
                }
            )
        }

        // Home Screen
        composable(Screen.Home.route) {
            val homeViewModel = viewModel { HomeViewModel(container.productRepository) }
            HomeScreen(
                viewModel = homeViewModel,
                onNavigateToDetail = { productId ->
                    navController.navigate(Screen.ProductDetail.createRoute(productId))
                },
                onNavigateToAdmin = {
                    navController.navigate(Screen.Admin.route)
                },
                onNavigateToMyTryOns = {
                    navController.navigate(Screen.MyTryOns.route)
                }
            )
        }

        // Product Detail Screen
        composable(
            route = Screen.ProductDetail.route,
            arguments = listOf(navArgument("productId") { type = NavType.StringType })
        ) { backStackEntry ->
            val productId = backStackEntry.arguments?.getString("productId") ?: ""
            val detailViewModel = viewModel { ProductDetailViewModel(container.productRepository, productId) }
            ProductDetailScreen(
                viewModel = detailViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToTryOn = { prodId, variantId ->
                    navController.navigate(Screen.PhotoInput.createRoute(prodId, variantId))
                }
            )
        }

        // Static Photo Input Screen
        composable(
            route = Screen.PhotoInput.route,
            arguments = listOf(
                navArgument("productId") { type = NavType.StringType },
                navArgument("variantId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val productId = backStackEntry.arguments?.getString("productId") ?: ""
            val variantId = backStackEntry.arguments?.getString("variantId") ?: ""
            PhotoInputScreen(
                productId = productId,
                variantId = variantId,
                productRepository = container.productRepository,
                sessionRepository = container.tryOnSessionRepository,
                onNavigateBack = { navController.popBackStack() },
                onPhotoSelected = {
                    navController.navigate(Screen.TryOnResult.createRoute(productId, variantId))
                }
            )
        }

        // Static Try-On Result Screen
        composable(
            route = Screen.TryOnResult.route,
            arguments = listOf(
                navArgument("productId") { type = NavType.StringType },
                navArgument("variantId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val productId = backStackEntry.arguments?.getString("productId") ?: ""
            val variantId = backStackEntry.arguments?.getString("variantId") ?: ""
            TryOnResultScreen(
                productId = productId,
                variantId = variantId,
                productRepository = container.productRepository,
                sessionRepository = container.tryOnSessionRepository,
                handDetector = container.handDetector,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToMyTryOns = {
                    navController.navigate(Screen.MyTryOns.route)
                }
            )
        }

        // My Saved Try-Ons Screen
        composable(Screen.MyTryOns.route) {
            MyTryOnsScreen(
                sessionRepository = container.tryOnSessionRepository,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // Admin Screen
        composable(Screen.Admin.route) {
            val adminViewModel = viewModel { AdminViewModel(container.productRepository, container.spriteExtractor) }
            AdminScreen(
                viewModel = adminViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
