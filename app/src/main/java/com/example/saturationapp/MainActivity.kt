package com.example.saturationapp // <-- Replace with your package name

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.provider.Settings.Global.putFloat
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.example.saturationapp.ui.theme.SaturationAppTheme // <-- Replace with your theme if needed
import com.topjohnwu.superuser.Shell // Import libsu Shell
import com.topjohnwu.superuser.internal.Utils.context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale // Required for formatting the float correctly

class MainActivity : ComponentActivity() {

    // --- State to control the visibility of the "No Root" dialog ---
    private var showNoRootDialog by mutableStateOf(false)
    // ---

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check for root access when the app starts.
        Shell.getShell { shell ->
            if (!shell.isRoot) {
                println("WARNING: Root access not available or denied.")
                // --- Trigger the dialog if no root ---
                // Ensure state update happens on the main thread
                runOnUiThread {
                    showNoRootDialog = true
                }
                // ---
            } else {
                println("INFO: Root access granted.")
            }
        }

        setContent {
            SaturationAppTheme { // Use your app's theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Your main screen content
                    SaturationControlScreen()

                    // --- Conditionally display the AlertDialog ---
                    if (showNoRootDialog) {
                        AlertDialog(
                            onDismissRequest = {
                                // Required: Action when dismissed by clicking outside or back button
                                showNoRootDialog = false
                            },
                            title = { Text("Root Access Required") },
                            text = { Text("This application requires root permissions to change screen saturation. Please ensure your device is rooted and grant permissions if prompted.") },
                            confirmButton = {
                                Button(onClick = {
                                    // Action for the confirmation button
                                    showNoRootDialog = false
                                }) {
                                    Text("OK")
                                }
                            }
                        )
                    }
                    // --- End of AlertDialog ---
                }
            }
        }
    }
}

@SuppressLint("RestrictedApi")
@Composable
fun SaturationControlScreen() {
    // Get context for SharedPreferences
    val context = LocalContext.current

    // Read initial value from SharedPreferences
    val initialSaturation = remember {
        val prefs = context.getSharedPreferences("SaturationPrefs", Context.MODE_PRIVATE)
        // Default to 1.0f if no value is saved
        prefs.getFloat("lastSaturation", 1.0f)
    }

    // State to hold the slider value, initialized with the loaded value
    var sliderValue by remember { mutableFloatStateOf(initialSaturation) }

    // Coroutine scope
    val coroutineScope = rememberCoroutineScope()
    // Feedback message state
    var feedbackMessage by remember { mutableStateOf<String?>(null) }



    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp), // Add padding around the content
        horizontalAlignment = Alignment.CenterHorizontally, // Center items horizontally
        verticalArrangement = Arrangement.Center // Center items vertically
    ) {
        Text(
            text = "Screen Saturation",
            style = MaterialTheme.typography.headlineMedium // Use a nice title style
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "(Root Required)",
            style = MaterialTheme.typography.labelSmall // Smaller text for the note
        )


        Spacer(modifier = Modifier.height(32.dp)) // More space before the slider

        // Display the current slider value, formatted to one decimal place
        Text(
            text = "Value: %.1f".format(Locale.US, sliderValue), // Use Locale.US for decimal point
            style = MaterialTheme.typography.bodyLarge
        )

        Spacer(modifier = Modifier.height(8.dp))

        Slider(
            value = sliderValue,
            onValueChange = { newValue ->
                // Update the slider value when the user interacts
                sliderValue = newValue
                feedbackMessage = null // Clear previous feedback when slider moves
            },
            valueRange = 0.0f..2.0f, // Define the min and max saturation values
            steps = 19, // Creates 20 steps (0.0, 0.1, ..., 2.0). Remove if you want continuous sliding.
            modifier = Modifier.padding(horizontal = 16.dp) // Add padding to slider ends
        )

        Spacer(modifier = Modifier.height(32.dp)) // More space before the button

        Button(onClick = {
            // Get the target value from the slider state
            val targetValue = sliderValue
            // --- Add Log: Value at start of onClick ---
            println("DEBUG: onClick triggered. Target sliderValue = $targetValue")
            // ---

            feedbackMessage = null // Clear previous feedback

            // Get context to read SharedPreferences
            val currentContext = context // Use the context obtained earlier via LocalContext.current

            // Launch the background task
            coroutineScope.launch(Dispatchers.IO) {
                var finalResult: Shell.Result? = null
                var valueActuallySet = targetValue // Keep track of the value we intend to save

                try {
                    // Read the *previous* value that was successfully set by this app
                    val prefs = currentContext.getSharedPreferences("SaturationPrefs", Context.MODE_PRIVATE)
                    val previousValue = prefs.getFloat("lastSaturation", 1.0f) // Default if none saved
                    println("DEBUG: Previous saved value = $previousValue")

                    // --- Check if workaround is needed: Target is 1.0, but previous wasn't 1.0 ---
                    if (targetValue == 1.0f && previousValue != 1.0f) { // <-- MODIFIED CONDITION
                        println("DEBUG: Applying workaround for non-1.0 -> 1.0")

                        // 1. Set intermediate value (e.g., 1.1)
                        val intermediateValue = 1.1f // Or try 0.9f if 1.1 causes issues
                        val intermediateFormatted = String.format(Locale.US, "%.1f", intermediateValue)
                        val intermediateCommand = "service call SurfaceFlinger 1022 f $intermediateFormatted"
                        println("Executing intermediate command: $intermediateCommand")
                        val intermediateResult = Shell.cmd(intermediateCommand).exec()
                        if (!intermediateResult.isSuccess) {
                            println("DEBUG: Intermediate command failed! Code: ${intermediateResult.code}, Err: ${intermediateResult.err.joinToString()}")
                            // Even if intermediate fails, still try the final command
                        } else {
                            println("DEBUG: Intermediate command successful.")
                            // Optional short delay - testing showed 500ms helped in one case, adjust if needed
                            try { Thread.sleep(500) } catch (e: InterruptedException) {}
                        }

                        // 2. Set final target value (1.0)
                        val finalFormatted = String.format(Locale.US, "%.1f", targetValue) // Should be "1.0"
                        val finalCommand = "service call SurfaceFlinger 1022 f $finalFormatted"
                        println("Executing final command: $finalCommand")
                        finalResult = Shell.cmd(finalCommand).exec()
                        valueActuallySet = targetValue // We attempted to set 1.0

                    } else {
                        // --- Normal execution path (target is not 1.0, OR target is 1.0 and previous was already 1.0) ---
                        val formattedValue = String.format(Locale.US, "%.1f", targetValue)
                        val command = "service call SurfaceFlinger 1022 f $formattedValue"
                        println("Executing command: $command")
                        finalResult = Shell.cmd(command).exec()
                        valueActuallySet = targetValue
                    }

                    // --- Handle the result (primarily based on the final command attempted) ---
                    launch(Dispatchers.Main) {
                        if (finalResult.isSuccess) { // Double check nullability if needed, but should be non-null here
                            val finalFormattedValue = String.format(Locale.US, "%.1f", valueActuallySet)
                            feedbackMessage = "Saturation set to $finalFormattedValue"
                            println("Command successful!")
                            // --- UPDATED SAVE LOGIC ---
                            val writePrefs = currentContext.getSharedPreferences("SaturationPrefs", Context.MODE_PRIVATE)
                            writePrefs.edit().apply {
                                // Always save the last successfully applied value
                                putFloat("lastSaturation", valueActuallySet)

                                // ONLY save as the user's custom pref if it's NOT 1.0
                                if (valueActuallySet != 1.0f) {
                                    putFloat("userCustomSaturation", valueActuallySet)
                                    println("DEBUG: Saved userCustomSaturation = $valueActuallySet")
                                }
                                apply()
                            }
                            // --- VALUE SAVED ---
                        } else {
                            // --- Enhanced Error Logging ---
                            val errorOutput = finalResult?.err?.joinToString("\n")?.trim() ?: "N/A"
                            val stdOutput = finalResult?.out?.joinToString("\n")?.trim() ?: "N/A"
                            val exitCode = finalResult?.code ?: -1
                            // Determine which command likely failed for logging
                            val failedCommand = if (targetValue == 1.0f && previousValue != 1.0f) {
                                "service call SurfaceFlinger 1022 f 1.0 (after workaround)"
                            } else {
                                "service call SurfaceFlinger 1022 f ${String.format(Locale.US, "%.1f", targetValue)}"
                            }

                            feedbackMessage = "Failed. Error code: $exitCode" +
                                    if (errorOutput.isNotEmpty() && errorOutput != "N/A") "\nDetails: $errorOutput" else ""

                            println("Command failed: $failedCommand")
                            println("DEBUG: Command Failed! Exit Code: $exitCode")
                            println("DEBUG: === STDERR START ===")
                            println(errorOutput.ifEmpty { "[No stderr output]" })
                            println("DEBUG: === STDERR END ===")
                            println("DEBUG: === STDOUT START ===")
                            println(stdOutput.ifEmpty { "[No stdout output]" })
                            println("DEBUG: === STDOUT END ===")
                            // --- End Enhanced Error Logging ---
                        }
                    }

                } catch (e: Exception) {
                    // Catch any unexpected errors during IO/Shell execution
                    launch(Dispatchers.Main) {
                        Log.e("SaturationAppOnClick", "Error in onClick coroutine", e)
                        feedbackMessage = "An unexpected error occurred."
                    }
                }
            }
        }) {
            Text("Apply Saturation")
        }

        Spacer(
            modifier = Modifier
                .height(16.dp)
                .alpha(if (feedbackMessage != null) 1f else 0f) // Visible only with feedback
        )

        // Text composable for the feedback message
        // It always occupies space (based on potential content) but is only visible
        // and shows actual content when feedbackMessage is not null.
        Text(
            text = feedbackMessage ?: "", // Show message or empty string (reserves some space)
            style = MaterialTheme.typography.bodyMedium,
            color = if (feedbackMessage?.startsWith("Failed") == true) MaterialTheme.colorScheme.error else LocalContentColor.current,
            modifier = Modifier.alpha(if (feedbackMessage != null) 1f else 0f) // Visible only with feedback
        )



        Image(
            painter = painterResource(id = R.drawable.reference_image), // Assuming you named it reference_image.png
            contentDescription = "Color Reference Image",
            modifier = Modifier.fillMaxWidth().height(1000.dp) // Adjust size as needed
        )
        Spacer(modifier = Modifier.height(50.dp)) // Space before your slider/controls


    }
}

// Basic Preview for Android Studio's Compose Preview panel
@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    SaturationAppTheme { // <-- Replace with your theme if needed
        SaturationControlScreen()
    }
}