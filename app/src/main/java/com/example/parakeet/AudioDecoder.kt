package com.example.parakeet

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Decodes any audio file Android can read (opus/ogg, m4a, mp3, wav, ...) to 16 kHz mono float PCM. */
object AudioDecoder {
    private const val TIMEOUT_US = 10_000L

    fun decode(file: File): FloatArray {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.path)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: error("No audio track in ${file.name}")
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            try {
                codec.configure(format, null, null, 0)
                codec.start()
                return drain(extractor, codec, format)
            } finally {
                codec.release()
            }
        } finally {
            extractor.release()
        }
    }

    private fun drain(extractor: MediaExtractor, codec: MediaCodec, format: MediaFormat): FloatArray {
        var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        var floatPcm = false
        var resampler: Resampler? = null
        var mono = FloatArray(8192)
        val info = MediaCodec.BufferInfo()
        var inputDone = false

        while (true) {
            if (!inputDone) {
                val index = codec.dequeueInputBuffer(TIMEOUT_US)
                if (index >= 0) {
                    val size = extractor.readSampleData(codec.getInputBuffer(index)!!, 0)
                    if (size < 0) {
                        codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }

            val index = codec.dequeueOutputBuffer(info, TIMEOUT_US)
            if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                val out = codec.outputFormat
                rate = out.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                channels = out.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                floatPcm = out.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                    out.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
            } else if (index >= 0) {
                val buffer = codec.getOutputBuffer(index)!!.order(ByteOrder.nativeOrder())
                buffer.position(info.offset)
                buffer.limit(info.offset + info.size)
                val frames: Int
                if (floatPcm) {
                    val samples = buffer.asFloatBuffer()
                    frames = samples.remaining() / channels
                    if (mono.size < frames) mono = FloatArray(frames)
                    for (f in 0 until frames) {
                        var sum = 0f
                        repeat(channels) { sum += samples.get() }
                        mono[f] = sum / channels
                    }
                } else {
                    val samples = buffer.asShortBuffer()
                    frames = samples.remaining() / channels
                    if (mono.size < frames) mono = FloatArray(frames)
                    for (f in 0 until frames) {
                        var sum = 0f
                        repeat(channels) { sum += samples.get() / 32768f }
                        mono[f] = sum / channels
                    }
                }
                codec.releaseOutputBuffer(index, false)

                val r = resampler ?: Resampler(rate, Parakeet.SAMPLE_RATE).also { resampler = it }
                r.push(mono, frames)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return r.finish()
            }
        }
    }
}

/** Streaming windowed-sinc resampler with a low-pass at the lower of the two Nyquist rates. */
class Resampler(private val inRate: Int, private val outRate: Int) {
    private val out = FloatBuilder()
    private val step = inRate.toDouble() / outRate
    private val cutoff = min(1.0, outRate.toDouble() / inRate) * 0.95
    private val half = ceil(ZERO_CROSSINGS / cutoff).toInt()
    private val kernel = FloatArray(half * RESOLUTION + 1) { i ->
        val d = i.toDouble() / RESOLUTION
        val x = d * cutoff
        val sinc = if (x == 0.0) 1.0 else sin(PI * x) / (PI * x)
        (cutoff * sinc * (0.5 + 0.5 * cos(PI * d / half))).toFloat()
    }

    private var buf = FloatArray(0)
    private var len = 0
    private var bufStart = 0L
    private var pos = 0.0

    fun push(samples: FloatArray, count: Int) {
        if (inRate == outRate) {
            out.add(samples, count)
            return
        }
        if (len + count > buf.size) buf = buf.copyOf(max(buf.size * 2, len + count))
        System.arraycopy(samples, 0, buf, len, count)
        len += count
        drain(final = false)
    }

    fun finish(): FloatArray {
        if (inRate != outRate) drain(final = true)
        return out.toArray()
    }

    private fun drain(final: Boolean) {
        val end = bufStart + len
        while (pos < end) {
            val center = floor(pos).toLong()
            if (!final && center + half >= end) break
            var acc = 0f
            for (k in max(center - half + 1, bufStart)..min(center + half, end - 1)) {
                val tap = (abs(pos - k) * RESOLUTION).toInt()
                if (tap < kernel.size) acc += buf[(k - bufStart).toInt()] * kernel[tap]
            }
            out.add(acc)
            pos += step
        }
        val drop = min((floor(pos).toLong() - half + 1 - bufStart).toInt(), len)
        if (drop > 0) {
            System.arraycopy(buf, drop, buf, 0, len - drop)
            len -= drop
            bufStart += drop
        }
    }

    private companion object {
        const val ZERO_CROSSINGS = 8
        const val RESOLUTION = 128
    }
}

class FloatBuilder {
    private var data = FloatArray(1 shl 16)
    private var size = 0

    fun add(value: Float) {
        if (size == data.size) data = data.copyOf(size * 2)
        data[size++] = value
    }

    fun add(values: FloatArray, count: Int) {
        if (size + count > data.size) data = data.copyOf(max(data.size * 2, size + count))
        System.arraycopy(values, 0, data, size, count)
        size += count
    }

    fun toArray(): FloatArray = data.copyOf(size)
}
