package com.prgramed.econtacts.data.carddav

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

data class DavResource(
    val href: String,
    val etag: String,
    val vcardData: String? = null,
)

object CardDavXmlParser {

    private fun createParser(xml: String): XmlPullParser {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = true
        val parser = factory.newPullParser()
        parser.setInput(xml.reader())
        return parser
    }

    fun parseMultistatus(xml: String): List<DavResource> {
        val resources = mutableListOf<DavResource>()
        val parser = createParser(xml)

        var href = ""
        var etag = ""
        var vcardData: String? = null
        var inResponse = false
        var currentTag = ""

        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    val localName = parser.name
                    when (localName) {
                        "response" -> {
                            inResponse = true
                            href = ""
                            etag = ""
                            vcardData = null
                        }
                        "href" -> currentTag = "href"
                        "getetag" -> currentTag = "getetag"
                        "address-data" -> currentTag = "address-data"
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inResponse) {
                        val text = parser.text?.trim() ?: ""
                        if (text.isNotEmpty()) {
                            when (currentTag) {
                                "href" -> href = text
                                "getetag" -> etag = text.removeSurrounding("\"")
                                "address-data" -> vcardData = text
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    val localName = parser.name
                    when (localName) {
                        "href", "getetag", "address-data" -> currentTag = ""
                        "response" -> {
                            if (inResponse && href.isNotBlank()) {
                                resources.add(DavResource(href, etag, vcardData))
                            }
                            inResponse = false
                        }
                    }
                }
            }
            parser.next()
        }

        return resources
    }

    fun parseHref(xml: String, tagName: String): String? {
        val parser = createParser(xml)

        var inTarget = false
        var inHref = false

        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    val localName = parser.name
                    if (localName == tagName) inTarget = true
                    if (inTarget && localName == "href") inHref = true
                }
                XmlPullParser.TEXT -> {
                    if (inHref) {
                        val text = parser.text?.trim()
                        if (!text.isNullOrBlank()) return text
                    }
                }
                XmlPullParser.END_TAG -> {
                    val localName = parser.name
                    if (localName == "href") inHref = false
                    if (localName == tagName) inTarget = false
                }
            }
            parser.next()
        }
        return null
    }
}
