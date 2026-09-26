package com.fuelexpenselog.app.ui.entry

import android.os.Bundle
import com.fuelexpenselog.app.format.InputText
import com.fuelexpenselog.domain.model.EntryTag
import com.fuelexpenselog.domain.model.FillUp
import com.fuelexpenselog.domain.model.Vehicle
import com.fuelexpenselog.domain.model.decodeOr
import com.fuelexpenselog.domain.time.CivilDate
import java.util.Currency
import java.util.Locale

/**
 * A fill-up exactly as typed so far. Lives in the SavedStateHandle, so a rotation or a
 * process death at the pump loses nothing.
 *
 * The three `edited` flags matter on an edit: a field still showing its stored value saves
 * that stored value back, untouched. Re-parsing the rounded text instead would quietly move a
 * reading typed in miles by a metre, or turn an imported unit-price row into a total row.
 */
data class FillUpForm(
    val vehicleId: Long,
    val odometerText: String,
    val odometerEdited: Boolean,
    val volumeText: String,
    val volumeEdited: Boolean,
    val totalText: String,
    val totalEdited: Boolean,
    val isFull: Boolean,
    /** A fill-up happened BEFORE this one and is not recorded. */
    val missedPrevious: Boolean,
    val date: CivilDate,
    val tag: EntryTag,
    val station: String,
    val note: String,
) {
    fun toBundle() = Bundle().apply {
        putLong("vehicleId", vehicleId)
        putString("odometerText", odometerText)
        putBoolean("odometerEdited", odometerEdited)
        putString("volumeText", volumeText)
        putBoolean("volumeEdited", volumeEdited)
        putString("totalText", totalText)
        putBoolean("totalEdited", totalEdited)
        putBoolean("isFull", isFull)
        putBoolean("missedPrevious", missedPrevious)
        putInt("date", date.value)
        putString("tag", tag.name)
        putString("station", station)
        putString("note", note)
    }

    companion object {
        fun fromBundle(b: Bundle) = FillUpForm(
            vehicleId = b.getLong("vehicleId"),
            odometerText = b.getString("odometerText").orEmpty(),
            odometerEdited = b.getBoolean("odometerEdited"),
            volumeText = b.getString("volumeText").orEmpty(),
            volumeEdited = b.getBoolean("volumeEdited"),
            totalText = b.getString("totalText").orEmpty(),
            totalEdited = b.getBoolean("totalEdited"),
            isFull = b.getBoolean("isFull", true),
            missedPrevious = b.getBoolean("missedPrevious"),
            date = CivilDate(b.getInt("date")),
            tag = decodeOr(b.getString("tag"), EntryTag.PERSONAL),
            station = b.getString("station").orEmpty(),
            note = b.getString("note").orEmpty(),
        )

        /** Full tank on, date today, odometer EMPTY - the last reading is a hint, not a prefill. */
        fun blank(vehicle: Vehicle, today: CivilDate) = FillUpForm(
            vehicleId = vehicle.id,
            odometerText = "",
            odometerEdited = false,
            volumeText = "",
            volumeEdited = false,
            totalText = "",
            totalEdited = false,
            isFull = true,
            missedPrevious = false,
            date = today,
            tag = vehicle.defaultTag,
            station = "",
            note = "",
        )

        fun of(fillUp: FillUp, vehicle: Vehicle, locale: Locale) = FillUpForm(
            vehicleId = fillUp.vehicleId,
            odometerText = fillUp.odometerM?.let { InputText.of(vehicle.distanceUnit.fromMetres(it), 1, locale) }.orEmpty(),
            odometerEdited = false,
            volumeText = InputText.of(fillUp.energy.inUnit(vehicle.volumeUnit), 3, locale),
            volumeEdited = false,
            totalText = fillUp.totalOrDerived()?.let { InputText.of(it.asDouble, moneyDecimals(it.currency), locale) }.orEmpty(),
            totalEdited = false,
            isFull = fillUp.isFull,
            missedPrevious = fillUp.missedPrevious,
            date = fillUp.date,
            tag = fillUp.tag,
            station = fillUp.station.orEmpty(),
            note = fillUp.note.orEmpty(),
        )

        /** The currency's own precision, and never fewer than two: fuel totals carry cents. */
        fun moneyDecimals(code: String): Int =
            maxOf(2, runCatching { Currency.getInstance(code).defaultFractionDigits }.getOrDefault(2))
    }
}
