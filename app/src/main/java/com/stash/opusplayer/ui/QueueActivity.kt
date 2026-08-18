package com.stash.opusplayer.ui

import android.os.Bundle
import android.util.Base64
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.stash.opusplayer.R
import com.stash.opusplayer.data.Song
import com.stash.opusplayer.databinding.ItemQueueSongBinding
import com.stash.opusplayer.utils.MetadataExtractor
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class QueueActivity : AppCompatActivity() {

    private lateinit var recycler: RecyclerView
    private lateinit var titleText: TextView
    private var adapter = QueueAdapter { index ->
        try {
            val mgr = (application as com.stash.opusplayer.StashOpusApplication).playerManager
            mgr.playFromPlaylist(index)
            finish()
        } catch (_: Exception) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_queue)

        findViewById<View>(R.id.backButton).setOnClickListener { finish() }
        titleText = findViewById(R.id.titleText)
        recycler = findViewById(R.id.queueRecycler)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        // Enable drag-and-drop reordering and swipe-to-remove
        val touchHelper = androidx.recyclerview.widget.ItemTouchHelper(object : androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback(
            androidx.recyclerview.widget.ItemTouchHelper.UP or androidx.recyclerview.widget.ItemTouchHelper.DOWN,
            androidx.recyclerview.widget.ItemTouchHelper.START or androidx.recyclerview.widget.ItemTouchHelper.END
        ) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                val from = vh.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                adapter.onItemMoved(from, to)
                val mgr = (application as com.stash.opusplayer.StashOpusApplication).playerManager
                mgr.moveItem(from, to)
                return true
            }
            override fun onSwiped(vh: RecyclerView.ViewHolder, dir: Int) {
                val pos = vh.bindingAdapterPosition
                val mgr = (application as com.stash.opusplayer.StashOpusApplication).playerManager
                mgr.removeItem(pos)
            }
            override fun isLongPressDragEnabled(): Boolean = true
        })
        touchHelper.attachToRecyclerView(recycler)

        val mgr = (application as com.stash.opusplayer.StashOpusApplication).playerManager
        lifecycleScope.launch {
            mgr.playlist.collectLatest { list ->
                adapter.submit(list, mgr.currentIndex.value)
                titleText.text = "Queue (${list.size})"
            }
        }
        lifecycleScope.launch {
            mgr.currentIndex.collectLatest { idx ->
                adapter.updateCurrent(idx)
            }
        }
    }

    private class QueueAdapter(
        val onClick: (Int) -> Unit
    ) : RecyclerView.Adapter<QueueViewHolder>() {
        private var items: List<Song> = emptyList()
        private var currentIndex: Int = -1
        private var metadataExtractor: MetadataExtractor? = null

        fun submit(list: List<Song>, current: Int) {
            items = list
            currentIndex = current
            notifyDataSetChanged()
        }
        fun updateCurrent(current: Int) {
            currentIndex = current
            notifyDataSetChanged()
        }
        fun onItemMoved(from: Int, to: Int) {
            if (from !in items.indices || to !in items.indices) return
            val mutable = items.toMutableList()
            val item = mutable.removeAt(from)
            mutable.add(to, item)
            items = mutable
            notifyItemMoved(from, to)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QueueViewHolder {
            if (metadataExtractor == null) metadataExtractor = MetadataExtractor(parent.context)
            val binding = ItemQueueSongBinding.inflate(android.view.LayoutInflater.from(parent.context), parent, false)
            return QueueViewHolder(binding, metadataExtractor, onClick)
        }
        override fun getItemCount(): Int = items.size
        override fun onBindViewHolder(holder: QueueViewHolder, position: Int) {
            holder.bind(items[position], position == currentIndex, position)
        }
    }

    /** Ported from the same three-tier album-art loading strategy as [com.stash.opusplayer.ui.adapters.SongAdapter.loadAlbumArt]/[com.stash.opusplayer.ui.adapters.ShelfSongAdapter]: cached artwork -> embedded Base64 bytes -> default icon. */
    private class QueueViewHolder(
        private val binding: ItemQueueSongBinding,
        private val metadataExtractor: MetadataExtractor?,
        val onClick: (Int) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(song: Song, isCurrent: Boolean, position: Int) {
            binding.queueSongTitle.text = song.displayName
            binding.queueSongArtist.text = song.artistName
            binding.queueNowPlayingIcon.visibility = if (isCurrent) View.VISIBLE else View.GONE
            binding.root.setOnClickListener { onClick(position) }
            loadArtwork(song)
        }

        private fun loadArtwork(song: Song) {
            val context = binding.root.context
            val cached = try { metadataExtractor?.loadCachedArtwork(context, song, 256) } catch (_: Exception) { null }
            if (cached != null) {
                Glide.with(context).load(cached).centerCrop().into(binding.queueSongArtwork)
                return
            }
            if (!song.albumArt.isNullOrEmpty()) {
                val artBytes = try { Base64.decode(song.albumArt, Base64.DEFAULT) } catch (_: IllegalArgumentException) { null }
                if (artBytes != null && artBytes.isNotEmpty()) {
                    Glide.with(context)
                        .load(artBytes)
                        .placeholder(R.drawable.ic_music_note)
                        .error(R.drawable.ic_music_note)
                        .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                        .centerCrop()
                        .into(binding.queueSongArtwork)
                    return
                }
            }
            Glide.with(context).load(R.drawable.ic_music_note).into(binding.queueSongArtwork)
        }
    }
}
