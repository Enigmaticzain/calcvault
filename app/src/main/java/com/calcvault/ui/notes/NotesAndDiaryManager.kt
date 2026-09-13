package com.calcvault.ui.notes

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.calcvault.emotional.themes.ThemeApplicator
import com.calcvault.storage.provider.StorageManager
import com.calcvault.ui.common.GlassUi
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

data class Note(
    val id: String,
    val title: String,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long,
    val isPinned: Boolean = false,
    val author: String = "",
    val tags: List<String> = emptyList()
)

data class DiaryEntry(
    val id: String,
    val date: Long,
    val mood: String,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long
)

class NotesManager(private val storageManager: StorageManager) {

    fun saveNote(note: Note) {
        val json = JSONObject().apply {
            put("id", note.id)
            put("title", note.title)
            put("content", note.content)
            put("createdAt", note.createdAt)
            put("updatedAt", note.updatedAt)
            put("author", note.author)
            put("isPinned", note.isPinned)
            put("tags", JSONArray(note.tags))
        }
        // Store as JSON string
        try {
            storageManager.write("notes/${note.id}", json.toString().toByteArray())
        } catch (e: Exception) {
            // Fallback: just keep in memory
        }
    }

    fun getNote(id: String): Note? {
        return try {
            val data = storageManager.read("notes/$id") ?: return null
            val json = JSONObject(String(data))
            Note(
                id = json.getString("id"),
                title = json.getString("title"),
                content = json.getString("content"),
                createdAt = json.getLong("createdAt"),
                updatedAt = json.getLong("updatedAt"),
                author = json.optString("author", com.calcvault.utils.SessionManager.localUserId),
                isPinned = json.optBoolean("isPinned", false),
                tags = json.optJSONArray("tags")?.let { arr ->
                    (0 until arr.length()).map { arr.getString(it) }
                } ?: emptyList()
            )
        } catch (e: Exception) {
            null
        }
    }

    fun getAllNotes(): List<Note> {
        val notes = mutableListOf<Note>()
        try {
            val keys = storageManager.list("notes/")
            keys.forEach { key ->
                val data = storageManager.read(key) ?: return@forEach
                val json = JSONObject(String(data))
                notes.add(
                    Note(
                        id = json.getString("id"),
                        title = json.getString("title"),
                        content = json.getString("content"),
                        createdAt = json.getLong("createdAt"),
                        updatedAt = json.getLong("updatedAt"),
                        author = json.optString("author", com.calcvault.utils.SessionManager.localUserId),
                        isPinned = json.optBoolean("isPinned", false),
                        tags = json.optJSONArray("tags")?.let { arr ->
                            (0 until arr.length()).map { arr.getString(it) }
                        } ?: emptyList()
                    )
                )
            }
        } catch (e: Exception) {
            // Fallback: return empty list
        }
        return notes.sortedByDescending { it.updatedAt }
    }

    fun deleteNote(id: String) {
        try {
            storageManager.delete("notes/$id")
        } catch (e: Exception) {
            // Ignore
        }
    }

    fun searchNotes(query: String): List<Note> {
        return getAllNotes().filter { note ->
            note.title.contains(query, ignoreCase = true) ||
                note.content.contains(query, ignoreCase = true) ||
                note.tags.any { it.contains(query, ignoreCase = true) }
        }
    }
}

class DiaryManager(private val storageManager: StorageManager) {

    fun saveDiaryEntry(entry: DiaryEntry) {
        val json = JSONObject().apply {
            put("id", entry.id)
            put("date", entry.date)
            put("mood", entry.mood)
            put("content", entry.content)
            put("createdAt", entry.createdAt)
            put("updatedAt", entry.updatedAt)
        }
        try {
            storageManager.write("diary/${entry.id}", json.toString().toByteArray())
        } catch (e: Exception) {
            // Fallback
        }
    }

    fun getDiaryEntry(id: String): DiaryEntry? {
        return try {
            val data = storageManager.read("diary/$id") ?: return null
            val json = JSONObject(String(data))
            DiaryEntry(
                id = json.getString("id"),
                date = json.getLong("date"),
                mood = json.getString("mood"),
                content = json.getString("content"),
                createdAt = json.getLong("createdAt"),
                updatedAt = json.getLong("updatedAt")
            )
        } catch (e: Exception) {
            null
        }
    }

    fun getDiaryEntriesByDate(startDate: Long, endDate: Long): List<DiaryEntry> {
        val entries = mutableListOf<DiaryEntry>()
        try {
            val keys = storageManager.list("diary/")
            keys.forEach { key ->
                val data = storageManager.read(key) ?: return@forEach
                val json = JSONObject(String(data))
                val date = json.getLong("date")
                if (date in startDate..endDate) {
                    entries.add(
                        DiaryEntry(
                            id = json.getString("id"),
                            date = date,
                            mood = json.getString("mood"),
                            content = json.getString("content"),
                            createdAt = json.getLong("createdAt"),
                            updatedAt = json.getLong("updatedAt")
                        )
                    )
                }
            }
        } catch (e: Exception) {
            // Fallback
        }
        return entries.sortedByDescending { it.date }
    }

    fun getAllDiaryEntries(): List<DiaryEntry> {
        val entries = mutableListOf<DiaryEntry>()
        try {
            val keys = storageManager.list("diary/")
            keys.forEach { key ->
                val data = storageManager.read(key) ?: return@forEach
                val json = JSONObject(String(data))
                entries.add(
                    DiaryEntry(
                        id = json.getString("id"),
                        date = json.getLong("date"),
                        mood = json.getString("mood"),
                        content = json.getString("content"),
                        createdAt = json.getLong("createdAt"),
                        updatedAt = json.getLong("updatedAt")
                    )
                )
            }
        } catch (e: Exception) {
            // Fallback
        }
        return entries.sortedByDescending { it.date }
    }

    fun deleteDiaryEntry(id: String) {
        try {
            storageManager.delete("diary/$id")
        } catch (e: Exception) {
            // Ignore
        }
    }

    fun getMoodStats(): Map<String, Int> {
        val stats = mutableMapOf<String, Int>()
        getAllDiaryEntries().forEach { entry ->
            stats[entry.mood] = (stats[entry.mood] ?: 0) + 1
        }
        return stats
    }
}

class NotesListActivity : AppCompatActivity() {

    private lateinit var notesManager: NotesManager
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: NotesAdapter
    private var notes = mutableListOf<Note>()
    private var showingPartnerNotes = false
    private lateinit var btnToggleFilter: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!StorageManager.isReady()) {
            Toast.makeText(this, "Storage not initialized", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        notesManager = NotesManager(StorageManager)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(-1, -1)
        }
        setContentView(root)

        // Toolbar
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(-1, dp(72))
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(16), 0, dp(16), 0)
        }
        root.addView(toolbar)

        TextView(this).apply {
            text = "Notes"
            textSize = 20f
            setTextColor(android.graphics.Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            toolbar.addView(this)
        }

        btnToggleFilter = Button(this).apply {
            text = "My Notes"
            layoutParams = LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(8) }
            setOnClickListener {
                showingPartnerNotes = !showingPartnerNotes
                text = if (showingPartnerNotes) "Partner's Notes" else "My Notes"
                loadNotes()
            }
            toolbar.addView(this)
        }

        Button(this).apply {
            text = "+ New"
            layoutParams = LinearLayout.LayoutParams(-2, -2)
            setOnClickListener { createNewNote() }
            toolbar.addView(this)
        }

        // RecyclerView
        recyclerView = RecyclerView(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
            layoutManager = LinearLayoutManager(this@NotesListActivity)
        }
        root.addView(recyclerView)

        adapter = NotesAdapter(notes) { note ->
            editNote(note)
        }
        recyclerView.adapter = adapter

        GlassUi.applyThemeChrome(this, root)
        GlassUi.markPanel(toolbar, alpha = 152, radiusDp = 0)

        loadNotes()
    }

    override fun onResume() {
        super.onResume()
        GlassUi.applyThemeChrome(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        ThemeApplicator.detach(this)
    }

    private fun loadNotes() {
        notes.clear()
        val all = notesManager.getAllNotes()
        val targetAuthor = if (showingPartnerNotes) com.calcvault.utils.SessionManager.partnerUserId else com.calcvault.utils.SessionManager.localUserId
        notes.addAll(all.filter { it.author == targetAuthor || it.author.isBlank() })
        adapter.notifyDataSetChanged()
    }

    private fun createNewNote() {
        val note = Note(
            id = UUID.randomUUID().toString(),
            title = "New Note",
            content = "",
            author = com.calcvault.utils.SessionManager.localUserId,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        editNote(note)
    }

    private fun editNote(note: Note) {
        val dialog = android.app.AlertDialog.Builder(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        val titleEdit = EditText(this).apply {
            setText(note.title)
            hint = "Title"
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 8 }
        }
        layout.addView(titleEdit)

        val contentEdit = EditText(this).apply {
            setText(note.content)
            hint = "Content"
            layoutParams = LinearLayout.LayoutParams(-1, 200)
        }
        layout.addView(contentEdit)

        dialog.setView(layout)
        dialog.setPositiveButton("Save") { _, _ ->
            val updated = note.copy(
                title = titleEdit.text.toString(),
                content = contentEdit.text.toString(),
                updatedAt = System.currentTimeMillis()
            )
            notesManager.saveNote(updated)
            loadNotes()
        }
        dialog.setNegativeButton("Delete") { _, _ ->
            notesManager.deleteNote(note.id)
            loadNotes()
        }
        dialog.show()
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}

class NotesAdapter(
    private val notes: List<Note>,
    private val onItemClick: (Note) -> Unit
) : RecyclerView.Adapter<NotesAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
        val density = parent.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val view = LinearLayout(parent.context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = RecyclerView.LayoutParams(-1, -2).apply {
                setMargins(dp(16), dp(8), dp(16), dp(8))
            }
            setPadding(dp(18), dp(14), dp(18), dp(14))
            background = GlassUi.glassDrawable(context, alpha = 138, radiusDp = 20)
        }
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val note = notes[position]
        holder.bind(note, onItemClick)
    }

    override fun getItemCount() = notes.size

    class ViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
        fun bind(note: Note, onItemClick: (Note) -> Unit) {
            val layout = itemView as LinearLayout
            layout.removeAllViews()

            TextView(itemView.context).apply {
                text = note.title
                textSize = 16f
                setTextColor(android.graphics.Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(-1, -2)
                layout.addView(this)
            }

            TextView(itemView.context).apply {
                text = note.content.take(100)
                textSize = 12f
                setTextColor(android.graphics.Color.parseColor("#D1D5DB"))
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = 4 }
                layout.addView(this)
            }

            val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            TextView(itemView.context).apply {
                text = dateFormat.format(Date(note.updatedAt))
                textSize = 10f
                setTextColor(android.graphics.Color.parseColor("#A7B0C0"))
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = 4 }
                layout.addView(this)
            }

            itemView.setOnClickListener { onItemClick(note) }
        }
    }
}
