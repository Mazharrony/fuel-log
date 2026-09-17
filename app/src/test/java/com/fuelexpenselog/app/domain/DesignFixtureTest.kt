package com.fuelexpenselog.app.domain

import com.fuelexpenselog.app.domain.units.ConsumptionConvention
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/**
 * The design prototype renders the same vehicle in all four consumption
 * conventions, and publishes the figure for each. That makes it a free
 * end-to-end check on the conversion maths: if these four numbers agree, the
 * app and the design agree about what a given car actually does.
 *
 * Source: the unit-variant table in the design source, screens 05 and 07.
 *
 *   Last tank   MPG US 34.2 | MPG UK 41.1 | L/100km 6.9 | km/L 14.5
 *   Average     MPG US 33.6 | MPG UK 40.4 | L/100km 7.0 | km/L 14.3
 */
class DesignFixtureTest {

    private data class Variant(
        val name: String,
        val mpgUs: Double,
        val mpgUk: Double,
        val lPer100Km: Double,
        val kmPerL: Double,
    )

    private val variants = listOf(
        Variant("last tank", mpgUs = 34.2, mpgUk = 41.1, lPer100Km = 6.9, kmPerL = 14.5),
        Variant("average", mpgUs = 33.6, mpgUk = 40.4, lPer100Km = 7.0, kmPerL = 14.3),
    )

    @Test
    fun `every convention reproduces the design prototype figures`() {
        for (v in variants) {
            // Take the US figure as the source of truth and derive the rest.
            val kmPerLitre = ConsumptionConvention.MPG_US.toKmPerLitre(v.mpgUs)

            fun check(convention: ConsumptionConvention, expected: Double) {
                val actual = convention.fromKmPerLitre(kmPerLitre)
                assertWithMessage("${v.name} as $convention")
                    .that(actual).isWithin(0.05).of(expected)
            }

            check(ConsumptionConvention.MPG_US, v.mpgUs)
            check(ConsumptionConvention.MPG_UK, v.mpgUk)
            check(ConsumptionConvention.L_PER_100KM, v.lPer100Km)
            check(ConsumptionConvention.KM_PER_L, v.kmPerL)
        }
    }

    @Test
    fun `the design figures are internally consistent to one decimal place`() {
        // Guards against the design table itself drifting: if someone edits one
        // of the four numbers without recomputing the others, this fails.
        for (v in variants) {
            val fromUk = ConsumptionConvention.MPG_UK.toKmPerLitre(v.mpgUk)
            val fromMetric = ConsumptionConvention.L_PER_100KM.toKmPerLitre(v.lPer100Km)
            val fromKmPerL = ConsumptionConvention.KM_PER_L.toKmPerLitre(v.kmPerL)

            assertWithMessage("${v.name}: UK vs km/L")
                .that(fromUk).isWithin(0.05).of(fromKmPerL)
            assertWithMessage("${v.name}: L/100km vs km/L")
                .that(fromMetric).isWithin(0.05).of(fromKmPerL)
        }
    }
}
