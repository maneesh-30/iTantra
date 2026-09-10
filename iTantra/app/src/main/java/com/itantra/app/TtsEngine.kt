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
 * TtsEngine — the text-to-speech module.
 *
 * Turns text into speech audio, fully offline, using sherpa-onnx with a VITS (Piper)
 * English voice. The model and tokens are read straight from the app's assets.
 *
 * One wrinkle: Piper voices need an "espeak-ng-data" folder to turn text into the sounds
 * of speech, and that folder must live on the real filesystem (it cannot be read from
 * inside the APK). So on first run we copy it from assets to the app's private storage,
 * then point the engine at that copied path.
 */
class TtsEngine(context: Context) {

    private val tts: OfflineTts

    init {
        // Copy espeak-ng-data from assets to app storage (once).
        val dataDir = File(context.filesDir, "espeak-ng-data")
        if (!dataDir.exists() || dataDir.listFiles().isNullOrEmpty()) {
            copyAssetDir(context.assets, "tts/espeak-ng-data", dataDir)
        }

        val config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                vits = OfflineTtsVitsModelConfig(
                    model = "tts/en_US-lessac-medium.onnx", // asset path
                    tokens = "tts/tokens.txt",              // asset path
                    dataDir = dataDir.absolutePath,         // real filesystem path
                ),
                numThreads = 2,
                debug = true, // print sherpa-onnx logs to logcat while we debug
            ),
        )

        tts = OfflineTts(context.assets, config)
    }

    /** The sample rate of the audio this voice produces (needed to play it back). */
    fun sampleRate(): Int = tts.sampleRate()

    /**
     * Turn text into speech audio.
     * @param speed 1.0 = normal; lower = slower, higher = faster.
     * @return samples in -1.0..1.0 plus the sample rate.
     */
    fun synthesize(text: String, speed: Float = 1.0f): GeneratedAudio {
        return tts.generate(text = text, sid = 0, speed = speed)
    }

    fun release() {
        tts.release()
    }

    /** Recursively copy a folder from assets to a real directory on disk. */
    private fun copyAssetDir(assets: AssetManager, assetPath: String, outDir: File) {
        val children = assets.list(assetPath) ?: emptyArray()
        if (children.isEmpty()) {
            // It's a file: copy it.
            outDir.parentFile?.mkdirs()
            assets.open(assetPath).use { input ->
                outDir.outputStream().use { output -> input.copyTo(output) }
            }
        } else {
            // It's a directory: recurse into each child.
            outDir.mkdirs()
            for (child in children) {
                copyAssetDir(assets, "$assetPath/$child", File(outDir, child))
            }
        }
    }
}
