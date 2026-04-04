package com.edgeai.app.ml

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class MlKitOcrEngine @Inject constructor() {

    companion object {
        private const val TAG = "MlKitOcr"
    }

    private val recognizer: TextRecognizer =
        TextRecognition.getClient(TextRecognizerOptions.Builder().build())

    private fun mergeHorizontalLines(lines: List<OcrLine>, verticalThreshold: Int = 20): List<OcrLine> {
        if (lines.isEmpty()) return lines

        val merged = mutableListOf<OcrLine>()
        var i = 0

        while (i < lines.size) {
            val current = lines[i]
            val sameRow = mutableListOf(current)

            var j = i + 1
            while (j < lines.size && Math.abs(lines[j].top - current.top) < verticalThreshold) {
                sameRow.add(lines[j])
                j++
            }

            if (sameRow.size > 1) {
                sameRow.sortBy { it.left }
                val mergedText = sameRow.joinToString("  ") { it.text }
                merged.add(OcrLine(
                    text = mergedText,
                    confidence = sameRow.minOf { it.confidence },
                    top = sameRow.first().top,
                    left = sameRow.first().left,
                ))
            } else {
                merged.add(current)
            }

            i = j
        }

        return merged
    }

    suspend fun recognizeText(bitmap: Bitmap): OcrResult =
        suspendCancellableCoroutine { cont ->
            val image = InputImage.fromBitmap(bitmap, 0)
            val startTime = System.currentTimeMillis()

            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val elapsed = System.currentTimeMillis() - startTime
                    Log.i(TAG, "OCR completed in ${elapsed}ms, found ${visionText.textBlocks.size} blocks")

                    val rawLines = mutableListOf<OcrLine>()
                    for (block in visionText.textBlocks) {
                        for (line in block.lines) {
                            rawLines.add(
                                OcrLine(
                                    text = line.text,
                                    confidence = line.confidence ?: 0f,
                                    top = line.boundingBox?.top ?: 0,
                                    left = line.boundingBox?.left ?: 0,
                                ),
                            )
                        }
                    }

                    var boundsTop = Int.MAX_VALUE
                    var boundsLeft = Int.MAX_VALUE
                    var boundsRight = 0
                    var boundsBottom = 0
                    for (block in visionText.textBlocks) {
                        block.boundingBox?.let { r ->
                            boundsTop = minOf(boundsTop, r.top)
                            boundsLeft = minOf(boundsLeft, r.left)
                            boundsRight = maxOf(boundsRight, r.right)
                            boundsBottom = maxOf(boundsBottom, r.bottom)
                        }
                    }

                    rawLines.sortBy { it.top }
                    val lines = mergeHorizontalLines(rawLines)
                    val fullText = lines.joinToString("\n") { it.text }

                    cont.resume(
                        OcrResult(
                            fullText = fullText,
                            lines = lines,
                            latencyMs = elapsed,
                            textBoundsTop = if (boundsTop == Int.MAX_VALUE) 0 else boundsTop,
                            textBoundsLeft = if (boundsLeft == Int.MAX_VALUE) 0 else boundsLeft,
                            textBoundsRight = boundsRight,
                            textBoundsBottom = boundsBottom,
                        ),
                    )
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "OCR failed", e)
                    cont.resumeWithException(e)
                }
        }
}

data class OcrResult(
    val fullText: String,
    val lines: List<OcrLine>,
    val latencyMs: Long,
    val textBoundsTop: Int = 0,
    val textBoundsLeft: Int = 0,
    val textBoundsRight: Int = 0,
    val textBoundsBottom: Int = 0,
)

data class OcrLine(
    val text: String,
    val confidence: Float,
    val top: Int,
    val left: Int = 0,
)
