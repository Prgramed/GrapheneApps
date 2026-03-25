package com.prgramed.econtacts.data.backup

import android.content.Context
import com.prgramed.econtacts.domain.model.Address
import com.prgramed.econtacts.domain.model.AddressType
import com.prgramed.econtacts.domain.model.Contact
import com.prgramed.econtacts.domain.model.Email
import com.prgramed.econtacts.domain.model.EmailType
import com.prgramed.econtacts.domain.model.PhoneNumber
import com.prgramed.econtacts.domain.model.PhoneType
import com.prgramed.econtacts.domain.repository.ContactRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContactBackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val contactRepository: ContactRepository,
) {

    private val backupsDir: File
        get() = File(context.filesDir, "backups").also { it.mkdirs() }

    suspend fun backupContacts(ids: List<Long>): File = withContext(Dispatchers.IO) {
        val file = File(backupsDir, "backup_${System.currentTimeMillis()}.vcf")
        try {
            file.bufferedWriter().use { writer ->
                for (id in ids) {
                    val contact = contactRepository.getById(id) ?: continue
                    writer.write(contactToVCard(contact))
                    writer.newLine()
                }
            }
        } catch (e: Exception) {
            // Best-effort — return whatever was written
        }
        file
    }

    suspend fun restoreFromBackup(file: File): Int = withContext(Dispatchers.IO) {
        var count = 0
        try {
            val lines = file.readLines()
            var inCard = false
            var fn = ""
            val phones = mutableListOf<PhoneNumber>()
            val emails = mutableListOf<Email>()
            var note: String? = null
            var org: String? = null
            var title: String? = null
            var bday: String? = null
            val addresses = mutableListOf<Address>()
            val websites = mutableListOf<String>()

            for (line in lines) {
                when {
                    line.startsWith("BEGIN:VCARD") -> {
                        inCard = true
                        fn = ""
                        phones.clear()
                        emails.clear()
                        note = null
                        org = null
                        title = null
                        bday = null
                        addresses.clear()
                        websites.clear()
                    }

                    line.startsWith("END:VCARD") && inCard -> {
                        if (fn.isNotBlank()) {
                            try {
                                val contact = Contact(
                                    displayName = fn,
                                    phoneNumbers = phones.toList(),
                                    emails = emails.toList(),
                                    note = note,
                                    organization = org,
                                    title = title,
                                    birthday = bday,
                                    addresses = addresses.toList(),
                                    websites = websites.toList(),
                                )
                                contactRepository.insert(contact)
                                count++
                            } catch (_: Exception) {
                                // Skip contacts that fail to insert
                            }
                        }
                        inCard = false
                    }

                    inCard -> parseVCardLine(line, fn, phones, emails, addresses, websites).let {
                        fn = it.fn
                        note = it.note ?: note
                        org = it.org ?: org
                        title = it.title ?: title
                        bday = it.bday ?: bday
                    }
                }
            }
        } catch (_: Exception) {
            // Best-effort restore
        }
        count
    }

    fun cleanOldBackups(maxAgeDays: Int = 7) {
        try {
            val cutoff = System.currentTimeMillis() - maxAgeDays * 24 * 60 * 60 * 1000L
            backupsDir.listFiles()?.forEach { file ->
                if (file.lastModified() < cutoff) {
                    file.delete()
                }
            }
        } catch (_: Exception) {
            // Best-effort cleanup
        }
    }

    private fun contactToVCard(contact: Contact): String = buildString {
        appendLine("BEGIN:VCARD")
        appendLine("VERSION:3.0")
        appendLine("FN:${contact.displayName}")

        contact.phoneNumbers.forEach { phone ->
            val typeParam = when (phone.type) {
                PhoneType.MOBILE -> "CELL"
                PhoneType.HOME -> "HOME"
                PhoneType.WORK -> "WORK"
                PhoneType.OTHER -> "OTHER"
            }
            appendLine("TEL;TYPE=$typeParam:${phone.number}")
        }

        contact.emails.forEach { email ->
            val typeParam = when (email.type) {
                EmailType.HOME -> "HOME"
                EmailType.WORK -> "WORK"
                EmailType.OTHER -> "OTHER"
            }
            appendLine("EMAIL;TYPE=$typeParam:${email.address}")
        }

        contact.note?.let { appendLine("NOTE:$it") }
        contact.organization?.let { appendLine("ORG:$it") }
        contact.title?.let { appendLine("TITLE:$it") }
        contact.birthday?.let { appendLine("BDAY:$it") }

        contact.addresses.forEach { addr ->
            val typeParam = when (addr.type) {
                AddressType.HOME -> "HOME"
                AddressType.WORK -> "WORK"
                AddressType.OTHER -> "OTHER"
            }
            appendLine("ADR;TYPE=$typeParam:;;${addr.street};${addr.city};${addr.region};${addr.postalCode};${addr.country}")
        }

        contact.websites.forEach { url ->
            appendLine("URL:$url")
        }

        appendLine("END:VCARD")
    }

    private data class ParseResult(
        val fn: String,
        val note: String? = null,
        val org: String? = null,
        val title: String? = null,
        val bday: String? = null,
    )

    private fun parseVCardLine(
        line: String,
        currentFn: String,
        phones: MutableList<PhoneNumber>,
        emails: MutableList<Email>,
        addresses: MutableList<Address>,
        websites: MutableList<String>,
    ): ParseResult {
        var fn = currentFn
        var note: String? = null
        var org: String? = null
        var title: String? = null
        var bday: String? = null

        try {
            when {
                line.startsWith("FN:") -> fn = line.removePrefix("FN:")

                line.startsWith("TEL") -> {
                    val value = line.substringAfter(":")
                    val type = when {
                        line.contains("CELL", ignoreCase = true) -> PhoneType.MOBILE
                        line.contains("HOME", ignoreCase = true) -> PhoneType.HOME
                        line.contains("WORK", ignoreCase = true) -> PhoneType.WORK
                        else -> PhoneType.OTHER
                    }
                    phones.add(PhoneNumber(number = value, type = type))
                }

                line.startsWith("EMAIL") -> {
                    val value = line.substringAfter(":")
                    val type = when {
                        line.contains("HOME", ignoreCase = true) -> EmailType.HOME
                        line.contains("WORK", ignoreCase = true) -> EmailType.WORK
                        else -> EmailType.OTHER
                    }
                    emails.add(Email(address = value, type = type))
                }

                line.startsWith("NOTE:") -> note = line.removePrefix("NOTE:")
                line.startsWith("ORG:") -> org = line.removePrefix("ORG:")
                line.startsWith("TITLE:") -> title = line.removePrefix("TITLE:")
                line.startsWith("BDAY:") -> bday = line.removePrefix("BDAY:")

                line.startsWith("ADR") -> {
                    val value = line.substringAfter(":")
                    val parts = value.split(";")
                    val type = when {
                        line.contains("HOME", ignoreCase = true) -> AddressType.HOME
                        line.contains("WORK", ignoreCase = true) -> AddressType.WORK
                        else -> AddressType.OTHER
                    }
                    addresses.add(
                        Address(
                            street = parts.getOrElse(2) { "" },
                            city = parts.getOrElse(3) { "" },
                            region = parts.getOrElse(4) { "" },
                            postalCode = parts.getOrElse(5) { "" },
                            country = parts.getOrElse(6) { "" },
                            type = type,
                        ),
                    )
                }

                line.startsWith("URL:") -> websites.add(line.removePrefix("URL:"))
            }
        } catch (_: Exception) {
            // Skip malformed lines
        }

        return ParseResult(fn = fn, note = note, org = org, title = title, bday = bday)
    }
}
