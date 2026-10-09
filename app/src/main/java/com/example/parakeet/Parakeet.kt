package com.example.parakeet

import android.content.Context
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** NVIDIA Parakeet TDT 0.6B v3 (int8) running on sherpa-onnx; weights are downloaded once on first use. */
object Parakeet {
    const val SAMPLE_RATE = 16_000

    private const val MODEL_DIR = "parakeet-tdt-0.6b-v3-int8"
    private const val BASE_URL = "https://huggingface.co/csukuangfj/sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8" +
        "/resolve/2bda32ec70b097a55adaa07d9a7173915b43cc78"

    private class ModelFile(val name: String, val size: Long, val sha256: String)

    private val files = listOf(
        ModelFile("encoder.int8.onnx", 652_184_281, "acfc2b4456377e15d04f0243af540b7fe7c992f8d898d751cf134c3a55fd2247"),
        ModelFile("decoder.int8.onnx", 11_845_275, "179e50c43d1a9de79c8a24149a2f9bac6eb5981823f2a2ed88d655b24248db4e"),
        ModelFile("joiner.int8.onnx", 6_355_277, "3164c13fc2821009440d20fcb5fdc78bff28b4db2f8d0f0b329101719c0948b3"),
        ModelFile("tokens.txt", 93_939, "d58544679ea4bc6ac563d1f545eb7d474bd6cfa467f0a6e2c1dc1c7d37e3c35d"),
    )

    private var recognizer: OfflineRecognizer? = null

    @Synchronized
    fun load(context: Context, progress: (String) -> Unit) {
        if (recognizer != null) return
        val dir = File(context.filesDir, MODEL_DIR).also { it.mkdirs() }
        download(dir, progress)
        progress("Loading model…")
        val config = OfflineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
            modelConfig = OfflineModelConfig(
                transducer = OfflineTransducerModelConfig(
                    encoder = File(dir, "encoder.int8.onnx").path,
                    decoder = File(dir, "decoder.int8.onnx").path,
                    joiner = File(dir, "joiner.int8.onnx").path,
                ),
                tokens = File(dir, "tokens.txt").path,
                modelType = "nemo_transducer",
                // Big cores only; spilling onto the little cores slows onnxruntime down.
                numThreads = minOf(4, Runtime.getRuntime().availableProcessors()),
            ),
        )
        recognizer = OfflineRecognizer(config = config)
    }

    /** Transcribes 16 kHz mono PCM samples [from, to). */
    fun transcribe(pcm: FloatArray, from: Int, to: Int): String {
        val r = checkNotNull(recognizer) { "Model not loaded" }
        val stream = r.createStream()
        try {
            stream.acceptWaveform(pcm.copyOfRange(from, to), SAMPLE_RATE)
            r.decode(stream)
            return r.getResult(stream).text.trim()
        } finally {
            stream.release()
        }
    }

    private fun download(dir: File, progress: (String) -> Unit) {
        val missing = files.filter { !File(dir, it.name).exists() }
        if (missing.isEmpty()) return
        val total = missing.sumOf { it.size }
        var done = 0L
        var lastPercent = -1
        for (file in missing) {
            fetch(file, File(dir, file.name)) { bytes ->
                val percent = ((done + bytes) * 100 / total).toInt()
                if (percent != lastPercent) {
                    lastPercent = percent
                    progress("Downloading model (one-time, ${total / 1_000_000} MB): $percent%")
                }
            }
            done += file.size
        }
    }

    /** Resumable download into `<target>.part`, renamed only after the SHA-256 matches. */
    private fun fetch(file: ModelFile, target: File, onBytes: (Long) -> Unit) {
        val part = File(target.path + ".part")
        var have = if (part.exists()) part.length() else 0L
        if (have > file.size) {
            part.delete()
            have = 0
        }
        if (have < file.size) {
            val conn = URL("$BASE_URL/${file.name}").openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            if (have > 0) conn.setRequestProperty("Range", "bytes=$have-")
            try {
                val append = when (conn.responseCode) {
                    HttpURLConnection.HTTP_PARTIAL -> true
                    HttpURLConnection.HTTP_OK -> false
                    else -> error("Model download failed: HTTP ${conn.responseCode} for ${file.name}")
                }
                if (!append) have = 0
                conn.inputStream.use { input ->
                    FileOutputStream(part, append).use { out ->
                        val buffer = ByteArray(1 shl 16)
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            out.write(buffer, 0, n)
                            have += n
                            onBytes(have)
                        }
                    }
                }
            } finally {
                conn.disconnect()
            }
        }
        if (part.length() != file.size || sha256(part) != file.sha256) {
            part.delete()
            error("Model file ${file.name} is corrupt, please retry")
        }
        check(part.renameTo(target)) { "Could not store ${file.name}" }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
