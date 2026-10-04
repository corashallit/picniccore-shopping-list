package earth.cora.picnic.release

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.w3c.dom.Element
import java.io.File
import java.security.MessageDigest
import javax.xml.parsers.DocumentBuilderFactory

/*
 * What the release scans read (WP10, docs/DESIGN_STICKER.md §16, §17, §20.18, §20.20): the repository's sources and
 * resources as files, the way CI checks them out. Tests run with the app module as their working directory.
 */

/** One piece of text and where it came from ("strings_list.xml list_end_note", "ListFixture.kt:42"). */
data class Found(val where: String, val text: String) {
    override fun toString(): String = "$where: \"$text\""
}

object Repo {
    /** The repository root (the app module's parent). */
    val root: File = listOf(File(".."), File(".")).map { it.canonicalFile }.first { File(it, "settings.gradle.kts").isFile && File(it, "app").isDirectory }

    fun file(path: String): File = File(root, path)

    val appMain: File get() = file("app/src/main")
    val appTest: File get() = file("app/src/test")
    val coreMain: File get() = file("core/src/main")
    val coreTest: File get() = file("core/src/test")

    /** Every file under [dir] (recursively) whose extension is one of [extensions] (all files when empty). */
    fun files(dir: File, vararg extensions: String): List<File> =
        dir.walkTopDown().filter { it.isFile && (extensions.isEmpty() || it.extension in extensions) }.sortedBy { it.path }.toList()

    /** [file]'s path from the repository root. */
    fun rel(file: File): String = file.canonicalFile.relativeTo(root).path
}

/** The Kotlin sources' string literals, outside comments (a small lexer: comments, "…", """…""", '…' and templates). */
object KotlinText {
    /** A string literal of a Kotlin file: its 1-based line and its text, with `${…}` and `$name` templates as "{}". */
    data class Literal(val line: Int, val text: String)

    fun literals(file: File): List<Literal> = literals(file.readText())

    fun literals(src: String): List<Literal> {
        val out = ArrayList<Literal>()
        var i = 0
        var line = 1
        val n = src.length
        fun at(k: Int) = if (k < n) src[k] else '\u0000'
        while (i < n) {
            val c = src[i]
            when {
                c == '\n' -> { line++; i++ }
                c == '/' && at(i + 1) == '/' -> { while (i < n && src[i] != '\n') i++ }
                c == '/' && at(i + 1) == '*' -> {
                    // Kotlin block comments nest.
                    var depth = 0
                    while (i < n) {
                        if (src[i] == '/' && at(i + 1) == '*') { depth++; i += 2; continue }
                        if (src[i] == '*' && at(i + 1) == '/') { depth--; i += 2; if (depth == 0) break; continue }
                        if (src[i] == '\n') line++
                        i++
                    }
                }
                c == '\'' -> {
                    i++
                    while (i < n && src[i] != '\'') { if (src[i] == '\\') i++; i++ }
                    i++
                }
                c == '"' -> {
                    val raw = at(i + 1) == '"' && at(i + 2) == '"'
                    val startLine = line
                    val sb = StringBuilder()
                    i += if (raw) 3 else 1
                    while (i < n) {
                        val d = src[i]
                        if (raw && d == '"' && at(i + 1) == '"' && at(i + 2) == '"') {
                            // """…"""" ends at the last three quotes.
                            var end = i
                            while (at(end + 3) == '"') { sb.append('"'); end++ }
                            i = end + 3
                            break
                        }
                        if (!raw && d == '"') { i++; break }
                        if (!raw && d == '\\') {
                            val e = at(i + 1)
                            when (e) {
                                'n' -> sb.append('\n')
                                't' -> sb.append('\t')
                                'u' -> { sb.append(src.substring(i + 2, minOf(n, i + 6)).toIntOrNull(16)?.toChar() ?: '?'); i += 4 }
                                else -> sb.append(e)
                            }
                            i += 2
                            continue
                        }
                        if (d == '$' && at(i + 1) == '{') {
                            // A template expression: skip it (it may hold nested strings) and mark its place.
                            var depth = 0
                            i++
                            while (i < n) {
                                if (src[i] == '{') depth++
                                if (src[i] == '}') { depth--; if (depth == 0) { i++; break } }
                                if (src[i] == '\n') line++
                                i++
                            }
                            sb.append("{}")
                            continue
                        }
                        if (d == '$' && (at(i + 1).isLetter() || at(i + 1) == '_')) {
                            i++
                            while (i < n && (src[i].isLetterOrDigit() || src[i] == '_')) i++
                            sb.append("{}")
                            continue
                        }
                        if (d == '\n') line++
                        sb.append(d)
                        i++
                    }
                    out += Literal(startLine, sb.toString())
                }
                else -> i++
            }
        }
        return out
    }

    /** The code of [src] with comments removed (string literals kept), for API scans. */
    fun code(src: String): String {
        val sb = StringBuilder()
        var i = 0
        val n = src.length
        fun at(k: Int) = if (k < n) src[k] else '\u0000'
        while (i < n) {
            val c = src[i]
            when {
                c == '/' && at(i + 1) == '/' -> { while (i < n && src[i] != '\n') i++ }
                c == '/' && at(i + 1) == '*' -> {
                    var depth = 0
                    while (i < n) {
                        if (src[i] == '/' && at(i + 1) == '*') { depth++; i += 2; continue }
                        if (src[i] == '*' && at(i + 1) == '/') { depth--; i += 2; if (depth == 0) break; continue }
                        if (src[i] == '\n') sb.append('\n')
                        i++
                    }
                }
                c == '"' -> {
                    // Copy a string literal through untouched (a "//" inside it is not a comment).
                    val raw = at(i + 1) == '"' && at(i + 2) == '"'
                    if (raw) {
                        val end = src.indexOf("\"\"\"", i + 3).let { if (it < 0) n else it + 3 }
                        sb.append(src, i, end)
                        i = end
                    } else {
                        sb.append(c)
                        i++
                        while (i < n && src[i] != '"' && src[i] != '\n') {
                            if (src[i] == '\\') { sb.append(src[i]); i++ }
                            if (i < n) sb.append(src[i])
                            i++
                        }
                        if (i < n) { sb.append(src[i]); i++ }
                    }
                }
                else -> { sb.append(c); i++ }
            }
        }
        return sb.toString()
    }

    /** Words read as copy: a literal with a space between letters, or a sentence's end (prose, not a key or a tag). */
    fun isProse(text: String): Boolean = Regex("""\p{L}[\p{L}'’,.!?…]*\s+\S*\p{L}""").containsMatchIn(text)

    /**
     * The literals of [file] that can reach a person: not a developer's message (an exception's, a log line's, a
     * require/check/error message, an assertion's), which no screen shows.
     */
    fun copyLiterals(file: File): List<Literal> {
        val lines = file.readLines()
        val developer = Regex("""\b(error|require|requireNotNull|check|checkNotNull|assert\w*|println|TODO)\s*[({]|\w*(Exception|Error)\(|\bLog\.\w\(|\blog\w*\(""")
        return literals(file).filter { lit -> isProse(lit.text) && !developer.containsMatchIn(lines.getOrElse(lit.line - 1) { "" }) }
    }
}

/** Android string resources: every <string>, <plurals> item and <string-array> item of every values* folder. */
object StringResources {
    data class Entry(val file: String, val name: String, val text: String)

    val all: List<Entry> by lazy {
        val res = File(Repo.appMain, "res")
        res.listFiles().orEmpty().filter { it.isDirectory && it.name.startsWith("values") }.sortedBy { it.name }
            .flatMap { dir -> dir.listFiles().orEmpty().filter { it.extension == "xml" }.sortedBy { it.name }.flatMap { entries(it) } }
    }

    private fun entries(file: File): List<Entry> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val out = ArrayList<Entry>()
        val root = doc.documentElement
        val kids = root.childNodes
        for (k in 0 until kids.length) {
            val e = kids.item(k) as? Element ?: continue
            val name = e.getAttribute("name")
            when (e.tagName) {
                "string" -> out += Entry(file.name, name, unescape(e.textContent))
                "plurals", "string-array" -> {
                    val items = e.getElementsByTagName("item")
                    for (j in 0 until items.length) out += Entry(file.name, "$name[$j]", unescape(items.item(j).textContent))
                }
            }
        }
        return out
    }

    /** Android's string escapes (\' \" \n \t \@ \?) and its unquoted whitespace collapse. */
    fun unescape(s: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (val e = s[i + 1]) {
                    'n' -> sb.append('\n')
                    't' -> sb.append('\t')
                    'u' -> { sb.append(s.substring(i + 2, minOf(s.length, i + 6)).toIntOrNull(16)?.toChar() ?: '?'); i += 4 }
                    else -> sb.append(e)
                }
                i += 2
                continue
            }
            sb.append(c)
            i++
        }
        return sb.toString().replace(Regex("[ \t\r\n]+"), " ").trim().removeSurrounding("\"")
    }
}

/** The core's catalog (icon_catalog.json): its output side (labels) and its input side (aliases, draw hints). */
object Catalog {
    private val json: JsonObject by lazy { Json.parseToJsonElement(File(Repo.coreMain, "resources/icon_catalog.json").readText()).jsonObject }

    private fun JsonObject.str(key: String): String = this[key]?.jsonPrimitive?.content.orEmpty()

    /** Every label the app shows: item, category and basket labels. */
    val labels: List<Found> by lazy {
        val out = ArrayList<Found>()
        for (group in listOf("icons", "categories", "baskets")) {
            val arr = (json[group] as? kotlinx.serialization.json.JsonArray) ?: continue
            for (e in arr) {
                val o = e as? JsonObject ?: continue
                val label = o.str("label").ifEmpty { o.str("name") }
                if (label.isNotEmpty()) out += Found("icon_catalog.json $group ${o.str("key").ifEmpty { o.str("kind") }}", label)
            }
        }
        out
    }

    /** Every catalog key (a sticker's DSL name). */
    val keys: List<String> by lazy { json.getValue("icons").jsonArray.map { it.jsonObject.str("key") } }

    /** Every category key (a contact sheet's name in the goldens). */
    val categories: List<String> by lazy { json.getValue("categories").jsonArray.map { it.jsonObject.str("key") } }
}

/** The test sources whose data renders into the goldens and the screens' samples (§17: generic names only). */
object Fixtures {
    /** Fixture, sample and rig files: their names say so (ListFixture, SaturdayShop, AddFixtures, …). */
    val files: List<File> by lazy {
        (Repo.files(Repo.appTest, "kt") + Repo.files(Repo.coreTest, "kt") + Repo.files(Repo.appMain, "kt"))
            .filter { Regex("(Fixtures?|Samples|SaturdayShop|Rig)\\.kt$").containsMatchIn(it.name) }
    }

    val literals: List<Found> by lazy {
        files.flatMap { f -> KotlinText.literals(f).map { Found("${Repo.rel(f)}:${it.line}", it.text) } }
    }
}

/** Git's blob id of [bytes] (what `git hash-object` prints), so a scan can recognise a file's exact content. */
fun gitBlobId(bytes: ByteArray): String {
    val md = MessageDigest.getInstance("SHA-1")
    md.update("blob ${bytes.size}\u0000".toByteArray())
    md.update(bytes)
    return md.digest().joinToString("") { "%02x".format(it) }
}
