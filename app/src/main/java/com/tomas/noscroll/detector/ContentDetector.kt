package com.tomas.noscroll.detector

interface ContentDetector {
    val packageName: String
    fun detect(tree: TreeSnapshot): DetectionResult
}
