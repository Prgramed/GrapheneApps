package dev.ecalendar.data.db.entity

import dev.ecalendar.domain.model.*

// Account
fun AccountEntity.toDomain() = CalendarAccount(
    id = id, type = AccountType.valueOf(type), displayName = displayName,
    baseUrl = baseUrl, username = username, colorHex = colorHex,
    lastSyncedAt = lastSyncedAt, isEnabled = isEnabled,
)

fun CalendarAccount.toEntity() = AccountEntity(
    id = id, type = type.name, displayName = displayName,
    baseUrl = baseUrl, username = username, colorHex = colorHex,
    lastSyncedAt = lastSyncedAt, isEnabled = isEnabled,
)

// CalendarSource
fun CalendarSourceEntity.toDomain() = CalendarSource(
    id = id, accountId = accountId, calDavUrl = calDavUrl,
    displayName = displayName, colorHex = colorHex, ctag = ctag,
    isReadOnly = isReadOnly, isVisible = isVisible, isMirror = isMirror,
)

fun CalendarSource.toEntity() = CalendarSourceEntity(
    id = id, accountId = accountId, calDavUrl = calDavUrl,
    displayName = displayName, colorHex = colorHex, ctag = ctag,
    isReadOnly = isReadOnly, isVisible = isVisible, isMirror = isMirror,
)

// EventSeries
fun EventSeriesEntity.toDomain() = EventSeries(
    uid = uid, calendarSourceId = calendarSourceId, rawIcs = rawIcs,
    etag = etag, serverUrl = serverUrl, isLocal = isLocal,
)

fun EventSeries.toEntity() = EventSeriesEntity(
    uid = uid, calendarSourceId = calendarSourceId, rawIcs = rawIcs,
    etag = etag, serverUrl = serverUrl, isLocal = isLocal,
)

// CalendarEvent
fun CalendarEventEntity.toDomain() = CalendarEvent(
    id = id, uid = uid, instanceStart = instanceStart, instanceEnd = instanceEnd,
    title = title, location = location, notes = notes, url = url,
    colorHex = colorHex, isAllDay = isAllDay, calendarSourceId = calendarSourceId,
    recurrenceId = recurrenceId, isCancelled = isCancelled, travelTimeMins = travelTimeMins,
)

fun CalendarEvent.toEntity() = CalendarEventEntity(
    id = id, uid = uid, instanceStart = instanceStart, instanceEnd = instanceEnd,
    title = title, location = location, notes = notes, url = url,
    colorHex = colorHex, isAllDay = isAllDay, calendarSourceId = calendarSourceId,
    recurrenceId = recurrenceId, isCancelled = isCancelled, travelTimeMins = travelTimeMins,
)

// SyncQueueItem
fun SyncQueueEntity.toDomain() = SyncQueueItem(
    id = id, accountId = accountId, calendarUrl = calendarUrl,
    eventUid = eventUid, operation = SyncOp.valueOf(operation),
    icsPayload = icsPayload, createdAt = createdAt, retryCount = retryCount,
)

fun SyncQueueItem.toEntity() = SyncQueueEntity(
    id = id, accountId = accountId, calendarUrl = calendarUrl,
    eventUid = eventUid, operation = operation.name,
    icsPayload = icsPayload, createdAt = createdAt, retryCount = retryCount,
)
