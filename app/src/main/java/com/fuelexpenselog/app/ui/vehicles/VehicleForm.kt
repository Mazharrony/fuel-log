package com.fuelexpenselog.app.ui.vehicles

import android.os.Bundle
import com.fuelexpenselog.domain.model.EntryTag
import com.fuelexpenselog.domain.model.FuelType
import com.fuelexpenselog.domain.model.Vehicle
import com.fuelexpenselog.domain.model.VehicleType
import com.fuelexpenselog.domain.model.decodeOr
import com.fuelexpenselog.domain.unit.ConsumptionFormat
import com.fuelexpenselog.domain.unit.DistanceUnit
import com.fuelexpenselog.domain.unit.EnergyUnit
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Exactly what the editor shows, as typed. Kept in the SavedStateHandle as a Bundle, so a
 * half-edited vehicle survives rotation and process death alike.
 */
data class VehicleForm(
    val name: String,
    val distanceUnit: DistanceUnit,
    val volumeUnit: EnergyUnit,
    val currency: String,
    val tankText: String,
    /**
     * False while the tank field still shows the stored capacity. Saving then writes the
     * stored value back untouched, so switching litres to gallons and back cannot drift the
     * capacity through two roundings.
     */
    val tankEdited: Boolean,
    val type: VehicleType,
    val fuelType: FuelType,
    val defaultTag: EntryTag,
    /** Null means "follow the app setting". */
    val format: ConsumptionFormat?,
    val active: Boolean,
) {
    fun toBundle() = Bundle().apply {
        putString("name", name)
        putString("distanceUnit", distanceUnit.name)
        putString("volumeUnit", volumeUnit.name)
        putString("currency", currency)
        putString("tankText", tankText)
        putBoolean("tankEdited", tankEdited)
        putString("type", type.name)
        putString("fuelType", fuelType.name)
        putString("defaultTag", defaultTag.name)
        putString("format", format?.name)
        putBoolean("active", active)
    }

    companion object {
        fun fromBundle(b: Bundle) = VehicleForm(
            name = b.getString("name").orEmpty(),
            distanceUnit = decodeOr(b.getString("distanceUnit"), DistanceUnit.KILOMETRE),
            volumeUnit = decodeOr(b.getString("volumeUnit"), EnergyUnit.LITRE).takeIf { it in EnergyUnit.liquid }
                ?: EnergyUnit.LITRE,
            currency = b.getString("currency") ?: "USD",
            tankText = b.getString("tankText").orEmpty(),
            tankEdited = b.getBoolean("tankEdited"),
            type = decodeOr(b.getString("type"), VehicleType.CAR),
            fuelType = decodeOr(b.getString("fuelType"), FuelType.PETROL),
            defaultTag = decodeOr(b.getString("defaultTag"), EntryTag.PERSONAL),
            format = b.getString("format")?.let { decodeOr<ConsumptionFormat>(it, ConsumptionFormat.L_PER_100KM) },
            active = b.getBoolean("active", true),
        )

        fun of(vehicle: Vehicle, locale: Locale) = VehicleForm(
            name = vehicle.name,
            distanceUnit = vehicle.distanceUnit,
            volumeUnit = vehicle.volumeUnit,
            currency = vehicle.currencyCode,
            tankText = vehicle.tankCapacity?.let { tankText(it.inUnit(vehicle.volumeUnit), locale) }.orEmpty(),
            tankEdited = false,
            type = vehicle.type,
            fuelType = vehicle.fuelType,
            defaultTag = vehicle.defaultTag,
            format = vehicle.consumptionFormat,
            active = !vehicle.isArchived,
        )

        /**
         * A capacity as the user would type it: at most two decimals, no trailing zeros, the
         * locale's decimal mark. DecimalParser reads it back whichever mark it carries.
         */
        fun tankText(value: Double, locale: Locale): String {
            val plain = BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
            val mark = DecimalFormatSymbols.getInstance(locale).decimalSeparator
            return if (mark == '.') plain else plain.replace('.', mark)
        }
    }
}
