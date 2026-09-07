package com.tomas.noscroll.detector

data class DetectionResult(
    val detector: String,
    val score: Int,
    val threshold: Int,
    val signals: Map<String, Int>,
    val blocked: Boolean,
    val reason: String,
)
