package com.fuelexpenselog.app.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Storage conventions, applied without exception:
 *
 *  - **No REAL columns anywhere.** Distance is Long metres, energy Long micro-units, money
 *    Long micros. Floats make comparisons approximate, indexed range scans approximate, and
 *    CSV round-trips lossy - none of which is survivable in an odometer chain.
 *  - **Dates are stored twice.** `occurredLocalDate` is the yyyymmdd civil date the user
 *    typed, which is what every month query uses; `occurredAtMillis` is only for ordering
 *    and tie-breaking. The civil date is timezone-free forever, so flying between countries
 *    never moves an entry into a different month.
 *  - **Enums are stored by name, never ordinal**, so reordering a declaration cannot
 *    silently rewrite somebody's history.
 *  - **Nothing derived is stored.** There is no consumption column, no month total, no
 *    "last done at". Cached derived values are how a list and its total end up disagreeing.
 */

@Entity(
    tableName = "vehicle",
    indices = [Index(value = ["isArchived", "sortOrder"])],
)
data class VehicleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String,
    val fuelType: String,
    val distanceUnit: String,
    val volumeUnit: String,
    /** Unused by the v1 UI. Present so an EV needs no migration, only a screen. */
    val energyUnit: String,
    /** Null means "use the app-level default". A UK car and a US car in one garage want
     *  different formats, and this is one nullable column now versus a retrofit later. */
    val consumptionFormat: String?,
    val currencyCode: String,
    /** Microlitres. Drives the over-capacity WARNING; it never blocks a save. */
    val tankCapacityMicro: Long?,
    /** Micro-kWh. EV-ready, unused in v1. */
    val batteryCapacityMicro: Long?,
    /** A sole trader sets this once and stops thinking about it. */
    val defaultTag: String,
    val make: String?,
    val model: String?,
    val plate: String?,
    val modelYear: Int?,
    val notes: String?,
    val sortOrder: Int = 0,
    /** Sold vehicles are hidden, never deleted: their history is still the owner's tax record. */
    val isArchived: Boolean = false,
    val createdAtMillis: Long,
)

/**
 * One energy event: a tank of petrol, or a charge.
 *
 * The generic `energyMicro` + `energyKind` shape is what makes this table EV-ready without a
 * migration. A plug-in hybrid needs both a petrol fill and a charge session, at different
 * odometers, so they cannot be columns on one row - one row per event with a kind
 * discriminator is the only model that survives a PHEV. The engine then computes per kind
 * for free.
 *
 * The cost is that `energyMicro`'s unit depends on a sibling column, mitigated by keeping
 * `energyKind` NOT NULL and adjacent and by never letting a bare Long cross into the domain:
 * it becomes an `Energy(kind, micro)` at the repository boundary.
 */
@Entity(
    tableName = "fill_up",
    foreignKeys = [
        ForeignKey(
            entity = VehicleEntity::class,
            parentColumns = ["id"],
            childColumns = ["vehicleId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        // The engine's read and the monthly range scan. Covers the ORDER BY, so SQLite
        // builds no temporary b-tree.
        Index(value = ["vehicleId", "occurredLocalDate", "odometerM"]),
        // MAX(odometerM) for "where is this vehicle now", which every distance-based
        // reminder needs on every screen open. Not served by the index above, whose leading
        // sort key is the date.
        Index(value = ["vehicleId", "odometerM"]),
        // The business/personal tax export. Without it, "all business fill-ups in FY2026"
        // is a full scan.
        Index(value = ["vehicleId", "tag", "occurredLocalDate"]),
        // Duplicate probe on re-import. Deliberately NOT unique: two genuine fills of the
        // same amount on the same day at the same station do happen.
        Index(value = ["importRowHash"]),
        Index(value = ["importBatchId"]),
    ],
)
data class FillUpEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vehicleId: Long,
    /** yyyymmdd. The month bucket is this divided by 100. */
    val occurredLocalDate: Int,
    val occurredAtMillis: Long,
    /** Null when the user did not record a reading. Never treat null as zero. */
    val odometerM: Long?,
    val energyKind: String,
    /** Microlitres when LIQUID, micro-kWh when ELECTRIC. See energyKind. */
    val energyMicro: Long,
    /** What they actually typed, so an edit shows it back rather than a conversion of it. */
    val energyUnitEntered: String,
    val isFull: Boolean,
    /** True means a fill-up happened BEFORE this one that is not recorded. */
    val missedPrevious: Boolean = false,
    /**
     * At least one of total/unitPrice is present; the other is derived at display time.
     * Storing both would put a rounded number the user never typed into their tax export.
     */
    val totalMicros: Long?,
    val unitPriceMicros: Long?,
    /** Snapshotted at entry time rather than joined: changing a vehicle's currency later
     *  must not silently restate what was actually paid. */
    val currencyCode: String,
    val tag: String,
    val station: String?,
    val fuelGrade: String?,
    val paymentMethod: String?,
    val note: String?,
    val importSource: String?,
    val importBatchId: Long?,
    val importRowHash: String?,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

@Entity(
    tableName = "expense",
    foreignKeys = [
        ForeignKey(
            entity = VehicleEntity::class,
            parentColumns = ["id"],
            childColumns = ["vehicleId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["vehicleId", "occurredLocalDate"]),
        // Serves two different queries: the month's category breakdown, and "when was the
        // last OIL_CHANGE" for reminder anchoring.
        Index(value = ["vehicleId", "category", "occurredLocalDate"]),
        Index(value = ["vehicleId", "tag", "occurredLocalDate"]),
        Index(value = ["reminderId"]),
        Index(value = ["importBatchId"]),
    ],
)
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vehicleId: Long,
    val occurredLocalDate: Int,
    val occurredAtMillis: Long,
    /** Optional: a service has a reading, a parking ticket does not. */
    val odometerM: Long?,
    val category: String,
    val totalMicros: Long,
    val currencyCode: String,
    val tag: String,
    /** Free text, and the reason the twelve categories never need to grow. "Congestion
     *  Charge" and "Dartford Crossing" are both TOLL with a vendor. */
    val vendor: String?,
    val note: String?,
    val reminderId: Long?,
    val importSource: String?,
    val importBatchId: Long?,
    val importRowHash: String?,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

/**
 * A break in the odometer the user has confirmed.
 *
 * Rows here record intent only. The engine detects anomalies from the data and proposes a
 * segment; nothing is written until someone says yes, because an automatic segment silently
 * discards every consumption figure that crosses it.
 */
@Entity(
    tableName = "odometer_segment",
    foreignKeys = [
        ForeignKey(
            entity = VehicleEntity::class,
            parentColumns = ["id"],
            childColumns = ["vehicleId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["vehicleId", "startsAtLocalDate"])],
)
data class OdometerSegmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vehicleId: Long,
    val startsAtLocalDate: Int,
    val startsAtMillis: Long,
    val reason: String,
    /** Non-null bridges the gap by this much (a rollover). Null is a hard break. */
    val offsetM: Long?,
    val note: String?,
    val createdAtMillis: Long,
)

@Entity(
    tableName = "reminder",
    foreignKeys = [
        ForeignKey(
            entity = VehicleEntity::class,
            parentColumns = ["id"],
            childColumns = ["vehicleId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        // The "what is due" list on a vehicle screen.
        Index(value = ["vehicleId", "isActive", "dueLocalDate"]),
        // The daily alarm's cross-vehicle scan. A different query with a different leading
        // column, so it earns its own index.
        Index(value = ["isActive", "notifyEnabled", "dueLocalDate"]),
    ],
)
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vehicleId: Long,
    val title: String,
    val kind: String,
    /** Links to an ExpenseCategory so completing one can pre-fill the expense. */
    val category: String?,
    val dueLocalDate: Int?,
    val repeatMonths: Int?,
    val warnDaysBefore: Int = 30,
    val dueOdometerM: Long?,
    val repeatDistanceM: Long?,
    val warnDistanceM: Long = 500_000,
    /** Repeats recompute from the anchor, never from wall-clock drift. */
    val anchorLocalDate: Int?,
    val anchorOdometerM: Long?,
    val isActive: Boolean = true,
    val notifyEnabled: Boolean = false,
    /** Dedupe, so the daily check cannot nag every morning about the same thing. */
    val lastNotifiedLocalDate: Int?,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

@Entity(
    tableName = "reminder_completion",
    foreignKeys = [
        ForeignKey(
            entity = ReminderEntity::class,
            parentColumns = ["id"],
            childColumns = ["reminderId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["reminderId", "completedLocalDate"])],
)
data class ReminderCompletionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val reminderId: Long,
    val vehicleId: Long,
    val completedLocalDate: Int,
    val odometerM: Long?,
    val expenseId: Long?,
    val note: String?,
    val createdAtMillis: Long,
)

/**
 * One import, so it can be undone as a unit.
 *
 * A small table with a large payoff: import is the highest-regret operation in this app, and
 * "Undo import: Fuelio, 412 rows, 14 Sep" is the difference between a recoverable mistake
 * and someone hand-deleting four hundred entries.
 */
@Entity(tableName = "import_batch")
data class ImportBatchEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val source: String,
    val fileName: String,
    val importedAtMillis: Long,
    val rowCount: Int,
    val vehicleId: Long?,
)
