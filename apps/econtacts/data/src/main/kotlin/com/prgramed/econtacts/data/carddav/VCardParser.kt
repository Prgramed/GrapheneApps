package com.prgramed.econtacts.data.carddav

import com.prgramed.econtacts.domain.model.Address
import com.prgramed.econtacts.domain.model.AddressType
import com.prgramed.econtacts.domain.model.Contact
import com.prgramed.econtacts.domain.model.Email
import com.prgramed.econtacts.domain.model.EmailType
import com.prgramed.econtacts.domain.model.PhoneNumber
import com.prgramed.econtacts.domain.model.PhoneType

object VCardParser {

    fun parse(vcardData: String): Contact {
        val unfolded = unfoldLines(vcardData)
        val lines = unfolded.lines()

        var displayName = ""
        var uid: String? = null
        var note: String? = null
        var organization: String? = null
        var title: String? = null
        var birthday: String? = null
        val phones = mutableListOf<PhoneNumber>()
        val emails = mutableListOf<Email>()
        val addresses = mutableListOf<Address>()
        val websites = mutableListOf<String>()

        for (line in lines) {
            when {
                line.startsWith("FN", ignoreCase = true) -> {
                    displayName = extractValue(line)
                }
                line.startsWith("UID", ignoreCase = true) -> {
                    uid = extractValue(line)
                }
                line.startsWith("TEL", ignoreCase = true) -> {
                    val number = extractValue(line)
                    val type = when {
                        containsParam(line, "CELL") || containsParam(line, "MOBILE") -> PhoneType.MOBILE
                        containsParam(line, "HOME") -> PhoneType.HOME
                        containsParam(line, "WORK") -> PhoneType.WORK
                        else -> PhoneType.OTHER
                    }
                    if (number.isNotBlank()) phones.add(PhoneNumber(number, type))
                }
                line.startsWith("EMAIL", ignoreCase = true) -> {
                    val address = extractValue(line)
                    val type = when {
                        containsParam(line, "HOME") -> EmailType.HOME
                        containsParam(line, "WORK") -> EmailType.WORK
                        else -> EmailType.OTHER
                    }
                    if (address.isNotBlank()) emails.add(Email(address, type))
                }
                line.startsWith("NOTE", ignoreCase = true) -> {
                    note = extractValue(line).replace("\\n", "\n").replace("\\,", ",")
                }
                line.startsWith("ORG", ignoreCase = true) -> {
                    val value = extractValue(line)
                    organization = value.split(";").firstOrNull()?.trim()?.takeIf { it.isNotBlank() }
                }
                line.startsWith("TITLE", ignoreCase = true) -> {
                    title = extractValue(line).takeIf { it.isNotBlank() }
                }
                line.startsWith("BDAY", ignoreCase = true) -> {
                    birthday = extractValue(line).takeIf { it.isNotBlank() }
                }
                line.startsWith("ADR", ignoreCase = true) -> {
                    val value = extractValue(line)
                    val parts = value.split(";")
                    // ADR format: PO;ext;street;city;region;postalCode;country
                    val street = parts.getOrNull(2)?.trim() ?: ""
                    val city = parts.getOrNull(3)?.trim() ?: ""
                    val region = parts.getOrNull(4)?.trim() ?: ""
                    val postalCode = parts.getOrNull(5)?.trim() ?: ""
                    val country = parts.getOrNull(6)?.trim() ?: ""
                    val type = when {
                        containsParam(line, "HOME") -> AddressType.HOME
                        containsParam(line, "WORK") -> AddressType.WORK
                        else -> AddressType.OTHER
                    }
                    if (street.isNotBlank() || city.isNotBlank() || country.isNotBlank()) {
                        addresses.add(Address(street, city, region, postalCode, country, type))
                    }
                }
                line.startsWith("URL", ignoreCase = true) -> {
                    val url = extractValue(line)
                    if (url.isNotBlank()) websites.add(url)
                }
            }
        }

        return Contact(
            displayName = displayName,
            phoneNumbers = phones,
            emails = emails,
            note = note,
            uid = uid,
            organization = organization,
            title = title,
            birthday = birthday,
            addresses = addresses,
            websites = websites,
        )
    }

    private fun unfoldLines(data: String): String =
        data.replace("\r\n ", "").replace("\r\n\t", "")
            .replace("\n ", "").replace("\n\t", "")

    private fun extractValue(line: String): String {
        val colonIndex = line.indexOf(':')
        return if (colonIndex >= 0) line.substring(colonIndex + 1).trim() else ""
    }

    private fun containsParam(line: String, param: String): Boolean {
        val paramPart = line.substringBefore(':')
        return paramPart.contains(param, ignoreCase = true)
    }
}
