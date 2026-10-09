package com.example.whistle

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.SimpleAdapter
import android.widget.TextView
import android.widget.Toast
import java.io.File

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var spinner: ProgressBar
    private lateinit var recordButton: Button
    private lateinit var deleteAllButton: Button
    private lateinit var list: ListView
    private var entries = emptyList<Storage.Entry>()
    private var recorder: Recorder? = null
    private val listener: (Transcriber.Event) -> Unit = ::onEvent

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        status = findViewById(R.id.status)
        spinner = findViewById(R.id.spinner)
        recordButton = findViewById(R.id.record)
        deleteAllButton = findViewById(R.id.delete_all)
        list = findViewById(R.id.list)

        findViewById<Button>(R.id.pick).setOnClickListener { pickFile() }
        recordButton.setOnClickListener { if (recorder == null) startRecording() else stopRecording() }
        deleteAllButton.setOnClickListener { confirmDeleteAll() }
        list.setOnItemClickListener { _, _, position, _ -> openResult(entries[position].audio) }

        if (savedInstanceState == null) handleShare(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShare(intent)
    }

    override fun onStart() {
        super.onStart()
        Transcriber.listener = listener
        refresh()
    }

    override fun onStop() {
        // Background mic access is blocked anyway, so finish the recording and transcribe it.
        stopRecording()
        if (Transcriber.listener === listener) Transcriber.listener = null
        super.onStop()
    }

    private fun handleShare(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val uri = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        } ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri ?: return
        Transcriber.transcribe(this, uri)
    }

    private fun onEvent(event: Transcriber.Event) {
        when (event) {
            is Transcriber.Event.Progress -> showStatus()
            is Transcriber.Event.Done -> {
                refresh()
                openResult(event.audio)
            }
            is Transcriber.Event.Failed -> {
                refresh()
                Toast.makeText(this, event.error, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun refresh() {
        entries = Storage.list()
        val rows = entries.map { entry ->
            val text = entry.transcript()
            mapOf(
                "title" to entry.audio.name,
                "preview" to when {
                    text == null -> getString(R.string.no_transcript)
                    text.isEmpty() -> getString(R.string.empty_transcript)
                    else -> text.take(160)
                },
            )
        }
        list.adapter = SimpleAdapter(
            this, rows, android.R.layout.simple_list_item_2,
            arrayOf("title", "preview"), intArrayOf(android.R.id.text1, android.R.id.text2),
        )
        deleteAllButton.isEnabled = entries.isNotEmpty()
        showStatus()
    }

    private fun showStatus() {
        status.text = when {
            recorder != null -> getString(R.string.recording)
            Transcriber.status.isNotEmpty() -> Transcriber.status
            else -> getString(R.string.saved_in, Storage.dir.path)
        }
        spinner.visibility = if (Transcriber.busy) View.VISIBLE else View.GONE
    }

    private fun openResult(audio: File) {
        startActivity(Intent(this, ResultActivity::class.java).putExtra(ResultActivity.EXTRA_AUDIO, audio.path))
    }

    private fun pickFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("*/*")
            .putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("audio/*", "application/ogg"))
        @Suppress("DEPRECATION")
        startActivityForResult(intent, REQUEST_PICK)
    }

    @Deprecated("Framework Activity API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (requestCode == REQUEST_PICK && resultCode == RESULT_OK && uri != null) {
            Transcriber.transcribe(this, uri)
        }
    }

    private fun startRecording() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_MIC)
            return
        }
        val file = Storage.newAudioFile("recording", "wav")
        recorder = try {
            Recorder(file)
        } catch (e: Exception) {
            file.delete()
            Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
            return
        }
        recordButton.setText(R.string.stop)
        showStatus()
    }

    private fun stopRecording() {
        val r = recorder ?: return
        recorder = null
        r.stop()
        recordButton.setText(R.string.record)
        Transcriber.transcribe(this, r.file)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode != REQUEST_MIC) return
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            startRecording()
        } else {
            Toast.makeText(this, R.string.mic_denied, Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmDeleteAll() {
        AlertDialog.Builder(this)
            .setMessage(R.string.confirm_delete_all)
            .setPositiveButton(R.string.delete_all) { _, _ ->
                Storage.deleteAll()
                refresh()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private companion object {
        const val REQUEST_PICK = 1
        const val REQUEST_MIC = 2
    }
}
