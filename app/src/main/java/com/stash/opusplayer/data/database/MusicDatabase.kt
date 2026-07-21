package com.stash.opusplayer.data.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.content.Context
import com.stash.opusplayer.data.MetadataInfo
import com.stash.opusplayer.data.MetadataDao

@Database(
    entities = [FavoriteEntity::class, PlaylistEntity::class, PlaylistTrackEntity::class, MetadataInfo::class, SongEntity::class],
    version = 4,
    exportSchema = false
)
abstract class MusicDatabase : RoomDatabase() {

    abstract fun favoriteDao(): FavoriteDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun metadataDao(): MetadataDao
    abstract fun songDao(): SongDao

    companion object {
        @Volatile
        private var INSTANCE: MusicDatabase? = null

        // Adds the persisted song library index ("songs" table). Brand new table, so no data
        // transformation is needed - just create it alongside the existing tables.
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `songs` (
                        `id` INTEGER NOT NULL,
                        `title` TEXT NOT NULL,
                        `artist` TEXT NOT NULL,
                        `album` TEXT NOT NULL,
                        `albumId` INTEGER NOT NULL,
                        `artistId` INTEGER NOT NULL,
                        `duration` INTEGER NOT NULL,
                        `path` TEXT NOT NULL,
                        `dateAdded` INTEGER NOT NULL,
                        `size` INTEGER NOT NULL,
                        `mimeType` TEXT NOT NULL,
                        `relativePath` TEXT NOT NULL,
                        `albumArt` TEXT,
                        `track` INTEGER NOT NULL,
                        `year` TEXT NOT NULL,
                        `genre` TEXT NOT NULL,
                        `bitrate` INTEGER NOT NULL,
                        `sampleRate` INTEGER NOT NULL,
                        `lastScanned` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_songs_path` ON `songs` (`path`)"
                )
            }
        }

        fun getDatabase(context: Context): MusicDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MusicDatabase::class.java,
                    "music_database"
                ).addMigrations(MIGRATION_3_4)
                 .fallbackToDestructiveMigration()
                 .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
