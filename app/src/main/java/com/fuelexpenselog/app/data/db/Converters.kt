package com.fuelexpenselog.app.data.db

import androidx.room.TypeConverter
import com.fuelexpenselog.app.domain.model.DistanceUnit
import com.fuelexpenselog.app.domain.model.ExpenseCategory
import com.fuelexpenselog.app.domain.model.VolumeUnit

/**
 * Enums are stored as their NAME, never their ordinal. Reordering an enum must
 * not silently rewrite somebody's history.
 *
 * Unknown values degrade instead of throwing, so a database written by a newer
 * version and restored onto an older build still opens - it just shows OTHER
 * where it does not recognise a category.
 */
class Converters {
    @TypeConverter
    fun distanceUnitToString(value: DistanceUnit): String = value.name

    @TypeConverter
    fun stringToDistanceUnit(value: String): DistanceUnit =
        runCatching { DistanceUnit.valueOf(value) }.getOrDefault(DistanceUnit.KILOMETRE)

    @TypeConverter
    fun volumeUnitToString(value: VolumeUnit): String = value.name

    @TypeConverter
    fun stringToVolumeUnit(value: String): VolumeUnit =
        runCatching { VolumeUnit.valueOf(value) }.getOrDefault(VolumeUnit.LITRE)

    @TypeConverter
    fun categoryToString(value: ExpenseCategory): String = value.name

    @TypeConverter
    fun stringToCategory(value: String): ExpenseCategory =
        runCatching { ExpenseCategory.valueOf(value) }.getOrDefault(ExpenseCategory.OTHER)
}
