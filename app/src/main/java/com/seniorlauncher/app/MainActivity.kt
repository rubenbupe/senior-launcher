package com.seniorlauncher.app

import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.seniorlauncher.app.ui.navigation.AppRouter
import com.seniorlauncher.app.ui.theme.SeniorLauncherTheme

class MainActivity : ComponentActivity() {

    private val requestHomeRoleLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (!isLauncherDefault()) {
                promptSetDefaultLauncher()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideSystemNavigation()
        promptSetLauncherIfNeeded()
        setContent {
            SeniorLauncherTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppRouter()
                }
            }
        }
    }


    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemNavigation()
        }
    }

    private fun hideSystemNavigation() {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.navigationBars())
    }

    private fun promptSetLauncherIfNeeded() {
        if (isLauncherDefault()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            val canRequestHomeRole = roleManager != null &&
                roleManager.isRoleAvailable(RoleManager.ROLE_HOME) &&
                !roleManager.isRoleHeld(RoleManager.ROLE_HOME)

            if (canRequestHomeRole) {
                requestHomeRoleLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME))
                return
            }
        }

        promptSetDefaultLauncher()
    }

    private fun isLauncherDefault(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager?.isRoleAvailable(RoleManager.ROLE_HOME) == true) {
                return roleManager.isRoleHeld(RoleManager.ROLE_HOME)
            }
        }

        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolveInfo = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolveInfo?.activityInfo?.packageName == packageName
    }

    private fun promptSetDefaultLauncher() {
        if (isLauncherDefault()) return

        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .addCategory(Intent.CATEGORY_DEFAULT)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        runCatching {
            startActivity(Intent.createChooser(intent, "Seleccionar launcher por defecto"))
        }.recoverCatching {
            startActivity(intent)
        }.onFailure { error ->
            if (error is ActivityNotFoundException) return
            throw error
        }
    }
}
