package com.itantra.app

import android.content.res.AssetManager
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineMoonshineModelConfig
import com.k2fsa.sherpa.onnx.OfflineNemoEncDecCtcModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig

/**
 * SttEngine — the speech-to-text module, now per-language.
 *
 * Offline, full-clip recognition (record a whole sentence, then transcribe). Which model it
 * loads depends on the language:
 *   - English: Moonshine base-en (fast, accurate for English).
 *   - Hindi:   AI4Bharat IndicConformer (NeMo CTC), purpose-built for Indian languages
 *              (clearly more accurate than Whisper small on Hindi; one shared vocab across
 *              all Indian languages, so the same recipe extends to Telugu, Tamil, etc.).
 *
 * Model files are read from the app's assets, so everything runs on the phone with no internet.
 */
class SttEngine(assetManager: AssetManager, val language: Language) {

    val recognizer: OfflineRecognizer

    init {
        val modelConfig = when (language) {
            Language.EN -> OfflineModelConfig(
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

            Language.HI -> OfflineModelConfig(
                // AI4Bharat IndicConformer (NeMo CTC), purpose-built for Indian languages.
                // Beat Whisper small clearly on Hindi and is a single model file.
                nemo = OfflineNemoEncDecCtcModelConfig(
                    model = "hi-stt-ic/model.int8.onnx",
                ),
                tokens = "hi-stt-ic/tokens.txt",
                numThreads = 2,
            )
        }

        val config = OfflineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = 16000, featureDim = 80),
            modelConfig = modelConfig,
        )

        recognizer = OfflineRecognizer(assetManager, config)
    }

    /** Transcribe one recorded clip (floats -1.0..1.0, mono, 16 kHz) → text. */
    fun transcribe(samples: FloatArray, sampleRate: Int = 16000): String {
        val stream = recognizer.createStream()
        stream.acceptWaveform(samples, sampleRate)
        recognizer.decode(stream)
        val text = recognizer.getResult(stream).text
        stream.release()
        return text
    }

    fun release() {
        recognizer.release()
    }
}
