// Test d'intégration : le cœur de l'application (le même code que dans l'APK)
// contre un VRAI serveur PEGSCO. Lancé par outils/integration/integration.py.
import cd.istboma.sco.coeur.*
import java.io.File

fun verifier(nom: String, ok: Boolean, detail: String = "") {
    println((if (ok) "OK    " else "ECHEC ") + nom + (if (!ok && detail.isNotEmpty()) " — $detail" else ""))
    if (!ok) System.exit(1)
}

fun main() {
    val url = System.getenv("PEGSCO_URL")
    val dossier = File(System.getenv("DOSSIER"))
    val prof = Moteur(Depot(File(dossier, "prof")))
    val cp = Moteur(Depot(File(dossier, "cp")))
    verifier("serveur disponible (Actualiser)", prof.serveurDisponible(url))
    try { prof.connecter(url, "AAAA-BBBB", "Test"); verifier("faux code refusé", false) } catch (e: ErreurApp) { verifier("faux code refusé", e.message!!.contains("Code invalide")) }
    prof.connecter(url, System.getenv("CODE_PROF").lowercase(), "Tecno Spark 10")
    cp.connecter(url.removePrefix("http://"), System.getenv("CODE_CP"), "Itel A70")
    val cProf = prof.donnees!!.cours.single()
    val cCP = cp.donnees!!.cours.single()
    verifier("profils : intervenant et CP", !prof.estCP && cp.estCP && cp.profil.texte("filiere") == "SG")
    verifier("listes : professeur ${cProf.etudiants.size}, CP ${cCP.etudiants.size}", cProf.etudiants.size == 5 && cCP.etudiants.size == 3)
    verifier("heure du téléphone correcte", !prof.heureDouteuse)
    val id = cProf.etudiants.associate { it.nom to it.id }

    // CP : appel de sa filière, tous présents
    val a1 = cp.nouvelAppel(cCP.id, listOf("COURS", "INTERROGATION"))
    cp.tousPresents(a1.uid); cp.valider(a1.uid)
    // Professeur : CIKU absent, EKO absent
    val a2 = prof.nouvelAppel(cProf.id, listOf("INTERROGATION", "COURS"))
    prof.marquer(a2.uid, id["CIKU"]!!, "A"); prof.marquer(a2.uid, id["EKO"]!!, "A"); prof.tousPresents(a2.uid); prof.valider(a2.uid)
    val b1 = cp.envoyer(); val b2 = prof.envoyer()
    verifier("envois reçus (CP : ${b1.resume} / prof : ${b2.resume})", b1.recus == 1 && b2.recus == 1 && cp.nbAEnvoyer() == 0)
    verifier("renvoi sans changement : rien à envoyer", cp.envoyer().recus == 0)

    // Correction dans l'heure → version 2 reçue
    prof.marquer(a2.uid, id["EKO"]!!, "P")
    verifier("correction : version 2 à envoyer", a2.version == 2 && prof.nbAEnvoyer() == 1)
    verifier("version 2 reçue", prof.envoyer().recus == 1)

    // Comparaison
    prof.actualiser()
    val ec = prof.comparer(cProf.id, prof.aujourdhui())
    verifier("comparaison : CIKU présent selon le CP, absent selon moi", ec.size == 1 && ec[0].etudiant.nom == "CIKU" && ec[0].selonCP == "P" && ec[0].selonMoi == "A", ec.toString())
    verifier("le CP ne reçoit pas l'appel du professeur", cp.actualiser().appelsCP.isEmpty())

    // Signalement + demande de blocage ; un CP ne peut pas signaler
    prof.signaler(cProf.id, id["AMBA"]!!, a2.uid, "FRAUDE", "antisèche pendant l'interrogation")
    prof.demanderBlocage(cProf.id, id["AMBA"]!!, "fraude à l'interrogation")
    try { cp.signaler(cCP.id, id["AMBA"]!!, "", "FRAUDE", "x"); verifier("CP : signalement refusé", false) } catch (e: ErreurApp) { verifier("CP : signalement refusé", true) }
    verifier("signalement et demande reçus", prof.envoyer().recus == 2)

    // Sauvegarde : un appel validé non envoyé (serveur « injoignable »)
    val a3 = cp.nouvelAppel(cCP.id, listOf("TP"))
    cp.marquer(a3.uid, id["AMBA"]!!, "A"); cp.tousPresents(a3.uid); cp.valider(a3.uid)
    val (nom, texte) = cp.sauvegarde(cp.aujourdhui())
    File(dossier, "sauvegarde.istsco").writeText(texte)
    verifier("sauvegarde du jour créée : $nom", nom.endsWith(".istsco") && Json.objet(Json.objet(texte).texte("contenu")).liste("appels").size == 2)
    println("UID_TP=" + a3.uid)
}
