package com.example.saturationapp // Use your actual package name

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews

class SaturationWidgetProvider : AppWidgetProvider() {

    private val WIDGET_CLICK_ACTION = "com.example.saturationapp.WIDGET_CLICK" // Must match receiver

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // Perform this loop procedure for each App Widget that belongs to this provider
        appWidgetIds.forEach { appWidgetId ->
            // Create an Intent to launch our WidgetClickReceiver
            val intent = Intent(context, WidgetClickReceiver::class.java).apply {
                action = WIDGET_CLICK_ACTION
                // Add widget ID in case needed later, though not used in current receiver
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }

            // Create the PendingIntent that will perform the broadcast
            val pendingIntent: PendingIntent = PendingIntent.getBroadcast(
                context,
                appWidgetId, // Use widget ID as request code to ensure uniqueness
                intent,
                // Use FLAG_IMMUTABLE or FLAG_MUTABLE based on Android version requirements
                // FLAG_UPDATE_CURRENT ensures extras are updated if intent is remade
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }
            )

            // Get the layout for the App Widget and attach an on-click listener
            val views = RemoteViews(context.packageName, R.layout.saturation_widget)
            views.setOnClickPendingIntent(R.id.widget_button, pendingIntent) // Set click listener on the button

            // Tell the AppWidgetManager to perform an update on the current app widget
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    // Handle widget deletion, enabling/disabling if needed (optional)
    // override fun onDeleted(context: Context, appWidgetIds: IntArray) { ... }
    // override fun onEnabled(context: Context) { ... }
    // override fun onDisabled(context: Context) { ... }
}