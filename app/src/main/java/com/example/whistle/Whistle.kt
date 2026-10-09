package com.example.whistle

import android.content.Context
import org.json.JSONObject
import java.io.File

/** Thin wrapper around the Needle engine with the Whistle speech model loaded. */
object Whistle {
    const val SAMPLE_RATE = 16_000
    const val MAX_SAMPLES = 30 * SAMPLE_RATE
    private const val MODEL = "whistle.cact"

    init {
        System.loadLibrary("whistle_jni")
    }

    private var loaded = false

    @Synchronized
    fun load(context: Context) {
        if (loaded) return
        // The engine needs a real file to mmap, so the bundled asset is copied once per install/update.
        val model = File(context.filesDir, MODEL)
        val installed = context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
        if (!model.exists() || model.lastModified() < installed) {
            val partial = File(context.filesDir, "$MODEL.part")
            context.assets.open(MODEL).use { input -> partial.outputStream().use { input.copyTo(it) } }
            check(partial.renameTo(model)) { "Could not install model" }
        }
        nativeLoad(model.path)
        loaded = true
    }

    /** Transcribes at most [MAX_SAMPLES] of 16 kHz mono PCM; returns the trimmed text (empty for silence). */
    fun transcribe(pcm: FloatArray, offset: Int, length: Int): String {
        require(length in 1..MAX_SAMPLES)
        val json = JSONObject(String(nativeTranscribe(pcm, offset, length), Charsets.UTF_8))
        return json.optString("text").trim()
    }

    private external fun nativeLoad(path: String)
    private external fun nativeTranscribe(pcm: FloatArray, offset: Int, length: Int): ByteArray
}
