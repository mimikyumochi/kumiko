package faith.mimikyumochi.core.vanilla

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

object VanillaAssets {
    private const val MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
    private val http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()

    private val KEPT = listOf(
        "assets/minecraft/textures/block/",
        "assets/minecraft/textures/item/",
        "assets/minecraft/textures/entity/",
        "assets/minecraft/textures/environment/",
        "data/minecraft/structure/end_city/",
    )

    val cacheDir = File(System.getProperty("user.home"), ".kumiko/assets")

    val default: File by lazy {
        cacheDir.listFiles { f -> f.isDirectory && !f.name.endsWith(".part") }?.maxByOrNull { it.lastModified() }
            ?: get(latestRelease())
    }

    fun read(path: String): ByteArray? = File(default, path).takeIf { it.isFile }?.readBytes()

    fun latestRelease(): String =
        fetchJson(MANIFEST)["latest"]!!.jsonObject["release"]!!.jsonPrimitive.content

    fun get(version: String): File {
        val dir = File(cacheDir, version)
        if (dir.isDirectory) return dir

        val entry = fetchJson(MANIFEST)["versions"]!!.jsonArray
            .map { it.jsonObject }
            .firstOrNull { it["id"]!!.jsonPrimitive.content == version }
            ?: error("unknown version $version")

        val client = fetchJson(entry["url"]!!.jsonPrimitive.content)["downloads"]!!
            .jsonObject["client"]!!.jsonObject
        val url = client["url"]!!.jsonPrimitive.content
        val expected = client["sha1"]!!.jsonPrimitive.content

        val tmp = File(cacheDir, "$version.part")
        tmp.deleteRecursively()
        tmp.mkdirs()
        try {
            val res = http.send(request(url), HttpResponse.BodyHandlers.ofInputStream())
            check(res.statusCode() == 200) { res.body().close(); "http ${res.statusCode()} for $url" }
            val md = MessageDigest.getInstance("SHA-1")
            DigestInputStream(res.body(), md).use { digest ->
                val zip = ZipInputStream(digest)
                val root = tmp.canonicalFile.toPath()
                while (true) {
                    val e = zip.nextEntry ?: break
                    if (e.isDirectory || KEPT.none { e.name.startsWith(it) }) continue
                    val out = File(tmp, e.name).canonicalFile
                    check(out.toPath().startsWith(root)) { "bad entry ${e.name}" }
                    out.parentFile.mkdirs()
                    out.outputStream().use { zip.copyTo(it) }
                }
                digest.readAllBytes()
            }
            val actual = md.digest().joinToString("") { "%02x".format(it) }
            check(actual == expected) { "sha1 mismatch for $version" }
            check(tmp.renameTo(dir)) { "could not move assets into place" }
        } catch (e: Exception) {
            tmp.deleteRecursively()
            throw e
        }
        return dir
    }

    private fun request(url: String) = HttpRequest.newBuilder(URI(url)).GET().build()

    private fun fetchJson(url: String): JsonObject {
        val res = http.send(request(url), HttpResponse.BodyHandlers.ofString())
        check(res.statusCode() == 200) { "http ${res.statusCode()} for $url" }
        return Json.parseToJsonElement(res.body()).jsonObject
    }
}
