package com.example.saturationapp // Use your actual package name

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale

class WidgetClickReceiver : BroadcastReceiver() {

    private val TAG = "SaturationWidgetClick"
    private val WIDGET_CLICK_ACTION = "com.example.saturationapp.WIDGET_CLICK" // Unique action string

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent?.action != WIDGET_CLICK_ACTION) {
            Log.w(TAG, "Ignoring intent: $intent")
            return
        }

        Log.d(TAG, "Widget click received")

        val pendingResult: PendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            var finalResult: Shell.Result? = null
            var valueIntendedToSet: Float = 1.0f // Value we aim to save if successful
            var targetValue: Float // The saturation value determined by the toggle logic
            var nextWidgetStateIsDefault: Boolean // Tracks the next toggle state

            try {
                val prefs = context.getSharedPreferences("SaturationPrefs", Context.MODE_PRIVATE)
                // Read the *user's preferred custom* saturation (last non-1.0 value set in app)
                // Default to 1.5f if never set, change if you prefer another default
                val userCustomSaturation = prefs.getFloat("userCustomSaturation", 1.5f)

                // Track what the widget *thinks* it last set (true=Default 1.0, false=Custom)
                val wasLastSetToDefault = prefs.getBoolean("widgetLastSetToDefault", true)

                // Determine the target value based on the toggle state
                if (wasLastSetToDefault) {
                    // Last time widget set 1.0, so now set the user's custom value
                    targetValue = userCustomSaturation
                    nextWidgetStateIsDefault = false
                    Log.d(TAG, "Widget toggling to USER SAVED value: $targetValue (from userCustomSaturation)")
                } else {
                    // Last time widget set custom, so now set default 1.0
                    targetValue = 1.0f
                    nextWidgetStateIsDefault = true
                    Log.d(TAG, "Widget toggling to DEFAULT value: $targetValue")
                }
                // Store the value we are trying to set for saving later
                valueIntendedToSet = targetValue

                // Read the last value *actually applied* by app or widget (for workaround check)
                val currentValue = prefs.getFloat("lastSaturation", 1.0f)
                Log.d(TAG, "Current lastSaturation value = $currentValue (for workaround check)")

                // --- Check if workaround is needed: Target is 1.0, previous wasn't 1.0 ---
                if (targetValue == 1.0f && currentValue != 1.0f) {
                    Log.d(TAG, "Applying non-1.0 -> 1.0 workaround from widget")

                    // 1. Set intermediate value (e.g., 1.1)
                    val intermediateValue = 1.1f
                    val intermediateFormatted = String.format(Locale.US, "%.1f", intermediateValue)
                    val intermediateCommand = "service call SurfaceFlinger 1022 f $intermediateFormatted"
                    Log.d(TAG,"Executing intermediate command: $intermediateCommand")
                    val intermediateResult = Shell.cmd(intermediateCommand).exec()
                    if (!intermediateResult.isSuccess) {
                        Log.w(TAG,"Intermediate command failed! Code: ${intermediateResult.code}, Err: ${intermediateResult.err.joinToString()}")
                    } else {
                        Log.d(TAG, "Intermediate command successful.")
                        try { Thread.sleep(500) } catch (e: InterruptedException) {}
                    }

                    // 2. Set final target value (1.0)
                    val finalFormatted = String.format(Locale.US, "%.1f", targetValue)
                    val finalCommand = "service call SurfaceFlinger 1022 f $finalFormatted"
                    Log.d(TAG, "Executing final command: $finalCommand")
                    finalResult = Shell.cmd(finalCommand).exec()

                } else {
                    // --- Normal execution path ---
                    val formattedValue = String.format(Locale.US, "%.1f", targetValue)
                    val command = "service call SurfaceFlinger 1022 f $formattedValue"
                    Log.d(TAG, "Executing command from widget: $command")
                    finalResult = Shell.cmd(command).exec()
                }

                // --- Handle the final result ---
                if (finalResult != null && finalResult.isSuccess) {
                    Log.d(TAG, "Widget final command successful.")
                    // Save the new toggle state AND the saturation value that was just applied
                    with(prefs.edit()) { // Use 'with' or '.apply' as needed
                        putBoolean("widgetLastSetToDefault", nextWidgetStateIsDefault)
                        // ALWAYS update lastSaturation with the value we just successfully applied
                        putFloat("lastSaturation", valueIntendedToSet)
                        apply()
                    }
                    Log.d(TAG, "Widget state updated: lastSetToDefault=$nextWidgetStateIsDefault, lastSaturation=$valueIntendedToSet")

                } else {
                    val exitCode = finalResult?.code ?: -1
                    val errorOutput = finalResult?.err?.joinToString("\n")?.trim() ?: "N/A"
                    Log.e(TAG, "Widget final command failed. Code: $exitCode, Error: $errorOutput")
                    // Don't update state if command failed
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error during widget click execution", e)
            } finally {
                pendingResult.finish()
                Log.d(TAG, "Widget click processing finished.")
            }
        }
    }
}