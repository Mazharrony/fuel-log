package com.fuelexpenselog.app.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.fuelexpenselog.app.domain.model.DistanceUnit
import com.fuelexpenselog.app.domain.model.ExpenseCategory
import com.fuelexpenselog.app.domain.model.VolumeUnit

@Entity(tableName = "vehicle")
data class VehicleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val distanceUnit: DistanceUnit,
    val volumeUnit: VolumeUnit,
    val currency: String,
    val tankCapacityLitres: Double?,
    val isActive: Boolean = true,
)

/**
 * Odometer is kilometres and volume is litres, always. The composite
 * (vehicleId, odometer) index exists because the engine's sort is by odometer -
 * let SQLite do that work.
 */
@Entity(
    tableName = "fill_up",
    foreignKeys = [
        ForeignKey(
            entity = VehicleEntity::class,
            parentColumns = ["id"],
            childColumns = ["vehicleId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("vehicleId", "odometer"), Index("vehicleId", "date")],
)
data class FillUpEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vehicleId: Long,
    val date: Long,
    val odometer: Double,
    val volume: Double,
    val totalCost: Double,
    val isFullTank: Boolean,
    val isMissedEntry: Boolean,
    val fuelType: String?,
    val note: String?,
)

@Entity(
    tableName = "expense",
    foreignKeys = [
        ForeignKey(
            entity = VehicleEntity::class,
            parentColumns = ["id"],
            childColumns = ["vehicleId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("vehicleId", "date"), Index("vehicleId", "category")],
)
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vehicleId: Long,
    val date: Long,
    val odometer: Double?,
    val category: ExpenseCategory,
    val totalCost: Double,
    val note: String?,
)
