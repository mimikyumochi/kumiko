package faith.mimikyumochi.core.testkit

import java.io.File

abstract class FixtureSuite(val dimension: String) {
    private val checks = linkedMapOf<String, (Row) -> Map<String, Any?>>()

    protected fun check(name: String, compute: (Row) -> Map<String, Any?>) {
        require(checks.put(name, compute) == null) { "check '$name' registered twice" }
    }

    protected fun <K, V> memo(build: (K) -> V): (K) -> V {
        val cache = HashMap<K, V>()
        return { key -> cache.getOrPut(key) { build(key) } }
    }

    fun load(name: String): Fixture {
        val path = "fixtures/$dimension/$name.txt"
        val text = javaClass.classLoader.getResource(path)?.readText()
            ?: throw AssertionError("missing fixture file $path")
        return Fixture.parse("$dimension/$name.txt", text)
    }

    fun discover(): Set<String> {
        val names = sortedSetOf<String>()
        for (url in javaClass.classLoader.getResources("fixtures/$dimension")) {
            require(url.protocol == "file") { "fixtures must be on disk to be discovered, got $url" }
            File(url.toURI()).listFiles { f -> f.isFile && f.extension == "txt" }
                ?.forEach { names += it.nameWithoutExtension }
        }
        return names
    }

    fun run(name: String) {
        val compute = checks[name] ?: throw AssertionError("no check registered for '$name'")
        val fixture = load(name)
        val failures = fixture.verify(compute)
        if (failures.isNotEmpty()) throw AssertionError(report(fixture, failures))
    }

    fun runAll() {
        val files = discover()
        val problems = mutableListOf<String>()

        (files - checks.keys).forEach { problems += "fixtures/$dimension/$it.txt has no check" }
        (checks.keys - files).forEach { problems += "check '$it' has no fixtures/$dimension/$it.txt" }

        for (name in checks.keys.filter { it in files }) {
            val fixture = load(name)
            val failures = fixture.verify(checks.getValue(name))
            println("${if (failures.isEmpty()) "PASS" else "FAIL"} $dimension/$name (${fixture.rows.size} rows)")
            if (failures.isNotEmpty()) problems += report(fixture, failures)
        }

        if (problems.isNotEmpty()) throw AssertionError(problems.joinToString("\n\n"))
    }

    private fun report(fixture: Fixture, failures: List<String>, limit: Int = 25): String {
        val shown = failures.take(limit).joinToString("\n") { "  $it" }
        val more = if (failures.size > limit) "\n  ... and ${failures.size - limit} more" else ""
        return "${fixture.name}: ${failures.size} mismatch(es) in ${fixture.rows.size} rows\n$shown$more"
    }
}
