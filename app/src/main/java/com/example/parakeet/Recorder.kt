package com.example.parakeet

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.concurrent.thread
import kotlin.math.max

/** Records 16 kHz mono 16-bit PCM straight into a WAV file. */
class Recorder(val file: File) {
    private val record: AudioRecord
    @Volatile private var running = true
    private val worker: Thread

    init {
        val minBuffer = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        @SuppressLint("MissingPermission") // checked by the caller
        val r = AudioRecord(
            MediaRecorder.AudioSource.MIC, RATE, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT, max(minBuffer, RATE),
        )
        if (r.state != AudioRecord.STATE_INITIALIZED) {
            r.release()
            error("Microphone unavailable")
        }
        record = r
        worker = thread(name = "recorder") { loop() }
    }

    private fun loop() {
        RandomAccessFile(file, "rw").use { raf ->
            raf.setLength(0)
            raf.write(ByteArray(HEADER_SIZE))
            val samples = ShortArray(RATE / 10)
            val bytes = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            var dataBytes = 0L
            try {
                record.startRecording()
                while (running) {
                    val n = record.read(samples, 0, samples.size)
                    if (n <= 0) continue
                    bytes.clear()
                    bytes.asShortBuffer().put(samples, 0, n)
                    raf.write(bytes.array(), 0, n * 2)
                    dataBytes += n * 2
                }
            } finally {
                record.stop()
                record.release()
                raf.seek(0)
                raf.write(header(dataBytes))
            }
        }
    }

    fun stop() {
        running = false
        worker.join()
    }

    private fun header(dataBytes: Long): ByteArray =
        ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray())
            putInt((36 + dataBytes).toInt())
            put("WAVE".toByteArray())
            put("fmt ".toByteArray())
            putInt(16)
            putShort(1) // PCM
            putShort(1) // mono
            putInt(RATE)
            putInt(RATE * 2)
            putShort(2)
            putShort(16)
            put("data".toByteArray())
            putInt(dataBytes.toInt())
        }.array()

    private companion object {
        const val RATE = Parakeet.SAMPLE_RATE
        const val HEADER_SIZE = 44
    }
}
