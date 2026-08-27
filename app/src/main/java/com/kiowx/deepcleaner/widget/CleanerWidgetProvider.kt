package com.kiowx.deepcleaner.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.kiowx.deepcleaner.MainActivity
import com.kiowx.deepcleaner.R
import com.kiowx.deepcleaner.core.AppPreferences
import com.kiowx.deepcleaner.core.StorageAccess
import com.kiowx.deepcleaner.core.formatBytes

class CleanerWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { manager.updateAppWidget(it, buildViews(context)) }
    }

    companion object {
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = android.content.ComponentName(context, CleanerWidgetProvider::class.java)
            manager.getAppWidgetIds(component).forEach { manager.updateAppWidget(it, buildViews(context)) }
        }

        private fun buildViews(context: Context): RemoteViews {
            val storage = StorageAccess.snapshot(context)
            val preferences = AppPreferences(context)
            val launch = Intent(context, MainActivity::class.java)
            val scan = Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_SAFE_SCAN)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            return RemoteViews(context.packageName, R.layout.widget_cleaner).apply {
                setTextViewText(R.id.widget_percent, "${(storage.usedFraction * 100).toInt()}% 已用")
                setTextViewText(R.id.widget_free, "可用 ${formatBytes(storage.available)}")
                setTextViewText(R.id.widget_cleanable, "上次发现 ${formatBytes(preferences.lastScanBytes)} 可清理")
                setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(context, 8100, launch, flags))
                setOnClickPendingIntent(R.id.widget_scan, PendingIntent.getActivity(context, 8101, scan, flags))
            }
        }
    }
}
