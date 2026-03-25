package com.prgramed.econtacts.data.vcard

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.prgramed.econtacts.domain.model.Address
import com.prgramed.econtacts.domain.model.AddressType
import com.prgramed.econtacts.domain.model.Contact
import com.prgramed.econtacts.domain.model.Email
import com.prgramed.econtacts.domain.model.PhoneNumber
import com.prgramed.econtacts.domain.model.PhoneType
import com.prgramed.econtacts.domain.repository.ContactRepository
import com.prgramed.econtacts.domain.repository.VCardRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VCardRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val contactRepository: ContactRepository,
) : VCardRepository {

    override suspend fun exportContacts(ids: List<Long>): Uri = withContext(Dispatchers.IO) {
        val contacts = ids.mapNotNull { contactRepository.getById(it) }
        val file = File(context.cacheDir, "contacts_export.vcf")
        file.bufferedWriter().use { writer ->
            contacts.forEach { contact ->
                writer.write("BEGIN:VCARD\r\n")
                writer.write("VERSION:3.0\r\n")
                writer.write("FN:${contact.displayName}\r\n")
                contact.phoneNumbers.forEach { phone ->
                    val type = when (phone.type) {
                        PhoneType.MOBILE -> "CELL"
                        PhoneType.HOME -> "HOME"
                        PhoneType.WORK -> "WORK"
                        PhoneType.OTHER -> "OTHER"
                    }
                    writer.write("TEL;TYPE=$type:${phone.number}\r\n")
                }
                contact.emails.forEach { email ->
                    writer.write("EMAIL:${email.address}\r\n")
                }
                val org = contact.organization
                if (!org.isNullOrBlank()) {
                    writer.write("ORG:$org\r\n")
                }
                val title = contact.title
                if (!title.isNullOrBlank()) {
                    writer.write("TITLE:$title\r\n")
                }
                val bday = contact.birthday
                if (!bday.isNullOrBlank()) {
                    writer.write("BDAY:$bday\r\n")
                }
                contact.addresses.forEach { addr ->
                    val addrType = when (addr.type) {
                        AddressType.HOME -> "HOME"
                        AddressType.WORK -> "WORK"
                        AddressType.OTHER -> "OTHER"
                    }
                    writer.write("ADR;TYPE=$addrType:;;${addr.street};${addr.city};${addr.region};${addr.postalCode};${addr.country}\r\n")
                }
                contact.websites.forEach { url ->
                    writer.write("URL:$url\r\n")
                }
                val note = contact.note
                if (!note.isNullOrBlank()) {
                    writer.write("NOTE:$note\r\n")
                }
                writer.write("END:VCARD\r\n")
            }
        }
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    override suspend fun importContacts(uri: Uri): Int = withContext(Dispatchers.IO) {
        var count = 0
        val lines = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use {
            it.readLines()
        } ?: return@withContext 0

        var currentName = ""
        var currentPhones = mutableListOf<PhoneNumber>()
        var currentEmails = mutableListOf<Email>()
        var currentNote: String? = null
        var currentOrg: String? = null
        var currentTitle: String? = null
        var currentBday: String? = null
        var currentAddresses = mutableListOf<Address>()
        var currentWebsites = mutableListOf<String>()

        for (line in lines) {
            when {
                line.startsWith("FN:") -> currentName = line.substringAfter("FN:")
                line.startsWith("TEL") -> {
                    val number = line.substringAfter(":")
                    if (number.isNotBlank()) {
                        val type = when {
                            "CELL" in line -> PhoneType.MOBILE
                            "HOME" in line -> PhoneType.HOME
                            "WORK" in line -> PhoneType.WORK
                            else -> PhoneType.OTHER
                        }
                        currentPhones.add(PhoneNumber(number, type))
                    }
                }
                line.startsWith("EMAIL") -> {
                    val address = line.substringAfter(":")
                    if (address.isNotBlank()) {
                        currentEmails.add(Email(address))
                    }
                }
                line.startsWith("NOTE:") -> currentNote = line.substringAfter("NOTE:")
                line.startsWith("ORG") -> {
                    val value = line.substringAfter(":")
                    currentOrg = value.split(";").firstOrNull()?.trim()?.takeIf { it.isNotBlank() }
                }
                line.startsWith("TITLE") -> {
                    currentTitle = line.substringAfter(":").takeIf { it.isNotBlank() }
                }
                line.startsWith("BDAY") -> {
                    currentBday = line.substringAfter(":").takeIf { it.isNotBlank() }
                }
                line.startsWith("ADR") -> {
                    val value = line.substringAfter(":")
                    val parts = value.split(";")
                    val street = parts.getOrNull(2)?.trim() ?: ""
                    val city = parts.getOrNull(3)?.trim() ?: ""
                    val region = parts.getOrNull(4)?.trim() ?: ""
                    val postalCode = parts.getOrNull(5)?.trim() ?: ""
                    val country = parts.getOrNull(6)?.trim() ?: ""
                    val addrType = when {
                        "HOME" in line.substringBefore(":") -> AddressType.HOME
                        "WORK" in line.substringBefore(":") -> AddressType.WORK
                        else -> AddressType.OTHER
                    }
                    if (street.isNotBlank() || city.isNotBlank() || country.isNotBlank()) {
                        currentAddresses.add(Address(street, city, region, postalCode, country, addrType))
                    }
                }
                line.startsWith("URL") -> {
                    val url = line.substringAfter(":")
                    if (url.isNotBlank()) currentWebsites.add(url)
                }
                line == "END:VCARD" -> {
                    if (currentName.isNotBlank() || currentPhones.isNotEmpty()) {
                        contactRepository.insert(
                            Contact(
                                displayName = currentName,
                                phoneNumbers = currentPhones.toList(),
                                emails = currentEmails.toList(),
                                note = currentNote,
                                organization = currentOrg,
                                title = currentTitle,
                                birthday = currentBday,
                                addresses = currentAddresses.toList(),
                                websites = currentWebsites.toList(),
                            ),
                        )
                        count++
                    }
                    currentName = ""
                    currentPhones = mutableListOf()
                    currentEmails = mutableListOf()
                    currentNote = null
                    currentOrg = null
                    currentTitle = null
                    currentBday = null
                    currentAddresses = mutableListOf()
                    currentWebsites = mutableListOf()
                }
            }
        }
        count
    }
}
