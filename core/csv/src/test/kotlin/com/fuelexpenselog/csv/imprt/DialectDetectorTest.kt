package com.fuelexpenselog.csv.imprt

import com.fuelexpenselog.csv.CharsetSniffer
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The confusion matrix: every fixture is detected as its own app, and scores that app
 * highest. Files that belong to no app fall through to Generic rather than to a near miss.
 */
class DialectDetectorTest {

    private fun detected(name: String) = Fixtures.plan(name).dialect

    @Test
    fun `each fixture is detected as its own app`() {
        assertThat(detected("fuelio.csv")).isEqualTo(DialectId.FUELIO)
        assertThat(detected("acar.csv")).isEqualTo(DialectId.ACAR)
        assertThat(detected("drivvo.csv")).isEqualTo(DialectId.DRIVVO)
        assertThat(detected("generic.csv")).isEqualTo(DialectId.GENERIC)
        assertThat(detected("german-semicolon.csv")).isEqualTo(DialectId.GENERIC)

        val (fillUps, expenses) = Fixtures.history(vehicleId = 1)
        val own = ImportPlanner.plan(Fixtures.ownExport(listOf(Fixtures.vehicle(id = 1)), fillUps, expenses))
        assertThat(own.dialect).isEqualTo(DialectId.FUEL_LOG)
    }

    @Test
    fun `every app's fixture scores its own dialect highest and the others nothing`() {
        val expected = mapOf(
            "fuelio.csv" to DialectId.FUELIO,
            "acar.csv" to DialectId.ACAR,
            "drivvo.csv" to DialectId.DRIVVO,
        )
        for ((name, dialect) in expected) {
            val (document, _) = ImportPlanner.read(CharsetSniffer.decode(Fixtures.bytes(name)).text)
            val scores = DialectDetector.scores(document)
            assertThat(scores.first().dialect).isEqualTo(dialect)
            assertThat(scores.first().score).isAtLeast(DialectDetector.THRESHOLD)
            assertThat(scores.drop(1).map { it.score }.toSet()).containsExactly(0)
        }
    }

    @Test
    fun `a file with the right columns but no app's signature is generic, not a guess`() {
        // Drivvo's English aliases fit this header, but nothing only Drivvo writes is in it.
        val (document, _) = ImportPlanner.read("Date,Odometer,Liters,Total cost\n2026-01-01,100,40,60\n")
        assertThat(DialectDetector.detect(document)).isEqualTo(DialectId.GENERIC)
    }

    @Test
    fun `the generic columns are guessed from aliases in several languages`() {
        val german = Fixtures.plan("german-semicolon.csv")
        val mapping = german.tables.single().mapping
        assertThat(german.header.withIndex().associate { it.value to it.index }).containsAtLeast("Datum", mapping[Field.DATE], "Liter", mapping[Field.VOLUME])
        assertThat(mapping.keys).containsAtLeast(Field.DATE, Field.ODOMETER, Field.VOLUME, Field.TOTAL, Field.FULL, Field.STATION, Field.NOTE)
        assertThat(german.encoding).isEqualTo(CharsetSniffer.Encoding.WINDOWS_1252)
        assertThat(german.delimiter).isEqualTo(';')
    }

    @Test
    fun `units stated in headers are read, and a header that states none leaves the vehicle's`() {
        val acar = Fixtures.plan("acar.csv")
        assertThat(acar.distanceUnit).isEqualTo(com.fuelexpenselog.domain.unit.DistanceUnit.MILE)
        assertThat(acar.volumeUnit).isEqualTo(com.fuelexpenselog.domain.unit.EnergyUnit.US_GALLON)

        val fuelio = Fixtures.plan("fuelio.csv")
        assertThat(fuelio.distanceUnit).isEqualTo(com.fuelexpenselog.domain.unit.DistanceUnit.KILOMETRE)
        assertThat(fuelio.volumeUnit).isEqualTo(com.fuelexpenselog.domain.unit.EnergyUnit.LITRE)

        assertThat(Fixtures.plan("drivvo.csv").distanceUnit).isNull()
    }
}
