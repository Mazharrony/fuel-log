package com.fuelexpenselog.app.ui.entry

import android.os.Bundle
import com.fuelexpenselog.app.format.InputText
import com.fuelexpenselog.domain.model.EntryTag
import com.fuelexpenselog.domain.model.Expense
import com.fuelexpenselog.domain.model.ExpenseCategory
import com.fuelexpenselog.domain.model.Vehicle
import com.fuelexpenselog.domain.model.decodeOr
import com.fuelexpenselog.domain.time.CivilDate
import java.util.Locale

/** An expense as typed so far. The same stored-or-typed rule as [FillUpForm]. */
data class ExpenseForm(
    val vehicleId: Long,
    val amountText: String,
    val amountEdited: Boolean,
    val odometerText: String,
    val odometerEdited: Boolean,
    val category: ExpenseCategory,
    val date: CivilDate,
    val tag: EntryTag,
    val vendor: String,
    val note: String,
) {
    fun toBundle() = Bundle().apply {
        putLong("vehicleId", vehicleId)
        putString("amountText", amountText)
        putBoolean("amountEdited", amountEdited)
        putString("odometerText", odometerText)
        putBoolean("odometerEdited", odometerEdited)
        putString("category", category.name)
        putInt("date", date.value)
        putString("tag", tag.name)
        putString("vendor", vendor)
        putString("note", note)
    }

    companion object {
        fun fromBundle(b: Bundle) = ExpenseForm(
            vehicleId = b.getLong("vehicleId"),
            amountText = b.getString("amountText").orEmpty(),
            amountEdited = b.getBoolean("amountEdited"),
            odometerText = b.getString("odometerText").orEmpty(),
            odometerEdited = b.getBoolean("odometerEdited"),
            category = decodeOr(b.getString("category"), ExpenseCategory.OTHER),
            date = CivilDate(b.getInt("date")),
            tag = decodeOr(b.getString("tag"), EntryTag.PERSONAL),
            vendor = b.getString("vendor").orEmpty(),
            note = b.getString("note").orEmpty(),
        )

        /**
         * "Other" is selected until the user picks: an honest default, where the first chip
         * would quietly file an unlabelled cost as an oil change.
         */
        fun blank(vehicle: Vehicle, today: CivilDate) = ExpenseForm(
            vehicleId = vehicle.id,
            amountText = "",
            amountEdited = false,
            odometerText = "",
            odometerEdited = false,
            category = ExpenseCategory.OTHER,
            date = today,
            tag = vehicle.defaultTag,
            vendor = "",
            note = "",
        )

        fun of(expense: Expense, vehicle: Vehicle, locale: Locale) = ExpenseForm(
            vehicleId = expense.vehicleId,
            amountText = InputText.of(expense.amount.asDouble, FillUpForm.moneyDecimals(expense.amount.currency), locale),
            amountEdited = false,
            odometerText = expense.odometerM?.let { InputText.of(vehicle.distanceUnit.fromMetres(it), 1, locale) }.orEmpty(),
            odometerEdited = false,
            category = expense.category,
            date = expense.date,
            tag = expense.tag,
            vendor = expense.vendor.orEmpty(),
            note = expense.note.orEmpty(),
        )
    }
}
