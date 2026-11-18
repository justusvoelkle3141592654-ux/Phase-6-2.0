package app.novaos.ui.launcher

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.Bundle
import android.util.SizeF
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import app.novaos.design.Nova
import app.novaos.ui.common.tr

/** Host für normale Android-App-Widgets (offizielle AppWidgetHost-API). */
class NovaWidgetHost(context: Context) : AppWidgetHost(context.applicationContext, HOST_ID) {
    val manager: AppWidgetManager = AppWidgetManager.getInstance(context.applicationContext)

    fun providers(context: Context): List<AppWidgetProviderInfo> {
        val lam = context.getSystemService(android.content.pm.LauncherApps::class.java)
        return runCatching { lam.profiles.flatMap { manager.getInstalledProvidersForProfile(it) } }.getOrDefault(manager.installedProviders)
            .sortedBy { it.loadLabel(context.packageManager).lowercase() }
    }

    /** Größe in Rasterzellen aus den Angaben des Widgets (Android 12+: targetCell). */
    fun cellSize(info: AppWidgetProviderInfo, columns: Int, rows: Int, cellWidthDp: Float, cellHeightDp: Float, density: Float): Pair<Int, Int> {
        val w = if (info.targetCellWidth > 0) info.targetCellWidth else (info.minWidth / density / cellWidthDp).toInt() + 1
        val h = if (info.targetCellHeight > 0) info.targetCellHeight else (info.minHeight / density / cellHeightDp).toInt() + 1
        return w.coerceIn(1, columns) to h.coerceIn(1, rows)
    }

    companion object {
        const val HOST_ID = 7301
    }
}

@Composable
fun AndroidWidgetView(host: NovaWidgetHost, appWidgetId: Int, modifier: Modifier = Modifier) {
    val info = remember(appWidgetId) { host.manager.getAppWidgetInfo(appWidgetId) }
    if (info == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(tr("Widget nicht mehr verfügbar", "Widget unavailable"), color = Nova.colors.textSecondary)
        }
        return
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val w = maxWidth.value
        val h = maxHeight.value
        AndroidView(
            factory = { ctx ->
                host.createView(ctx, appWidgetId, info).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    setPadding(0, 0, 0, 0)
                }
            },
            update = { v: AppWidgetHostView ->
                runCatching { v.updateAppWidgetSize(Bundle(), listOf(SizeF(w, h))) }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
