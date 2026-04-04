package com.edgeai.app.ml

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.util.Log
import com.edgeai.app.config.AppConfig
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImagePreprocessor @Inject constructor(
    private val config: AppConfig,
) {
    companion object {
        private const val TAG = "ImagePreprocessor"
        private const val FADED_STD_DEV_THRESHOLD = 45.0
        private const val FADED_MEAN_THRESHOLD = 200.0
    }

    fun process(source: Bitmap): Bitmap {
        val maxDim = config.uiConfig.imageMaxDimension
        Log.d(TAG, "Input: ${source.width}x${source.height}")

        var result = resize(source, maxDim)

        val stats = analyzeCenterRegion(result)
        Log.d(TAG, "Center stats: mean=${stats.mean.toInt()}, stdDev=${stats.stdDev.toInt()}, isFaded=${stats.isFaded}")

        if (stats.isFaded) {
            Log.d(TAG, "Faded document detected — applying contrast enhancement")
            result = toGrayscale(result)
            result = enhanceContrast(result)
        } else {
            Log.d(TAG, "Clear document — light contrast boost")
            result = boostContrast(result, factor = 1.3f)
        }

        Log.d(TAG, "Output: ${result.width}x${result.height}")
        return result
    }

    private fun resize(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= maxDimension && h <= maxDimension) return bitmap
        val scale = maxDimension.toFloat() / maxOf(w, h)
        return Bitmap.createScaledBitmap(bitmap, (w * scale).toInt(), (h * scale).toInt(), true)
    }

    private fun boostContrast(bitmap: Bitmap, factor: Float): Bitmap {
        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint()
        val translate = (1f - factor) * 128f
        val cm = ColorMatrix(floatArrayOf(
            factor, 0f, 0f, 0f, translate,
            0f, factor, 0f, 0f, translate,
            0f, 0f, factor, 0f, translate,
            0f, 0f, 0f, 1f, 0f,
        ))
        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return output
    }

    private fun analyzeCenterRegion(bitmap: Bitmap): ImageStats {
        val w = bitmap.width
        val h = bitmap.height
        val startX = w / 4
        val endX = w * 3 / 4
        val startY = h / 4
        val endY = h * 3 / 4

        var sum = 0.0
        var sumSq = 0.0
        var count = 0

        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        for (y in startY until endY step 2) {
            for (x in startX until endX step 2) {
                val p = pixels[y * w + x]
                val gray = (Color.red(p) * 0.299 + Color.green(p) * 0.587 + Color.blue(p) * 0.114)
                sum += gray
                sumSq += gray * gray
                count++
            }
        }

        val mean = sum / count
        val variance = (sumSq / count) - (mean * mean)
        val stdDev = Math.sqrt(variance)

        val isFaded = stdDev < FADED_STD_DEV_THRESHOLD && mean > FADED_MEAN_THRESHOLD

        return ImageStats(mean, stdDev, isFaded)
    }

    private fun toGrayscale(bitmap: Bitmap): Bitmap {
        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint()
        val cm = ColorMatrix()
        cm.setSaturation(0f)
        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return output
    }

    private fun enhanceContrast(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val tileSize = 64
        val output = IntArray(w * h)

        for (ty in 0 until h step tileSize) {
            for (tx in 0 until w step tileSize) {
                val endX = minOf(tx + tileSize, w)
                val endY = minOf(ty + tileSize, h)

                var minVal = 255
                var maxVal = 0
                for (y in ty until endY) {
                    for (x in tx until endX) {
                        val gray = Color.red(pixels[y * w + x])
                        if (gray < minVal) minVal = gray
                        if (gray > maxVal) maxVal = gray
                    }
                }

                val range = maxOf(maxVal - minVal, 1)
                for (y in ty until endY) {
                    for (x in tx until endX) {
                        val idx = y * w + x
                        val gray = Color.red(pixels[idx])
                        val stretched = ((gray - minVal) * 255 / range).coerceIn(0, 255)
                        output[idx] = Color.rgb(stretched, stretched, stretched)
                    }
                }
            }
        }

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(output, 0, w, 0, 0, w, h)
        return result
    }

    private data class ImageStats(
        val mean: Double,
        val stdDev: Double,
        val isFaded: Boolean,
    )
}
