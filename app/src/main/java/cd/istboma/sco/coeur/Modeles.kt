package cd.istboma.sco.coeur

import java.time.Duration
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

// Modèle de l'application IST/B-SCO (Kotlin pur, sans Android).

/** Délai pendant lequel un appel validé reste modifiable (même règle que le serveur). */
val DELAI_CORRECTION: Duration = Duration.ofHours(1)

/** Écart d'heure au-delà duquel l'heure du téléphone est jugée douteuse. */
val ECART_HEURE_MAX: Duration = Duration.ofMinutes(15)

val TYPES_SEANCE = linkedMapOf("COURS" to "Cours", "INTERROGATION" to "Interrogation", "TP" to "TP", "EXAMEN" to "Examen", "AUTRE" to "Autre")
val MOTIFS = linkedMapOf(
    "PARTI_AVANT_FIN" to "Parti avant la fin", "FRAUDE" to "Fraude", "COPIE_NON_RENDUE" to "Copie non rendue",
    "COMPORTEMENT" to "Comportement", "ECART" to "Écart d'appel", "AUTRE" to "Autre",
)

fun heureTexte(t: OffsetDateTime): String = t.truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
fun lireHeure(s: String): OffsetDateTime? = try { if (s.isBlank()) null else OffsetDateTime.parse(s) } catch (e: Exception) { null }

/**
 * Horloge du téléphone. [monotone] ne recule jamais (temps écoulé depuis le
 * démarrage du téléphone) : il sert à empêcher qu'on recule l'heure du
 * téléphone pour garder un appel modifiable plus d'une heure.
 */
interface Horloge {
    fun maintenant(): OffsetDateTime
    fun monotone(): Long
    fun demarrage(): String
}

object HorlogeSysteme : Horloge {
    override fun maintenant(): OffsetDateTime = OffsetDateTime.now()
    override fun monotone(): Long = System.nanoTime() / 1_000_000
    override fun demarrage(): String = "jvm"
}

data class Etudiant(val id: Long, val matricule: String, val nom: String, val postnom: String, val prenom: String, val sexe: String, val filiere: String) {
    val nomComplet: String get() = listOf(nom, postnom, prenom).filter { it.isNotBlank() }.joinToString(" ")

    companion object {
        fun de(m: Map<String, Any?>) = Etudiant(m.long("id"), m.texte("matricule"), m.texte("nom"), m.texte("postnom"), m.texte("prenom"), m.texte("sexe"), m.texte("filiere"))
    }
}

data class Cours(val id: Long, val intitule: String, val annee: String, val classe: String, val filieres: String, val etudiants: List<Etudiant>) {
    val titre: String get() = "$classe — $intitule"

    companion object {
        fun de(m: Map<String, Any?>) = Cours(m.long("id"), m.texte("intitule"), m.texte("annee"), m.texte("classe"), m.texte("filieres"), m.liste("etudiants").map { Etudiant.de(it) })
    }
}

/** Appel envoyé par un CP, reçu par le professeur pour comparaison (lecture seule). */
data class AppelCP(val uid: String, val coursId: Long, val date: String, val types: List<String>, val version: Int, val auteur: String,
                   val heureDouteuse: Boolean, val presences: Map<Long, String>, val historique: List<Map<String, Any?>>) {
    companion object {
        fun de(m: Map<String, Any?>): AppelCP {
            @Suppress("UNCHECKED_CAST")
            val pres = (m["presences"] as? List<Any?>)?.mapNotNull { it as? Map<String, Any?> }?.associate { it.long("etudiant_id") to it.texte("statut") } ?: emptyMap()
            return AppelCP(m.texte("uid"), m.long("cours_id"), m.texte("date"), m.textes("types"), m.int("version"), m.texte("auteur"), m.bool("heure_douteuse"), pres, m.liste("historique"))
        }
    }
}

/** Données reçues du serveur à « Actualiser » (gardées sur le téléphone pour travailler hors ligne). */
data class Donnees(val profil: Map<String, Any?>, val cours: List<Cours>, val appelsCP: List<AppelCP>, val recuLe: String) {
    fun cours(id: Long): Cours? = cours.firstOrNull { it.id == id }

    companion object {
        fun de(m: Map<String, Any?>, recuLe: String) = Donnees(m.obj("profil"), m.liste("cours").map { Cours.de(it) }, m.liste("appels_cp").map { AppelCP.de(it) }, recuLe)
    }
}

/** État d'un appel sur le téléphone. */
enum class EtatAppel { EN_COURS, VALIDE_MODIFIABLE, VERROUILLE }

/**
 * Un appel (une séance) fait sur ce téléphone.
 * version 0 = pas encore validé ; chaque modification après un envoi crée une nouvelle version.
 */
data class Appel(
    val uid: String,
    val coursId: Long,
    val date: String,
    val heureAppel: String,
    var types: List<String>,
    var typeAutre: String = "",
    val presences: LinkedHashMap<Long, String> = LinkedHashMap(),
    var valideLe: String = "",
    var valideMono: Long = 0,
    var valideDemarrage: String = "",
    var version: Int = 0,
    var modifieLe: String = "",
    var envoyeVersion: Int = 0,
    var refusVersion: Int = 0,
    var refus: String = "",
) {
    val valide: Boolean get() = version > 0
    val aEnvoyer: Boolean get() = valide && version > envoyeVersion && version != refusVersion
    fun nb(statut: String) = presences.values.count { it == statut }

    fun versJson(): Map<String, Any?> = linkedMapOf(
        "uid" to uid, "cours_id" to coursId, "date" to date, "heure_appel" to heureAppel, "types" to types, "type_autre" to typeAutre,
        "presences" to presences.map { (k, v) -> linkedMapOf("etudiant_id" to k, "statut" to v) },
        "valide_le" to valideLe, "valide_mono" to valideMono, "valide_demarrage" to valideDemarrage, "version" to version, "modifie_le" to modifieLe,
        "envoye_version" to envoyeVersion, "refus_version" to refusVersion, "refus" to refus,
    )

    /** Contenu envoyé au serveur (sans les informations internes du téléphone). */
    fun versEnvoi(): Map<String, Any?> = linkedMapOf(
        "uid" to uid, "cours_id" to coursId, "date" to date, "heure_appel" to heureAppel, "types" to types, "type_autre" to typeAutre,
        "valide_le" to valideLe, "version" to version, "modifie_le" to modifieLe,
        "presences" to presences.map { (k, v) -> linkedMapOf("etudiant_id" to k, "statut" to v) },
    )

    companion object {
        fun de(m: Map<String, Any?>): Appel {
            val p = LinkedHashMap<Long, String>()
            for (x in m.liste("presences")) p[x.long("etudiant_id")] = x.texte("statut")
            return Appel(m.texte("uid"), m.long("cours_id"), m.texte("date"), m.texte("heure_appel"), m.textes("types"), m.texte("type_autre"), p,
                m.texte("valide_le"), m.long("valide_mono"), m.texte("valide_demarrage"), m.int("version"), m.texte("modifie_le"),
                m.int("envoye_version"), m.int("refus_version"), m.texte("refus"))
        }
    }
}

/** Signalement d'un intervenant (fraude, parti avant la fin…). */
data class Signalement(val uid: String, val coursId: Long, val etudiantId: Long, val appelUid: String, val date: String, val motif: String, val texte: String,
                       val creeLe: String, var envoye: Boolean = false, var refus: String = "") {
    fun versEnvoi(): Map<String, Any?> = linkedMapOf("uid" to uid, "cours_id" to coursId, "etudiant_id" to etudiantId, "appel_uid" to appelUid,
        "date" to date, "motif" to motif, "texte" to texte, "cree_le" to creeLe)
    fun versJson(): Map<String, Any?> = LinkedHashMap(versEnvoi()).apply { put("envoye", envoye); put("refus", refus) }

    companion object {
        fun de(m: Map<String, Any?>) = Signalement(m.texte("uid"), m.long("cours_id"), m.long("etudiant_id"), m.texte("appel_uid"), m.texte("date"),
            m.texte("motif"), m.texte("texte"), m.texte("cree_le"), m.bool("envoye"), m.texte("refus"))
    }
}

/** Demande de blocage (examen) : l'Institut décide. */
data class Demande(val uid: String, val coursId: Long, val etudiantId: Long, val motif: String, val creeLe: String, var envoye: Boolean = false, var refus: String = "") {
    fun versEnvoi(): Map<String, Any?> = linkedMapOf("uid" to uid, "cours_id" to coursId, "etudiant_id" to etudiantId, "motif" to motif, "cree_le" to creeLe)
    fun versJson(): Map<String, Any?> = LinkedHashMap(versEnvoi()).apply { put("envoye", envoye); put("refus", refus) }

    companion object {
        fun de(m: Map<String, Any?>) = Demande(m.texte("uid"), m.long("cours_id"), m.long("etudiant_id"), m.texte("motif"), m.texte("cree_le"), m.bool("envoye"), m.texte("refus"))
    }
}

/** Différence entre l'appel du CP et celui du professeur pour un étudiant. */
data class Ecart(val etudiant: Etudiant, val selonCP: String, val selonMoi: String, val auteurCP: String)

class ErreurApp(message: String) : Exception(message)
