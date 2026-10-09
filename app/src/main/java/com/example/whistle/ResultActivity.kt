package com.example.whistle

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import java.io.File

class ResultActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_result)

        val entry = Storage.Entry(File(intent.getStringExtra(EXTRA_AUDIO) ?: return finish()))
        val transcript = entry.transcript()
        title = entry.audio.name
        findViewById<TextView>(R.id.files).text = "${entry.audio.path}\n${entry.text.path}"
        findViewById<TextView>(R.id.transcript).text = when {
            transcript == null -> getString(R.string.no_transcript)
            transcript.isEmpty() -> getString(R.string.empty_transcript)
            else -> transcript
        }

        findViewById<Button>(R.id.copy).setOnClickListener {
            getSystemService(ClipboardManager::class.java)
                .setPrimaryClip(ClipData.newPlainText(entry.audio.name, transcript.orEmpty()))
            // Android 13+ shows its own clipboard confirmation.
            if (Build.VERSION.SDK_INT < 33) Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.delete).setOnClickListener {
            AlertDialog.Builder(this)
                .setMessage(R.string.confirm_delete)
                .setPositiveButton(R.string.delete) { _, _ ->
                    Storage.delete(entry)
                    finish()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    companion object {
        const val EXTRA_AUDIO = "audio"
    }
}
