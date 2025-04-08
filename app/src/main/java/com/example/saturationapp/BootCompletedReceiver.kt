package com.example.saturationapp // Use your actual package name

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log // Import Log
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale

class BootCompletedReceiver : BroadcastReceiver() {

    private val TAG = "SaturationBootReceiver" // Tag for logging

    override fun onReceive(context: Context?, intent: Intent?) {
        // Ensure context is not null and the correct action is received
        if (context != null && intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "Boot completed intent received.") // Log that receiver started

            // Use goAsync to handle work off the main thread and keep receiver alive
            val pendingResult: PendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // Read the saved saturation value
                    val prefs = context.getSharedPreferences("SaturationPrefs", Context.MODE_PRIVATE)
                    // Default to 1.0f if nothing saved
                    val lastSaturation = prefs.getFloat("lastSaturation", 1.0f)
                    Log.d(TAG, "Retrieved last saturation: $lastSaturation")

                    // Format the command
                    val formattedValue = String.format(Locale.US, "%.1f", lastSaturation)
                    val command = "service call SurfaceFlinger 1022 f $formattedValue"
                    Log.d(TAG, "Executing command on boot: $command")

                    // Execute the command using libsu (needs root!)
                    val result = Shell.cmd(command).exec()

                    // Log the result
                    if (result.isSuccess) {
                        Log.d(TAG, "Successfully applied saturation $formattedValue on boot.")
                    } else {
                        Log.e(TAG, "Failed to apply saturation on boot. Code: ${result.code}, Error: ${result.err.joinToString()}")
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "Error during boot execution", e)
                } finally {
                    // Must call finish() so the receiver doesn't timeout
                    pendingResult.finish()
                    Log.d(TAG, "Receiver finished.")
                }
            }
        } else {
            Log.w(TAG, "Received intent with null context or wrong action: ${intent?.action}")
        }
    }
}