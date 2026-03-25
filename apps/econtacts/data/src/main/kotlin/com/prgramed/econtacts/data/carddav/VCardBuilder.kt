package com.prgramed.econtacts.data.carddav

import com.prgramed.econtacts.domain.model.Address
import com.prgramed.econtacts.domain.model.AddressType
import com.prgramed.econtacts.domain.model.Contact
import com.prgramed.econtacts.domain.model.EmailType
import com.prgramed.econtacts.domain.model.PhoneType
import java.util.UUID

object VCardBuilder {

    fun build(contact: Contact): String = buildString {
        append("BEGIN:VCARD\r\n")
        append("VERSION:3.0\r\n")
        append("PRODID:-//eContacts//GrapheneApps//EN\r\n")
        append("UID:${contact.uid ?: UUID.randomUUID().toString()}\r\n")
        append("FN:${escapeValue(contact.displayName)}\r\n")

        contact.phoneNumbers.forEach { phone ->
            val type = when (phone.type) {
                PhoneType.MOBILE -> "CELL"
                PhoneType.HOME -> "HOME"
                PhoneType.WORK -> "WORK"
                PhoneType.OTHER -> "OTHER"
            }
            append("TEL;TYPE=$type:${phone.number}\r\n")
        }

        contact.emails.forEach { email ->
            val type = when (email.type) {
                EmailType.HOME -> "HOME"
                EmailType.WORK -> "WORK"
                EmailType.OTHER -> "OTHER"
            }
            append("EMAIL;TYPE=$type:${email.address}\r\n")
        }

        val org = contact.organization
        if (!org.isNullOrBlank()) {
            append("ORG:${escapeValue(org)}\r\n")
        }

        val title = contact.title
        if (!title.isNullOrBlank()) {
            append("TITLE:${escapeValue(title)}\r\n")
        }

        val bday = contact.birthday
        if (!bday.isNullOrBlank()) {
            append("BDAY:$bday\r\n")
        }

        contact.addresses.forEach { addr ->
            val type = when (addr.type) {
                AddressType.HOME -> "HOME"
                AddressType.WORK -> "WORK"
                AddressType.OTHER -> "OTHER"
            }
            append("ADR;TYPE=$type:;;${escapeValue(addr.street)};${escapeValue(addr.city)};${escapeValue(addr.region)};${escapeValue(addr.postalCode)};${escapeValue(addr.country)}\r\n")
        }

        contact.websites.forEach { url ->
            append("URL:$url\r\n")
        }

        val note = contact.note
        if (!note.isNullOrBlank()) {
            append("NOTE:${escapeValue(note)}\r\n")
        }

        append("END:VCARD\r\n")
    }

    private fun escapeValue(value: String): String =
        value.replace("\\", "\\\\").replace(",", "\\,").replace("\n", "\\n")
}
