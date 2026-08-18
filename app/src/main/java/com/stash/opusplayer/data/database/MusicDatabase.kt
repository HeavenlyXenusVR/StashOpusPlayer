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
    entities = [FavoriteEntity::class, PlaylistEntity::class, PlaylistTrackEntity::class, MetadataInfo::class, SongEntity::class, SmartPlaylistEntity::class, RecentlyDeletedEntity::class, CorruptFileEntity::class, BpmCacheEntity::class],
    version = 7,
    exportSchema = false
)
abstract class MusicDatabase : RoomDatabase() {

    abstract fun favoriteDao(): FavoriteDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun metadataDao(): MetadataDao
    abstract fun songDao(): SongDao
    abstract fun smartPlaylistDao(): SmartPlaylistDao
    abstract fun recentlyDeletedDao(): RecentlyDeletedDao
    abstract fun corruptFileDao(): CorruptFileDao
    abstract fun bpmCacheDao(): BpmCacheDao

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

        // Adds the "smart_playlists" table (see SmartPlaylistEntity) -- brand
        // new table backing the Lua-scripted smart playlist feature, same
        // "no data transformation needed" shape as MIGRATION_3_4.
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `smart_playlists` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `luaScript` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        // Adds the "recently_deleted" (in-app trash staging, see
        // RecentlyDeletedService) and "corrupt_files" (see AudioFileValidator /
        // CorruptFileFinderWorker) tables -- both brand new, no data
        // transformation needed, same shape as MIGRATION_3_4/MIGRATION_4_5.
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `recently_deleted` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `songId` INTEGER NOT NULL,
                        `title` TEXT NOT NULL,
                        `artist` TEXT NOT NULL,
                        `album` TEXT NOT NULL,
                        `originalPath` TEXT NOT NULL,
                        `trashFileName` TEXT NOT NULL,
                        `sizeBytes` INTEGER NOT NULL,
                        `deletedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `corrupt_files` (
                        `songId` INTEGER NOT NULL,
                        `title` TEXT NOT NULL,
                        `artist` TEXT NOT NULL,
                        `path` TEXT NOT NULL,
                        `sizeBytes` INTEGER NOT NULL,
                        `reason` TEXT NOT NULL,
                        `flaggedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`songId`)
                    )
                    """.trimIndent()
                )
            }
        }

        // Adds the "bpm_cache" table -- see BpmAnalyzer/BpmCacheEntity. Brand
        // new table, no data transformation needed, same shape as the prior
        // migrations.
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `bpm_cache` (
                        `songId` INTEGER NOT NULL,
                        `bpm` REAL NOT NULL,
                        `sizeBytes` INTEGER NOT NULL,
                        `analyzedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`songId`)
                    )
                    """.trimIndent()
                )
            }
        }

        fun getDatabase(context: Context): MusicDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MusicDatabase::class.java,
                    "music_database"
                ).addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                 .fallbackToDestructiveMigration()
                 .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
