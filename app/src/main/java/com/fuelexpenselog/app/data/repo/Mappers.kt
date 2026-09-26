package com.fuelexpenselog.app.data.repo

import com.fuelexpenselog.app.data.db.entity.ExpenseEntity
import com.fuelexpenselog.app.data.db.entity.FillUpEntity
import com.fuelexpenselog.app.data.db.entity.ImportBatchEntity
import com.fuelexpenselog.app.data.db.entity.OdometerSegmentEntity
import com.fuelexpenselog.app.data.db.entity.VehicleEntity
import com.fuelexpenselog.domain.consumption.DeclaredSegment
import com.fuelexpenselog.domain.consumption.SegmentReason
import com.fuelexpenselog.domain.model.Decoded
import com.fuelexpenselog.domain.model.EntryTag
import com.fuelexpenselog.domain.model.Expense
import com.fuelexpenselog.domain.model.ExpenseCategory
import com.fuelexpenselog.domain.model.FillUp
import com.fuelexpenselog.domain.model.FuelType
import com.fuelexpenselog.domain.model.Vehicle
import com.fuelexpenselog.domain.model.VehicleType
import com.fuelexpenselog.domain.model.decodeOr
import com.fuelexpenselog.domain.model.decodeStrict
import com.fuelexpenselog.domain.money.Money
import com.fuelexpenselog.domain.time.CivilDate
import com.fuelexpenselog.domain.unit.ConsumptionFormat
import com.fuelexpenselog.domain.unit.DistanceUnit
import com.fuelexpenselog.domain.unit.Energy
import com.fuelexpenselog.domain.unit.EnergyKind
import com.fuelexpenselog.domain.unit.EnergyUnit

/**
 * Entity to domain, and back.
 *
 * The interesting part is not the field copying - it is the two decode policies, and what
 * happens when a value does not decode.
 *
 * A **category or type** falls back to OTHER: the row still opens and its cost still counts
 * toward the month, and the distinction lost is survivable.
 *
 * A **unit or currency** does not fall back, because there is no honest default. Guessing
 * litres for an unrecognised volume unit silently changes what every figure computed from
 * that row means. Instead the raw value is recorded in `unreadable`, a display-safe
 * substitute is used so the row is still visible, and the repository refuses to write it
 * back. The scenario is a backup from a newer version restored onto an older build; without
 * this, editing the note on such a row would permanently destroy the real value.
 */

/** Collects unrecognised values while decoding a single row. */
private class Unreadable {
    val found = mutableListOf<String>()

    fun <T : Enum<T>> strict(field: String, raw: String?, fallback: T, decoded: Decoded<T>): T =
        when (decoded) {
            is Decoded.Known -> decoded.value
            is Decoded.Unknown -> {
                found += "$field=${decoded.raw.ifEmpty { "null" }}"
                fallback
            }
        }

    fun currency(field: String, raw: String, fallback: String): String =
        if (raw.length == 3) raw else {
            found += "$field=$raw"
            fallback
        }
}

fun VehicleEntity.toDomain(): Vehicle {
    val bad = Unreadable()

    val volume = bad.strict("volumeUnit", volumeUnit, EnergyUnit.LITRE, decodeStrict(volumeUnit))
    return Vehicle(
        id = id,
        name = name,
        type = decodeOr(type, VehicleType.OTHER),
        fuelType = decodeOr(fuelType, FuelType.OTHER),
        distanceUnit = bad.strict("distanceUnit", distanceUnit, DistanceUnit.KILOMETRE, decodeStrict(distanceUnit)),
        // Guard the invariant the domain type asserts: a stored volume unit that decodes to
        // kWh would otherwise throw on construction and make the row unopenable.
        volumeUnit = if (volume.kind == EnergyKind.LIQUID) volume else EnergyUnit.LITRE,
        energyUnit = bad.strict("energyUnit", energyUnit, EnergyUnit.KWH, decodeStrict(energyUnit)),
        consumptionFormat = consumptionFormat?.let {
            (decodeStrict<ConsumptionFormat>(it) as? Decoded.Known)?.value
        },
        currencyCode = bad.currency("currencyCode", currencyCode, "USD"),
        tankCapacity = tankCapacityMicro?.let { Energy(EnergyKind.LIQUID, it) },
        batteryCapacity = batteryCapacityMicro?.let { Energy(EnergyKind.ELECTRIC, it) },
        defaultTag = decodeOr(defaultTag, EntryTag.PERSONAL),
        make = make,
        model = model,
        plate = plate,
        modelYear = modelYear,
        notes = notes,
        sortOrder = sortOrder,
        isArchived = isArchived,
        createdAtMillis = createdAtMillis,
        unreadable = bad.found,
    )
}

fun Vehicle.toEntity(): VehicleEntity = VehicleEntity(
    id = id,
    name = name,
    type = type.name,
    fuelType = fuelType.name,
    distanceUnit = distanceUnit.name,
    volumeUnit = volumeUnit.name,
    energyUnit = energyUnit.name,
    consumptionFormat = consumptionFormat?.name,
    currencyCode = currencyCode,
    tankCapacityMicro = tankCapacity?.micro,
    batteryCapacityMicro = batteryCapacity?.micro,
    defaultTag = defaultTag.name,
    make = make,
    model = model,
    plate = plate,
    modelYear = modelYear,
    notes = notes,
    sortOrder = sortOrder,
    isArchived = isArchived,
    createdAtMillis = createdAtMillis,
)

fun FillUpEntity.toDomain(): FillUp {
    val bad = Unreadable()

    val kind = bad.strict("energyKind", energyKind, EnergyKind.LIQUID, decodeStrict(energyKind))
    val entered = bad.strict("energyUnitEntered", energyUnitEntered, EnergyUnit.LITRE, decodeStrict(energyUnitEntered))
    val currency = bad.currency("currencyCode", currencyCode, "USD")

    return FillUp(
        id = id,
        vehicleId = vehicleId,
        date = CivilDate(occurredLocalDate),
        instantMillis = occurredAtMillis,
        odometerM = odometerM,
        energy = Energy(kind, energyMicro),
        energyUnitEntered = if (entered.kind == kind) entered else defaultUnitFor(kind),
        isFull = isFull,
        missedPrevious = missedPrevious,
        total = totalMicros?.let { Money(it, currency) },
        unitPrice = unitPriceMicros?.let { Money(it, currency) },
        tag = decodeOr(tag, EntryTag.PERSONAL),
        station = station,
        fuelGrade = fuelGrade,
        paymentMethod = paymentMethod,
        note = note,
        unreadable = bad.found,
    )
}

private fun defaultUnitFor(kind: EnergyKind) =
    if (kind == EnergyKind.LIQUID) EnergyUnit.LITRE else EnergyUnit.KWH

/** One import, as Settings lists it for undoing. */
data class ImportBatch(val id: Long, val source: String, val fileName: String, val importedAtMillis: Long, val rowCount: Int)

fun ImportBatchEntity.toDomain(): ImportBatch = ImportBatch(id, source, fileName, importedAtMillis, rowCount)

/**
 * Where an imported row came from: the source, the batch that undoes it, and its fingerprint
 * from the file. An edit keeps it, so "Undo import" still takes the row away and re-importing
 * the file still recognises it.
 */
data class ImportStamp(val source: String, val batchId: Long, val rowHash: String)

fun FillUpEntity.importStamp(): ImportStamp? =
    if (importSource != null && importBatchId != null && importRowHash != null) ImportStamp(importSource, importBatchId, importRowHash) else null

fun ExpenseEntity.importStamp(): ImportStamp? =
    if (importSource != null && importBatchId != null && importRowHash != null) ImportStamp(importSource, importBatchId, importRowHash) else null

fun FillUp.toEntity(nowMillis: Long, createdAtMillis: Long = nowMillis, import: ImportStamp? = null): FillUpEntity {
    val currency = total?.currency ?: unitPrice?.currency ?: "USD"
    require(total != null || unitPrice != null) {
        "A fill-up must carry a total or a unit price; the other is derived at display time."
    }
    return FillUpEntity(
        id = id,
        vehicleId = vehicleId,
        occurredLocalDate = date.value,
        occurredAtMillis = instantMillis,
        odometerM = odometerM,
        energyKind = energy.kind.name,
        energyMicro = energy.micro,
        energyUnitEntered = energyUnitEntered.name,
        isFull = isFull,
        missedPrevious = missedPrevious,
        totalMicros = total?.micros,
        unitPriceMicros = unitPrice?.micros,
        currencyCode = currency,
        tag = tag.name,
        station = station,
        fuelGrade = fuelGrade,
        paymentMethod = paymentMethod,
        note = note,
        importSource = import?.source,
        importBatchId = import?.batchId,
        importRowHash = import?.rowHash,
        createdAtMillis = createdAtMillis,
        updatedAtMillis = nowMillis,
    )
}

fun ExpenseEntity.toDomain(): Expense {
    val bad = Unreadable()
    val currency = bad.currency("currencyCode", currencyCode, "USD")

    return Expense(
        id = id,
        vehicleId = vehicleId,
        date = CivilDate(occurredLocalDate),
        instantMillis = occurredAtMillis,
        odometerM = odometerM,
        category = decodeOr(category, ExpenseCategory.OTHER),
        amount = Money(totalMicros, currency),
        tag = decodeOr(tag, EntryTag.PERSONAL),
        vendor = vendor,
        note = note,
        reminderId = reminderId,
        unreadable = bad.found,
    )
}

fun Expense.toEntity(nowMillis: Long, createdAtMillis: Long = nowMillis, import: ImportStamp? = null): ExpenseEntity = ExpenseEntity(
    id = id,
    vehicleId = vehicleId,
    occurredLocalDate = date.value,
    occurredAtMillis = instantMillis,
    odometerM = odometerM,
    category = category.name,
    totalMicros = amount.micros,
    currencyCode = amount.currency,
    tag = tag.name,
    vendor = vendor,
    note = note,
    reminderId = reminderId,
    importSource = import?.source,
    importBatchId = import?.batchId,
    importRowHash = import?.rowHash,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = nowMillis,
)

fun OdometerSegmentEntity.toDomain(): DeclaredSegment = DeclaredSegment(
    startsAt = CivilDate(startsAtLocalDate),
    reason = decodeOr(reason, SegmentReason.MANUAL_CORRECTION),
    offsetM = offsetM,
)
