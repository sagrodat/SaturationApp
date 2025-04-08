package com.example.saturationapp // <-- Replace with your package name

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.example.saturationapp.ui.theme.SaturationAppTheme // <-- Replace with your theme if needed
import com.topjohnwu.superuser.Shell // Import libsu Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale // Required for formatting the float correctly

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Optional: Check if root access is available when the app starts.
        // This runs asynchronously and doesn't block the UI thread.
        Shell.getShell { shell ->
            if (!shell.isRoot) {
                // You could show a persistent message or disable UI elements
                // if root isn't available. For now, we just print to logcat.
                println("WARNING: Root access not available or denied.")
            } else {
                println("INFO: Root access granted.")
            }
        }

        setContent {
            // Apply your app's theme
            SaturationAppTheme { // <-- Replace with your theme if needed
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SaturationControlScreen()
                }
            }
        }
    }
}

@Composable
fun SaturationControlScreen() {
    // State to hold the slider value (0.0f to 2.0f, default is 1.0f)
    var sliderValue by remember { mutableFloatStateOf(1.0f) }
    // Coroutine scope for running background tasks (like root commands)
    val coroutineScope = rememberCoroutineScope()
    // State to show feedback message to the user (e.g., success or failure)
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
            // Clear previous feedback before executing
            feedbackMessage = null

            // Format the shell command with the current slider value.
            // Crucially, use Locale.US to ensure the decimal separator is '.'
            val formattedValue = String.format(Locale.US, "%.1f", sliderValue)
            val command = "service call SurfaceFlinger 1022 f $formattedValue"

            // Launch the root command execution in a background IO thread
            // This prevents blocking the main UI thread.
            coroutineScope.launch(Dispatchers.IO) {
                println("Executing command: $command") // Log the command being run

                // Execute the command using libsu's Shell.su()
                val result: Shell.Result = Shell.cmd(command).exec()

                // After execution, switch back to the Main thread to update UI
                launch(Dispatchers.Main) {
                    if (result.isSuccess) {
                        feedbackMessage = "Saturation set to $formattedValue"
                        println("Command successful!")
                    } else {
                        // Provide detailed error feedback if it failed
                        val errorOutput = result.err.joinToString("\n").trim()
                        feedbackMessage = "Failed. Error code: ${result.code}" +
                                if (errorOutput.isNotEmpty()) "\nDetails: $errorOutput" else ""

                        // Log detailed error for debugging
                        println("Command failed: $command")
                        println("Exit Code: ${result.code}")
                        println("STDOUT: ${result.out.joinToString("\n")}")
                        println("STDERR: ${errorOutput}")
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