package com.budgetmanager.data.remote

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * Conversion dates <-> chaînes ISO pour Postgrest.
 *
 * Convention : les `timestamptz` sont échangés en UTC. On sérialise un
 * [LocalDateTime] comme heure UTC, et on relit en normalisant sur UTC → l'heure
 * « murale » reste stable dans les deux sens (l'app est mono-fuseau, France).
 */
object DtoDates {

    fun parseDateTime(s: String?): LocalDateTime? = s?.let { raw ->
        runCatching { OffsetDateTime.parse(raw).withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime() }
            .getOrElse { runCatching { LocalDateTime.parse(raw) }.getOrNull() }
    }

    fun parseDate(s: String?): LocalDate? = s?.let { raw ->
        runCatching { LocalDate.parse(raw.take(10)) }.getOrNull()
    }

    fun formatDateTime(dt: LocalDateTime): String =
        dt.atOffset(ZoneOffset.UTC).toString()

    fun formatDate(d: LocalDate): String = d.toString()

    /** timestamptz ISO -> epoch millis (pour les champs createdAt/updatedAt en Long). */
    fun parseEpochMillis(s: String?): Long? = s?.let { raw ->
        runCatching { OffsetDateTime.parse(raw).toInstant().toEpochMilli() }.getOrNull()
    }
}
