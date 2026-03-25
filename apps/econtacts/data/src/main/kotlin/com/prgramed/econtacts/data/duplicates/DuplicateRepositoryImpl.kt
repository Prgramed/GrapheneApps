package com.prgramed.econtacts.data.duplicates

import com.prgramed.econtacts.domain.model.Contact
import com.prgramed.econtacts.domain.model.DuplicateGroup
import com.prgramed.econtacts.domain.repository.ContactRepository
import com.prgramed.econtacts.domain.repository.DuplicateRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

@Singleton
class DuplicateRepositoryImpl @Inject constructor(
    private val contactRepository: ContactRepository,
) : DuplicateRepository {

    override fun findDuplicates(): Flow<List<DuplicateGroup>> = flow {
        val contacts = contactRepository.getAll().first()
        val duplicateGroups = mutableListOf<DuplicateGroup>()
        val processed = mutableSetOf<Long>()

        // Index by normalized phone number for O(n) phone matching
        val phoneIndex = mutableMapOf<String, MutableList<Int>>()
        contacts.forEachIndexed { idx, c ->
            c.phoneNumbers.forEach { p ->
                val norm = normalize(p.number)
                if (norm.isNotBlank()) phoneIndex.getOrPut(norm) { mutableListOf() }.add(idx)
            }
        }

        // Index by name prefix (first 3 chars) for bucketed name matching
        val nameIndex = mutableMapOf<String, MutableList<Int>>()
        contacts.forEachIndexed { idx, c ->
            val prefix = c.displayName.lowercase().take(3)
            if (prefix.length == 3) nameIndex.getOrPut(prefix) { mutableListOf() }.add(idx)
        }

        // Find phone duplicates
        for ((_, indices) in phoneIndex) {
            if (indices.size < 2) continue
            val anchor = indices.first()
            if (contacts[anchor].id in processed) continue
            val group = mutableListOf(contacts[anchor])
            for (k in 1 until indices.size) {
                val c = contacts[indices[k]]
                if (c.id in processed) continue
                group.add(c)
            }
            if (group.size > 1) {
                processed.addAll(group.map { it.id })
                duplicateGroups.add(DuplicateGroup(group, "Same phone number"))
            }
        }

        // Find name duplicates (only within same prefix bucket)
        for ((_, indices) in nameIndex) {
            if (indices.size < 2) continue
            for (i in indices.indices) {
                val ci = contacts[indices[i]]
                if (ci.id in processed) continue
                val group = mutableListOf(ci)
                for (j in i + 1 until indices.size) {
                    val cj = contacts[indices[j]]
                    if (cj.id in processed) continue
                    if (isSimilarName(ci.displayName, cj.displayName)) {
                        group.add(cj)
                    }
                }
                if (group.size > 1) {
                    processed.addAll(group.map { it.id })
                    duplicateGroups.add(DuplicateGroup(group, "Similar name"))
                }
            }
        }

        emit(duplicateGroups)
    }.flowOn(Dispatchers.IO)

    override suspend fun mergeContacts(primaryId: Long, mergeIds: List<Long>) {
        val primary = contactRepository.getById(primaryId) ?: return
        val toMerge = mergeIds.mapNotNull { contactRepository.getById(it) }

        val allPhones = (primary.phoneNumbers + toMerge.flatMap { it.phoneNumbers })
            .distinctBy { it.number.replace(Regex("[^\\d+]"), "") }
        val allEmails = (primary.emails + toMerge.flatMap { it.emails })
            .distinctBy { it.address.lowercase() }
        val mergedNote = listOfNotNull(primary.note, *toMerge.mapNotNull { it.note }.toTypedArray())
            .joinToString("\n")
            .ifBlank { null }

        contactRepository.update(
            primary.copy(
                phoneNumbers = allPhones,
                emails = allEmails,
                note = mergedNote,
                starred = primary.starred || toMerge.any { it.starred },
            ),
        )

        contactRepository.delete(mergeIds)
    }

    private fun hasMatchingPhone(a: Contact, b: Contact): Boolean {
        val aNumbers = a.phoneNumbers.map { normalize(it.number) }.toSet()
        val bNumbers = b.phoneNumbers.map { normalize(it.number) }.toSet()
        return aNumbers.intersect(bNumbers).isNotEmpty()
    }

    private fun normalize(number: String): String = number.replace(Regex("[^\\d+]"), "")

    private fun isSimilarName(a: String, b: String): Boolean {
        if (a.isBlank() || b.isBlank()) return false
        val aLow = a.lowercase()
        val bLow = b.lowercase()
        if (aLow == bLow) return true
        // Only consider names that share the same first 3 chars
        if (aLow.take(3) != bLow.take(3)) return false
        val maxLen = maxOf(aLow.length, bLow.length)
        // Allow 1 edit for names ≤8 chars, 2 for longer names
        val threshold = if (maxLen <= 8) 1 else 2
        return levenshtein(aLow, bLow) <= threshold
    }

    private fun levenshtein(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = min(min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost)
            }
        }
        return dp[a.length][b.length]
    }
}
