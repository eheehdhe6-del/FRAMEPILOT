package com.example.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ui.export.ExportPreviewScreen
import com.example.ui.library.LibraryViewModel
import com.example.ui.library.ProjectLibraryScreen
import com.example.ui.studio.StudioScreen
import com.example.ui.studio.StudioViewModel

object Routes {
    const val LIBRARY = "library"
    const val STUDIO = "studio/{projectId}"
    const val EXPORT = "export/{projectId}"

    fun studio(projectId: String) = "studio/$projectId"
    fun export(projectId: String) = "export/$projectId"
}

@Composable
fun FramePilotNavHost() {
    val navController = rememberNavController()
    val libraryViewModel: LibraryViewModel = viewModel()

    NavHost(
        navController = navController,
        startDestination = Routes.LIBRARY
    ) {
        composable(Routes.LIBRARY) {
            ProjectLibraryScreen(
                viewModel = libraryViewModel,
                onOpenProject = { projectId ->
                    navController.navigate(Routes.studio(projectId))
                },
                onOpenExport = { projectId ->
                    navController.navigate(Routes.export(projectId))
                }
            )
        }

        composable(
            route = Routes.STUDIO,
            arguments = listOf(navArgument("projectId") { type = NavType.StringType })
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId") ?: return@composable
            val studioViewModel: StudioViewModel = viewModel()
            StudioScreen(
                projectId = projectId,
                viewModel = studioViewModel,
                onBackToLibrary = {
                    libraryViewModel.refresh()
                    navController.popBackStack()
                },
                onOpenExport = { id ->
                    navController.navigate(Routes.export(id))
                }
            )
        }

        composable(
            route = Routes.EXPORT,
            arguments = listOf(navArgument("projectId") { type = NavType.StringType })
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId") ?: return@composable
            ExportPreviewScreen(
                projectId = projectId,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
