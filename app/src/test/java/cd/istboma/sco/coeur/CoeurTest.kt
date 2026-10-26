package cd.istboma.sco.coeur

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.Duration
import java.time.OffsetDateTime

/** Horloge réglable : simule le temps qui passe, les changements d'heure et les redémarrages. */
class HorlogeTest(var t: OffsetDateTime = OffsetDateTime.parse("2026-10-26T08:00:00+01:00")) : Horloge {
    var mono = 1_000_000L
    var boot = "boot-1"
    fun avancer(d: Duration) { t = t.plus(d); mono += d.toMillis() }
    override fun maintenant() = t
    override fun monotone() = mono
    override fun demarrage() = boot
}

/** Faux serveur : répond comme PEGSCO. */
class FauxServeur : Transport {
    val envois = ArrayList<String>()
    var signatures = ArrayList<String>()
    var refuserJeton = false
    var reponseEnvoi: (Map<String, Any?>) -> Map<String, Any?> = { e ->
        mapOf("heure_serveur" to "2026-10-26T08:00:00+01:00",
            "appels" to e.liste("appels").map { mapOf("uid" to it.texte("uid"), "version" to it.long("version"), "statut" to "RECU") },
            "signalements" to e.liste("signalements").map { mapOf("uid" to it.texte("uid"), "statut" to "RECU") },
            "demandes" to e.liste("demandes").map { mapOf("uid" to it.texte("uid"), "statut" to "RECU") })
    }
    val donnees = mapOf(
        "profil" to mapOf("type" to "INTERVENANT", "nom" to "MVUEZOLO KIESE"),
        "cours" to listOf(mapOf("id" to 7, "intitule" to "Anatomie", "annee" to "2025-2026", "classe" to "L1", "filieres" to "SF, SG",
            "etudiants" to listOf(mapOf("id" to 1, "nom" to "AMBA", "postnom" to "X"), mapOf("id" to 2, "nom" to "BOLA", "postnom" to "Y"), mapOf("id" to 3, "nom" to "CIKU", "postnom" to "Z")))),
        "appels_cp" to listOf(mapOf("uid" to "u-cp", "cours_id" to 7, "date" to "2026-10-26", "types" to listOf("COURS"), "version" to 1, "auteur" to "CP SG — BOLA",
            "presences" to listOf(mapOf("etudiant_id" to 1, "statut" to "P"), mapOf("etudiant_id" to 2, "statut" to "P"), mapOf("etudiant_id" to 3, "statut" to "P")))),
    )

    override fun requete(methode: String, url: String, corps: ByteArray?, entetes: Map<String, String>, delaiMs: Int): Pair<Int, String> {
        val chemin = url.substringAfter("8100")
        if (refuserJeton && chemin != "/mobile/ping") return 401 to """{"erreur":"téléphone non autorisé"}"""
        return when (chemin) {
            "/mobile/ping" -> 200 to """{"statut":"ok","logiciel":"PEGSCO","heure_serveur":"2026-10-26T08:00:00+01:00"}"""
            "/mobile/connexion" -> {
                val c = Json.objet(String(corps!!))
                if (c.texte("code") != "K7Q2-M9XA") 401 to """{"erreur":"code invalide"}"""
                else 200 to Json.ecrire(mapOf("jeton" to "jeton-1", "cle_signature" to "cle-test", "appareil_id" to 5, "profil" to donnees["profil"]))
            }
            "/mobile/donnees" -> 200 to Json.ecrire(donnees)
            "/mobile/envoi" -> {
                val t = String(corps!!)
                envois.add(t)
                signatures.add(entetes["X-Signature"] ?: "")
                if (signer("cle-test", corps) != entetes["X-Signature"]) 401 to """{"erreur":"signature invalide"}"""
                else 200 to Json.ecrire(reponseEnvoi(Json.objet(t)))
            }
            else -> 404 to """{"erreur":"inconnu"}"""
        }
    }
}

class CoeurTest {
    private fun dossier(): File = Files.createTempDirectory("istbsco").toFile()

    private fun moteur(h: HorlogeTest, s: FauxServeur, d: File = dossier()) = Moteur(Depot(d), h) { Client(it, s) }

    private fun attendErreur(msg: String, f: () -> Unit) {
        try { f(); fail("erreur attendue : $msg") } catch (e: ErreurApp) { assertTrue("« ${e.message} » devrait contenir « $msg »", e.message!!.contains(msg)) }
    }

    @Test fun json() {
        val t = """{"a":1,"b":[true,null,"é\"\n"],"c":{"d":-2.5,"e":"é"}}"""
        val m = Json.objet(t)
        assertEquals(1L, m["a"])
        assertEquals("é\"\n", (m["b"] as List<*>)[2])
        assertEquals(-2.5, m.obj("c")["d"])
        assertEquals("é", m.obj("c").texte("e"))
        assertEquals(m, Json.objet(Json.ecrire(m)))
    }

    @Test fun signatureCommeLeServeur() {
        // Même valeur que Signer() côté Go (et hmac Python).
        assertEquals("592ef22f9cd473de67bc280cab144f33ffbc1cf3f4223e97942dc7008da41c9f", signer("cle-test", """{"a":1}""".toByteArray()))
    }

    @Test fun adresseServeur() {
        assertEquals("http://192.168.1.10:8100", Client.normaliserAdresse(" 192.168.1.10 "))
        assertEquals("http://192.168.1.10:9000", Client.normaliserAdresse("http://192.168.1.10:9000/"))
        attendErreur("http://") { Client.normaliserAdresse("https://pegsco") }
    }

    @Test fun connexionEtActualisation() {
        val h = HorlogeTest(); val s = FauxServeur()
        val m = moteur(h, s)
        attendErreur("8 caractères") { m.connecter("192.168.1.10", "K7Q2", "Tecno") }
        attendErreur("Code invalide") { m.connecter("192.168.1.10", "AAAA-AAAA", "Tecno") }
        m.connecter("192.168.1.10", "K7Q2-M9XA", "Tecno")
        assertTrue(m.connecte)
        assertEquals(3, m.donnees!!.cours(7)!!.etudiants.size)
        assertFalse(m.heureDouteuse)
        h.t = h.t.plusMinutes(40) // le téléphone avance de 40 min
        m.actualiser()
        assertTrue(m.heureDouteuse)
    }

    @Test fun appelValidationEtDelaiDUneHeure() {
        val h = HorlogeTest(); val s = FauxServeur()
        val m = moteur(h, s)
        m.connecter("192.168.1.10", "K7Q2-M9XA", "Tecno")
        val a = m.nouvelAppel(7, listOf("COURS", "INTERROGATION"))
        m.marquer(a.uid, 1, "P")
        attendErreur("non marqué") { m.valider(a.uid) }
        m.marquer(a.uid, 3, "A")
        m.tousPresents(a.uid)
        assertEquals("A", a.presences[3]); assertEquals("P", a.presences[2])
        m.valider(a.uid)
        assertEquals(1, a.version)
        assertEquals(EtatAppel.VALIDE_MODIFIABLE, m.etat(a))
        // Modifiable 1 h : avant l'envoi, la version reste 1.
        h.avancer(Duration.ofMinutes(20))
        m.marquer(a.uid, 2, "A")
        assertEquals(1, a.version)
        assertEquals(40L, m.minutesRestantes(a))
        // Envoi, puis correction : nouvelle version.
        val b = m.envoyer()
        assertEquals(1, b.recus)
        assertEquals(0, m.nbAEnvoyer())
        m.marquer(a.uid, 2, "P")
        assertEquals(2, a.version)
        assertEquals(1, m.nbAEnvoyer())
        // Reculer l'heure du téléphone ne prolonge pas le délai (temps monotone).
        h.avancer(Duration.ofMinutes(41))
        h.t = h.t.minusHours(2)
        assertEquals(EtatAppel.VERROUILLE, m.etat(a))
        attendErreur("verrouillé") { m.marquer(a.uid, 1, "A") }
        // Après redémarrage, c'est l'heure du téléphone qui compte : toujours verrouillé si l'heure est juste.
        h.boot = "boot-2"; h.t = h.t.plusHours(2)
        assertEquals(EtatAppel.VERROUILLE, m.etat(a))
        // modifie_le = validation + temps réellement écoulé (pas l'heure modifiée du téléphone)
        assertEquals(heureTexte(lireHeure(a.valideLe)!!.plusMinutes(20)), a.modifieLe)
    }

    @Test fun conservationSurDisque() {
        val h = HorlogeTest(); val s = FauxServeur(); val d = dossier()
        val m = moteur(h, s, d)
        m.connecter("192.168.1.10", "K7Q2-M9XA", "Tecno")
        m.definirPin("2468")
        val a = m.nouvelAppel(7, listOf("TP"))
        m.tousPresents(a.uid); m.valider(a.uid)
        val m2 = moteur(h, s, d) // l'application est relancée
        assertTrue(m2.connecte)
        assertTrue(m2.verifierPin("2468")); assertFalse(m2.verifierPin("1111"))
        assertEquals(1, m2.appels().size)
        assertEquals(1, m2.nbAEnvoyer())
        assertEquals(3, m2.donnees!!.cours(7)!!.etudiants.size)
    }

    @Test fun refusEtSessionCoupee() {
        val h = HorlogeTest(); val s = FauxServeur()
        val m = moteur(h, s)
        m.connecter("192.168.1.10", "K7Q2-M9XA", "Tecno")
        val a = m.nouvelAppel(7, listOf("COURS"))
        m.tousPresents(a.uid); m.valider(a.uid)
        s.reponseEnvoi = { e -> mapOf("appels" to e.liste("appels").map { mapOf("uid" to it.texte("uid"), "version" to it.long("version"), "statut" to "REFUSE", "raison" to "date future refusée") }) }
        val b = m.envoyer()
        assertEquals(1, b.refuses)
        assertEquals("date future refusée", a.refus)
        assertEquals(0, m.nbAEnvoyer()) // pas renvoyé tel quel
        s.refuserJeton = true
        m.marquer(a.uid, 1, "A") // nouvelle version
        attendErreur("Reconnectez-vous") { m.envoyer() }
        assertTrue(m.sessionRefusee)
        assertEquals(1, m.nbAEnvoyer()) // l'appel reste sur le téléphone
    }

    @Test fun sauvegardeSignee() {
        val h = HorlogeTest(); val s = FauxServeur()
        val m = moteur(h, s)
        m.connecter("192.168.1.10", "K7Q2-M9XA", "Tecno")
        attendErreur("Rien à sauvegarder") { m.sauvegarde(null) }
        val a = m.nouvelAppel(7, listOf("COURS"))
        m.tousPresents(a.uid); m.valider(a.uid)
        val (nom, texte) = m.sauvegarde(null)
        assertTrue(nom, nom.endsWith(".istsco") && nom.contains("MVUEZOLO"))
        val f = Json.objet(texte)
        assertEquals("IST-B-SCO-SAUVEGARDE", f.texte("format"))
        assertEquals(5L, f.long("appareil_id"))
        assertEquals(signer("cle-test", f.texte("contenu").toByteArray()), f.texte("signature"))
        assertEquals(a.uid, Json.objet(f.texte("contenu")).liste("appels")[0].texte("uid"))
    }

    @Test fun signalementsEtComparaison() {
        val h = HorlogeTest(); val s = FauxServeur()
        val m = moteur(h, s)
        m.connecter("192.168.1.10", "K7Q2-M9XA", "Tecno")
        val a = m.nouvelAppel(7, listOf("COURS"))
        m.marquer(a.uid, 3, "A"); m.tousPresents(a.uid); m.valider(a.uid)
        val e = m.comparer(7, "2026-10-26")
        assertEquals(1, e.size)
        assertEquals("CIKU", e[0].etudiant.nom); assertEquals("P", e[0].selonCP); assertEquals("A", e[0].selonMoi)
        m.signaler(7, 1, a.uid, "FRAUDE", "antisèche")
        m.demanderBlocage(7, 1, "fraude")
        assertEquals(3, m.nbAEnvoyer())
        val b = m.envoyer()
        assertEquals(3, b.recus)
        assertTrue(s.envois.last().contains("antisèche"))
    }
}
