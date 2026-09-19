package com.fuelexpenselog.domain.unit

/**
 * How a consumption figure is written. Every conversion routes through a single pivot -
 * kilometres per canonical unit (per litre, or per kWh) - and every factor is DERIVED from
 * the constants in [Canonical] rather than hard-coded as 235.21 or 282.48.
 *
 * That matters because it means there is exactly one place in the app a unit can be wrong.
 * A hard-coded 235.215 is a number nobody can check by reading it.
 */
enum class ConsumptionFormat(
    val kind: EnergyKind,
    /**
     * Not cosmetic. L/100km and kWh/100km FALL as efficiency rises while every other format
     * climbs, so the month-on-month delta arrow and its improved/worsened colour must invert
     * for those two. Getting this backwards is the classic bug in this category of app: the
     * app cheerfully congratulates you for using more fuel.
     */
    val lowerIsBetter: Boolean,
) {
    L_PER_100KM(EnergyKind.LIQUID, lowerIsBetter = true),
    KM_PER_L(EnergyKind.LIQUID, lowerIsBetter = false),
    MPG_US(EnergyKind.LIQUID, lowerIsBetter = false),
    MPG_UK(EnergyKind.LIQUID, lowerIsBetter = false),

    KWH_PER_100KM(EnergyKind.ELECTRIC, lowerIsBetter = true),
    KM_PER_KWH(EnergyKind.ELECTRIC, lowerIsBetter = false),
    MI_PER_KWH(EnergyKind.ELECTRIC, lowerIsBetter = false),
    ;

    fun fromKmPerUnit(kmPerUnit: Double): Double = when (this) {
        L_PER_100KM, KWH_PER_100KM -> 100.0 / kmPerUnit
        KM_PER_L, KM_PER_KWH -> kmPerUnit
        MPG_US -> kmPerUnit * LITRES_PER_US_GALLON / KM_PER_MILE
        MPG_UK -> kmPerUnit * LITRES_PER_IMP_GALLON / KM_PER_MILE
        MI_PER_KWH -> kmPerUnit / KM_PER_MILE
    }

    fun toKmPerUnit(shown: Double): Double = when (this) {
        L_PER_100KM, KWH_PER_100KM -> 100.0 / shown
        KM_PER_L, KM_PER_KWH -> shown
        MPG_US -> shown * KM_PER_MILE / LITRES_PER_US_GALLON
        MPG_UK -> shown * KM_PER_MILE / LITRES_PER_IMP_GALLON
        MI_PER_KWH -> shown * KM_PER_MILE
    }

    /**
     * The figure for a measured span, or **null when it is not meaningful**. Null renders as
     * a dash: the app saying it does not know, rather than a zero, which would be a lie.
     */
    fun of(distanceM: Long, energy: Energy): Double? {
        require(energy.kind == kind) { "$this cannot express a ${energy.kind} figure" }

        val kmPerUnit = kmPerUnit(distanceM, energy) ?: return null
        val shown = fromKmPerUnit(kmPerUnit)
        return shown.takeIf { it.isFinite() && it > 0.0 }
    }

    companion object {
        private const val KM_PER_MILE = Canonical.METRES_PER_MILE / 1000.0
        private const val LITRES_PER_US_GALLON =
            Canonical.MICROLITRES_PER_US_GALLON / Canonical.MICRO_PER_LITRE
        private const val LITRES_PER_IMP_GALLON =
            Canonical.MICROLITRES_PER_IMP_GALLON / Canonical.MICRO_PER_LITRE

        /**
         * The pivot: kilometres per litre, or kilometres per kWh. Both canonical units are
         * 1e6 micro, so one expression serves both kinds.
         */
        fun kmPerUnit(distanceM: Long, energy: Energy): Double? {
            if (distanceM <= 0L || energy.micro <= 0L) return null

            val km = distanceM / 1000.0
            val units = energy.micro / Canonical.MICRO_PER_LITRE
            return (km / units).takeIf { it.isFinite() && it > 0.0 }
        }

        fun forKind(kind: EnergyKind): List<ConsumptionFormat> = entries.filter { it.kind == kind }

        val defaultFor: (EnergyKind) -> ConsumptionFormat = { kind ->
            when (kind) {
                EnergyKind.LIQUID -> L_PER_100KM
                EnergyKind.ELECTRIC -> KWH_PER_100KM
            }
        }
    }
}

/**
 * Bounds for flagging a figure as odd.
 *
 * An out-of-range figure is still SHOWN, just marked. Suppressing a number reads as a bug,
 * whereas flagging one reads as the app paying attention - and the user is the only one who
 * knows whether they really did tow a caravan up a mountain that week.
 */
object Plausibility {
    const val MIN_KM_PER_LITRE = 1.0
    const val MAX_KM_PER_LITRE = 100.0

    const val MIN_KM_PER_KWH = 0.5
    const val MAX_KM_PER_KWH = 20.0

    fun isPlausible(kmPerUnit: Double, kind: EnergyKind): Boolean {
        if (!kmPerUnit.isFinite()) return false
        return when (kind) {
            EnergyKind.LIQUID -> kmPerUnit in MIN_KM_PER_LITRE..MAX_KM_PER_LITRE
            EnergyKind.ELECTRIC -> kmPerUnit in MIN_KM_PER_KWH..MAX_KM_PER_KWH
        }
    }
}
