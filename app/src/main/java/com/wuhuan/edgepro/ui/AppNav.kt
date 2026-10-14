package com.wuhuan.edgepro.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.wuhuan.edgepro.ui.screens.LogScreen
import com.wuhuan.edgepro.ui.screens.SettingsScreen
import com.wuhuan.edgepro.ui.screens.ShellScreen
import com.wuhuan.edgepro.ui.screens.TaskEditScreen
import com.wuhuan.edgepro.ui.screens.TaskListScreen

object Routes {
    const val TASKS = "tasks"
    const val EDIT = "edit/{id}"
    const val LOGS = "logs"
    const val SHELL = "shell"
    const val SETTINGS = "settings"
    const val NEW_TASK_ID = "new"

    fun edit(id: String) = "edit/$id"
}

@Composable
fun AppNav() {
    val nav = rememberNavController()
    val vm: MainViewModel = viewModel()
    NavHost(navController = nav, startDestination = Routes.TASKS) {
        composable(Routes.TASKS) {
            TaskListScreen(
                vm = vm,
                onEdit = { nav.navigate(Routes.edit(it)) },
                onNew = { nav.navigate(Routes.edit(Routes.NEW_TASK_ID)) },
                onLogs = { nav.navigate(Routes.LOGS) },
                onShell = { nav.navigate(Routes.SHELL) },
                onSettings = { nav.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.EDIT) { entry ->
            TaskEditScreen(
                vm = vm,
                taskId = entry.arguments?.getString("id") ?: Routes.NEW_TASK_ID,
                onBack = { nav.popBackStack() },
            )
        }
        composable(Routes.LOGS) { LogScreen(vm = vm, onBack = { nav.popBackStack() }) }
        composable(Routes.SHELL) { ShellScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.SETTINGS) { SettingsScreen(vm = vm, onBack = { nav.popBackStack() }) }
    }
}
