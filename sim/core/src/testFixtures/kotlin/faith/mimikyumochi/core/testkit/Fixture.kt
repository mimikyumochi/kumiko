package faith.mimikyumochi.core.testkit

import faith.mimikyumochi.core.version.MCVersion

class Fixture(
    val name: String,
    val inputs: List<String>,
    val outputs: List<String>,
    val rows: List<Row>,
) {
    companion object {
        const val SKIP = "*"
        const val NONE = "none"

        fun parse(name: String, text: String): Fixture {
            var inputs: List<String>? = null
            var outputs: List<String> = emptyList()
            val rows = mutableListOf<Row>()

            text.lineSequence().forEachIndexed { index, raw ->
                val line = raw.substringBefore('#').trim()
                if (line.isEmpty()) return@forEachIndexed
                val lineNo = index + 1

                val parts = line.split('|')
                if (parts.size > 2) throw FixtureFormatException(name, lineNo, "more than one '|'")
                val left = parts[0].trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
                val right = parts.getOrNull(1)?.trim()?.split(Regex("\\s+"))?.filter { it.isNotEmpty() } ?: emptyList()

                if (inputs == null) {
                    if (parts.size != 2) throw FixtureFormatException(name, lineNo, "header needs 'inputs | outputs'")
                    val all = left + right
                    val dup = all.groupBy { it }.filter { it.value.size > 1 }.keys
                    if (dup.isNotEmpty()) throw FixtureFormatException(name, lineNo, "duplicate column(s) $dup")
                    if (right.isEmpty()) throw FixtureFormatException(name, lineNo, "header has no output columns")
                    inputs = left
                    outputs = right
                    return@forEachIndexed
                }

                val ins = inputs
                if (parts.size != 2 || left.size != ins.size || right.size != outputs.size) {
                    throw FixtureFormatException(
                        name, lineNo,
                        "expected ${ins.size} inputs | ${outputs.size} outputs, got ${left.size} | ${right.size}"
                    )
                }
                rows += Row(name, lineNo, (ins.zip(left) + outputs.zip(right)).toMap())
            }

            return Fixture(name, inputs ?: throw FixtureFormatException(name, 0, "no header"), outputs, rows)
        }

        fun format(value: Any?): String = when (value) {
            null -> NONE
            is IntArray -> if (value.isEmpty()) NONE else value.joinToString(",")
            is LongArray -> if (value.isEmpty()) NONE else value.joinToString(",")
            is Collection<*> -> if (value.isEmpty()) NONE else value.joinToString(",") { format(it) }
            is Enum<*> -> value.name
            else -> value.toString()
        }
    }

    fun verify(compute: (Row) -> Map<String, Any?>): List<String> {
        val failures = mutableListOf<String>()
        for (row in rows) {
            val actual = try {
                compute(row)
            } catch (e: Throwable) {
                if (e is FixtureFormatException) throw e
                failures += "${row.where}: threw ${e::class.simpleName}: ${e.message}  [${row.describeInputs(inputs)}]"
                continue
            }

            val unknown = actual.keys - outputs.toSet()
            if (unknown.isNotEmpty()) {
                throw FixtureFormatException(name, row.line, "check produced column(s) $unknown that the file doesn't have")
            }
            for (column in outputs) {
                val expected = row.string(column)
                if (expected == SKIP) continue
                if (column !in actual) {
                    failures += "${row.where}: '$column' was not produced by the check"
                    continue
                }
                val got = format(actual[column])
                if (got != expected) {
                    failures += "${row.where}: $column expected $expected, got $got  [${row.describeInputs(inputs)}]"
                }
            }
        }
        return failures
    }
}

class Row(val fixture: String, val line: Int, private val values: Map<String, String>) {
    val where get() = "$fixture:$line"

    fun string(column: String): String =
        values[column] ?: throw FixtureFormatException(fixture, line, "no column '$column'")

    fun int(column: String): Int = parse(column, String::toIntOrNull)
    fun long(column: String): Long = parse(column, String::toLongOrNull)
    fun double(column: String): Double = parse(column, String::toDoubleOrNull)
    fun float(column: String): Float = parse(column, String::toFloatOrNull)
    fun bool(column: String): Boolean = parse(column, String::toBooleanStrictOrNull)

    fun version(column: String = "version"): MCVersion {
        val raw = string(column)
        if (raw.equals("latest", ignoreCase = true)) return MCVersion.LATEST
        val name = "V" + raw.removePrefix("V").removePrefix("v").replace('.', '_')
        return MCVersion.entries.firstOrNull { it.name == name }
            ?: throw FixtureFormatException(fixture, line, "unknown version '$raw' (known: ${MCVersion.entries})")
    }

    internal fun describeInputs(inputs: List<String>) = inputs.joinToString(" ") { "$it=${values[it]}" }

    private fun <T> parse(column: String, parser: (String) -> T?): T {
        val raw = string(column)
        return parser(raw) ?: throw FixtureFormatException(fixture, line, "column '$column': can't parse '$raw'")
    }
}

class FixtureFormatException(fixture: String, line: Int, message: String) :
    IllegalArgumentException("$fixture:$line: $message")
