package com.stash.opusplayer.ui.adapters

import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.Glide
import com.stash.opusplayer.R
import com.stash.opusplayer.data.Song
import com.stash.opusplayer.utils.MetadataExtractor

/**
 * Horizontal-shelf song card adapter, used by the Home screen's
 * Favorites/Recently Added shelves (see `item_shelves_header.xml`). Album
 * art loading mirrors [SongAdapter.loadAlbumArt]'s exact three-tier
 * strategy (cached artwork -> embedded Base64 bytes -> default icon) --
 * duplicated here rather than shared because [SongAdapter]'s art-loading is
 * a private method tied to its own list/grid item ViewBinding types, not
 * this compact shelf-card layout.
 */
class ShelfSongAdapter(
    private val metadataExtractor: MetadataExtractor?,
    private val onSongClick: (Song) -> Unit
) : RecyclerView.Adapter<ShelfSongAdapter.ViewHolder>() {

    private var songs: List<Song> = emptyList()

    fun submitList(newSongs: List<Song>) {
        songs = newSongs
        notifyDataSetChanged()
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val artwork: android.widget.ImageView = view.findViewById(R.id.shelfSongArtwork)
        val title: android.widget.TextView = view.findViewById(R.id.shelfSongTitle)
        val artist: android.widget.TextView = view.findViewById(R.id.shelfSongArtist)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_shelf_song, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val song = songs[position]
        holder.title.text = song.displayName
        holder.artist.text = song.artistName
        holder.itemView.setOnClickListener { onSongClick(song) }
        loadArtwork(holder, song)
    }

    private fun loadArtwork(holder: ViewHolder, song: Song) {
        val context = holder.itemView.context
        val cached = try { metadataExtractor?.loadCachedArtwork(context, song, 256) } catch (_: Exception) { null }
        if (cached != null) {
            Glide.with(context).load(cached).centerCrop().into(holder.artwork)
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
                    .into(holder.artwork)
                return
            }
        }
        Glide.with(context).load(R.drawable.ic_music_note).into(holder.artwork)
    }

    override fun getItemCount(): Int = songs.size
}
