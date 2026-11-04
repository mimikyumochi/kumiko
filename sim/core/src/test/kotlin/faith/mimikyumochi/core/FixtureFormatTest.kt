package faith.mimikyumochi.core

import faith.mimikyumochi.core.testkit.Fixture
import faith.mimikyumochi.core.testkit.FixtureFormatException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FixtureFormatTest {
    private val text = """
        # comment
        a   b  | sum   list
        1   2  | 3     1,2      # trailing comment
        2   2  | 5     *
        0   0  | 0     none
    """.trimIndent()

    private fun check(row: faith.mimikyumochi.core.testkit.Row): Map<String, Any?> {
        val a = row.int("a")
        val b = row.int("b")
        return mapOf("sum" to a + b, "list" to listOf(a, b).filter { it != 0 })
    }

    @Test
    fun parsesHeaderAndRows() {
        val f = Fixture.parse("t", text)
        assertEquals(listOf("a", "b"), f.inputs)
        assertEquals(listOf("sum", "list"), f.outputs)
        assertEquals(3, f.rows.size)
        assertEquals(3, f.rows[0].line)
    }

    @Test
    fun reportsOnlyRealMismatches() {
        val failures = Fixture.parse("t", text).verify(::check)
        assertEquals(1, failures.size, failures.toString())
        assertTrue(failures[0].startsWith("t:4: sum expected 5, got 4"), failures[0])
    }

    @Test
    fun rejectsMalformedFiles() {
        assertFailsWith<FixtureFormatException> { Fixture.parse("t", "a b c\n1 2 3") }
        assertFailsWith<FixtureFormatException> { Fixture.parse("t", "a | b\n1 2 | 3") }
        assertFailsWith<FixtureFormatException> { Fixture.parse("t", "a | a\n1 | 1") }
        assertFailsWith<FixtureFormatException> {
            Fixture.parse("t", "a | b\n1 | 1").verify { mapOf("typo" to 1) }
        }
    }

    @Test
    fun formatsLikeTheFiles() {
        assertEquals("none", Fixture.format(null))
        assertEquals("none", Fixture.format(emptyList<Int>()))
        assertEquals("3,1,4", Fixture.format(intArrayOf(3, 1, 4)))
        assertEquals("true", Fixture.format(true))
    }
}
