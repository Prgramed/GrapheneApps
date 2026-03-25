package com.prgramed.econtacts.domain.util

import com.prgramed.econtacts.domain.model.Address
import com.prgramed.econtacts.domain.model.AddressType
import com.prgramed.econtacts.domain.model.Contact
import com.prgramed.econtacts.domain.model.Email
import com.prgramed.econtacts.domain.model.EmailType
import com.prgramed.econtacts.domain.model.PhoneNumber
import com.prgramed.econtacts.domain.model.PhoneType

object VCardParser {

    fun parse(vcfText: String): Contact {
        var displayName = ""
        val phones = mutableListOf<PhoneNumber>()
        val emails = mutableListOf<Email>()
        val addresses = mutableListOf<Address>()
        val websites = mutableListOf<String>()
        var org: String? = null
        var title: String? = null
        var note: String? = null
        var birthday: String? = null

        // Unfold continuation lines (lines starting with space/tab are continuations)
        val unfolded = vcfText.replace(Regex("\r?\n[ \t]"), "")

        for (rawLine in unfolded.lines()) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.equals("BEGIN:VCARD", ignoreCase = true) ||
                line.equals("END:VCARD", ignoreCase = true) ||
                line.startsWith("VERSION:", ignoreCase = true)
            ) continue

            val colonIdx = line.indexOf(':')
            if (colonIdx < 0) continue
            val property = line.substring(0, colonIdx).uppercase()
            val value = line.substring(colonIdx + 1).trim()
            if (value.isBlank()) continue

            when {
                property.startsWith("FN") -> displayName = value
                property.startsWith("TEL") -> {
                    val type = parsePhoneType(property)
                    phones.add(PhoneNumber(number = value, type = type))
                }
                property.startsWith("EMAIL") -> {
                    val type = parseEmailType(property)
                    emails.add(Email(address = value, type = type))
                }
                property.startsWith("ORG") -> org = value.replace(";", " ").trim()
                property.startsWith("TITLE") -> title = value
                property.startsWith("NOTE") -> note = value
                property.startsWith("BDAY") -> birthday = value
                property.startsWith("URL") -> websites.add(value)
                property.startsWith("ADR") -> {
                    // ADR: PO Box;Extended;Street;City;Region;Postal;Country
                    val parts = value.split(";")
                    addresses.add(
                        Address(
                            street = parts.getOrElse(2) { "" },
                            city = parts.getOrElse(3) { "" },
                            region = parts.getOrElse(4) { "" },
                            postalCode = parts.getOrElse(5) { "" },
                            country = parts.getOrElse(6) { "" },
                            type = parseAddressType(property),
                        ),
                    )
                }
            }
        }

        // Fallback: if FN is empty, try N field
        if (displayName.isBlank()) {
            for (rawLine in unfolded.lines()) {
                val line = rawLine.trim()
                val colonIdx = line.indexOf(':')
                if (colonIdx < 0) continue
                val prop = line.substring(0, colonIdx).uppercase()
                val v = line.substring(colonIdx + 1).trim()
                if (prop.startsWith("N") && !prop.startsWith("NOTE")) {
                    // N: Last;First;Middle;Prefix;Suffix
                    val parts = v.split(";")
                    val first = parts.getOrElse(1) { "" }
                    val last = parts.getOrElse(0) { "" }
                    displayName = "$first $last".trim()
                    break
                }
            }
        }

        return Contact(
            displayName = displayName,
            phoneNumbers = phones,
            emails = emails,
            organization = org,
            title = title,
            note = note,
            birthday = birthday,
            addresses = addresses,
            websites = websites,
        )
    }

    private fun parsePhoneType(property: String): PhoneType = when {
        property.contains("WORK", ignoreCase = true) -> PhoneType.WORK
        property.contains("HOME", ignoreCase = true) -> PhoneType.HOME
        property.contains("CELL", ignoreCase = true) -> PhoneType.MOBILE
        else -> PhoneType.MOBILE
    }

    private fun parseEmailType(property: String): EmailType = when {
        property.contains("WORK", ignoreCase = true) -> EmailType.WORK
        property.contains("HOME", ignoreCase = true) -> EmailType.HOME
        else -> EmailType.HOME
    }

    private fun parseAddressType(property: String): AddressType = when {
        property.contains("WORK", ignoreCase = true) -> AddressType.WORK
        property.contains("HOME", ignoreCase = true) -> AddressType.HOME
        else -> AddressType.HOME
    }
}
