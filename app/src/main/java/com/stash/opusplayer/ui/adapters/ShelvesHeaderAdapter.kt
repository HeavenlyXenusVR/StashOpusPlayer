package com.stash.opusplayer.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.stash.opusplayer.R
import com.stash.opusplayer.data.Song
import com.stash.opusplayer.utils.MetadataExtractor

/**
 * Single-item [RecyclerView.Adapter] holding the Home screen's shelves
 * (Favorites, Recently Added) plus an "All Songs" label, meant to be
 * combined with [SongAdapter] via `androidx.recyclerview.widget.ConcatAdapter`
 * so the shelves scroll away together with the full song list underneath
 * rather than living in a separate, non-virtualized container -- important
 * since the song list itself can be large.
 *
 * Always reports [getItemCount] = 1 (never 0), even before any shelf data
 * has loaded or if both shelves end up empty -- each shelf section instead
 * collapses to [View.GONE] on its own via [submitShelves]. This keeps
 * "position 0 in the ConcatAdapter is always this header" a fixed
 * invariant, which `MusicLibraryFragment`'s `GridLayoutManager.SpanSizeLookup`
 * (full-width span for the header, 1-span for song items) depends on.
 *
 * "Recently Played" was deliberately NOT added as a third shelf: there is
 * no local on-device play-history table today -- play logging
 * ([com.stash.opusplayer.history.PlayHistoryLogger]) only pushes to the
 * bridge's `/user/history` and is silent no-op when logged out, with no
 * local read-back. A shelf backed by that would be empty for anyone not
 * signed into the bridge, which most of this screen's other data isn't
 * gated behind.
 */
class ShelvesHeaderAdapter(
    private val metadataExtractor: MetadataExtractor?,
    private val onSongClick: (Song) -> Unit
) : RecyclerView.Adapter<ShelvesHeaderAdapter.ViewHolder>() {

    private var favorites: List<Song> = emptyList()
    private var recentlyAdded: List<Song> = emptyList()
    private var hasAnySongs: Boolean = false
    private var boundHolder: ViewHolder? = null

    fun submitShelves(favorites: List<Song>, recentlyAdded: List<Song>, hasAnySongs: Boolean) {
        this.favorites = favorites
        this.recentlyAdded = recentlyAdded
        this.hasAnySongs = hasAnySongs
        boundHolder?.bind()
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val favoritesSection: View = view.findViewById(R.id.favoritesShelfSection)
        val favoritesRecycler: RecyclerView = view.findViewById(R.id.favoritesShelfRecycler)
        val recentlyAddedSection: View = view.findViewById(R.id.recentlyAddedShelfSection)
        val recentlyAddedRecycler: RecyclerView = view.findViewById(R.id.recentlyAddedShelfRecycler)
        val allSongsHeader: View = view.findViewById(R.id.allSongsHeader)

        private val favoritesAdapter = ShelfSongAdapter(metadataExtractor, onSongClick)
        private val recentlyAddedAdapter = ShelfSongAdapter(metadataExtractor, onSongClick)

        init {
            favoritesRecycler.layoutManager = LinearLayoutManager(view.context, LinearLayoutManager.HORIZONTAL, false)
            favoritesRecycler.adapter = favoritesAdapter
            recentlyAddedRecycler.layoutManager = LinearLayoutManager(view.context, LinearLayoutManager.HORIZONTAL, false)
            recentlyAddedRecycler.adapter = recentlyAddedAdapter
        }

        fun bind() {
            favoritesSection.visibility = if (favorites.isEmpty()) View.GONE else View.VISIBLE
            favoritesAdapter.submitList(favorites)
            recentlyAddedSection.visibility = if (recentlyAdded.isEmpty()) View.GONE else View.VISIBLE
            recentlyAddedAdapter.submitList(recentlyAdded)
            allSongsHeader.visibility = if (hasAnySongs && (favorites.isNotEmpty() || recentlyAdded.isNotEmpty())) View.VISIBLE else View.GONE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_shelves_header, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        boundHolder = holder
        holder.bind()
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        if (boundHolder === holder) boundHolder = null
    }

    override fun getItemCount(): Int = 1
}
