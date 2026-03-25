package com.prgramed.econtacts.data.carddav

object CardDavXmlBuilder {

    fun propfindAddressBook(): String = """
        <?xml version="1.0" encoding="UTF-8"?>
        <d:propfind xmlns:d="DAV:" xmlns:card="urn:ietf:params:xml:ns:carddav">
            <d:prop>
                <d:getetag/>
                <d:getcontenttype/>
            </d:prop>
        </d:propfind>
    """.trimIndent()

    fun addressBookMultiget(hrefs: List<String>): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8"?>""")
        append("""<card:addressbook-multiget xmlns:d="DAV:" xmlns:card="urn:ietf:params:xml:ns:carddav">""")
        append("<d:prop><d:getetag/><card:address-data/></d:prop>")
        hrefs.forEach { href ->
            append("<d:href>$href</d:href>")
        }
        append("</card:addressbook-multiget>")
    }

    fun addressBookQuery(): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8"?>""")
        append("""<card:addressbook-query xmlns:d="DAV:" xmlns:card="urn:ietf:params:xml:ns:carddav">""")
        append("<d:prop>")
        append("<d:getetag/>")
        append("</d:prop>")
        append("</card:addressbook-query>")
    }

    fun propfindPrincipal(): String = """
        <?xml version="1.0" encoding="UTF-8"?>
        <d:propfind xmlns:d="DAV:">
            <d:prop>
                <d:current-user-principal/>
            </d:prop>
        </d:propfind>
    """.trimIndent()

    fun propfindAddressBookHome(): String = """
        <?xml version="1.0" encoding="UTF-8"?>
        <d:propfind xmlns:d="DAV:" xmlns:card="urn:ietf:params:xml:ns:carddav">
            <d:prop>
                <card:addressbook-home-set/>
            </d:prop>
        </d:propfind>
    """.trimIndent()
}
