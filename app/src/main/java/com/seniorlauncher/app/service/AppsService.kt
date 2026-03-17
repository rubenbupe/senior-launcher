package com.seniorlauncher.app.service

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.withClip
import com.seniorlauncher.app.AllowedAppOption
import com.seniorlauncher.app.getMiniAppOptions

object AppsService {
    fun buildOrderedHomeApps(
        availableAppOptions: List<AllowedAppOption>,
        selectedIds: List<String>
    ): List<AllowedAppOption> {
        if (availableAppOptions.isEmpty()) return emptyList()

        val byId = availableAppOptions.associateBy { it.id }

        return selectedIds.mapNotNull { id -> byId[id] }
    }

    fun toRoundedSquareAdaptiveDrawable(
        resources: Resources,
        drawable: Drawable?,
        sizePx: Int
    ): Drawable? {
        if (drawable == null) return null

        val target = sizePx.coerceAtLeast(1)
        val bitmap = createBitmap(target, target)
        val canvas = Canvas(bitmap)
        val radius = target * 0.22f
        val path = Path().apply {
            addRoundRect(
                RectF(0f, 0f, target.toFloat(), target.toFloat()),
                radius,
                radius,
                Path.Direction.CW
            )
        }

        canvas.withClip(path) {
            if (drawable is AdaptiveIconDrawable) {
                val background = drawable.background
                background?.setBounds(0, 0, target, target)
                background?.draw(this)

                val foreground = drawable.foreground
                foreground?.setBounds(0, 0, target, target)
                foreground?.draw(this)
            } else {
                drawable.setBounds(0, 0, target, target)
                drawable.draw(this)
            }

        }

        return bitmap.toDrawable(resources)
    }

    fun getAvailableApps(context: Context): List<AllowedAppOption> {
        val miniApps = getMiniAppOptions()
        val packageManager = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val apps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(
                mainIntent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(mainIntent, PackageManager.MATCH_ALL)
        }

        val launchableApps = apps.mapNotNull { resolveInfo ->
            val activityInfo = resolveInfo.activityInfo ?: return@mapNotNull null
            val packageName = activityInfo.packageName

            if (packageName == context.packageName) return@mapNotNull null

            val label = resolveInfo.loadLabel(packageManager).toString()
            AllowedAppOption(
                id = "pkg:$packageName",
                label = label,
                packageName = packageName,
                isMiniApp = false
            )
        }
            .distinctBy { it.id }
            .sortedBy { it.label.lowercase() }

        return miniApps + launchableApps
    }

    fun openInstalledApp(context: Context, packageName: String) {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }
}