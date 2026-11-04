package com.glassplayer.app.data

import android.net.Uri

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val genre: String,
    val folderPath: String,
    val folderName: String,
    val durationMs: Long,
    val dateAdded: Long,
    val uri: Uri,
)

data class Playlist(
    val id: String,
    val name: String,
    val songIds: List<Long>,
)

/** A named bucket of songs: an artist, album, genre, folder or playlist. */
data class Group(
    val key: String,
    val name: String,
    val songs: List<Song>,
)
