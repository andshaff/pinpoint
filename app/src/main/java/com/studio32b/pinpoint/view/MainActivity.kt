package com.studio32b.pinpoint.view

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.studio32b.pinpoint.model.PlayerScore
import com.studio32b.pinpoint.ui.theme.PinPointTheme
import com.studio32b.pinpoint.viewmodel.MainViewModel
import com.yalantis.ucrop.UCrop
import com.yalantis.ucrop.UCropActivity
import java.io.File
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.Divider
import androidx.compose.material3.Switch


private val smallColumnGap: Dp = 4.dp
private val normalColumnGap: Dp = 6.dp

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var darkThemeEnabled by remember { mutableStateOf(false) }

            // Animate the primary color when theme changes
            val animatedPrimary = animateColorAsState(
                targetValue = if (darkThemeEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary
            )

            PinPointTheme(darkTheme = darkThemeEnabled) {
                val mainViewModel: MainViewModel = viewModel()
                val context = LocalContext.current

                var showDialog by remember { mutableStateOf(false) }
                var showSettingsDialog by remember { mutableStateOf(false) }

                val sourceImageUri by mainViewModel.sourceImageUri.collectAsState()
                val croppedImageUri by mainViewModel.croppedImageUri.collectAsState()
                val playerScores by mainViewModel.playerScores.collectAsState()
                val loading by mainViewModel.loading.collectAsState()
                val error by mainViewModel.errorMessage.collectAsState()
                val recognizedText by mainViewModel.recognizedText.collectAsState()

                // UCrop launcher
                val cropLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                    if (result.resultCode == RESULT_OK && result.data != null) {
                        UCrop.getOutput(result.data!!)?.let { croppedUri ->
                            mainViewModel.onCroppedImageReceived(context, croppedUri)
                        }
                    } else if (result.resultCode == UCrop.RESULT_ERROR) {
                        val err = UCrop.getError(result.data!!)
                        mainViewModel.setErrorMessage("Crop error: ${err?.message ?: "Unknown"}")
                    }
                }

                fun launchUCrop(sourceUri: Uri) {
                    val destinationFileName = "cropped_${System.currentTimeMillis()}.jpg"
                    val destinationUri = Uri.fromFile(File(context.cacheDir, destinationFileName))

                    val options = UCrop.Options().apply {
                        setStatusBarColor(ContextCompat.getColor(context, android.R.color.transparent))
                        setToolbarColor(ContextCompat.getColor(context, com.studio32b.pinpoint.R.color.colorPrimaryDark))
                        setToolbarWidgetColor(ContextCompat.getColor(context, android.R.color.white))
                        setFreeStyleCropEnabled(true)
                        setAllowedGestures(UCropActivity.NONE, UCropActivity.NONE, UCropActivity.NONE)
                        setHideBottomControls(true)
                    }

                    val uCropIntent = UCrop.of(sourceUri, destinationUri)
                        .withOptions(options)
                        .getIntent(context)

                    cropLauncher.launch(uCropIntent)
                }

                // Pick from gallery
                val pickPhotoLauncher = rememberLauncherForActivityResult(PickVisualMedia()) { uri: Uri? ->
                    uri?.let { launchUCrop(it) }
                }

                // Take picture launcher
                val takePhotoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
                    if (success) sourceImageUri?.let { launchUCrop(it) }
                }

                // Permission launcher
                val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
                    if (isGranted) {
                        val newUri = mainViewModel.createCameraImageUri(context)
                        takePhotoLauncher.launch(newUri)
                    }
                }

                Scaffold(
                    topBar = {
                        PinPointTopAppBar(
                            darkThemeEnabled = darkThemeEnabled,
                            onToggleDarkMode = { darkThemeEnabled = !darkThemeEnabled },
                            onExitClick = { finish() }
                        )
                    },
                    bottomBar = {
                        BottomAppBar {
                            // Spacer to push the IconButton to the end, if desired
                            // Spacer(Modifier.weight(1f))

                            IconButton(onClick = { showDialog = true }) {
                                Icon(
                                    imageVector = Icons.Outlined.AddPhotoAlternate,
                                    contentDescription = "Parse Image" // Important for accessibility
                                )
                            }
                        }
                    }
                ) { paddingValues ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                    ) {
                        croppedImageUri?.let {
                            Text(
                                "Select the bowlers whose scores you want to save.", // Updated text from your file
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 24.dp), // Apply similar padding here
                                textAlign = TextAlign.Center
                            )
                        } ?: Greeting("Welcome!")

                        if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

                        error?.let { Text("Error: $it", color = MaterialTheme.colorScheme.error) }

                        if (playerScores.isNotEmpty()) ScoreList(playerScores)

                        if (recognizedText.isNotBlank()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Raw OCR text:", style = MaterialTheme.typography.titleMedium)
                            Text(
                                recognizedText,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp)
                                    .verticalScroll(rememberScrollState())
                            )
                        }
                    }

                    if (showDialog) {
                        AlertDialog(
                            onDismissRequest = { showDialog = false },
                            title = { Text("Choose an option") },
                            text = { Text("Select Image or Take Photo") },
                            confirmButton = {
                                TextButton(onClick = {
                                    showDialog = false
                                    pickPhotoLauncher.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
                                }) { Text("Select Image") }
                            },
                            dismissButton = {
                                TextButton(onClick = {
                                    showDialog = false
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                        val uri = mainViewModel.createCameraImageUri(context)
                                        takePhotoLauncher.launch(uri)
                                    } else permissionLauncher.launch(Manifest.permission.CAMERA)
                                }) { Text("Take Photo") }
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinPointTopAppBar(
    darkThemeEnabled: Boolean,
    onToggleDarkMode: () -> Unit,
    onExitClick: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    CenterAlignedTopAppBar(
        title = { Text("Pinpoint") },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.primary,
            titleContentColor = MaterialTheme.colorScheme.onPrimary
        ),
        actions = {
            IconButton(onClick = { expanded = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "Menu", tint = MaterialTheme.colorScheme.onPrimary)
            }

            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                // Dark Mode toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Dark Mode  ", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = darkThemeEnabled,
                        onCheckedChange = { onToggleDarkMode() }
                    )
                }
                Divider()
                DropdownMenuItem(
                    text = { Text("Exit") },
                    onClick = {
                        expanded = false
                        onExitClick()
                    }
                )
            }
        }
    )
}


@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Ready to roll? Tap the add image icon below to take or choose a photo of your bowling scores.",
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 24.dp)

    )
}

@Composable
fun ScoreList(scores: List<PlayerScore>) {
    val scoreHeaderTextStyle = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
    val playerNameTextStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp)
    ) {
        items(scores) { score ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    Text(
                        text = score.playerName,
                        style = playerNameTextStyle,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp),
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Score Headers Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("G1", style = scoreHeaderTextStyle, modifier = Modifier.weight(0.7f), textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.width(smallColumnGap))
                        Text("G2", style = scoreHeaderTextStyle, modifier = Modifier.weight(0.7f), textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.width(smallColumnGap))
                        Text("G3", style = scoreHeaderTextStyle, modifier = Modifier.weight(0.7f), textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.width(normalColumnGap))
                        Text("Scr", style = scoreHeaderTextStyle, modifier = Modifier.weight(0.8f), textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.width(smallColumnGap))
                        Text("Hdcp", style = scoreHeaderTextStyle, modifier = Modifier.weight(0.8f), textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.width(smallColumnGap))
                        Text("Total", style = scoreHeaderTextStyle, modifier = Modifier.weight(0.8f), textAlign = TextAlign.End)
                    }

                    // Actual Scores Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(score.gameScores.getOrElse(0) { "" }.toString(), modifier = Modifier.weight(0.7f), textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.width(smallColumnGap))
                        Text(score.gameScores.getOrElse(1) { "" }.toString(), modifier = Modifier.weight(0.7f), textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.width(smallColumnGap))
                        Text(score.gameScores.getOrElse(2) { "" }.toString(), modifier = Modifier.weight(0.7f), textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.width(normalColumnGap))
                        Text(score.scratch.toString(), modifier = Modifier.weight(0.8f), textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.width(smallColumnGap))
                        Text(score.hdcp.toString(), modifier = Modifier.weight(0.8f), textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.width(smallColumnGap))
                        Text(score.total.toString(), modifier = Modifier.weight(0.8f), textAlign = TextAlign.End)
                    }
                }
            }
        }
    }
}
