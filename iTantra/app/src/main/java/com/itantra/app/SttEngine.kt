package com.itantra.app

import android.content.res.AssetManager
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineMoonshineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig

/**
 * SttEngine — the speech-to-text module.
 *
 * This uses an OFFLINE (full-clip) recognizer, not a streaming one. We record a
 * whole sentence first (while the push-to-talk button is held), then transcribe the
 * entire clip at once. A full-clip model sees the whole sentence, so it is far more
 * accurate than a streaming model that decodes word-by-word.
 *
 * Model: Moonshine base-en (int8) — a small, fast, accurate offline English model.
 * Everything runs on the phone from bundled assets, so it works with no internet.
 *
 * Later, Indian-language models (AI4Bharat) plug in here the same way, one per language.
 */
class SttEngine(assetManager: AssetManager) {

    val recognizer: OfflineRecognizer

    init {
        // Moonshine v1 needs four model files (all read from assets/).
        val modelConfig = OfflineModelConfig(
            moonshine = OfflineMoonshineModelConfig(
                preprocessor = "preprocess.onnx",
                encoder = "encode.int8.onnx",
                uncachedDecoder = "uncached_decode.int8.onnx",
                cachedDecoder = "cached_decode.int8.onnx",
            ),
            tokens = "tokens.txt",
            numThreads = 2,
            modelType = "moonshine",
        )

        val config = OfflineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = 16000, featureDim = 80),
            modelConfig = modelConfig,
        )

        recognizer = OfflineRecognizer(assetManager, config)
    }

    /**
     * Transcribe one recorded clip.
     * @param samples audio as floats in -1.0..1.0, mono, 16 kHz
     * @return the recognized text
     */
    fun transcribe(samples: FloatArray, sampleRate: Int = 16000): String {
        val stream = recognizer.createStream()
        stream.acceptWaveform(samples, sampleRate)
        recognizer.decode(stream)
        val text = recognizer.getResult(stream).text
        stream.release()
        return text
    }

    /** Free the native model memory when we are done. */
    fun release() {
        recognizer.release()
    }
}
