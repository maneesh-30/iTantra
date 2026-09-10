package com.itantra.app

import android.content.Context
import android.content.res.AssetManager
import com.k2fsa.sherpa.onnx.GeneratedAudio
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.io.File

/**
 * TtsEngine — the text-to-speech module, now per-language.
 *
 * Offline speech synthesis using sherpa-onnx with Piper VITS voices:
 *   - English: en_US-lessac-medium
 *   - Hindi:   hi_IN-rohan-medium
 *
 * Both are Piper voices, so both need the same `espeak-ng-data` folder (which turns text into
 * the sounds of speech). Piper needs it on the real filesystem, so on first run we copy the
 * shared copy (bundled once under assets/tts/espeak-ng-data) into app storage and point every
 * language's voice at it.
 */
class TtsEngine(context: Context, val language: Language) {

    private val tts: OfflineTts

    init {
        // Copy the shared espeak-ng-data from assets to app storage (once).
        val dataDir = File(context.filesDir, "espeak-ng-data")
        if (!dataDir.exists() || dataDir.listFiles().isNullOrEmpty()) {
            copyAssetDir(context.assets, "tts/espeak-ng-data", dataDir)
        }

        val vits = when (language) {
            Language.EN -> OfflineTtsVitsModelConfig(
                model = "tts/en_US-lessac-medium.onnx",
                tokens = "tts/tokens.txt",
                dataDir = dataDir.absolutePath,
            )
            Language.HI -> OfflineTtsVitsModelConfig(
                model = "hi-tts/hi_IN-rohan-medium.onnx",
                tokens = "hi-tts/tokens.txt",
                dataDir = dataDir.absolutePath,
            )
        }

        val config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(vits = vits, numThreads = 2),
        )

        tts = OfflineTts(context.assets, config)
    }

    fun sampleRate(): Int = tts.sampleRate()

    fun synthesize(text: String, speed: Float = 1.0f): GeneratedAudio =
        tts.generate(text = text, sid = 0, speed = speed)

    fun release() {
        tts.release()
    }

    /** Recursively copy a folder from assets to a real directory on disk. */
    private fun copyAssetDir(assets: AssetManager, assetPath: String, outDir: File) {
        val children = assets.list(assetPath) ?: emptyArray()
        if (children.isEmpty()) {
            outDir.parentFile?.mkdirs()
            assets.open(assetPath).use { input ->
                outDir.outputStream().use { output -> input.copyTo(output) }
            }
        } else {
            outDir.mkdirs()
            for (child in children) {
                copyAssetDir(assets, "$assetPath/$child", File(outDir, child))
            }
        }
    }
}
