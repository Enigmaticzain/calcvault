package com.calcvault.ui.vault

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.calcvault.R
import com.calcvault.storage.provider.StorageManager

/**
 * Simple vault browser for stored keys/files.
 * Keeps existing feature wiring alive for manifest navigation.
 */
class VaultFilesActivity : AppCompatActivity() {

    private lateinit var tvFileCount: TextView
    private lateinit var rvFiles: RecyclerView
    private val filesAdapter = VaultFilesAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vault_files)

        tvFileCount = findViewById(R.id.tvFileCount)
        rvFiles = findViewById(R.id.rvFiles)
        rvFiles.layoutManager = LinearLayoutManager(this)
        rvFiles.adapter = filesAdapter

        refreshFiles()
    }

    override fun onResume() {
        super.onResume()
        refreshFiles()
    }

    private fun refreshFiles() {
        val keys = if (StorageManager.isReady()) {
            StorageManager.list().sorted()
        } else {
            emptyList()
        }

        tvFileCount.text = if (StorageManager.isReady()) {
            "${keys.size} files stored securely"
        } else {
            "Storage unavailable"
        }

        filesAdapter.submit(keys)
    }

    private class VaultFilesAdapter : RecyclerView.Adapter<VaultFilesAdapter.ItemVH>() {
        private val items = mutableListOf<String>()

        fun submit(newItems: List<String>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemVH {
            val view = LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_list_item_1, parent, false)
            return ItemVH(view)
        }

        override fun onBindViewHolder(holder: ItemVH, position: Int) {
            holder.text.text = items[position]
        }

        override fun getItemCount(): Int = items.size

        class ItemVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val text: TextView = itemView.findViewById(android.R.id.text1)
        }
    }
}
