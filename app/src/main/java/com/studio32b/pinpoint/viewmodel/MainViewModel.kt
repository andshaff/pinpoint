package com.studio32b.pinpoint.viewmodel

import com.studio32b.pinpoint.BuildConfig
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.studio32b.pinpoint.model.PlayerScore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.abs

class MainViewModel : ViewModel() {

    // UI state flows
    private val _playerScores = MutableStateFlow<List<PlayerScore>>(emptyList())
    val playerScores: StateFlow<List<PlayerScore>> = _playerScores

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val _cameraPermissionGranted = MutableStateFlow(false)
    val cameraPermissionGranted: StateFlow<Boolean> = _cameraPermissionGranted

    // URIs (source/cropped)
    private val _sourceImageUri = MutableStateFlow<Uri?>(null)
    val sourceImageUri: StateFlow<Uri?> = _sourceImageUri

    private val _croppedImageUri = MutableStateFlow<Uri?>(null)
    val croppedImageUri: StateFlow<Uri?> = _croppedImageUri

    // NEW: Raw recognized OCR text
    private val _recognizedText = MutableStateFlow("")
    val recognizedText: StateFlow<String> = _recognizedText

    fun setCameraPermissionGranted(granted: Boolean) {
        _cameraPermissionGranted.value = granted
    }

    fun createCameraImageUri(context: Context): Uri {
        val photoFile = File.createTempFile("photo_${System.currentTimeMillis()}", ".jpg", context.cacheDir)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", photoFile)
        _sourceImageUri.value = uri
        _croppedImageUri.value = null
        _recognizedText.value = ""  // Clear OCR text on new image
        return uri
    }

    fun onCroppedImageReceived(context: Context, croppedUri: Uri) {
        _croppedImageUri.value = croppedUri
        processImage(context, croppedUri)
    }

    fun processImage(context: Context, imageUri: Uri) {
        _loading.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            try {
                val inputStream = context.contentResolver.openInputStream(imageUri)
                val bitmap = inputStream?.use { android.graphics.BitmapFactory.decodeStream(it) }
                if (bitmap == null) {
                    _errorMessage.value = "Failed to decode image."
                    _loading.value = false
                    return@launch
                }

                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                val inputImage = InputImage.fromBitmap(bitmap, 0)

                recognizer.process(inputImage)
                    .addOnSuccessListener { visionText ->
                        _recognizedText.value = visionText.text
                        if (BuildConfig.DEBUG) {
                            Log.d("MainViewModel", "Raw OCR Text:\n${visionText.text}")
                        }

                        val parsed = parseVisionText(visionText)
                        _playerScores.value = parsed
                        _errorMessage.value = null
                        _loading.value = false
                    }
                    .addOnFailureListener { e ->
                        if (BuildConfig.DEBUG) {
                            Log.e("MainViewModel", "OCR failed", e)
                        }
                        _errorMessage.value = "OCR failed: ${e.message ?: "unknown"}"
                        _loading.value = false
                    }
            } catch (ex: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.e("MainViewModel", "Exception in processImage", ex)
                }
                _errorMessage.value = "Error: ${ex.message ?: "unknown"}"
                _loading.value = false
            }
        }
    }

    private fun parseVisionText(visionText: Text): List<PlayerScore> {
        val knownHeaders = listOf("Player", "Game 1", "Game 2", "Game 3", "Scratch", "Hdcp", "Total")

        val allElements = visionText.textBlocks.flatMap { it.lines }.flatMap { it.elements }
        if (allElements.isEmpty()) {
            if (BuildConfig.DEBUG) {
                Log.w("MainViewModel", "No text elements found")
            }
            return emptyList()
        }

        val rowClusters = mutableListOf<MutableList<Text.Element>>()
        val sortedElements = allElements.sortedBy { it.boundingBox?.top ?: 0 }

        for (element in sortedElements) {
            val top = element.boundingBox?.top ?: continue
            val cluster = rowClusters.find { cluster ->
                val clusterTop = cluster[0].boundingBox?.top ?: 0
                abs(clusterTop - top) <= 15
            }
            if (cluster != null) cluster.add(element) else rowClusters.add(mutableListOf(element))
        }

        val sortedRows = rowClusters.map { cluster -> cluster.sortedBy { it.boundingBox?.left ?: 0 } }

        val headerRowIndex = sortedRows.indexOfFirst { row ->
            val texts = row.map { it.text }
            val matchedHeaders = texts.mapNotNull { closestHeader(it, knownHeaders) }
            matchedHeaders.size >= 3
        }

        if (headerRowIndex == -1) {
            if (BuildConfig.DEBUG) {
                Log.w("MainViewModel", "No header row found.")
            }
            return emptyList()
        }

        val headerRow = sortedRows[headerRowIndex]
        val combinedHeaders = mutableListOf<Pair<String, Int>>()
        var i = 0
        while (i < headerRow.size) {
            val elem = headerRow[i]
            val text = elem.text
            if (text.equals("Game", ignoreCase = true) && i + 1 < headerRow.size) {
                val nextElem = headerRow[i + 1]
                if (nextElem.text.matches(Regex("\\d+"))) {
                    val combinedText = "Game ${nextElem.text}"
                    val left = elem.boundingBox?.left ?: 0
                    val right = nextElem.boundingBox?.right ?: 0
                    val centerX = (left + right) / 2
                    combinedHeaders.add(combinedText to centerX)
                    i += 2
                    continue
                }
            }
            val box = elem.boundingBox
            val centerX = if (box != null) box.left + box.width() / 2 else 0
            combinedHeaders.add(text to centerX)
            i++
        }

        val headers = mutableListOf<String>()
        val headerCenters = mutableListOf<Int>()
        for ((text, centerX) in combinedHeaders) {
            if (!headers.contains(text)) {
                headers.add(text)
                headerCenters.add(centerX)
            }
        }

        if (BuildConfig.DEBUG) {
            Log.d("MainViewModel", "Detected headers: $headers")
        }

        val playerColRight = if (headerCenters.size > 1)
            (headerCenters[0] + headerCenters[1]) / 2 else headerCenters[0] + 200

        val playerScores = mutableListOf<PlayerScore>()

        for (rowIndex in (headerRowIndex + 1) until sortedRows.size) {
            val row = sortedRows[rowIndex]

            if (row.any { it.text.equals("Team", ignoreCase = true) }) break

            val columns = assignWordsToColumnsWithPlayer(row, headerCenters, playerColRight)

            while (columns.size < headers.size) columns.add("")

            val playerName = columns.getOrNull(0) ?: ""
            val game1 = columns.getOrNull(1) ?: ""
            val game2 = columns.getOrNull(2) ?: ""
            val game3 = columns.getOrNull(3) ?: ""
            val scratch = columns.getOrNull(4) ?: ""
            val hdcp = columns.getOrNull(5) ?: ""
            val total = columns.getOrNull(6) ?: ""

            playerScores.add(
                PlayerScore(
                    playerName.trim(),
                    listOf(game1.trim(), game2.trim(), game3.trim()),
                    scratch.trim(),
                    hdcp.trim(),
                    total.trim()
                )
            )
        }

        return playerScores
    }

    private fun assignWordsToColumnsWithPlayer(
        row: List<Text.Element>,
        columnCenters: List<Int>,
        playerColumnRightBoundary: Int,
        threshold: Int = 40
    ): MutableList<String> {
        if (columnCenters.isEmpty()) return mutableListOf(row.joinToString(" ") { it.text })

        val columns = MutableList(columnCenters.size) { "" }

        val playerWords = row.filter { word ->
            val box = word.boundingBox ?: return@filter false
            val centerX = box.left + box.width() / 2
            centerX <= playerColumnRightBoundary + threshold
        }
        columns[0] = playerWords.joinToString(" ") { it.text }

        val remainingWords = row.filterNot { playerWords.contains(it) }

        for (colIndex in 1 until columnCenters.size) {
            val colX = columnCenters[colIndex]
            val wordsForColumn = remainingWords.filter { word ->
                val box = word.boundingBox ?: return@filter false
                val centerX = box.left + box.width() / 2
                kotlin.math.abs(centerX - colX) < threshold
            }
            columns[colIndex] = wordsForColumn.joinToString(" ") { it.text }
        }

        return columns
    }

    private fun levenshtein(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j

        for (i in 1..a.length) {
            for (j in 1..b.length) {
                dp[i][j] = if (a[i - 1].equals(b[j - 1], ignoreCase = true)) {
                    dp[i - 1][j - 1]
                } else {
                    minOf(dp[i - 1][j] + 1, dp[i][j - 1] + 1, dp[i - 1][j - 1] + 1)
                }
            }
        }
        return dp[a.length][b.length]
    }

    private fun closestHeader(token: String, headers: List<String>, maxDistance: Int = 3): String? {
        val loweredToken = token.lowercase()

        if (loweredToken.startsWith("game")) {
            val suffix = loweredToken.removePrefix("game").trim()
            if (suffix in listOf("1", "2", "3")) {
                return "Game $suffix"
            }
        }

        return headers.minByOrNull { levenshtein(it.lowercase(), token.lowercase()) }?.takeIf {
            levenshtein(it.lowercase(), token.lowercase()) <= maxDistance
        }
    }

    public fun setErrorMessage(msg: String?) {
        _errorMessage.value = msg
    }
}
