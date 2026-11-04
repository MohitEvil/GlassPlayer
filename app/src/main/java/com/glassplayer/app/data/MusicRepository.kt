package com.glassplayer.app.data

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import java.io.File

object MusicRepository {

    /** Reads every audio file known to MediaStore. Call from a background thread. */
    fun load(ctx: Context): List<Song> {
        val res = ctx.contentResolver
        val genreByAudioId = HashMap<Long, String>()

        try {
            res.query(
                MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Audio.Genres._ID, MediaStore.Audio.Genres.NAME),
                null, null, null
            )?.use { gc ->
                while (gc.moveToNext()) {
                    val gid = gc.getLong(0)
                    val gname = gc.getString(1)?.takeIf { it.isNotBlank() } ?: continue
                    val members = MediaStore.Audio.Genres.Members.getContentUri("external", gid)
                    res.query(
                        members,
                        arrayOf(MediaStore.Audio.Genres.Members.AUDIO_ID),
                        null, null, null
                    )?.use { mc ->
                        while (mc.moveToNext()) genreByAudioId[mc.getLong(0)] = gname
                    }
                }
            }
        } catch (_: Exception) {
            // Genre lookup is best-effort.
        }

        val out = ArrayList<Song>()
        val proj = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATA,
        )
        try {
            res.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                proj,
                "${MediaStore.Audio.Media.DURATION} > 0",
                null,
                "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val path = c.getString(7) ?: ""
                    val parent = if (path.isNotEmpty()) File(path).parentFile else null
                    out += Song(
                        id = id,
                        title = c.getString(1)?.takeIf { it.isNotBlank() } ?: "Unknown title",
                        artist = c.getString(2)?.takeIf { it.isNotBlank() } ?: "<unknown>",
                        album = c.getString(3)?.takeIf { it.isNotBlank() } ?: "<unknown>",
                        albumId = c.getLong(4),
                        genre = genreByAudioId[id] ?: "Unknown Genre",
                        folderPath = parent?.absolutePath ?: "unknown",
                        folderName = parent?.name ?: "Unknown",
                        durationMs = c.getLong(5),
                        dateAdded = c.getLong(6),
                        uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id),
                    )
                }
            }
        } catch (_: Exception) {
        }
        return out
    }
}
