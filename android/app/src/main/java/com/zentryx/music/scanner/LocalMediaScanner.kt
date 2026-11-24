package com.zentryx.music.scanner

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import com.zentryx.music.database.dao.SongDao
import com.zentryx.music.database.entities.SongEntity
import com.zentryx.music.di.IoDispatcher
import com.zentryx.music.models.AudioFormat
import com.zentryx.music.player.SettingsPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Scans local USB OTG drives, MicroSD cards, internal device storage (via MediaStore & direct mount points),
 * and user-selected SAF directory trees so Zentryx Music works reliably on automotive headunits.
 */
@Singleton
class LocalMediaScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val songDao: SongDao,
    private val artworkDiskCacheManager: ArtworkDiskCacheManager,
    private val settingsPreferences: SettingsPreferences,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    suspend fun scanLocalAudio(explicitTreeUri: Uri? = null, additive: Boolean = false): Int = withContext(ioDispatcher) {
        val discovered = mutableListOf<SongEntity>()
        val keepIds = mutableListOf<String>()
        val seenKeys = HashSet<String>()

        if (explicitTreeUri != null) {
            val label = runCatching {
                DocumentFile.fromTreeUri(context, explicitTreeUri)?.name ?: explicitTreeUri.lastPathSegment
            }.getOrNull() ?: "USB Audio"
            settingsPreferences.addLocalSourceFolder(explicitTreeUri.toString(), label)
        }

        if (additive && explicitTreeUri != null) {
            scanSafTree(explicitTreeUri, discovered, keepIds, seenKeys)
            if (discovered.isNotEmpty()) {
                discovered.chunked(30).forEach { batch ->
                    songDao.insertSongs(batch)
                }
            }
            return@withContext discovered.size
        }

        val savedUris = settingsPreferences.localSourceUrisFlow.firstOrNull().orEmpty()
            .mapNotNull { (uriStr, _) -> runCatching { Uri.parse(uriStr) }.getOrNull() }
            .toMutableList()
        if (explicitTreeUri != null && savedUris.none { it == explicitTreeUri }) {
            savedUris.add(explicitTreeUri)
        }

        for (treeUri in savedUris) {
            scanSafTree(treeUri, discovered, keepIds, seenKeys)
        }

        val hasPerm = hasReadAudioPermission()
        if (hasPerm) {
            scanMediaStoreAudio(discovered, keepIds, seenKeys)
            scanMountedExternalStorage(discovered, keepIds, seenKeys)
        }

        if (discovered.isNotEmpty()) {
            discovered.chunked(30).forEach { batch ->
                songDao.insertSongs(batch)
            }
        }

        if (!additive) {
            if (keepIds.isNotEmpty()) {
                if (keepIds.size <= 900) {
                    songDao.deleteLocalSongsNotIn(keepIds)
                } else {
                    val keepSet = keepIds.toHashSet()
                    val stale = songDao.getAllSongsOnce()
                        .filter { it.sourceType == "LOCAL" && !keepSet.contains(it.id) }
                        .map { it.id }
                    stale.chunked(500).forEach { songDao.deleteSongs(it) }
                }
            } else if (savedUris.isEmpty() && hasPerm) {
                songDao.deleteAllLocalSongs()
            }
        }

        discovered.size
    }

    fun hasReadAudioPermission(): Boolean {
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Enumerates mounted removable USB OTG drives and MicroSD volumes visible to the headunit.
     */
    fun listMountedRemovableVolumes(): List<String> {
        val detected = linkedSetOf<String>()
        runCatching {
            File("/storage").listFiles()?.forEach { vol ->
                if (vol.isDirectory && vol.name !in setOf("self", "emulated") && vol.canRead()) {
                    detected.add("USB/SD: ${vol.name}")
                }
            }
        }
        listOf("/mnt/usb_storage", "/mnt/usb", "/mnt/media_rw").forEach { path ->
            runCatching {
                val f = File(path)
                if (f.exists() && f.isDirectory && f.canRead()) {
                    val children = f.listFiles()?.filter { it.isDirectory && it.canRead() }.orEmpty()
                    if (children.isNotEmpty()) {
                        children.forEach { child -> detected.add("USB: ${child.name}") }
                    } else {
                        detected.add("Mount: ${f.name}")
                    }
                }
            }
        }
        return detected.toList()
    }

    private suspend fun scanSafTree(
        treeUri: Uri,
        outSongs: MutableList<SongEntity>,
        keepIds: MutableList<String>,
        seenKeys: MutableSet<String>
    ) {
        val rootDoc = DocumentFile.fromTreeUri(context, treeUri) ?: return
        val rootName = rootDoc.name ?: "USB Audio"
        val queue = ArrayDeque<Pair<DocumentFile, String>>()
        queue.add(rootDoc to "Local/$rootName")

        while (queue.isNotEmpty()) {
            val (dir, relFolder) = queue.removeFirst()
            val children = try {
                dir.listFiles()
            } catch (_: Throwable) {
                emptyArray()
            }
            for (child in children) {
                val childName = child.name ?: continue
                if (childName.startsWith(".")) continue
                if (child.isDirectory) {
                    queue.add(child to "$relFolder/$childName")
                } else if (AudioFormat.isSupported(childName)) {
                    val relativePath = "$relFolder/$childName"
                    val id = generateLocalId(relativePath)
                    if (!seenKeys.add(id)) continue
                    keepIds.add(id)

                    val existing = songDao.getSongById(id)
                    if (existing != null && existing.lastModified == child.lastModified() && existing.fileSize == child.length()) {
                        outSongs.add(existing)
                        continue
                    }

                    val entity = extractLocalSongEntity(
                        id = id,
                        contentUri = child.uri,
                        fileName = childName,
                        relativePath = relativePath,
                        fileSize = child.length(),
                        lastModified = child.lastModified(),
                        fallbackMime = child.type,
                        existing = existing
                    )
                    outSongs.add(entity)
                }
            }
        }
    }

    private suspend fun scanMediaStoreAudio(
        outSongs: MutableList<SongEntity>,
        keepIds: MutableList<String>,
        seenKeys: MutableSet<String>
    ) {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.IS_MUSIC
        )
        // Include both IS_MUSIC != 0 and general audio/* entries (headunit USB scanners sometimes leave IS_MUSIC = 0)
        val selection = "(${MediaStore.Audio.Media.IS_MUSIC} != 0 OR ${MediaStore.Audio.Media.MIME_TYPE} LIKE 'audio/%')"

        try {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                "${MediaStore.Audio.Media.TITLE} ASC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val modCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
                val trackCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
                val yearCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
                val isMusicCol = cursor.getColumnIndex(MediaStore.Audio.Media.IS_MUSIC)

                while (cursor.moveToNext()) {
                    val mediaId = cursor.getLong(idCol)
                    val displayName = cursor.getString(nameCol) ?: "track_$mediaId.mp3"
                    if (!AudioFormat.isSupported(displayName)) continue

                    val durationMs = cursor.getLong(durCol).coerceAtLeast(0L)
                    val isMusic = if (isMusicCol >= 0) cursor.getInt(isMusicCol) else 1
                    // Skip tiny <3s notification/UI sounds when not flagged as music
                    if (isMusic == 0 && durationMs in 1L..2999L) continue

                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        mediaId
                    )
                    val artist = cursor.getString(artistCol)?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: "Unknown Artist"
                    val album = cursor.getString(albumCol)?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: "Unknown Album"
                    val title = cursor.getString(titleCol)?.takeIf { it.isNotBlank() } ?: displayName.substringBeforeLast('.')
                    val mime = cursor.getString(mimeCol)
                        ?: AudioFormat.fromExtension(displayName.substringAfterLast('.', ""))?.mimeType
                        ?: "audio/mpeg"
                    val fileSize = cursor.getLong(sizeCol).coerceAtLeast(0L)
                    val lastMod = cursor.getLong(modCol) * 1000L
                    val trackNum = cursor.getInt(trackCol).takeIf { it > 0 }?.let { it % 1000 }
                    val year = cursor.getInt(yearCol).takeIf { it > 1900 }

                    val relativePath = "Local/Device/$artist/$album/$displayName"
                    val songId = generateLocalId("ms:$mediaId:$relativePath")
                    if (!seenKeys.add(songId)) continue
                    keepIds.add(songId)

                    val existing = songDao.getSongById(songId)
                    val artPath = existing?.artworkPath ?: artworkDiskCacheManager.getExistingArtworkPath(artist, album)

                    outSongs.add(
                        SongEntity(
                            id = songId,
                            title = title,
                            artist = artist,
                            album = album,
                            durationMs = durationMs,
                            fileUrl = contentUri.toString(),
                            relativePath = relativePath,
                            mimeType = mime,
                            trackNumber = trackNum,
                            year = year,
                            fileSize = fileSize,
                            lastModified = lastMod,
                            artworkPath = artPath,
                            isFavorite = existing?.isFavorite ?: false,
                            isDownloaded = true,
                            localFilePath = contentUri.toString(),
                            lyricsText = existing?.lyricsText,
                            sourceType = "LOCAL"
                        )
                    )
                }
            }
        } catch (_: Throwable) {
        }
    }

    /**
     * Fallback direct filesystem probe for Android 9 (API 28) / headunit USB OTG & MicroSD mounts
     * when MediaStore hasn't indexed removable USB volumes.
     */
    private suspend fun scanMountedExternalStorage(
        outSongs: MutableList<SongEntity>,
        keepIds: MutableList<String>,
        seenKeys: MutableSet<String>
    ) {
        val candidateRoots = buildList {
            File("/storage").listFiles()?.forEach { vol ->
                if (vol.isDirectory && vol.name !in setOf("self", "emulated") && vol.canRead()) {
                    add(vol to "USB/${vol.name}")
                }
            }
            listOf("/mnt/usb_storage", "/mnt/usb", "/mnt/media_rw").forEach { path ->
                val f = File(path)
                if (f.exists() && f.isDirectory && f.canRead()) {
                    add(f to "USB/${f.name}")
                }
            }
            @Suppress("DEPRECATION")
            val externalRoot = android.os.Environment.getExternalStorageDirectory()
            val wowRadioDir = File(externalRoot, "WoW Radio")
            if (wowRadioDir.exists() && wowRadioDir.isDirectory && wowRadioDir.canRead()) {
                add(wowRadioDir to "Local/WoW Radio")
            }
            @Suppress("DEPRECATION")
            val emulatedMusic = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MUSIC).absolutePath)
            if (emulatedMusic.exists() && emulatedMusic.isDirectory && emulatedMusic.canRead()) {
                add(emulatedMusic to "Local/Music")
            }
        }

        val queue = ArrayDeque<Triple<File, String, Int>>()
        candidateRoots.forEach { (dir, rel) -> queue.add(Triple(dir, rel, 0)) }
        val existingUriSet = outSongs.mapTo(HashSet()) { it.fileUrl }
        val existingSizeTitleSet = outSongs.mapTo(HashSet()) { "${it.fileSize}:${it.title.lowercase()}" }

        while (queue.isNotEmpty()) {
            val (dir, relFolder, depth) = queue.removeFirst()
            if (depth > 6) continue
            val children = try {
                dir.listFiles() ?: emptyArray()
            } catch (_: Throwable) {
                emptyArray()
            }
            for (child in children) {
                val name = child.name
                if (name.startsWith(".")) continue
                if (child.isDirectory) {
                    if (name == "Android" || name == "LOST.DIR") continue
                    queue.add(Triple(child, "$relFolder/$name", depth + 1))
                } else if (child.isFile && AudioFormat.isSupported(name) && child.canRead()) {
                    val fileUri = Uri.fromFile(child)
                    val fileUriStr = fileUri.toString()
                    val baseTitle = name.substringBeforeLast('.').lowercase()
                    if (existingUriSet.contains(fileUriStr)) continue
                    if (existingSizeTitleSet.contains("${child.length()}:$baseTitle")) continue

                    val relativePath = "$relFolder/$name"
                    val songId = generateLocalId("fs:${child.absolutePath}")
                    if (!seenKeys.add(songId)) continue
                    keepIds.add(songId)

                    val existing = songDao.getSongById(songId)
                    val entity = if (existing != null &&
                        existing.lastModified == child.lastModified() &&
                        existing.fileSize == child.length()
                    ) {
                        existing
                    } else {
                        extractLocalSongEntity(
                            id = songId,
                            contentUri = fileUri,
                            fileName = name,
                            relativePath = relativePath,
                            fileSize = child.length(),
                            lastModified = child.lastModified(),
                            fallbackMime = null,
                            existing = existing
                        )
                    }
                    outSongs.add(entity)
                    existingUriSet.add(fileUriStr)
                }
            }
        }
    }

    private fun extractLocalSongEntity(
        id: String,
        contentUri: Uri,
        fileName: String,
        relativePath: String,
        fileSize: Long,
        lastModified: Long,
        fallbackMime: String?,
        existing: SongEntity?
    ): SongEntity {
        val retriever = MediaMetadataRetriever()
        var title = fileName.substringBeforeLast('.')
        var artist = "Unknown Artist"
        var album = "Unknown Album"
        var genre: String? = null
        var trackNumber: Int? = null
        var year: Int? = null
        var durationMs = 0L
        var bitrateKbps: Int? = null
        var sampleRateHz: Int? = null
        var artPath: String? = existing?.artworkPath

        try {
            if (contentUri.scheme == "file" && contentUri.path != null) {
                retriever.setDataSource(contentUri.path)
            } else {
                retriever.setDataSource(context, contentUri)
            }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }?.let { title = it.trim() }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() }?.let { artist = it.trim() }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.takeIf { it.isNotBlank() }?.let { album = it.trim() }
            genre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)?.takeIf { it.isNotBlank() }
            trackNumber = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
                ?.substringBefore('/')?.trim()?.toIntOrNull()
            year = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)?.take(4)?.toIntOrNull()
            durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            bitrateKbps = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull()?.let { it / 1000 }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                sampleRateHz = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toIntOrNull()
            }
            if (artPath == null) {
                artPath = artworkDiskCacheManager.getExistingArtworkPath(artist, album)
                    ?: retriever.embeddedPicture?.let { bytes ->
                        artworkDiskCacheManager.saveArtwork(artist, album, bytes)
                    }
            }
        } catch (_: Throwable) {
        } finally {
            try {
                retriever.release()
            } catch (_: Throwable) {}
        }

        val format = AudioFormat.fromExtension(fileName.substringAfterLast('.', ""))
        val concreteFallback = fallbackMime?.substringBefore(';')?.trim()?.lowercase()
            ?.takeIf { it.startsWith("audio/") && it != "audio/*" }
        val mime = concreteFallback
            ?: format?.mimeType
            ?: "audio/mpeg"

        return SongEntity(
            id = id,
            title = title,
            artist = artist,
            album = album,
            durationMs = durationMs,
            fileUrl = contentUri.toString(),
            relativePath = relativePath,
            mimeType = mime,
            trackNumber = trackNumber,
            year = year,
            genre = genre,
            fileSize = fileSize,
            lastModified = lastModified,
            artworkPath = artPath,
            isFavorite = existing?.isFavorite ?: false,
            isDownloaded = true,
            localFilePath = contentUri.toString(),
            lyricsText = existing?.lyricsText,
            sourceType = "LOCAL",
            sampleRateHz = sampleRateHz,
            bitrateKbps = bitrateKbps
        )
    }

    private fun generateLocalId(key: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(("local:$key").toByteArray(Charsets.UTF_8))
        return "loc_" + bytes.joinToString("") { "%02x".format(it) }.take(28)
    }
}
