package com.example.whistle

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.io.File
import java.util.concurrent.Executors

/** Background queue: import -> decode -> split into <=30 s parts -> Whistle -> join -> .txt */
object Transcriber {
    sealed interface Event {
        data object Progress : Event
        data class Done(val audio: File) : Event
        data class Failed(val error: String) : Event
    }

    /** Main-thread state for the UI. */
    var status = ""
        private set
    val busy get() = pending > 0
    var listener: ((Event) -> Unit)? = null

    private var pending = 0
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private val worker = Executors.newSingleThreadExecutor()

    fun transcribe(context: Context, uri: Uri) {
        val app = context.applicationContext
        started("Importing…")
        // Copy right away on a separate thread: the URI grant from the sharing app is short-lived.
        io.execute {
            val file = try {
                Storage.import(app, uri)
            } catch (e: Exception) {
                finished(Event.Failed("Import failed: ${e.message}"), "Import failed: ${e.message}")
                return@execute
            }
            worker.execute { run(app, file) }
        }
    }

    fun transcribe(context: Context, audio: File) {
        val app = context.applicationContext
        started("Queued ${audio.name}")
        worker.execute { run(app, audio) }
    }

    private fun run(context: Context, audio: File) {
        val startedAt = SystemClock.elapsedRealtime()
        try {
            progress("Loading model…")
            Whistle.load(context)
            progress("Decoding ${audio.name}…")
            val pcm = AudioDecoder.decode(audio)
            val parts = split(pcm)
            val texts = parts.mapIndexed { i, range ->
                progress("Transcribing ${audio.name}: part ${i + 1}/${parts.size}")
                Whistle.transcribe(pcm, range.first, range.last - range.first + 1)
            }
            if (!audio.exists()) {
                val message = "${audio.name} was deleted during transcription"
                return finished(Event.Failed(message), message)
            }
            Storage.Entry(audio).text.writeText(texts.filter { it.isNotEmpty() }.joinToString(" "))
            val seconds = (SystemClock.elapsedRealtime() - startedAt) / 1000.0
            finished(
                Event.Done(audio),
                "Transcribed %s (%.0f s audio) in %.1f s".format(
                    audio.name, pcm.size.toDouble() / Whistle.SAMPLE_RATE, seconds,
                ),
            )
        } catch (e: Throwable) {
            val message = "Failed on ${audio.name}: ${e.message ?: e}"
            finished(Event.Failed(message), message)
        }
    }

    /** Cuts before the 30 s limit at the quietest 300 ms between 20 s and 30 s, so words are not split. */
    private fun split(pcm: FloatArray): List<IntRange> {
        val parts = mutableListOf<IntRange>()
        var start = 0
        while (pcm.size - start > Whistle.MAX_SAMPLES) {
            val cut = quietestPoint(pcm, start + 20 * Whistle.SAMPLE_RATE, start + Whistle.MAX_SAMPLES)
            parts += start until cut
            start = cut
        }
        if (start < pcm.size) parts += start until pcm.size
        return parts
    }

    private fun quietestPoint(pcm: FloatArray, from: Int, to: Int): Int {
        val frame = Whistle.SAMPLE_RATE / 50 // 20 ms
        val window = 15 // frames = 300 ms
        val energy = DoubleArray((to - from) / frame) { i ->
            var e = 0.0
            for (j in from + i * frame until from + (i + 1) * frame) e += pcm[j] * pcm[j]
            e
        }
        var sum = (0 until window).sumOf { energy[it] }
        var best = sum
        var bestStart = 0
        for (i in window until energy.size) {
            sum += energy[i] - energy[i - window]
            if (sum < best) {
                best = sum
                bestStart = i - window + 1
            }
        }
        return from + (bestStart + window / 2) * frame
    }

    private fun started(message: String) {
        pending++
        status = message
        listener?.invoke(Event.Progress)
    }

    private fun progress(message: String) = main.post {
        status = message
        listener?.invoke(Event.Progress)
    }

    private fun finished(event: Event, message: String) {
        main.post {
            pending--
            status = message
            listener?.invoke(event)
        }
    }
}
