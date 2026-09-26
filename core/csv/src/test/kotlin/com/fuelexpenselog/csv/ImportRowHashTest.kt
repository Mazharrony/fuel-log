package com.fuelexpenselog.csv

import com.fuelexpenselog.domain.model.ExpenseCategory
import com.fuelexpenselog.domain.time.CivilDate
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Golden values computed outside this code (Python's hashlib over the same canonical
 * strings). If one changes, every row already imported stops matching its re-import.
 */
class ImportRowHashTest {

    private val date = CivilDate.of(2026, 9, 17)

    @Test
    fun `a fill-up hashes to its golden value`() {
        assertThat(ImportRowHash.fillUp(1, date, 48_700_000, 41_500_000, 62_000_000))
            .isEqualTo("8ccc95a3bd04f191a0c79fcc36781adf3537583d867b0742df137ab968a9bc50")
    }

    @Test
    fun `missing odometer and total hash as empty, not as zero`() {
        assertThat(ImportRowHash.fillUp(1, date, null, 41_500_000, null))
            .isEqualTo("b8baea9c82180670fb94552106a2e81f0d94e10d5e5b111c373f74004bd9821a")
        assertThat(ImportRowHash.fillUp(1, date, null, 41_500_000, null))
            .isNotEqualTo(ImportRowHash.fillUp(1, date, 0, 41_500_000, 0))
    }

    @Test
    fun `an expense hashes to its golden value`() {
        assertThat(ImportRowHash.expense(1, date, null, ExpenseCategory.OIL_CHANGE, 89_900_000))
            .isEqualTo("69e26258270d3d999c7d7ef2bd27bfa862a455082663c776cae4b007f1c174b6")
    }
}
