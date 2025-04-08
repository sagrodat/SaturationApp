package com.example.saturationapp // <-- Replace with your package name

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.provider.Settings.Global.putFloat // This import seems unused and might be incorrect? Consider removing if not needed.
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons // Import icons
import androidx.compose.material.icons.filled.Info // Import Info icon
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color // Import Color for tinting
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.example.saturationapp.ui.theme.SaturationAppTheme // Use your theme
import com.topjohnwu.superuser.Shell // Import libsu Shell
// Remove this unused import if `context` below refers to LocalContext.current
// import com.topjohnwu.superuser.internal.Utils.context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale // Required for formatting the float correctly

class MainActivity : ComponentActivity() {

    // State for the "No Root" dialog
    private var showNoRootDialog by mutableStateOf(false)
    // --- State for the "Info" dialog ---
    private var showInfoDialog by mutableStateOf(false)
    // ---

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check for root access when the app starts.
        Shell.getShell { shell ->
            if (!shell.isRoot) {
                println("WARNING: Root access not available or denied.")
                runOnUiThread {
                    showNoRootDialog = true
                }
            } else {
                println("INFO: Root access granted.")
            }
        }

        setContent {
            SaturationAppTheme { // Use your app's theme
                // --- Wrap Surface in a Box to allow overlaying ---
                Box(modifier = Modifier.fillMaxSize()) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        // Your main screen content
                        SaturationControlScreen() // Contains the Column

                        // --- Conditionally display the "No Root" AlertDialog ---
                        if (showNoRootDialog) {
                            AlertDialog(
                                onDismissRequest = { showNoRootDialog = false },
                                title = { Text("Root Access Required") },
                                text = { Text("This application requires root permissions to change screen saturation. Please ensure your device is rooted and grant permissions if prompted.") },
                                confirmButton = {
                                    Button(onClick = { showNoRootDialog = false }) {
                                        Text("OK")
                                    }
                                }
                            )
                        }
                        // --- End of "No Root" AlertDialog ---

                        // --- Conditionally display the "Info" AlertDialog ---
                        if (showInfoDialog) {
                            AlertDialog(
                                onDismissRequest = { showInfoDialog = false },
                                title = { Text("App Information") },
                                text = { Text("The saturation value you set is saved automatically. On device boot, the last saved value will be applied.\n\nA homescreen widget is also available to quickly toggle between your saved saturation and the default (1.0) setting.") },
                                confirmButton = {
                                    Button(onClick = { showInfoDialog = false }) {
                                        Text("OK")
                                    }
                                }
                            )
                        }
                        // --- End of "Info" AlertDialog ---

                    } // End Surface

                    // --- Info Button positioned in the Top End corner ---
                    IconButton(
                        onClick = { showInfoDialog = true },
                        modifier = Modifier
                            .align(Alignment.TopEnd) // Position in corner
                            .padding(12.dp) // Add some padding from the edge
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Info, // Use standard Material info icon
                            contentDescription = "App Information",
                            tint = Color.White // Set the icon color to white
                        )
                    }
                    // --- End Info Button ---

                } // End Box
            } // End Theme
        } // End setContent
    } // End onCreate
} // End Activity


@SuppressLint("RestrictedApi") // Consider if this annotation is truly needed or if the usage can be refactored
@Composable
fun SaturationControlScreen() {
    // Get context for SharedPreferences
    val context = LocalContext.current

    // Read initial value from SharedPreferences
    val initialSaturation = remember {
        val prefs = context.getSharedPreferences("SaturationPrefs", Context.MODE_PRIVATE)
        prefs.getFloat("lastSaturation", 1.0f)
    }

    var sliderValue by remember { mutableFloatStateOf(initialSaturation) }
    val coroutineScope = rememberCoroutineScope()
    var feedbackMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            // Add top padding to avoid overlapping with potential status bar or the info button area
            .padding(top = 40.dp), // Adjust as needed
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // --- Content of SaturationControlScreen ---
        Text(
            text = "Screen Saturation",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "(Root Required)",
            style = MaterialTheme.typography.labelSmall
        )
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = "Value: %.1f".format(Locale.US, sliderValue),
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(8.dp))
        Slider(
            value = sliderValue,
            onValueChange = { newValue ->
                sliderValue = newValue
                feedbackMessage = null
            },
            valueRange = 0.0f..2.0f,
            steps = 19,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(32.dp))

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
                        // Make sure finalResult is not null before accessing isSuccess
                        if (finalResult != null && finalResult.isSuccess) {
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

        // --- Feedback message display (using alpha) ---
        Spacer(
            modifier = Modifier
                .height(16.dp)
                .alpha(if (feedbackMessage != null) 1f else 0f)
        )
        Text(
            text = feedbackMessage ?: "",
            style = MaterialTheme.typography.bodyMedium,
            color = if (feedbackMessage?.startsWith("Failed") == true) MaterialTheme.colorScheme.error else LocalContentColor.current,
            modifier = Modifier.alpha(if (feedbackMessage != null) 1f else 0f)
        )
        // --- End of feedback message display ---


        // Optional Reference Image - Uncomment and ensure R.drawable.reference_image exists
        Image(
            painter = painterResource(id = R.drawable.reference_image),
            contentDescription = "Color Reference Image",
            modifier = Modifier.fillMaxWidth().height(400.dp) // Adjust size
        )
        Spacer(modifier = Modifier.height(16.dp))


    } // End Column
} // End SaturationControlScreen

// Basic Preview remains the same, maybe update to show Box/Button
@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    SaturationAppTheme {
        Box(modifier = Modifier.fillMaxSize()){
            SaturationControlScreen()
            IconButton(onClick = { }, modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)) {
                Icon(imageVector = Icons.Filled.Info, contentDescription = "Info Preview", tint = Color.White)
            }
        }
    }
}