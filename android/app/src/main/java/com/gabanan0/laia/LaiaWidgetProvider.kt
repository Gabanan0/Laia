package com.gabanan0.laia

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class LaiaWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val launchIntent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        appWidgetIds.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.laia_widget)
            views.setImageViewBitmap(R.id.laia_widget_face, LaiaFace.bitmap())
            views.setOnClickPendingIntent(R.id.laia_widget_face, pendingIntent)
            manager.updateAppWidget(id, views)
        }
    }
}
