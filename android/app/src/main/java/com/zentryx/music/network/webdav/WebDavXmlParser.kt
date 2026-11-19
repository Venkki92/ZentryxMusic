package com.zentryx.music.network.webdav

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/**
 * High-performance, streaming XML parser for WebDAV multistatus responses.
 * Uses Android's built-in XmlPullParser with zero DOM tree allocation,
 * ensuring strict compliance with the < 150MB RAM budget on AutoChips AC8259P headunits.
 */
@Singleton
class WebDavXmlParser internal constructor(
    private val urlHelper: WebDavUrlHelper,
    private val parserFactory: () -> XmlPullParser
) {
    @Inject
    constructor(urlHelper: WebDavUrlHelper) : this(
        urlHelper = urlHelper,
        parserFactory = {
            Xml.newPullParser().apply {
                setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            }
        }
    )

    private val httpDateFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("GMT")
    }

    /**
     * Parses a WebDAV multistatus XML stream into a list of WebDavItem models.
     * Optionally excludes the queried folder entry (returned as the first item in Depth: 1 requests).
     */
    fun parseMultistatus(
        inputStream: InputStream,
        requestedPath: String? = null
    ): List<WebDavItem> {
        val items = mutableListOf<WebDavItem>()
        val parser = parserFactory()
        parser.setInput(inputStream, "UTF-8")

        var eventType = parser.eventType
        var currentHref = ""
        var isCollection = false
        var contentLength: Long = 0L
        var contentType: String? = null
        var eTag: String? = null
        var lastModified: Long = 0L
        var permissions: String? = null

        // Track XML hierarchy
        var inResponse = false
        var inProp = false
        var inResourceType = false

        val normalizedRequestedPath = requestedPath
            ?.let { urlHelper.decodePath(extractPathOnly(it)).trimEnd('/') }

        while (eventType != XmlPullParser.END_DOCUMENT) {
            val tagName = parser.name?.lowercase()?.substringAfterLast(':') ?: ""

            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (tagName) {
                        "response" -> {
                            inResponse = true
                            currentHref = ""
                            isCollection = false
                            contentLength = 0L
                            contentType = null
                            eTag = null
                            lastModified = 0L
                            permissions = null
                        }
                        "href" -> {
                            if (inResponse) {
                                currentHref = safeNextText(parser)
                            }
                        }
                        "prop" -> {
                            inProp = true
                        }
                        "resourcetype" -> {
                            inResourceType = true
                        }
                        "collection" -> {
                            if (inResourceType) {
                                isCollection = true
                            }
                        }
                        "getcontentlength" -> {
                            if (inProp) {
                                val text = safeNextText(parser)
                                text.toLongOrNull()?.takeIf { it > 0L }?.let { contentLength = it }
                            }
                        }
                        "getcontenttype" -> {
                            if (inProp) {
                                val text = safeNextText(parser)
                                if (text.isNotEmpty()) contentType = text
                            }
                        }
                        "getetag" -> {
                            if (inProp) {
                                val text = safeNextText(parser).removeSurrounding("\"")
                                if (text.isNotEmpty()) eTag = text
                            }
                        }
                        "getlastmodified" -> {
                            if (inProp) {
                                val text = safeNextText(parser)
                                val parsed = parseHttpDate(text)
                                if (parsed > 0L) lastModified = parsed
                            }
                        }
                        "permissions" -> {
                            if (inProp) {
                                val text = safeNextText(parser)
                                if (text.isNotEmpty()) permissions = text
                            }
                        }
                    }
                }

                XmlPullParser.END_TAG -> {
                    when (tagName) {
                        "response" -> {
                            inResponse = false
                            if (currentHref.isNotEmpty()) {
                                val decodedHrefPath = urlHelper.decodePath(extractPathOnly(currentHref)).trimEnd('/')
                                val isSelfFolder = normalizedRequestedPath != null &&
                                    decodedHrefPath.equals(normalizedRequestedPath, ignoreCase = true)

                                if (!isSelfFolder) {
                                    val displayName = urlHelper.extractNameFromHref(currentHref)
                                    if (displayName.isNotEmpty()) {
                                        items.add(
                                            WebDavItem(
                                                href = currentHref,
                                                name = displayName,
                                                isDirectory = isCollection,
                                                contentLength = contentLength,
                                                contentType = contentType,
                                                eTag = eTag,
                                                lastModified = lastModified,
                                                permissions = permissions
                                            )
                                        )
                                    }
                                }
                            }
                        }
                        "prop" -> {
                            inProp = false
                        }
                        "resourcetype" -> {
                            inResourceType = false
                        }
                    }
                }
            }
            eventType = parser.next()
        }

        return items
    }

    private fun extractPathOnly(hrefOrUrl: String): String {
        return try {
            if (hrefOrUrl.startsWith("http://") || hrefOrUrl.startsWith("https://")) {
                java.net.URI(hrefOrUrl).rawPath ?: hrefOrUrl
            } else {
                hrefOrUrl
            }
        } catch (_: Exception) {
            hrefOrUrl
        }
    }

    private fun safeNextText(parser: XmlPullParser): String {
        return try {
            parser.nextText().trim()
        } catch (_: Exception) {
            ""
        }
    }

    private fun parseHttpDate(dateString: String): Long {
        if (dateString.isBlank()) return 0L
        return try {
            synchronized(httpDateFormat) {
                httpDateFormat.parse(dateString)?.time ?: 0L
            }
        } catch (e: Exception) {
            0L
        }
    }
}
