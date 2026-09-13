package com.calcvault.ui.media

import android.graphics.Bitmap
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.calcvault.emotional.ThemeEngine
import com.calcvault.emotional.themes.ThemeApplicator
import com.calcvault.messaging.AppendOnlyMessageDB
import com.calcvault.messaging.MessageRecord
import com.calcvault.storage.MediaChunkManager
import com.calcvault.storage.USBStorageEngine
import com.calcvault.ui.common.GlassUi
import kotlinx.coroutines.*
import org.json.JSONObject

/**
 * MediaActivity
 */
class MediaActivity : AppCompatActivity() {

    private lateinit var storageEngine: USBStorageEngine
    private lateinit var messageDB: AppendOnlyMessageDB
    private lateinit var mediaManager: MediaChunkManager
    private lateinit var themeEngine: ThemeEngine

    private lateinit var rvGrid: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var spinnerFilter: Spinner

    private val mediaItems = mutableListOf<MediaItem>()
    private lateinit var adapter: MediaGridAdapter

    private var currentFilter = MediaFilter.ALL
    enum class MediaFilter { ALL, IMAGES, VIDEOS, AUDIO, FILES }

    data class MediaItem(
        val messageRecord: MessageRecord,
        var thumbnail: Bitmap? = null,
        val type: MediaChunkManager.MediaType
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        storageEngine = USBStorageEngine.getInstance(this)
        messageDB = AppendOnlyMessageDB(storageEngine)
        mediaManager = MediaChunkManager(storageEngine, messageDB)
        themeEngine = ThemeEngine(this)

        buildUI()

        // Fix: Apply theme AFTER building UI
        GlassUi.applyThemeChrome(this)

        loadMedia()
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
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
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
            text = "Media"
            textSize = 20f
            setTextColor(theme.primaryText)
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }
        header.addView(tvTitle)

        // Filter spinner
        spinnerFilter = Spinner(this).apply {
            val options = arrayOf("All", "Images", "Videos", "Audio", "Files")
            adapter = ArrayAdapter(
                this@MediaActivity,
                android.R.layout.simple_spinner_item,
                options
            ).also {
                it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                    currentFilter = MediaFilter.values()[pos]
                    applyFilter()
                }
                override fun onNothingSelected(p: AdapterView<*>?) {}
            }
        }
        header.addView(spinnerFilter)

        // Grid
        rvGrid = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(this@MediaActivity, 3)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }
        adapter = MediaGridAdapter(mediaItems) { item -> openMediaFullscreen(item) }
        rvGrid.adapter = adapter
        layout.addView(rvGrid)

        // Empty state
        tvEmpty = TextView(this).apply {
            text = "No media yet.\nSend a photo or video to see it here."
            textSize = 15f
            setTextColor(theme.secondaryText)
            gravity = Gravity.CENTER
            visibility = View.GONE
        }
        layout.addView(tvEmpty)
    }

    private fun loadMedia() {
        lifecycleScope.launch(Dispatchers.IO) {
            val mediaMessages = messageDB.getMessages(AppendOnlyMessageDB.MSG_IMAGE) +
                messageDB.getMessages(AppendOnlyMessageDB.MSG_VIDEO) +
                messageDB.getMessages(AppendOnlyMessageDB.MSG_AUDIO) +
                messageDB.getMessages(AppendOnlyMessageDB.MSG_FILE)

            val items = mediaMessages.map { msg ->
                val type = when (msg.type) {
                    AppendOnlyMessageDB.MSG_IMAGE -> MediaChunkManager.MediaType.IMAGE
                    AppendOnlyMessageDB.MSG_VIDEO -> MediaChunkManager.MediaType.VIDEO
                    AppendOnlyMessageDB.MSG_AUDIO -> MediaChunkManager.MediaType.AUDIO
                    AppendOnlyMessageDB.MSG_FILE -> MediaChunkManager.MediaType.FILE
                    else -> MediaChunkManager.MediaType.FILE
                }

                val extra = try { JSONObject(msg.extra) } catch (e: Exception) { null }
                val thumbHash = extra?.optString("thumb") ?: ""
                val thumb = if (thumbHash.isNotEmpty()) mediaManager.decodeThumbnail(thumbHash) else null

                MediaItem(msg, thumb, type)
            }.sortedByDescending { it.messageRecord.timestamp }

            withContext(Dispatchers.Main) {
                mediaItems.clear()
                mediaItems.addAll(items)
                adapter.notifyDataSetChanged()
                tvEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun applyFilter() {
        val filtered = when (currentFilter) {
            MediaFilter.ALL -> mediaItems
            MediaFilter.IMAGES -> mediaItems.filter { it.type == MediaChunkManager.MediaType.IMAGE }
            MediaFilter.VIDEOS -> mediaItems.filter { it.type == MediaChunkManager.MediaType.VIDEO }
            MediaFilter.AUDIO -> mediaItems.filter { it.type == MediaChunkManager.MediaType.AUDIO }
            MediaFilter.FILES -> mediaItems.filter { it.type == MediaChunkManager.MediaType.FILE }
        }
        adapter.updateItems(filtered)
    }

    private fun openMediaFullscreen(item: MediaItem) {
        lifecycleScope.launch {
            val materialized = SecureMediaOpenHelper.materialize(
                activity = this@MediaActivity,
                mediaManager = mediaManager,
                message = item.messageRecord
            )
            if (materialized == null) {
                Toast.makeText(this@MediaActivity, "Failed to open media", Toast.LENGTH_SHORT).show()
            } else {
                SecureMediaOpenHelper.open(this@MediaActivity, materialized)
            }
        }
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

class MediaGridAdapter(
    private var items: List<MediaActivity.MediaItem>,
    private val onClick: (MediaActivity.MediaItem) -> Unit
) : RecyclerView.Adapter<MediaGridAdapter.VH>() {

    inner class VH(val container: FrameLayout) : RecyclerView.ViewHolder(container)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val size = parent.measuredWidth / 3
        val container = FrameLayout(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(size, size)
        }
        return VH(container)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.container.removeAllViews()

        val iv = android.widget.ImageView(holder.container.context).apply {
            scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            background = GlassUi.glassDrawable(context, alpha = 135, radiusDp = 18)
            if (item.thumbnail != null) setImageBitmap(item.thumbnail)
        }

        val badge = android.widget.TextView(holder.container.context).apply {
            text = when (item.type) {
                MediaChunkManager.MediaType.IMAGE -> ""
                MediaChunkManager.MediaType.VIDEO -> "▶"
                MediaChunkManager.MediaType.AUDIO -> "🎵"
                MediaChunkManager.MediaType.FILE -> "📄"
            }
            textSize = 18f
            setTextColor(0xFFFFFFFF.toInt())
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.START
            ).also { it.setMargins(8, 0, 0, 8) }
        }

        holder.container.addView(iv)
        holder.container.addView(badge)
        holder.container.setOnClickListener { onClick(item) }
    }

    override fun getItemCount() = items.size

    fun updateItems(newItems: List<MediaActivity.MediaItem>) {
        items = newItems
        notifyDataSetChanged()
    }
}
