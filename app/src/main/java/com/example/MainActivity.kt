package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.PreferencesRepository
import com.example.data.SwingDatabase
import com.example.data.SwingRepository
import com.example.ui.TracerViewModel
import com.example.ui.TracerViewModelFactory
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.SwingDetailScreen
import com.example.ui.screens.TracerScreen
import com.example.ui.screens.UploadTraceScreen
import com.example.ui.theme.ElectricEmerald
import com.example.ui.theme.FairwaySurface
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SageMuted
import com.example.ui.theme.SwingArcTheme
import com.example.ui.theme.TurfWhite

object Routes {
    const val TRACER = "tracer"
    const val UPLOAD = "upload"
    const val HISTORY = "history"
    const val DETAIL = "detail/{swingId}"
    fun detail(swingId: Long) = "detail/$swingId"
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val db = SwingDatabase.getInstance(applicationContext)
        val swingRepo = SwingRepository(db.swingDao())
        val prefsRepo = PreferencesRepository(applicationContext)
        val vmFactory = TracerViewModelFactory(swingRepo, prefsRepo)

        setContent {
            SwingArcTheme {
                val viewModel: TracerViewModel = viewModel(factory = vmFactory)
                SwingArcApp(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun SwingArcApp(viewModel: TracerViewModel) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showTopLevelNav = currentRoute == Routes.TRACER ||
        currentRoute == Routes.UPLOAD ||
        currentRoute == Routes.HISTORY

    val navItemColors = NavigationBarItemDefaults.colors(
        selectedIconColor = ObsidianBg,
        selectedTextColor = ElectricEmerald,
        indicatorColor = ElectricEmerald,
        unselectedIconColor = SageMuted,
        unselectedTextColor = SageMuted
    )

    val railItemColors = NavigationRailItemDefaults.colors(
        selectedIconColor = ObsidianBg,
        selectedTextColor = ElectricEmerald,
        indicatorColor = ElectricEmerald,
        unselectedIconColor = SageMuted,
        unselectedTextColor = SageMuted
    )

    fun navigateTopLevel(route: String) {
        navController.navigate(route) {
            popUpTo(Routes.TRACER) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = ObsidianBg,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (showTopLevelNav && !useRail) {
                    NavigationBar(
                        containerColor = FairwaySurface,
                        contentColor = TurfWhite,
                        windowInsets = WindowInsets.navigationBars
                    ) {
                        NavigationBarItem(
                            selected = currentRoute == Routes.TRACER,
                            onClick = { navigateTopLevel(Routes.TRACER) },
                            icon = {
                                Icon(
                                    imageVector = if (currentRoute == Routes.TRACER) Icons.Filled.CameraAlt else Icons.Outlined.CameraAlt,
                                    contentDescription = stringResource(R.string.nav_tracer)
                                )
                            },
                            label = { Text(stringResource(R.string.nav_tracer)) },
                            colors = navItemColors,
                            modifier = Modifier.testTag("nav_tracer_tab")
                        )
                        NavigationBarItem(
                            selected = currentRoute == Routes.UPLOAD,
                            onClick = { navigateTopLevel(Routes.UPLOAD) },
                            icon = {
                                Icon(
                                    imageVector = if (currentRoute == Routes.UPLOAD) Icons.Filled.CloudUpload else Icons.Outlined.CloudUpload,
                                    contentDescription = stringResource(R.string.nav_upload)
                                )
                            },
                            label = { Text(stringResource(R.string.nav_upload)) },
                            colors = navItemColors,
                            modifier = Modifier.testTag("nav_upload_tab")
                        )
                        NavigationBarItem(
                            selected = currentRoute == Routes.HISTORY,
                            onClick = { navigateTopLevel(Routes.HISTORY) },
                            icon = {
                                Icon(
                                    imageVector = if (currentRoute == Routes.HISTORY) Icons.Filled.Timeline else Icons.Outlined.Timeline,
                                    contentDescription = stringResource(R.string.nav_sessions)
                                )
                            },
                            label = { Text(stringResource(R.string.nav_sessions)) },
                            colors = navItemColors,
                            modifier = Modifier.testTag("nav_sessions_tab")
                        )
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (showTopLevelNav && useRail) {
                    NavigationRail(
                        containerColor = FairwaySurface,
                        contentColor = TurfWhite
                    ) {
                        NavigationRailItem(
                            selected = currentRoute == Routes.TRACER,
                            onClick = { navigateTopLevel(Routes.TRACER) },
                            icon = {
                                Icon(
                                    imageVector = if (currentRoute == Routes.TRACER) Icons.Filled.CameraAlt else Icons.Outlined.CameraAlt,
                                    contentDescription = stringResource(R.string.nav_tracer)
                                )
                            },
                            label = { Text(stringResource(R.string.nav_tracer)) },
                            colors = railItemColors,
                            modifier = Modifier.testTag("nav_tracer_rail")
                        )
                        NavigationRailItem(
                            selected = currentRoute == Routes.UPLOAD,
                            onClick = { navigateTopLevel(Routes.UPLOAD) },
                            icon = {
                                Icon(
                                    imageVector = if (currentRoute == Routes.UPLOAD) Icons.Filled.CloudUpload else Icons.Outlined.CloudUpload,
                                    contentDescription = stringResource(R.string.nav_upload)
                                )
                            },
                            label = { Text(stringResource(R.string.nav_upload)) },
                            colors = railItemColors,
                            modifier = Modifier.testTag("nav_upload_rail")
                        )
                        NavigationRailItem(
                            selected = currentRoute == Routes.HISTORY,
                            onClick = { navigateTopLevel(Routes.HISTORY) },
                            icon = {
                                Icon(
                                    imageVector = if (currentRoute == Routes.HISTORY) Icons.Filled.Timeline else Icons.Outlined.Timeline,
                                    contentDescription = stringResource(R.string.nav_sessions)
                                )
                            },
                            label = { Text(stringResource(R.string.nav_sessions)) },
                            colors = railItemColors,
                            modifier = Modifier.testTag("nav_sessions_rail")
                        )
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = Routes.TRACER,
                    modifier = Modifier.fillMaxSize()
                ) {
                    composable(Routes.TRACER) {
                        TracerScreen(
                            viewModel = viewModel,
                            onInspectSwing = { swingId ->
                                navController.navigate(Routes.detail(swingId))
                            }
                        )
                    }
                    composable(Routes.UPLOAD) {
                        UploadTraceScreen(
                            viewModel = viewModel,
                            onInspectSwing = { swingId ->
                                navController.navigate(Routes.detail(swingId))
                            }
                        )
                    }
                    composable(Routes.HISTORY) {
                        HistoryScreen(
                            viewModel = viewModel,
                            onSelectSwing = { swingId ->
                                navController.navigate(Routes.detail(swingId))
                            },
                            onOpenCamera = { navigateTopLevel(Routes.TRACER) },
                            onOpenUpload = { navigateTopLevel(Routes.UPLOAD) }
                        )
                    }
                    composable(
                        route = Routes.DETAIL,
                        arguments = listOf(navArgument("swingId") { type = NavType.LongType })
                    ) { entry ->
                        val swingId = entry.arguments?.getLong("swingId") ?: 0L
                        SwingDetailScreen(
                            swingId = swingId,
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}
