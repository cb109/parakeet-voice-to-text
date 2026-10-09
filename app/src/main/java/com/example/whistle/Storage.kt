package com.example.whistle

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Everything lives in Download/Whistle: `<stamp>_<name>.<ext>` is the audio,
 * `<stamp>_<name>.txt` next to it is the transcript.
 */
object Storage {
    val dir: File
        get() = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Whistle")
            .also { it.mkdirs() }

    class Entry(val audio: File) {
        val text = File(audio.parentFile, audio.nameWithoutExtension + ".txt")
        fun transcript(): String? = text.takeIf { it.exists() }?.readText()
    }

    fun list(): List<Entry> =
        dir.listFiles { f -> f.isFile && !f.name.startsWith(".") && f.extension != "txt" }
            .orEmpty()
            .sortedByDescending { it.name }
            .map(::Entry)

    fun newAudioFile(label: String, extension: String): File {
        val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        val name = label.replace(Regex("[^A-Za-z0-9._-]+"), "_").trim('_', '.').take(60).ifEmpty { "audio" }
        val ext = extension.lowercase().replace(Regex("[^a-z0-9]"), "").take(8)
            .takeUnless { it.isEmpty() || it == "txt" } ?: "audio"
        var i = 0
        while (true) {
            val base = if (i == 0) "${stamp}_$name" else "${stamp}_${name}_$i"
            val file = File(dir, "$base.$ext")
            if (!file.exists() && !File(dir, "$base.txt").exists()) return file
            i++
        }
    }

    /** Copies a picked or shared content URI into the storage folder. */
    fun import(context: Context, uri: Uri): File {
        val resolver = context.contentResolver
        val displayName = runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment ?: "audio"
        val ext = displayName.substringAfterLast('.', "").ifEmpty {
            MimeTypeMap.getSingleton().getExtensionFromMimeType(resolver.getType(uri)).orEmpty()
        }
        val target = newAudioFile(displayName.substringBeforeLast('.'), ext)
        try {
            val input = resolver.openInputStream(uri) ?: error("Cannot open $uri")
            input.use { i -> target.outputStream().use { i.copyTo(it) } }
        } catch (e: Exception) {
            target.delete()
            throw e
        }
        return target
    }

    fun delete(entry: Entry) {
        entry.text.delete()
        entry.audio.delete()
    }

    fun deleteAll() {
        dir.listFiles()?.filter { it.isFile }?.forEach { it.delete() }
    }
}
