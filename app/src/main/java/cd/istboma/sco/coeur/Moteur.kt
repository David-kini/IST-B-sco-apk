package cd.istboma.sco.coeur

import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.util.UUID

/**
 * Cœur de l'application : connexion avec le code, actualisation, appels,
 * validation (+1 h de correction), envoi, sauvegarde, signalements, comparaison.
 * Aucune dépendance Android : testé sur PC contre le vrai serveur PEGSCO.
 */
class Moteur(
    private val depot: Depot,
    private val horloge: Horloge = HorlogeSysteme,
    private val fabriqueClient: (String) -> Client = { Client(it) },
) {
    private var etat: MutableMap<String, Any?> = LinkedHashMap(depot.lire(ETAT) ?: emptyMap())
    private val appels = ArrayList<Appel>()
    private val signalements = ArrayList<Signalement>()
    private val demandes = ArrayList<Demande>()
    var donnees: Donnees? = null
        private set

    init {
        depot.lire(APPELS)?.liste("appels")?.forEach { appels.add(Appel.de(it)) }
        depot.lire(SIGNALEMENTS)?.let { m ->
            m.liste("signalements").forEach { signalements.add(Signalement.de(it)) }
            m.liste("demandes").forEach { demandes.add(Demande.de(it)) }
        }
        depot.lire(DONNEES)?.let { donnees = Donnees.de(it.obj("donnees"), it.texte("recu_le")) }
        if (etat.texte("appareil_uid").isEmpty()) {
            etat["appareil_uid"] = UUID.randomUUID().toString()
            sauverEtat()
        }
    }

    // ---------- Session ----------

    val connecte: Boolean get() = etat.texte("jeton").isNotEmpty()
    val adresseServeur: String get() = etat.texte("serveur")
    val aUnPin: Boolean get() = etat.texte("pin_hash").isNotEmpty()
    val profil: Map<String, Any?> get() = etat.obj("profil")
    val estCP: Boolean get() = profil.texte("type") == "CP"
    val nomUtilisateur: String get() = profil.texte("nom")
    val ecartHorlogeSecondes: Long get() = etat.long("ecart_horloge_s")
    val derniereActualisation: String get() = etat.texte("actualise_le")
    /** Le serveur a refusé le jeton (code régénéré ou désactivé) : il faut le nouveau code. */
    val sessionRefusee: Boolean get() = etat.bool("session_refusee")

    private fun sauverEtat() = depot.ecrire(ETAT, etat)
    private fun sauverAppels() = depot.ecrire(APPELS, mapOf("appels" to appels.map { it.versJson() }))
    private fun sauverSignalements() = depot.ecrire(SIGNALEMENTS, mapOf("signalements" to signalements.map { it.versJson() }, "demandes" to demandes.map { it.versJson() }))

    private fun hashPin(sel: String, pin: String): String =
        MessageDigest.getInstance("SHA-256").digest("$sel:$pin".toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    fun definirPin(pin: String) {
        if (pin.length < 4 || pin.length > 8 || !pin.all { it.isDigit() }) throw ErreurApp("Le code PIN doit avoir 4 à 8 chiffres.")
        val b = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val sel = b.joinToString("") { "%02x".format(it) }
        etat["pin_sel"] = sel
        etat["pin_hash"] = hashPin(sel, pin)
        sauverEtat()
    }

    fun verifierPin(pin: String): Boolean = aUnPin && hashPin(etat.texte("pin_sel"), pin) == etat.texte("pin_hash")

    /** Vérifie que le serveur répond (bouton « Actualiser »). */
    fun serveurDisponible(adresse: String = adresseServeur): Boolean = try { fabriqueClient(adresse).ping(); true } catch (e: Exception) { false }

    /** Première connexion (ou nouveau code) : adresse du serveur + code donné par le secrétariat. */
    fun connecter(adresse: String, code: String, nomAppareil: String) {
        val c = fabriqueClient(adresse)
        val code_ = code.trim()
        if (code_.replace("-", "").replace(" ", "").length != 8) throw ErreurApp("Le code de connexion a 8 caractères (ex. K7Q2-M9XA).")
        val ping = c.ping()
        val r = try {
            c.connexion(code_, etat.texte("appareil_uid"), nomAppareil)
        } catch (e: ErreurServeur) {
            throw ErreurApp(if (e.statut == 401) "Code invalide ou désactivé : demandez votre code au secrétariat." else e.message ?: "Erreur du serveur")
        }
        val ancien = profil.texte("nom")
        etat["serveur"] = c.base
        etat["jeton"] = r.texte("jeton")
        etat["cle"] = r.texte("cle_signature")
        etat["appareil_id"] = r.long("appareil_id")
        etat["profil"] = r.obj("profil")
        etat["session_refusee"] = false
        mesurerHorloge(ping.texte("heure_serveur"))
        if (ancien.isNotEmpty() && ancien != r.obj("profil").texte("nom")) {
            // Autre personne sur ce téléphone : on garde les appels non envoyés (ils restent à envoyer ou à sauvegarder).
            etat["ancien_profil"] = ancien
        }
        sauverEtat()
        actualiser()
    }

    /** Oublie la session (il faudra un code pour se reconnecter). Refusé s'il reste des envois. */
    fun deconnecter(forcer: Boolean = false) {
        if (!forcer && (nbAEnvoyer() > 0)) throw ErreurApp("Il reste des éléments à envoyer : envoyez-les (ou faites une sauvegarde) avant de vous déconnecter.")
        for (k in listOf("jeton", "cle", "profil", "appareil_id", "session_refusee")) etat.remove(k)
        sauverEtat()
        donnees = null
        depot.effacer(DONNEES)
    }

    private fun mesurerHorloge(heureServeur: String) {
        val hs = lireHeure(heureServeur) ?: return
        etat["ecart_horloge_s"] = Duration.between(hs, horloge.maintenant()).seconds
    }

    /** L'heure du téléphone diffère de plus de 15 min de celle du serveur. */
    val heureDouteuse: Boolean get() = Math.abs(ecartHorlogeSecondes) > ECART_HEURE_MAX.seconds

    private fun client(): Client {
        if (!connecte) throw ErreurApp("Téléphone non connecté : saisissez l'adresse du serveur et votre code.")
        return fabriqueClient(adresseServeur)
    }

    private fun <T> avecSession(f: () -> T): T = try {
        f()
    } catch (e: ErreurServeur) {
        if (e.statut == 401 && !e.message.orEmpty().contains("signature")) {
            etat["session_refusee"] = true
            sauverEtat()
            throw ErreurApp("Accès refusé : votre code a été régénéré ou désactivé. Reconnectez-vous avec le nouveau code (vos appels restent sur le téléphone).")
        }
        throw ErreurApp(e.message ?: "Erreur du serveur")
    } catch (e: ErreurReseau) {
        throw ErreurApp(e.message ?: "Serveur injoignable")
    }

    /** « Actualiser » : vérifie le serveur et télécharge cours, listes et appels des CP. */
    fun actualiser(): Donnees = avecSession {
        val c = client()
        val ping = c.ping()
        mesurerHorloge(ping.texte("heure_serveur"))
        val brut = c.donnees(etat.texte("jeton"))
        val maintenant = heureTexte(horloge.maintenant())
        val d = Donnees.de(brut, maintenant)
        depot.ecrire(DONNEES, mapOf("donnees" to brut, "recu_le" to maintenant))
        etat["profil"] = brut.obj("profil")
        etat["actualise_le"] = maintenant
        etat["session_refusee"] = false
        sauverEtat()
        donnees = d
        d
    }

    // ---------- Appels ----------

    fun appels(): List<Appel> = appels.sortedWith(compareByDescending<Appel> { it.date }.thenByDescending { it.heureAppel })
    fun appel(uid: String): Appel = appels.firstOrNull { it.uid == uid } ?: throw ErreurApp("Appel introuvable.")
    fun aujourdhui(): String = horloge.maintenant().toLocalDate().toString()

    fun nouvelAppel(coursId: Long, types: List<String>, typeAutre: String = ""): Appel {
        val cours = donnees?.cours(coursId) ?: throw ErreurApp("Cours inconnu : actualisez d'abord.")
        if (cours.etudiants.isEmpty()) throw ErreurApp("Aucun étudiant dans ce cours.")
        val a = Appel(UUID.randomUUID().toString(), coursId, aujourdhui(), heureTexte(horloge.maintenant()), verifierTypes(types, typeAutre), typeAutre.trim())
        appels.add(a)
        sauverAppels()
        return a
    }

    private fun verifierTypes(types: List<String>, autre: String): List<String> {
        val t = types.map { it.uppercase() }.distinct().sorted()
        if (t.isEmpty()) throw ErreurApp("Choisissez au moins un type de séance (cours, interrogation…).")
        if (t.any { it !in TYPES_SEANCE }) throw ErreurApp("Type de séance inconnu.")
        if ("AUTRE" in t && autre.isBlank()) throw ErreurApp("Précisez le type « Autre ».")
        return t
    }

    fun etat(a: Appel): EtatAppel = when {
        !a.valide -> EtatAppel.EN_COURS
        modifiable(a) -> EtatAppel.VALIDE_MODIFIABLE
        else -> EtatAppel.VERROUILLE
    }

    /** Temps écoulé depuis la validation, mesuré sans tenir compte des changements d'heure du téléphone. */
    private fun ecouleDepuisValidation(a: Appel): Duration? {
        if (a.valideDemarrage == horloge.demarrage() && a.valideMono > 0) {
            val ms = horloge.monotone() - a.valideMono
            return if (ms < 0) null else Duration.ofMillis(ms)
        }
        val v = lireHeure(a.valideLe) ?: return null
        val d = Duration.between(v, horloge.maintenant())
        return if (d.isNegative) null else d
    }

    private fun modifiable(a: Appel): Boolean {
        if (!a.valide) return true
        val e = ecouleDepuisValidation(a) ?: return false
        return e <= DELAI_CORRECTION
    }

    /** Minutes restantes pour corriger un appel validé (0 = verrouillé). */
    fun minutesRestantes(a: Appel): Long {
        if (!a.valide) return DELAI_CORRECTION.toMinutes()
        val e = ecouleDepuisValidation(a) ?: return 0
        return maxOf(0, DELAI_CORRECTION.minus(e).toMinutes())
    }

    private fun avantModification(a: Appel) {
        if (!modifiable(a)) throw ErreurApp("Appel verrouillé : plus d'une heure après sa validation. Seul le secrétariat peut le corriger.")
        if (a.valide) {
            // Nouvelle version si la précédente est partie (ou a été refusée).
            if (a.version <= a.envoyeVersion || a.version == a.refusVersion) a.version++
            val e = ecouleDepuisValidation(a) ?: Duration.ZERO
            a.modifieLe = heureTexte(lireHeure(a.valideLe)!!.plus(e))
            a.refus = ""
        }
    }

    fun marquer(uid: String, etudiantId: Long, statut: String) {
        val a = appel(uid)
        val s = statut.uppercase()
        if (s != "P" && s != "A") throw ErreurApp("Statut : P (présent) ou A (absent).")
        if (donnees?.cours(a.coursId)?.etudiants?.any { it.id == etudiantId } == false) throw ErreurApp("Étudiant hors de ce cours.")
        if (a.presences[etudiantId] == s) return
        avantModification(a)
        a.presences[etudiantId] = s
        sauverAppels()
    }

    /** Marque présents tous les étudiants pas encore marqués. */
    fun tousPresents(uid: String) {
        val a = appel(uid)
        val cours = donnees?.cours(a.coursId) ?: throw ErreurApp("Cours inconnu : actualisez.")
        val manquants = cours.etudiants.filter { it.id !in a.presences }
        if (manquants.isEmpty()) return
        avantModification(a)
        for (e in manquants) a.presences[e.id] = "P"
        sauverAppels()
    }

    fun changerTypes(uid: String, types: List<String>, typeAutre: String) {
        val a = appel(uid)
        val t = verifierTypes(types, typeAutre)
        if (t == a.types && typeAutre.trim() == a.typeAutre) return
        avantModification(a)
        a.types = t
        a.typeAutre = typeAutre.trim()
        sauverAppels()
    }

    fun nonMarques(a: Appel): List<Etudiant> = donnees?.cours(a.coursId)?.etudiants?.filter { it.id !in a.presences } ?: emptyList()

    /** Valide l'appel : il reste modifiable pendant 1 h, puis il est verrouillé. */
    fun valider(uid: String) {
        val a = appel(uid)
        if (a.valide) return
        val manque = nonMarques(a)
        if (manque.isNotEmpty()) throw ErreurApp("${manque.size} étudiant(s) non marqué(s) : marquez chacun Présent ou Absent.")
        if (a.presences.isEmpty()) throw ErreurApp("Appel vide.")
        val n = horloge.maintenant()
        a.valideLe = heureTexte(n)
        a.valideMono = horloge.monotone()
        a.valideDemarrage = horloge.demarrage()
        a.modifieLe = a.valideLe
        a.version = 1
        sauverAppels()
    }

    /** Supprime un appel pas encore validé (erreur de cours, test…). */
    fun supprimerBrouillon(uid: String) {
        val a = appel(uid)
        if (a.valide) throw ErreurApp("Un appel validé ne peut pas être supprimé.")
        appels.remove(a)
        sauverAppels()
    }

    // ---------- Signalements et demandes ----------

    private fun exigerIntervenant() { if (estCP) throw ErreurApp("Seuls les intervenants (professeur, assistant…) peuvent signaler.") }

    fun signaler(coursId: Long, etudiantId: Long, appelUid: String, motif: String, texte: String): Signalement {
        exigerIntervenant()
        if (motif !in MOTIFS) throw ErreurApp("Choisissez un motif.")
        if (motif == "AUTRE" && texte.isBlank()) throw ErreurApp("Décrivez le cas.")
        val date = if (appelUid.isNotEmpty()) appel(appelUid).date else aujourdhui()
        val s = Signalement(UUID.randomUUID().toString(), coursId, etudiantId, appelUid, date, motif, texte.trim(), heureTexte(horloge.maintenant()))
        signalements.add(s)
        sauverSignalements()
        return s
    }

    fun demanderBlocage(coursId: Long, etudiantId: Long, motif: String): Demande {
        exigerIntervenant()
        if (motif.isBlank()) throw ErreurApp("Le motif est obligatoire.")
        if (etudiantId <= 0) throw ErreurApp("Choisissez l'étudiant.")
        val d = Demande(UUID.randomUUID().toString(), coursId, etudiantId, motif.trim(), heureTexte(horloge.maintenant()))
        demandes.add(d)
        sauverSignalements()
        return d
    }

    fun signalements(): List<Signalement> = signalements.sortedByDescending { it.creeLe }
    fun demandes(): List<Demande> = demandes.sortedByDescending { it.creeLe }

    // ---------- Envoi ----------

    fun nbAEnvoyer(): Int = appels.count { it.aEnvoyer } + signalements.count { !it.envoye && it.refus.isEmpty() } + demandes.count { !it.envoye && it.refus.isEmpty() }

    private fun contenuEnvoi(appelsChoisis: List<Appel>, sig: List<Signalement>, dem: List<Demande>): String = Json.ecrire(linkedMapOf(
        "envoye_le" to heureTexte(horloge.maintenant()),
        "appels" to appelsChoisis.map { it.versEnvoi() },
        "signalements" to sig.map { it.versEnvoi() },
        "demandes" to dem.map { it.versEnvoi() },
    ))

    data class BilanEnvoi(val recus: Int, val dejaRecus: Int, val refuses: Int, val raisons: List<String>) {
        val resume: String get() = buildString {
            append("$recus élément(s) reçu(s) par le serveur")
            if (dejaRecus > 0) append(", $dejaRecus déjà reçu(s)")
            if (refuses > 0) append(", $refuses refusé(s)")
            append('.')
        }
    }

    /** Envoie tout ce qui attend (appels validés, nouvelles versions, signalements, demandes). */
    fun envoyer(): BilanEnvoi = avecSession {
        val ap = appels.filter { it.aEnvoyer }
        val sg = signalements.filter { !it.envoye && it.refus.isEmpty() }
        val dm = demandes.filter { !it.envoye && it.refus.isEmpty() }
        if (ap.isEmpty() && sg.isEmpty() && dm.isEmpty()) return@avecSession BilanEnvoi(0, 0, 0, emptyList())
        val corps = contenuEnvoi(ap, sg, dm).toByteArray(Charsets.UTF_8)
        val r = client().envoi(etat.texte("jeton"), etat.texte("cle"), corps)
        mesurerHorloge(r.texte("heure_serveur"))
        var recus = 0; var deja = 0; var refuses = 0
        val raisons = ArrayList<String>()
        val noms = { a: Appel -> (donnees?.cours(a.coursId)?.titre ?: "cours ${a.coursId}") + " du " + a.date }
        for (x in r.liste("appels")) {
            val a = appels.firstOrNull { it.uid == x.texte("uid") } ?: continue
            val v = x.int("version")
            when (x.texte("statut")) {
                "RECU" -> { recus++; a.envoyeVersion = maxOf(a.envoyeVersion, v); a.refus = "" }
                "DEJA_RECU" -> { deja++; a.envoyeVersion = maxOf(a.envoyeVersion, v); a.refus = "" }
                else -> { refuses++; a.refusVersion = v; a.refus = x.texte("raison"); raisons.add(noms(a) + " : " + a.refus) }
            }
        }
        for (x in r.liste("signalements")) {
            val s = signalements.firstOrNull { it.uid == x.texte("uid") } ?: continue
            when (x.texte("statut")) {
                "RECU" -> { recus++; s.envoye = true }
                "DEJA_RECU" -> { deja++; s.envoye = true }
                else -> { refuses++; s.refus = x.texte("raison"); raisons.add("Signalement : " + s.refus) }
            }
        }
        for (x in r.liste("demandes")) {
            val d = demandes.firstOrNull { it.uid == x.texte("uid") } ?: continue
            when (x.texte("statut")) {
                "RECU" -> { recus++; d.envoye = true }
                "DEJA_RECU" -> { deja++; d.envoye = true }
                else -> { refuses++; d.refus = x.texte("raison"); raisons.add("Demande de blocage : " + d.refus) }
            }
        }
        sauverAppels()
        sauverSignalements()
        BilanEnvoi(recus, deja, refuses, raisons)
    }

    // ---------- Fichier de sauvegarde ----------

    /**
     * Fichier signé à remettre au secrétariat quand le serveur est injoignable.
     * date = null : tout ce qui n'est pas encore envoyé ; sinon tous les appels validés de cette date.
     */
    fun sauvegarde(date: String?): Pair<String, String> {
        if (etat.texte("cle").isEmpty()) throw ErreurApp("Téléphone jamais connecté : impossible de signer la sauvegarde.")
        val ap = if (date == null) appels.filter { it.aEnvoyer } else appels.filter { it.valide && it.date == date }
        val sg = if (date == null) signalements.filter { !it.envoye } else signalements.filter { it.date == date }
        val dm = if (date == null) demandes.filter { !it.envoye } else demandes.filter { it.creeLe.startsWith(date) }
        if (ap.isEmpty() && sg.isEmpty() && dm.isEmpty()) throw ErreurApp("Rien à sauvegarder pour ce choix (seuls les appels VALIDÉS sont sauvegardés).")
        val contenu = contenuEnvoi(ap, sg, dm)
        val n = horloge.maintenant()
        val f = linkedMapOf(
            "format" to "IST-B-SCO-SAUVEGARDE", "version" to 1, "appareil_id" to etat.long("appareil_id"),
            "auteur" to nomUtilisateur, "cree_le" to heureTexte(n), "date" to (date ?: aujourdhui()),
            "contenu" to contenu, "signature" to signer(etat.texte("cle"), contenu.toByteArray(Charsets.UTF_8)),
        )
        val nom = "IST-B-SCO_" + nomUtilisateur.replace(Regex("[^A-Za-z0-9]+"), "-").trim('-').take(40) + "_" + (date ?: "a-envoyer") + "_" +
            n.toLocalTime().toString().take(5).replace(":", "h") + ".istsco"
        return nom to Json.ecrire(f)
    }

    // ---------- Comparaison (professeur) ----------

    /** Appels des CP reçus pour ce cours et cette date (le professeur seulement). */
    fun appelsCP(coursId: Long, date: String): List<AppelCP> = donnees?.appelsCP?.filter { it.coursId == coursId && it.date == date } ?: emptyList()

    /** Étudiants notés différemment par le CP et par moi le même jour. */
    fun comparer(coursId: Long, date: String): List<Ecart> {
        val cours = donnees?.cours(coursId) ?: return emptyList()
        val miens = HashMap<Long, String>()
        for (a in appels.filter { it.coursId == coursId && it.date == date && it.valide }) for ((id, s) in a.presences) miens[id] = if (miens[id] == "P" || s == "P") "P" else "A"
        val cp = HashMap<Long, Pair<String, String>>()
        for (a in appelsCP(coursId, date)) for ((id, s) in a.presences) {
            val avant = cp[id]?.first
            cp[id] = (if (avant == "P" || s == "P") "P" else "A") to a.auteur
        }
        return cours.etudiants.mapNotNull { e ->
            val m = miens[e.id] ?: return@mapNotNull null
            val c = cp[e.id] ?: return@mapNotNull null
            if (m == c.first) null else Ecart(e, c.first, m, c.second)
        }
    }

    companion object {
        const val ETAT = "etat.json"
        const val APPELS = "appels.json"
        const val SIGNALEMENTS = "signalements.json"
        const val DONNEES = "donnees.json"
    }
}
