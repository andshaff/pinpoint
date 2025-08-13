package com.studio32b.pinpoint.model

data class PlayerScore(
    val playerName: String,
    val gameScores: List<String>,
    val scratch: String,
    val hdcp: String,
    val total: String
)