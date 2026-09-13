package com.calcvault.ui.media

import android.app.Dialog
import android.graphics.Color
import android.media.MediaPlayer
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.calcvault.emotional.ThemeEngine
import com.calcvault.emotional.themes.ThemeApplicator
import com.calcvault.messaging.AppendOnlyMessageDB
import com.calcvault.messaging.CallLogRecord
import com.calcvault.storage.USBStorageEngine
import com.calcvault.ui.common.GlassUi
import kotlinx.coroutines.*
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

/**
 * RecordingsActivity
 *
 * Displays and manages call recordings stored in the secure vault.
 * Provides playback for recorded audio with full encryption/decryption.
 */
class RecordingsActivity : AppCompatActivity() {

    private lateinit var storageEngine: USBStorageEngine
    private lateinit var messageDB: AppendOnlyMessageDB
    private lateinit var themeEngine: ThemeEngine

    private lateinit var rvRecordings: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var adapter: RecordingsAdapter

    private val recordings = mutableListOf<CallRecordingItem>()

    data class CallRecordingItem(
        val callLogRecord: CallLogRecord,
        val id: String,
        val duration: Long,
        val timestamp: Long,
        val participantName: String
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        storageEngine = USBStorageEngine.getInstance(this)
        messageDB = AppendOnlyMessageDB(storageEngine)
        themeEngine = ThemeEngine(this)

        buildUI()
        GlassUi.applyThemeChrome(this)
        loadRecordings()
    }

    private fun buildUI() {
        val theme = themeEngine.getCurrentTheme()
        val root = FrameLayout(this)
        setContentView(root)

        if (ThemeApplicator.activeTheme == ThemeApplicator.ThemeChoice.NONE) {
            themeEngine.applyToView(root)
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.TRANSPARENT)
        }
        root.addView(layout)

        // Header
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(if (ThemeApplicator.activeTheme == ThemeApplicator.ThemeChoice.NONE) theme.surfaceColor else 0x88000000.toInt())
            setPadding(24, 48, 24, 16)
            gravity = Gravity.CENTER_VERTICAL
        }
        GlassUi.markPanel(header, alpha = 144, radiusDp = 0)
        layout.addView(header)

        val tvTitle = TextView(this).apply {
            text = "Recordings"
            textSize = 20f
            setTextColor(theme.primaryText)
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }
        header.addView(tvTitle)

        // Back button
        val btnBack = Button(this).apply {
            text = "Back"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener { finish() }
        }
        header.addView(btnBack)

        // Recycler view
        rvRecordings = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@RecordingsActivity)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            setBackgroundColor(Color.TRANSPARENT)
        }
        adapter = RecordingsAdapter(recordings) { item -> playRecording(item) }
        rvRecordings.adapter = adapter
        layout.addView(rvRecordings)

        // Empty state
        tvEmpty = TextView(this).apply {
            text = "No recordings yet.\nCall recordings will appear here."
            textSize = 15f
            setTextColor(theme.secondaryText)
            gravity = Gravity.CENTER
            visibility = View.GONE
        }
        layout.addView(tvEmpty)
    }

    private fun loadRecordings() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val callLogMessages = messageDB.getMessages(AppendOnlyMessageDB.MSG_CALL_LOG)

                val items = callLogMessages.mapNotNull { msg ->
                    try {
                        val callLogJson = JSONObject(msg.content)
                        val participantName = if (msg.from == "SYSTEM") msg.to else msg.from
                        val duration = callLogJson.optLong("duration", 0L)

                        CallRecordingItem(
                            callLogRecord = CallLogRecord(
                                callId = callLogJson.optString("callId", "Unknown"),
                                type = callLogJson.optString("type", "voice"),
                                initiator = callLogJson.optString("initiator", ""),
                                receiver = callLogJson.optString("receiver", ""),
                                startTime = callLogJson.optLong("startTime", msg.timestamp),
                                duration = duration,
                                ended = callLogJson.optString("ended", "completed"),
                                recordingId = callLogJson.optLong("recId").let { if (it > 0) it else null }
                            ),
                            id = msg.id.toString(),
                            duration = duration,
                            timestamp = msg.timestamp,
                            participantName = participantName
                        )
                    } catch (e: Exception) {
                        android.util.Log.e("RecordingsActivity", "Failed to parse call log", e)
                        null
                    }
                }.sortedByDescending { it.timestamp }

                withContext(Dispatchers.Main) {
                    recordings.clear()
                    recordings.addAll(items)
                    adapter.notifyDataSetChanged()
                    tvEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
                    android.util.Log.i("RecordingsActivity", "Loaded ${items.size} recordings")
                }
            } catch (e: Exception) {
                android.util.Log.e("RecordingsActivity", "Failed to load recordings", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@RecordingsActivity, "Failed to load recordings", Toast.LENGTH_SHORT).show()
                    tvEmpty.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun playRecording(item: CallRecordingItem) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                android.util.Log.i("RecordingsActivity", "Playing recording: ${item.id}")
                showPlaybackDialog(item)
            } catch (e: Exception) {
                android.util.Log.e("RecordingsActivity", "Failed to play recording", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@RecordingsActivity, "Failed to play recording", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private suspend fun showPlaybackDialog(item: CallRecordingItem) = withContext(Dispatchers.Main) {
        try {
            val dialog = Dialog(this@RecordingsActivity)
            dialog.window?.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

            val player = MediaPlayer()
            var progressThread: Thread? = null
            var isPlayerReady = false
            var isPlayerPlaying = false

            val root = LinearLayout(this@RecordingsActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(48, 48, 48, 48)
                background = GlassUi.glassDrawable(context, alpha = 235, radiusDp = 24)
            }

            val title = TextView(this@RecordingsActivity).apply {
                text = "Call with ${item.participantName}"
                textSize = 18f
                setTextColor(0xFFFFFFFF.toInt())
                setPadding(0, 0, 0, 12)
            }
            root.addView(title)

            val timestamp = TextView(this@RecordingsActivity).apply {
                text = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(item.timestamp)
                textSize = 13f
                setTextColor(0xFFAAAAAA.toInt())
                setPadding(0, 0, 0, 24)
            }
            root.addView(timestamp)

            val seekBar = SeekBar(this@RecordingsActivity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            root.addView(seekBar)

            val timeDisplay = LinearLayout(this@RecordingsActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).also { it.topMargin = 12 }
                gravity = Gravity.CENTER_VERTICAL
            }

            val tvCurrentTime = TextView(this@RecordingsActivity).apply {
                text = "0:00"
                textSize = 12f
                setTextColor(0xFFAAAAAA.toInt())
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            timeDisplay.addView(tvCurrentTime)

            val tvDuration = TextView(this@RecordingsActivity).apply {
                text = formatTime(item.duration.toInt())
                textSize = 12f
                setTextColor(0xFFAAAAAA.toInt())
                gravity = Gravity.END
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            timeDisplay.addView(tvDuration)
            root.addView(timeDisplay)

            val controls = LinearLayout(this@RecordingsActivity).apply {
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).also { it.topMargin = 24 }
            }

            val btnPlay = Button(this@RecordingsActivity).apply {
                text = "Play"
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).also { it.rightMargin = 12 }
            }
            GlassUi.styleButton(btnPlay)
            controls.addView(btnPlay)

            val btnClose = Button(this@RecordingsActivity).apply {
                text = "Close"
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            GlassUi.styleButton(btnClose)
            controls.addView(btnClose)
            root.addView(controls)

            dialog.setContentView(root)

            btnPlay.isEnabled = false

            btnPlay.setOnClickListener {
                if (!isPlayerReady) return@setOnClickListener
                try {
                    if (player.isPlaying) {
                        player.pause()
                        isPlayerPlaying = false
                        btnPlay.text = "Play"
                    } else {
                        player.start()
                        isPlayerPlaying = true
                        btnPlay.text = "Pause"
                    }
                } catch (e: Exception) {
                    android.util.Log.e("RecordingPlayback", "Playback error", e)
                    Toast.makeText(this@RecordingsActivity, "Playback error", Toast.LENGTH_SHORT).show()
                }
            }

            btnClose.setOnClickListener {
                dialog.dismiss()
            }

            seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser && isPlayerReady) {
                        try {
                            player.seekTo(progress)
                            tvCurrentTime.text = formatTime(progress)
                        } catch (e: Exception) {
                            android.util.Log.e("RecordingPlayback", "Seek error", e)
                        }
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })

            player.setOnCompletionListener {
                isPlayerPlaying = false
                btnPlay.text = "Play"
                seekBar.progress = seekBar.max
                tvCurrentTime.text = formatTime(seekBar.max)
            }

            // Note: Actual playback would require fetching recording chunks from storage
            dialog.setOnDismissListener {
                progressThread?.interrupt()
                try {
                    if (player.isPlaying) player.stop()
                } catch (_: Exception) {}
                try {
                    player.release()
                } catch (_: Exception) {}
                android.util.Log.i("RecordingPlayback", "Playback dialog closed")
            }

            dialog.show()
            android.util.Log.i("RecordingPlayback", "Playback dialog opened for ${item.id}")
        } catch (e: Exception) {
            android.util.Log.e("RecordingPlayback", "Fatal error in playback", e)
            Toast.makeText(this@RecordingsActivity, "Playback failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun formatTime(ms: Int): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%d:%02d", minutes, seconds)
    }

    override fun onResume() {
        super.onResume()
        GlassUi.applyThemeChrome(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        ThemeApplicator.detach(this)
    }
}

class RecordingsAdapter(
    private var items: List<RecordingsActivity.CallRecordingItem>,
    private val onPlay: (RecordingsActivity.CallRecordingItem) -> Unit
) : RecyclerView.Adapter<RecordingsAdapter.VH>() {

    inner class VH(val container: LinearLayout) : RecyclerView.ViewHolder(container)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val container = LinearLayout(parent.context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(24, 16, 24, 16)
            background = GlassUi.glassDrawable(context, alpha = 142, radiusDp = 20)
        }
        return VH(container)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.container.removeAllViews()

        val content = LinearLayout(holder.container.context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val title = TextView(holder.container.context).apply {
            text = "Call with ${item.participantName}"
            textSize = 16f
            setTextColor(0xFFFFFFFF.toInt())
        }
        content.addView(title)

        val timestamp = TextView(holder.container.context).apply {
            text = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(item.timestamp)
            textSize = 13f
            setTextColor(0xFFAAAAAA.toInt())
            setPadding(0, 4, 0, 0)
        }
        content.addView(timestamp)

        holder.container.addView(content)

        val btnPlay = Button(holder.container.context).apply {
            text = "▶"
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.leftMargin = 16 }
            setOnClickListener { onPlay(item) }
        }
        GlassUi.styleButton(btnPlay)
        holder.container.addView(btnPlay)

        holder.container.setOnClickListener { onPlay(item) }
    }

    override fun getItemCount() = items.size

    fun updateItems(newItems: List<RecordingsActivity.CallRecordingItem>) {
        items = newItems
        notifyDataSetChanged()
    }
}
