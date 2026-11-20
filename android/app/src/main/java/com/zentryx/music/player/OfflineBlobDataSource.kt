package com.zentryx.music.player

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import java.io.ByteArrayInputStream
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.util.Locale

/**
 * Resolves an IndexedDB offline track ID (`idb://offline_tracks/<trackId>`) to either
 * a local cached blob [File] or raw decoded bytes.
 */
fun interface IndexedDbOfflineBlobResolver {
    fun resolve(trackId: String): OfflineBlobSource?
}

sealed class OfflineBlobSource {
    data class FileBacked(val file: File, val mimeType: String? = null) : OfflineBlobSource()
    data class MemoryBacked(val bytes: ByteArray, val mimeType: String? = null) : OfflineBlobSource()
}

/**
 * Default resolver that locates IndexedDB-exported / offline-cached audio blobs across:
 * 1. App-private IndexedDB blob mirror (`filesDir/zentryx_idb_blobs/<trackId>.{blob,song,mp3,flac,...}`)
 * 2. Offline downloads folder (`filesDir/offline_music/`) matching `<trackId>` prefix
 * 3. Android WebView Chromium IndexedDB blob directories (`app_webview/Default/IndexedDB/<origin>.indexeddb.blob/`)
 */
class AndroidIndexedDbBlobResolver(
    private val context: Context
) : IndexedDbOfflineBlobResolver {
    override fun resolve(trackId: String): OfflineBlobSource? {
        val safeId = trackId.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val idPrefix = trackId.replace(Regex("[^a-zA-Z0-9_-]"), "").take(16)

        val searchDirs = buildList {
            add(File(context.filesDir, "zentryx_idb_blobs"))
            add(File(context.filesDir, "offline_music"))
            context.getExternalFilesDir(null)?.let {
                add(File(it, "zentryx_idb_blobs"))
                add(File(it, "offline_music"))
            }
            // Chromium WebView IndexedDB blob directory (if PWA/WebView previously cached blobs on device)
            val webViewIdb = File(context.dataDir, "app_webview/Default/IndexedDB")
            if (webViewIdb.exists() && webViewIdb.isDirectory) {
                webViewIdb.listFiles()?.forEach { originDir ->
                    if (originDir.isDirectory && originDir.name.endsWith(".indexeddb.blob")) {
                        add(originDir)
                        originDir.listFiles()?.filter { it.isDirectory }?.forEach { sub -> add(sub) }
                    }
                }
            }
        }

        for (dir in searchDirs) {
            if (!dir.exists() || !dir.isDirectory) continue
            val directCandidates = listOf(
                File(dir, safeId),
                File(dir, "$safeId.blob"),
                File(dir, "$safeId.song"),
                File(dir, "$safeId.mp3"),
                File(dir, "$safeId.flac"),
                File(dir, "$safeId.m4a"),
                File(dir, "$safeId.ogg"),
                File(dir, "$safeId.opus"),
                File(dir, "$safeId.wav")
            )
            directCandidates.firstOrNull { it.exists() && it.isFile && it.length() > 0L }?.let { file ->
                return OfflineBlobSource.FileBacked(file)
            }

            if (idPrefix.isNotBlank()) {
                dir.listFiles()?.firstOrNull { f ->
                    f.isFile && f.length() > 0L && (
                        f.name.startsWith("${idPrefix}_", ignoreCase = true) ||
                            f.nameWithoutExtension.equals(safeId, ignoreCase = true)
                        )
                }?.let { matched ->
                    return OfflineBlobSource.FileBacked(matched)
                }
            }
        }
        return null
    }
}

/**
 * Media3 [DataSource] that transparently decodes and streams:
 * - `.song` / `.audio` local files (raw audio bytes, binary `ZSONG\u0001` containers, or JSON IndexedDB blob envelopes),
 * - `content://` SAF URIs pointing to `.song` files,
 * - `idb://offline_tracks/<trackId>` and `zentryx-idb://...` IndexedDB cached blobs,
 * - `data:audio/...;base64,...` inline blob URIs.
 *
 * Supports byte-range seeking (`DataSpec.position` & `DataSpec.length`) and exposes the sniffed
 * `Content-Type` header via [getResponseHeaders] so ExoPlayer's extractors decode reliably.
 */
@OptIn(UnstableApi::class)
class OfflineBlobDataSource(
    private val context: Context?,
    private val blobResolver: IndexedDbOfflineBlobResolver
) : BaseDataSource(/* isNetwork = */ false) {

    class Factory(
        private val context: Context,
        private val blobResolver: IndexedDbOfflineBlobResolver = AndroidIndexedDbBlobResolver(context)
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource = OfflineBlobDataSource(context, blobResolver)
    }

    private var currentUri: Uri? = null
    private var randomAccessFile: RandomAccessFile? = null
    private var inputStream: InputStream? = null
    private var bytesRemaining: Long = 0L
    private var opened = false
    private var detectedContentType: String? = null

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        currentUri = dataSpec.uri
        val lengthToRead = openUriString(
            uriStr = dataSpec.uri.toString(),
            position = dataSpec.position,
            length = dataSpec.length,
            androidUri = dataSpec.uri
        )
        opened = true
        transferStarted(dataSpec)
        return lengthToRead
    }

    internal fun openUriString(
        uriStr: String,
        position: Long = 0L,
        length: Long = C.LENGTH_UNSET.toLong(),
        androidUri: Uri? = null
    ): Long {
        detectedContentType = null
        return when {
            uriStr.startsWith("${SongMediaItemConverter.SCHEME_DATA}:", ignoreCase = true) -> {
                val (mime, bytes) = SongMediaItemConverter.decodeDataUri(uriStr)
                    ?: throw IOException("Invalid base64 data URI for offline blob")
                detectedContentType = mime
                openByteArray(bytes, position, length)
            }

            uriStr.startsWith("${SongMediaItemConverter.SCHEME_IDB}://", ignoreCase = true) ||
                uriStr.startsWith("${SongMediaItemConverter.SCHEME_ZENTRYX_IDB}://", ignoreCase = true) -> {
                val trackId = SongMediaItemConverter.parseIndexedDbTrackId(uriStr)
                    ?: throw IOException("Malformed IndexedDB offline blob URI: $uriStr")
                val resolved = blobResolver.resolve(trackId)
                    ?: throw IOException("Offline IndexedDB blob not found for trackId=$trackId")
                when (resolved) {
                    is OfflineBlobSource.MemoryBacked -> {
                        val parsed = SongMediaItemConverter.parseSongBytes(resolved.bytes)
                        detectedContentType = parsed.detectedMimeType ?: resolved.mimeType
                        when (parsed.formatKind) {
                            SongContainerKind.INDEXED_DB_JSON_BLOB -> {
                                val blob = parsed.decodedBlobBytes
                                    ?: throw IOException("IndexedDB JSON blob missing audio payload")
                                openByteArray(blob, position, length)
                            }
                            SongContainerKind.ZSONG_BINARY_CONTAINER -> {
                                val offset = parsed.payloadByteOffset.toInt()
                                val sliced = resolved.bytes.copyOfRange(offset, resolved.bytes.size)
                                openByteArray(sliced, position, length)
                            }
                            SongContainerKind.RAW_AUDIO -> openByteArray(resolved.bytes, position, length)
                        }
                    }
                    is OfflineBlobSource.FileBacked -> {
                        openSongOrBlobFile(resolved.file, resolved.mimeType, position, length)
                    }
                }
            }

            uriStr.startsWith("content://", ignoreCase = true) -> {
                val resolvedUri = androidUri ?: Uri.parse(uriStr)
                openContentUri(resolvedUri, position, length)
            }

            else -> {
                val path = androidUri?.path?.takeIf { it.isNotBlank() }
                    ?: uriStr.removePrefix("file://")
                val file = File(path)
                if (!file.exists() || !file.isFile) {
                    throw IOException("Offline .song file does not exist: ${file.absolutePath}")
                }
                openSongOrBlobFile(file, null, position, length)
            }
        }
    }

    private fun openSongOrBlobFile(file: File, fallbackMime: String?, position: Long, length: Long): Long {
        val parsed = SongMediaItemConverter.inspectSongFile(file)
        detectedContentType = parsed.detectedMimeType ?: fallbackMime

        if (parsed.formatKind == SongContainerKind.INDEXED_DB_JSON_BLOB) {
            val bytes = parsed.decodedBlobBytes
                ?: throw IOException("IndexedDB .song JSON file has empty audio payload: ${file.name}")
            return openByteArray(bytes, position, length)
        }

        val payloadOffset = parsed.payloadByteOffset
        val payloadTotalSize = (file.length() - payloadOffset).coerceAtLeast(0L)
        if (position > payloadTotalSize) {
            throw EOFException("Requested position $position beyond .song payload size $payloadTotalSize")
        }

        val raf = RandomAccessFile(file, "r")
        raf.seek(payloadOffset + position)
        randomAccessFile = raf

        val available = payloadTotalSize - position
        bytesRemaining = if (length != C.LENGTH_UNSET.toLong()) {
            minOf(available, length)
        } else {
            available
        }
        return bytesRemaining
    }

    private fun openContentUri(uri: Uri, position: Long, length: Long): Long {
        val ctx = context ?: throw IOException("Context required to open content:// URI: $uri")
        val rawStream = ctx.contentResolver.openInputStream(uri)
            ?: throw IOException("Unable to open SAF content URI: $uri")

        // Peek first bytes to detect ZSONG container, IndexedDB JSON blob, or raw audio
        val probe = ByteArray(4096)
        val probeRead = rawStream.read(probe)
        if (probeRead <= 0) {
            rawStream.close()
            throw IOException("Empty offline SAF .song stream: $uri")
        }

        val firstNonWs = probe.take(probeRead).firstOrNull { b ->
            val c = b.toInt().toChar()
            c != ' ' && c != '\n' && c != '\r' && c != '\t'
        }

        // If JSON-wrapped IndexedDB blob or ZSONG binary container, decode cleanly
        val isZsong = probeRead >= 10 &&
            SongMediaItemConverter.ZSONG_MAGIC_BYTES.indices.all { probe[it] == SongMediaItemConverter.ZSONG_MAGIC_BYTES[it] }
        if (firstNonWs == '{'.code.toByte() || isZsong) {
            val allBytes = rawStream.use { rest ->
                val out = java.io.ByteArrayOutputStream(maxOf(probeRead, 16 * 1024))
                out.write(probe, 0, probeRead)
                rest.copyTo(out)
                out.toByteArray()
            }
            val parsed = SongMediaItemConverter.parseSongBytes(allBytes)
            detectedContentType = parsed.detectedMimeType
            val payload = when (parsed.formatKind) {
                SongContainerKind.INDEXED_DB_JSON_BLOB -> parsed.decodedBlobBytes
                    ?: throw IOException("Empty JSON blob payload in SAF .song file")
                SongContainerKind.ZSONG_BINARY_CONTAINER -> {
                    val off = parsed.payloadByteOffset.toInt()
                    allBytes.copyOfRange(off, allBytes.size)
                }
                SongContainerKind.RAW_AUDIO -> allBytes
            }
            return openByteArray(payload, position, length)
        }

        // Raw audio bytes in SAF `.song`: sniff MIME and re-open cleanly at requested offset
        detectedContentType = SongMediaItemConverter.sniffAudioMimeType(probe, probeRead)
        rawStream.close()

        val freshStream = ctx.contentResolver.openInputStream(uri)
            ?: throw IOException("Unable to re-open SAF content URI: $uri")
        if (position > 0L) {
            var remainingSkip = position
            while (remainingSkip > 0L) {
                val skipped = freshStream.skip(remainingSkip)
                if (skipped <= 0L) {
                    if (freshStream.read() == -1) {
                        freshStream.close()
                        throw EOFException("SAF .song stream shorter than requested position $position")
                    }
                    remainingSkip -= 1L
                } else {
                    remainingSkip -= skipped
                }
            }
        }
        inputStream = freshStream

        val totalSize = runCatching {
            ctx.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
        }.getOrNull()?.takeIf { it >= 0L }

        bytesRemaining = when {
            length != C.LENGTH_UNSET.toLong() -> length
            totalSize != null -> (totalSize - position).coerceAtLeast(0L)
            else -> C.LENGTH_UNSET.toLong()
        }
        return bytesRemaining
    }

    private fun openByteArray(bytes: ByteArray, position: Long, length: Long): Long {
        if (position > bytes.size.toLong()) {
            throw EOFException("Requested position $position beyond blob size ${bytes.size}")
        }
        val offset = position.toInt()
        val available = bytes.size - offset
        val toRead = if (length != C.LENGTH_UNSET.toLong()) {
            minOf(available.toLong(), length).toInt()
        } else {
            available
        }
        inputStream = ByteArrayInputStream(bytes, offset, toRead)
        bytesRemaining = toRead.toLong()
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT

        val maxToRead = if (bytesRemaining == C.LENGTH_UNSET.toLong()) {
            length
        } else {
            minOf(bytesRemaining, length.toLong()).toInt()
        }

        val bytesRead = try {
            val raf = randomAccessFile
            val stream = inputStream
            when {
                raf != null -> raf.read(buffer, offset, maxToRead)
                stream != null -> stream.read(buffer, offset, maxToRead)
                else -> C.RESULT_END_OF_INPUT
            }
        } catch (e: IOException) {
            throw IOException("Failed reading offline .song / IndexedDB blob", e)
        }

        if (bytesRead == -1) {
            return C.RESULT_END_OF_INPUT
        }

        if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
            bytesRemaining -= bytesRead.toLong()
        }
        bytesTransferred(bytesRead)
        return bytesRead
    }

    override fun getUri(): Uri? = currentUri

    override fun getResponseHeaders(): Map<String, List<String>> {
        val mime = detectedContentType ?: return emptyMap()
        return mapOf("Content-Type" to listOf(mime))
    }

    override fun close() {
        currentUri = null
        detectedContentType = null
        try {
            randomAccessFile?.close()
            inputStream?.close()
        } finally {
            randomAccessFile = null
            inputStream = null
            if (opened) {
                opened = false
                transferEnded()
            }
        }
    }
}
