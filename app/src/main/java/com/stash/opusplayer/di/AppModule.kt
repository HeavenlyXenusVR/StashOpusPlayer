package com.stash.opusplayer.di

import android.content.Context
import com.stash.opusplayer.data.MetadataDao
import com.stash.opusplayer.data.database.FavoriteDao
import com.stash.opusplayer.data.database.MusicDatabase
import com.stash.opusplayer.data.database.PlaylistDao
import com.stash.opusplayer.data.database.SmartPlaylistDao
import com.stash.opusplayer.data.database.SongDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Root Hilt module. Feature modules (networking, DataStore, etc.) added by
 * later tasks should live in their own files under this package rather than
 * growing this one indefinitely.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideMusicDatabase(@ApplicationContext context: Context): MusicDatabase =
        MusicDatabase.getDatabase(context)

    @Provides
    fun provideFavoriteDao(database: MusicDatabase): FavoriteDao = database.favoriteDao()

    @Provides
    fun providePlaylistDao(database: MusicDatabase): PlaylistDao = database.playlistDao()

    @Provides
    fun provideMetadataDao(database: MusicDatabase): MetadataDao = database.metadataDao()

    @Provides
    fun provideSongDao(database: MusicDatabase): SongDao = database.songDao()

    @Provides
    fun provideSmartPlaylistDao(database: MusicDatabase): SmartPlaylistDao = database.smartPlaylistDao()
}
