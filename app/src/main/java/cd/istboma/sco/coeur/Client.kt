package cd.istboma.sco.coeur

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

// Dialogue avec le serveur PEGSCO (réseau local, HTTP). Chaque envoi est
// signé avec la clé du téléphone (HMAC-SHA256) : un contenu modifié en route
// est refusé par le serveur.

fun signer(cle: String, contenu: ByteArray): String {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(cle.toByteArray(Charsets.UTF_8), "HmacSHA256"))
    return mac.doFinal(contenu).joinToString("") { "%02x".format(it) }
}

/** Erreur renvoyée par le serveur (statut HTTP + message lisible). */
class ErreurServeur(val statut: Int, message: String) : Exception(message)

/** Serveur injoignable (Wi-Fi coupé, PC éteint, mauvaise adresse…). */
class ErreurReseau(message: String) : Exception(message)

interface Transport {
    /** Retourne (statut HTTP, corps). Lève IOException si le serveur est injoignable. */
    fun requete(methode: String, url: String, corps: ByteArray?, entetes: Map<String, String>, delaiMs: Int): Pair<Int, String>
}

object TransportHttp : Transport {
    override fun requete(methode: String, url: String, corps: ByteArray?, entetes: Map<String, String>, delaiMs: Int): Pair<Int, String> {
        val c = URI(url).toURL().openConnection() as HttpURLConnection
        try {
            c.requestMethod = methode
            c.connectTimeout = delaiMs
            c.readTimeout = delaiMs * 3
            c.useCaches = false
            c.setRequestProperty("Accept", "application/json")
            for ((k, v) in entetes) c.setRequestProperty(k, v)
            if (corps != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                c.setFixedLengthStreamingMode(corps.size)
                c.outputStream.use { it.write(corps) }
            }
            val statut = c.responseCode
            val flux = if (statut >= 400) c.errorStream else c.inputStream
            val texte = flux?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
            return statut to texte
        } finally {
            c.disconnect()
        }
    }
}

class Client(adresse: String, private val transport: Transport = TransportHttp, private val delaiMs: Int = 6000) {
    val base: String = normaliserAdresse(adresse)

    private fun appel(methode: String, chemin: String, corps: ByteArray? = null, jeton: String = "", cle: String = ""): Map<String, Any?> {
        val ent = LinkedHashMap<String, String>()
        if (jeton.isNotEmpty()) ent["Authorization"] = "Bearer $jeton"
        if (cle.isNotEmpty() && corps != null) ent["X-Signature"] = signer(cle, corps)
        val (statut, texte) = try {
            transport.requete(methode, base + chemin, corps, ent, delaiMs)
        } catch (e: IOException) {
            throw ErreurReseau("Serveur injoignable ($base). Vérifiez le Wi-Fi et que le PC PEGSCO est allumé.")
        }
        val json = try { Json.lire(texte) } catch (e: Exception) { null }
        @Suppress("UNCHECKED_CAST")
        val m = json as? Map<String, Any?> ?: emptyMap()
        if (statut !in 200..299) throw ErreurServeur(statut, m.texte("erreur").ifEmpty { "Erreur du serveur ($statut)" })
        if (json !is Map<*, *>) throw ErreurServeur(statut, "Ce serveur ne répond pas comme PEGSCO : vérifiez l'adresse.")
        return m
    }

    fun ping(): Map<String, Any?> {
        val r = appel("GET", "/mobile/ping")
        if (r.texte("logiciel") != "PEGSCO") throw ErreurServeur(200, "Ce serveur n'est pas PEGSCO : vérifiez l'adresse.")
        return r
    }

    fun connexion(code: String, appareilUid: String, appareilNom: String): Map<String, Any?> =
        appel("POST", "/mobile/connexion", Json.ecrire(mapOf("code" to code, "appareil_uid" to appareilUid, "appareil_nom" to appareilNom)).toByteArray(Charsets.UTF_8))

    fun donnees(jeton: String): Map<String, Any?> = appel("GET", "/mobile/donnees", jeton = jeton)

    fun envoi(jeton: String, cle: String, corps: ByteArray): Map<String, Any?> = appel("POST", "/mobile/envoi", corps, jeton, cle)

    companion object {
        /** « 192.168.1.10 » → « http://192.168.1.10:8100 » ; garde le port et le chemin éventuels. */
        fun normaliserAdresse(a: String): String {
            var s = a.trim().trimEnd('/')
            if (s.isEmpty()) throw ErreurApp("Saisissez l'adresse du serveur PEGSCO (ex. 192.168.1.10:8100).")
            if (s.startsWith("https://", true)) throw ErreurApp("Utilisez http:// (réseau local de l'Institut), pas https://.")
            if (!s.startsWith("http://", true)) s = "http://$s"
            val hote = s.removePrefix("http://").removePrefix("HTTP://").substringBefore('/')
            if (hote.isBlank() || hote.contains(' ')) throw ErreurApp("Adresse du serveur invalide.")
            if (!hote.contains(':')) s = s.replaceFirst(hote, "$hote:8100")
            return s
        }
    }
}
