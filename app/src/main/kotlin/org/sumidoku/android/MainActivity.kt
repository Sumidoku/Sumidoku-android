package org.sumidoku.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import org.sumidoku.android.feature.servers.ServersScreen
import org.sumidoku.android.ui.theme.SumidokuTheme

object Routes {
    const val SERVERS = "servers"
    const val LIBRARY_DEBUG = "library_debug"
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SumidokuTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = Routes.SERVERS) {
                    composable(Routes.SERVERS) {
                        ServersScreen(
                            onServerSelected = { navController.navigate(Routes.LIBRARY_DEBUG) },
                        )
                    }
                    composable(Routes.LIBRARY_DEBUG) {
                        LibraryDebugScreen(onBack = { navController.popBackStack() })
                    }
                }
            }
        }
    }
}
