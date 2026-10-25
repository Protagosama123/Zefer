package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.example.ui.navigation.ZeferNavGraph
import com.example.ui.theme.ZeferBg
import com.example.ui.theme.ZeferTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val zeferApp = application as ZeferApp

        setContent {
            ZeferTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = ZeferBg
                ) {
                    val navController = rememberNavController()
                    ZeferNavGraph(
                        navController = navController,
                        app = zeferApp
                    )
                }
            }
        }
    }
}
