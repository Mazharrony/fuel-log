package com.fuelexpenselog.csv

import com.fuelexpenselog.domain.model.ExpenseCategory
import com.fuelexpenselog.domain.time.CivilDate
import java.security.MessageDigest

/**
 * The fingerprint that marks a row as already imported.
 *
 * Defined here, next to the exporter, so the importer cannot drift from it: re-importing the
 * app's own export - the common case - has to produce the very same hashes. Canonical values
 * only (metres, micro-units, micros, the civil date), so a unit or locale change between two
 * imports does not make the same fill-up look new.
 */
object ImportRowHash {

    fun fillUp(vehicleId: Long, date: CivilDate, odometerM: Long?, energyMicro: Long, totalMicros: Long?): String =
        sha256("fill_up|$vehicleId|${date.value}|${odometerM ?: ""}|$energyMicro|${totalMicros ?: ""}")

    fun expense(vehicleId: Long, date: CivilDate, odometerM: Long?, category: ExpenseCategory, amountMicros: Long): String =
        sha256("expense|$vehicleId|${date.value}|${odometerM ?: ""}|${category.name}|$amountMicros")

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
