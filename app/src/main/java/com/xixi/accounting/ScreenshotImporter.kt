package com.xixi.accounting

import android.content.Context
import android.graphics.ImageDecoder
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.math.min
import kotlin.math.sqrt

data class RecognizedScreenshot(val hash: String, val candidates: List<LedgerEntry>)

object ScreenshotImporter {
    suspend fun recognize(context: Context, uri: Uri, confirmedYear: Int?): RecognizedScreenshot = withContext(Dispatchers.IO) {
        val digest = MessageDigest.getInstance("SHA-256")
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val buffer = ByteArray(8192)
            var total = 0L
            while (true) {
                ensureActive()
                val count = stream.read(buffer)
                if (count < 0) break
                total += count
                require(total <= 32L * 1024 * 1024) { "图片过大，请分段截图后导入（单张不超过 32 MB）" }
                digest.update(buffer, 0, count)
            }
        } ?: error("无法读取图片，请重新选择")
        val hash = digest.digest().joinToString("") { "%02x".format(it) }
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            val width = info.size.width
            val height = info.size.height
            val scale = min(1.0, min(8192.0 / maxOf(width, height), sqrt(12_000_000.0 / (width.toDouble() * height))))
            decoder.setTargetSize(maxOf(1, (width * scale).toInt()), maxOf(1, (height * scale).toInt()))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        try {
            val recognized = suspendCoroutine<Text> { continuation ->
                recognizer.process(InputImage.fromBitmap(bitmap, 0))
                    .addOnSuccessListener { continuation.resume(it) }
                    .addOnFailureListener { continuation.resumeWithException(it) }
                    .addOnCanceledListener { continuation.resumeWithException(IllegalStateException("识别已取消，请重试")) }
            }
            ensureActive()
            val lines = recognized.textBlocks.flatMap { block -> block.lines }.mapNotNull { line ->
                line.boundingBox?.let { box -> OcrLine(line.text, box.left, box.top, box.right, box.bottom) }
            }
            RecognizedScreenshot(hash, ScreenshotParser.parse(lines, confirmedYear))
        } finally {
            recognizer.close()
            bitmap.recycle()
        }
    }
}
