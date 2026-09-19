package com.fuelexpenselog.domain.unit

import kotlin.math.roundToLong

/**
 * Canonical storage for the whole app.
 *
 * Distance is **metres**, energy is **micro-units**, money is **micros**. All Long, no
 * Double, no `REAL` column anywhere. Integers make comparisons exact, indexed range scans
 * exact, and CSV round-trips lossless - none of which is true once a float is involved in
 * the middle of an odometer chain.
 *
 * Conversion happens only at the input and display edges, never in between. Changing a
 * vehicle's unit changes what is shown; it never rewrites what is stored.
 */
object Canonical {
    /** Exact by definition: the international mile is 1609.344 m. */
    const val METRES_PER_MILE: Double = 1609.344

    const val MICRO_PER_LITRE: Double = 1_000_000.0

    /**
     * Exact by definition. A US gallon is 231 cubic inches and an inch is 2.54 cm, so
     * 231 x 2.54^3 = 3785.411784 cm3 = 3.785411784 L.
     */
    const val MICROLITRES_PER_US_GALLON: Double = 3_785_411.784

    /** Exact by definition: the imperial gallon is 4.54609 L. */
    const val MICROLITRES_PER_IMP_GALLON: Double = 4_546_090.0

    const val MICRO_PER_KWH: Double = 1_000_000.0

    /** Money is stored to 1e-6, not to the minor unit: fuel is priced to three decimals. */
    const val MICROS_PER_MAJOR_UNIT: Double = 1_000_000.0
}

enum class EnergyKind {
    /** Petrol, diesel, LPG, CNG - anything measured as a volume. */
    LIQUID,

    /** Measured in kWh. Not exposed in the v1 UI, but the schema and engine already carry it. */
    ELECTRIC,
}

/**
 * What the user typed their fill-up in. Stored alongside the canonical amount so an edit
 * shows back exactly what they entered rather than a converted approximation of it.
 */
enum class EnergyUnit(
    val kind: EnergyKind,
    val microPerUnit: Double,
) {
    LITRE(EnergyKind.LIQUID, Canonical.MICRO_PER_LITRE),
    US_GALLON(EnergyKind.LIQUID, Canonical.MICROLITRES_PER_US_GALLON),
    IMP_GALLON(EnergyKind.LIQUID, Canonical.MICROLITRES_PER_IMP_GALLON),
    KWH(EnergyKind.ELECTRIC, Canonical.MICRO_PER_KWH),
    ;

    fun toMicro(display: Double): Long = (display * microPerUnit).roundToLong()

    fun fromMicro(micro: Long): Double = micro / microPerUnit

    companion object {
        val liquid: List<EnergyUnit> get() = entries.filter { it.kind == EnergyKind.LIQUID }
    }
}

enum class DistanceUnit(val metresPerUnit: Double) {
    KILOMETRE(1000.0),
    MILE(Canonical.METRES_PER_MILE),
    ;

    fun toMetres(display: Double): Long = (display * metresPerUnit).roundToLong()

    fun fromMetres(metres: Long): Double = metres / metresPerUnit
}

/**
 * An energy amount that cannot be misread.
 *
 * The storage column is a bare Long whose unit depends on a sibling column, which is the one
 * genuinely awkward consequence of making the table EV-ready. This type is the mitigation:
 * no bare Long crosses a module boundary, so "micro-what?" is never a question the reader
 * has to answer from context.
 */
data class Energy(val kind: EnergyKind, val micro: Long) {
    init {
        require(micro >= 0) { "Energy cannot be negative: $micro" }
    }

    operator fun plus(other: Energy): Energy {
        require(kind == other.kind) {
            "Refusing to add $kind to ${other.kind}: litres and kWh are not the same quantity"
        }
        return Energy(kind, micro + other.micro)
    }

    fun inUnit(unit: EnergyUnit): Double {
        require(unit.kind == kind) { "$unit cannot express a $kind amount" }
        return unit.fromMicro(micro)
    }

    val isZero: Boolean get() = micro == 0L

    companion object {
        fun of(unit: EnergyUnit, display: Double) = Energy(unit.kind, unit.toMicro(display))

        fun zero(kind: EnergyKind) = Energy(kind, 0L)
    }
}
