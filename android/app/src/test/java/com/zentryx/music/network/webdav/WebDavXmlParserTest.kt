package com.zentryx.music.network.webdav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.Reader
import javax.xml.parsers.DocumentBuilderFactory

class WebDavXmlParserTest {

    private val urlHelper = WebDavUrlHelper()
    private val parser = WebDavXmlParser(urlHelper) { DomBackedPullParser() }

    @Test
    fun `parseMultistatus excludes self folder even with percent-encoded email username`() {
        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <d:multistatus xmlns:d="DAV:">
              <d:response>
                <d:href>/remote.php/dav/files/driver@example.com/Music/</d:href>
                <d:propstat>
                  <d:prop>
                    <d:resourcetype><d:collection/></d:resourcetype>
                    <d:getetag>"folder-etag"</d:getetag>
                  </d:prop>
                  <d:status>HTTP/1.1 200 OK</d:status>
                </d:propstat>
              </d:response>
              <d:response>
                <d:href>/remote.php/dav/files/driver@example.com/Music/Rock%20Classics/</d:href>
                <d:propstat>
                  <d:prop>
                    <d:resourcetype><d:collection/></d:resourcetype>
                  </d:prop>
                  <d:status>HTTP/1.1 200 OK</d:status>
                </d:propstat>
              </d:response>
              <d:response>
                <d:href>/remote.php/dav/files/driver@example.com/Music/Track%2001.flac</d:href>
                <d:propstat>
                  <d:prop>
                    <d:resourcetype/>
                    <d:getcontentlength>32456789</d:getcontentlength>
                    <d:getcontenttype>audio/flac</d:getcontenttype>
                    <d:getetag>"track-etag-1"</d:getetag>
                    <d:getlastmodified>Thu, 15 Feb 2024 12:00:00 GMT</d:getlastmodified>
                  </d:prop>
                  <d:status>HTTP/1.1 200 OK</d:status>
                </d:propstat>
                <d:propstat>
                  <d:prop>
                    <d:getcontentlength/>
                    <d:getcontenttype/>
                    <d:getetag/>
                  </d:prop>
                  <d:status>HTTP/1.1 404 Not Found</d:status>
                </d:propstat>
              </d:response>
            </d:multistatus>
        """.trimIndent()

        val items = parser.parseMultistatus(
            ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)),
            requestedPath = "/remote.php/dav/files/driver%40example.com/Music/"
        )

        assertEquals(2, items.size)

        val folder = items[0]
        assertTrue(folder.isDirectory)
        assertEquals("Rock Classics", folder.name)

        val track = items[1]
        assertFalse(track.isDirectory)
        assertEquals("Track 01.flac", track.name)
        assertEquals(32456789L, track.contentLength)
        assertEquals("audio/flac", track.contentType)
        assertEquals("track-etag-1", track.eTag)
        assertTrue(track.lastModified > 0L)
    }

    @Test
    fun `parseMultistatus extracts Nextcloud oc permissions for folders and files`() {
        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <d:multistatus xmlns:d="DAV:" xmlns:oc="http://owncloud.org/ns">
              <d:response>
                <d:href>/remote.php/dav/files/driver/Music/</d:href>
                <d:propstat>
                  <d:prop>
                    <d:resourcetype><d:collection/></d:resourcetype>
                    <oc:permissions>RGDNVCK</oc:permissions>
                  </d:prop>
                  <d:status>HTTP/1.1 200 OK</d:status>
                </d:propstat>
              </d:response>
              <d:response>
                <d:href>/remote.php/dav/files/driver/Music/Shared%20Mix/</d:href>
                <d:propstat>
                  <d:prop>
                    <d:resourcetype><d:collection/></d:resourcetype>
                    <oc:permissions>RG</oc:permissions>
                  </d:prop>
                  <d:status>HTTP/1.1 200 OK</d:status>
                </d:propstat>
              </d:response>
              <d:response>
                <d:href>/remote.php/dav/files/driver/Music/Song.mp3</d:href>
                <d:propstat>
                  <d:prop>
                    <d:resourcetype/>
                    <d:getcontentlength>4194304</d:getcontentlength>
                    <d:getcontenttype>audio/mpeg</d:getcontenttype>
                    <oc:permissions>RGDNVW</oc:permissions>
                  </d:prop>
                  <d:status>HTTP/1.1 200 OK</d:status>
                </d:propstat>
              </d:response>
            </d:multistatus>
        """.trimIndent()

        val items = parser.parseMultistatus(
            ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)),
            requestedPath = "/remote.php/dav/files/driver/Music/"
        )

        assertEquals(2, items.size)
        assertEquals("RG", items[0].permissions)
        assertEquals("RGDNVW", items[1].permissions)
    }

    /**
     * Minimal JVM XmlPullParser adapter backed by standard javax.xml.parsers.DocumentBuilderFactory
     * (available in both JDK and android.jar) so WebDavXmlParser's production loop runs directly in JUnit.
     */
    private class DomBackedPullParser : XmlPullParser {
        private data class Ev(val type: Int, val name: String? = null, val text: String = "")

        private val events = mutableListOf<Ev>()
        private var index = 0

        override fun setInput(inputStream: InputStream?, inputEncoding: String?) {
            val dbf = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = false }
            val doc = dbf.newDocumentBuilder().parse(inputStream)
            events.clear()
            events.add(Ev(XmlPullParser.START_DOCUMENT))
            appendElement(doc.documentElement)
            events.add(Ev(XmlPullParser.END_DOCUMENT))
            index = 0
        }

        private fun appendElement(el: Element) {
            events.add(Ev(XmlPullParser.START_TAG, el.tagName))
            val children = el.childNodes
            for (i in 0 until children.length) {
                val child = children.item(i)
                when (child.nodeType) {
                    Node.ELEMENT_NODE -> appendElement(child as Element)
                    Node.TEXT_NODE, Node.CDATA_SECTION_NODE -> {
                        val txt = child.nodeValue.orEmpty()
                        if (txt.isNotEmpty()) {
                            events.add(Ev(XmlPullParser.TEXT, null, txt))
                        }
                    }
                }
            }
            events.add(Ev(XmlPullParser.END_TAG, el.tagName))
        }

        override fun next(): Int {
            if (index < events.size - 1) index++
            return events[index].type
        }

        override fun nextToken(): Int = next()

        override fun nextText(): String {
            val sb = StringBuilder()
            while (index < events.size - 1) {
                val nextEv = events[index + 1]
                if (nextEv.type == XmlPullParser.TEXT) {
                    index++
                    sb.append(nextEv.text)
                } else if (nextEv.type == XmlPullParser.END_TAG) {
                    index++
                    break
                } else {
                    break
                }
            }
            return sb.toString()
        }

        override fun getEventType(): Int = events.getOrNull(index)?.type ?: XmlPullParser.END_DOCUMENT
        override fun getName(): String? = events.getOrNull(index)?.name
        override fun getText(): String = events.getOrNull(index)?.text ?: ""
        override fun setFeature(name: String?, state: Boolean) {}
        override fun getFeature(name: String?): Boolean = false
        override fun setProperty(name: String?, value: Any?) {}
        override fun getProperty(name: String?): Any? = null
        override fun setInput(inReader: Reader?) { throw UnsupportedOperationException() }
        override fun getInputEncoding(): String? = "UTF-8"
        override fun defineEntityReplacementText(entityName: String?, replacementText: String?) {}
        override fun getNamespaceCount(depth: Int): Int = 0
        override fun getNamespacePrefix(pos: Int): String? = null
        override fun getNamespaceUri(pos: Int): String? = null
        override fun getNamespace(prefix: String?): String? = null
        override fun getDepth(): Int = 0
        override fun getPositionDescription(): String = ""
        override fun getLineNumber(): Int = 0
        override fun getColumnNumber(): Int = 0
        override fun isWhitespace(): Boolean = false
        override fun getTextCharacters(holderForStartAndLength: IntArray?): CharArray = CharArray(0)
        override fun getNamespace(): String? = null
        override fun getPrefix(): String? = null
        override fun isEmptyElementTag(): Boolean = false
        override fun getAttributeCount(): Int = 0
        override fun getAttributeNamespace(index: Int): String = ""
        override fun getAttributeName(index: Int): String = ""
        override fun getAttributePrefix(index: Int): String? = null
        override fun getAttributeType(index: Int): String = "CDATA"
        override fun isAttributeDefault(index: Int): Boolean = false
        override fun getAttributeValue(index: Int): String? = null
        override fun getAttributeValue(namespace: String?, name: String?): String? = null
        override fun require(type: Int, namespace: String?, name: String?) {}
        override fun nextTag(): Int = next()
    }
}
