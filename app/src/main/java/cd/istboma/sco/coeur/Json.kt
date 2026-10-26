package cd.istboma.sco.coeur

// JSON minimal en Kotlin pur (aucune bibliothèque) : le même code tourne sur
// Android et sur un PC (tests). Objets = LinkedHashMap<String, Any?>,
// tableaux = List<Any?>, nombres = Long ou Double, texte = String, null.

object Json {
    fun lire(texte: String): Any? {
        val l = Lecteur(texte)
        l.espaces()
        val v = l.valeur()
        l.espaces()
        if (l.i != texte.length) throw IllegalArgumentException("JSON : texte en trop à la position ${l.i}")
        return v
    }

    fun objet(texte: String): Map<String, Any?> =
        lire(texte) as? Map<String, Any?> ?: throw IllegalArgumentException("JSON : objet attendu")

    fun ecrire(v: Any?): String = StringBuilder().also { ecrire(it, v) }.toString()

    private fun ecrire(sb: StringBuilder, v: Any?) {
        when (v) {
            null -> sb.append("null")
            is String -> chaine(sb, v)
            is Boolean -> sb.append(if (v) "true" else "false")
            is Int, is Long, is Short, is Byte -> sb.append(v.toString())
            is Double -> if (v == Math.floor(v) && !v.isInfinite() && Math.abs(v) < 1e15) sb.append(v.toLong().toString()) else sb.append(v.toString())
            is Float -> ecrire(sb, v.toDouble())
            is Map<*, *> -> {
                sb.append('{')
                var premier = true
                for ((k, x) in v) {
                    if (!premier) sb.append(',')
                    premier = false
                    chaine(sb, k.toString())
                    sb.append(':')
                    ecrire(sb, x)
                }
                sb.append('}')
            }
            is Iterable<*> -> {
                sb.append('[')
                var premier = true
                for (x in v) {
                    if (!premier) sb.append(',')
                    premier = false
                    ecrire(sb, x)
                }
                sb.append(']')
            }
            is Array<*> -> ecrire(sb, v.toList())
            else -> chaine(sb, v.toString())
        }
    }

    private fun chaine(sb: StringBuilder, s: String) {
        sb.append('"')
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                else -> if (c < ' ' || c == ' ' || c == ' ') sb.append(String.format("\\u%04x", c.code)) else sb.append(c)
            }
        }
        sb.append('"')
    }

    private class Lecteur(val s: String) {
        var i = 0
        fun espaces() { while (i < s.length && s[i].isWhitespace()) i++ }
        fun erreur(m: String): Nothing = throw IllegalArgumentException("JSON : $m (position $i)")
        fun valeur(): Any? {
            if (i >= s.length) erreur("fin inattendue")
            return when (s[i]) {
                '{' -> objet()
                '[' -> tableau()
                '"' -> chaine()
                't' -> mot("true", true)
                'f' -> mot("false", false)
                'n' -> mot("null", null)
                else -> nombre()
            }
        }
        fun mot(m: String, v: Any?): Any? {
            if (!s.startsWith(m, i)) erreur("valeur inconnue")
            i += m.length
            return v
        }
        fun objet(): Map<String, Any?> {
            val m = LinkedHashMap<String, Any?>()
            i++
            espaces()
            if (i < s.length && s[i] == '}') { i++; return m }
            while (true) {
                espaces()
                if (i >= s.length || s[i] != '"') erreur("clé attendue")
                val k = chaine()
                espaces()
                if (i >= s.length || s[i] != ':') erreur("« : » attendu")
                i++
                espaces()
                m[k] = valeur()
                espaces()
                if (i >= s.length) erreur("fin inattendue")
                when (s[i]) {
                    ',' -> i++
                    '}' -> { i++; return m }
                    else -> erreur("« , » ou « } » attendu")
                }
            }
        }
        fun tableau(): List<Any?> {
            val l = ArrayList<Any?>()
            i++
            espaces()
            if (i < s.length && s[i] == ']') { i++; return l }
            while (true) {
                espaces()
                l.add(valeur())
                espaces()
                if (i >= s.length) erreur("fin inattendue")
                when (s[i]) {
                    ',' -> i++
                    ']' -> { i++; return l }
                    else -> erreur("« , » ou « ] » attendu")
                }
            }
        }
        fun chaine(): String {
            i++
            val sb = StringBuilder()
            while (true) {
                if (i >= s.length) erreur("texte non terminé")
                val c = s[i++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (i >= s.length) erreur("échappement incomplet")
                        when (val e = s[i++]) {
                            '"' -> sb.append('"'); '\\' -> sb.append('\\'); '/' -> sb.append('/')
                            'b' -> sb.append('\b'); 'f' -> sb.append('\u000C'); 'n' -> sb.append('\n')
                            'r' -> sb.append('\r'); 't' -> sb.append('\t')
                            'u' -> {
                                if (i + 4 > s.length) erreur("\\u incomplet")
                                sb.append(s.substring(i, i + 4).toInt(16).toChar())
                                i += 4
                            }
                            else -> erreur("échappement inconnu \\$e")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }
        fun nombre(): Any {
            val d = i
            if (i < s.length && s[i] == '-') i++
            while (i < s.length && (s[i].isDigit() || s[i] in ".eE+-")) i++
            val t = s.substring(d, i)
            if (t.isEmpty() || t == "-") erreur("nombre attendu")
            return if (t.any { it in ".eE" }) t.toDouble() else t.toLongOrNull() ?: t.toDouble()
        }
    }
}

// Lecture pratique des objets JSON.
@Suppress("UNCHECKED_CAST")
fun Map<String, Any?>.obj(k: String): Map<String, Any?> = this[k] as? Map<String, Any?> ?: emptyMap()
@Suppress("UNCHECKED_CAST")
fun Map<String, Any?>.liste(k: String): List<Map<String, Any?>> = (this[k] as? List<Any?>)?.mapNotNull { it as? Map<String, Any?> } ?: emptyList()
fun Map<String, Any?>.texte(k: String): String = this[k]?.let { if (it is Double && it == Math.floor(it)) it.toLong().toString() else it.toString() } ?: ""
fun Map<String, Any?>.long(k: String): Long = when (val v = this[k]) { is Long -> v; is Int -> v.toLong(); is Double -> v.toLong(); is String -> v.toLongOrNull() ?: 0L; else -> 0L }
fun Map<String, Any?>.int(k: String): Int = long(k).toInt()
fun Map<String, Any?>.bool(k: String): Boolean = this[k] == true
fun Map<String, Any?>.textes(k: String): List<String> = (this[k] as? List<*>)?.map { it.toString() } ?: emptyList()
