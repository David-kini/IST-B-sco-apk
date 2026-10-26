package cd.istboma.sco

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.TextView
import cd.istboma.sco.coeur.Appel
import cd.istboma.sco.coeur.Cours
import cd.istboma.sco.coeur.Depot
import cd.istboma.sco.coeur.ErreurApp
import cd.istboma.sco.coeur.EtatAppel
import cd.istboma.sco.coeur.Etudiant
import cd.istboma.sco.coeur.Horloge
import cd.istboma.sco.coeur.MOTIFS
import cd.istboma.sco.coeur.Moteur
import cd.istboma.sco.coeur.TYPES_SEANCE
import cd.istboma.sco.coeur.lireHeure
import cd.istboma.sco.coeur.texte
import java.io.File
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors

/** Horloge Android : temps monotone depuis le démarrage, numéro de démarrage du téléphone. */
class HorlogeAndroid(private val activite: Activity) : Horloge {
    override fun maintenant(): OffsetDateTime = OffsetDateTime.now()
    override fun monotone(): Long = SystemClock.elapsedRealtime()
    override fun demarrage(): String = try {
        "boot-" + Settings.Global.getInt(activite.contentResolver, Settings.Global.BOOT_COUNT)
    } catch (e: Exception) {
        "t-" + ((System.currentTimeMillis() - SystemClock.elapsedRealtime()) / 60000)
    }
}

/**
 * IST/B-SCO : prise de présence pour les professeurs, assistants et CP.
 * Interface construite en code (aucune bibliothèque) : Android 8 à 15, 32 et 64 bits.
 */
class MainActivity : Activity() {
    private lateinit var moteur: Moteur
    private val travail = Executors.newSingleThreadExecutor()
    private val principal = Handler(Looper.getMainLooper())
    private val pile = ArrayList<() -> Unit>()
    private var deverrouille = false
    private var misEnPauseLe = 0L
    private var sauvegardeEnAttente: Pair<String, String>? = null
    private var dernierFichier: Uri? = null
    private var serveurDispo: Boolean? = null

    private val NOIR = Color.parseColor("#1C1C1C")
    private val JAUNE = Color.parseColor("#F0C400")
    private val FOND = Color.parseColor("#F4F4F2")
    private val GRIS = Color.parseColor("#6B6B66")
    private val VERT = Color.parseColor("#2E6B3A")
    private val VERT_CLAIR = Color.parseColor("#E8F3EA")
    private val ROUGE = Color.parseColor("#B3261E")
    private val ROUGE_CLAIR = Color.parseColor("#FBEAEA")
    private val BORD = Color.parseColor("#D9D9D4")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        moteur = Moteur(Depot(File(filesDir, "ist-b-sco")), HorlogeAndroid(this))
        demarrer()
    }

    override fun onPause() {
        super.onPause()
        misEnPauseLe = SystemClock.elapsedRealtime()
    }

    override fun onResume() {
        super.onResume()
        // Verrouillage par PIN après 5 minutes hors de l'application.
        if (deverrouille && moteur.aUnPin && misEnPauseLe > 0 && SystemClock.elapsedRealtime() - misEnPauseLe > 5 * 60_000) {
            deverrouille = false
            demarrer()
        }
    }

    private fun demarrer() {
        pile.clear()
        when {
            !moteur.connecte || moteur.sessionRefusee -> ouvrir { ecranConnexion() }
            moteur.aUnPin && !deverrouille -> ouvrir { ecranPin() }
            !moteur.aUnPin -> ouvrir { ecranNouveauPin() }
            else -> ouvrir { ecranAccueil() }
        }
    }

    // ---------- Navigation ----------

    private fun ouvrir(ecran: () -> Unit) {
        pile.add(ecran)
        ecran()
    }

    private fun remplacer(ecran: () -> Unit) {
        if (pile.isNotEmpty()) pile.removeAt(pile.size - 1)
        ouvrir(ecran)
    }

    private fun rafraichir() = pile.lastOrNull()?.invoke()

    @Deprecated("Navigation arrière")
    override fun onBackPressed() {
        if (pile.size > 1) {
            pile.removeAt(pile.size - 1)
            rafraichir()
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    // ---------- Petits outils d'interface ----------

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun fond(couleur: Int, bord: Int = 0, rayon: Int = 8): GradientDrawable = GradientDrawable().apply {
        setColor(couleur)
        cornerRadius = dp(rayon).toFloat()
        if (bord != 0) setStroke(dp(1), bord)
    }

    private fun texte(t: CharSequence, taille: Float = 15f, gras: Boolean = false, couleur: Int = NOIR): TextView = TextView(this).apply {
        text = t
        textSize = taille
        setTextColor(couleur)
        if (gras) setTypeface(typeface, Typeface.BOLD)
    }

    private fun bouton(t: String, principal_: Boolean = true, action: () -> Unit): Button = Button(this).apply {
        text = t
        isAllCaps = false
        textSize = 15f
        setTextColor(NOIR)
        background = fond(if (principal_) JAUNE else Color.WHITE, NOIR)
        setPadding(dp(14), dp(10), dp(14), dp(10))
        setOnClickListener { action() }
    }

    private fun champ(indice: String, valeur: String = "", type: Int = InputType.TYPE_CLASS_TEXT): EditText = EditText(this).apply {
        hint = indice
        setText(valeur)
        inputType = type
        textSize = 16f
        setSingleLine(type and InputType.TYPE_TEXT_FLAG_MULTI_LINE == 0)
        background = fond(Color.WHITE, BORD, 6)
        setPadding(dp(12), dp(10), dp(12), dp(10))
    }

    private fun colonne(): LinearLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun rangee(): LinearLayout = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }

    private fun LinearLayout.ajouter(v: View, haut: Int = 8, poids: Float = 0f, largeur: Int = ViewGroup.LayoutParams.MATCH_PARENT): View {
        val lp = if (orientation == LinearLayout.HORIZONTAL)
            LinearLayout.LayoutParams(if (poids > 0) 0 else ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, poids).apply { leftMargin = dp(haut) }
        else LinearLayout.LayoutParams(largeur, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(haut) }
        addView(v, lp)
        return v
    }

    private fun carte(): LinearLayout = colonne().apply {
        background = fond(Color.WHITE, BORD)
        setPadding(dp(14), dp(12), dp(14), dp(14))
    }

    private fun message(t: String, erreur: Boolean): TextView = texte(t, 14f, couleur = if (erreur) ROUGE else VERT).apply {
        background = fond(if (erreur) ROUGE_CLAIR else VERT_CLAIR, if (erreur) Color.parseColor("#E3A6A2") else Color.parseColor("#9BC3A3"), 6)
        setPadding(dp(12), dp(10), dp(12), dp(10))
    }

    private fun alerte(t: String): TextView = texte(t, 14f).apply {
        background = fond(Color.parseColor("#FFF8D6"), Color.parseColor("#E6D27A"), 6)
        setPadding(dp(12), dp(10), dp(12), dp(10))
    }

    /** Page : bandeau noir (titre) + contenu défilant. */
    private fun page(titre: String, sousTitre: String = "", retour: Boolean = pile.size > 1, contenu: LinearLayout.() -> Unit) {
        val racine = colonne().apply { setBackgroundColor(FOND) }
        val bandeau = rangee().apply {
            setBackgroundColor(NOIR)
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        if (retour) bandeau.addView(texte("‹", 30f, true, JAUNE).apply {
            setPadding(dp(4), 0, dp(14), 0)
            setOnClickListener { @Suppress("DEPRECATION") onBackPressed() }
        })
        val tt = colonne()
        tt.addView(texte(titre, 18f, true, JAUNE).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END })
        if (sousTitre.isNotEmpty()) tt.addView(texte(sousTitre, 13f, couleur = Color.parseColor("#CCCCCC")))
        bandeau.addView(tt, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val barreJaune = View(this).apply { setBackgroundColor(JAUNE) }
        racine.addView(bandeau)
        racine.addView(barreJaune, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(4)))
        val defile = ScrollView(this).apply { isFillViewport = true }
        val corps = colonne().apply { setPadding(dp(14), dp(6), dp(14), dp(24)) }
        corps.contenu()
        defile.addView(corps)
        racine.addView(defile, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(racine)
    }

    private fun erreur(e: Throwable) {
        val m = when (e) {
            is ErreurApp -> e.message
            else -> "Erreur inattendue : " + (e.message ?: e.javaClass.simpleName)
        }
        AlertDialog.Builder(this).setTitle("IST/B-SCO").setMessage(m).setPositiveButton("OK", null).show()
    }

    private fun info(titre: String, m: String, ensuite: (() -> Unit)? = null) {
        AlertDialog.Builder(this).setTitle(titre).setMessage(m).setPositiveButton("OK") { _, _ -> ensuite?.invoke() }.setCancelable(ensuite == null).show()
    }

    private fun confirmer(titre: String, m: String, oui: String, action: () -> Unit) {
        AlertDialog.Builder(this).setTitle(titre).setMessage(m).setPositiveButton(oui) { _, _ -> action() }.setNegativeButton("Annuler", null).show()
    }

    /** Action rapide sur le téléphone (sans réseau) : erreurs affichées. */
    private fun faire(action: () -> Unit) {
        try {
            action()
        } catch (e: Exception) {
            erreur(e)
        }
    }

    /** Travail réseau en arrière-plan avec une fenêtre d'attente. */
    private fun <T> enTache(attente: String, travail_: () -> T, ok: (T) -> Unit) {
        val d = AlertDialog.Builder(this).setMessage(attente).setCancelable(false).create()
        d.show()
        travail.execute {
            val r = runCatching(travail_)
            principal.post {
                if (!isFinishing) d.dismiss()
                r.fold(ok) { erreur(it) }
            }
        }
    }

    private fun heureLocale(s: String): String = lireHeure(s)?.atZoneSameInstant(java.time.ZoneId.systemDefault())?.format(DateTimeFormatter.ofPattern("dd/MM HH:mm")) ?: "—"
    private fun nomTypes(a: Appel) = a.types.joinToString(" + ") { TYPES_SEANCE[it] ?: it } + if (a.typeAutre.isNotEmpty()) " (${a.typeAutre})" else ""
    private fun nomAppareil(): String = (Build.MANUFACTURER.replaceFirstChar { it.uppercase() } + " " + Build.MODEL).trim()

    // ---------- Connexion ----------

    private fun ecranConnexion() = page("IST/B-SCO", "Prise de présence — IST Boma", retour = false) {
        ajouter(texte("Connexion du téléphone", 18f, true), 14)
        ajouter(texte("Le secrétariat vous donne un code (ex. K7Q2-M9XA). Le téléphone doit être sur le Wi-Fi de l'Institut.", 14f, couleur = GRIS))
        if (moteur.sessionRefusee) ajouter(alerte("Votre code a été régénéré ou désactivé. Saisissez le nouveau code : vos appels restent sur le téléphone."), 10)
        val adresse = champ("Adresse du serveur (ex. 192.168.1.10:8100)", moteur.adresseServeur.removePrefix("http://"), InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        val code = champ("Code de connexion", "", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD)
        ajouter(texte("Adresse du serveur PEGSCO", 13f, couleur = GRIS), 16)
        ajouter(adresse, 4)
        ajouter(texte("Code de connexion", 13f, couleur = GRIS), 12)
        ajouter(code, 4)
        ajouter(bouton("Se connecter") {
            val a = adresse.text.toString()
            val c = code.text.toString()
            enTache("Connexion au serveur…", { moteur.connecter(a, c, nomAppareil()) }) {
                demarrerApresConnexion()
            }
        }, 18)
        ajouter(bouton("Vérifier que le serveur répond", false) {
            val a = adresse.text.toString()
            enTache("Recherche du serveur…", { cd.istboma.sco.coeur.Client.normaliserAdresse(a).let { moteur.serveurDisponible(it) } }) { ok ->
                info("Serveur", if (ok) "Le serveur PEGSCO répond." else "Serveur injoignable : vérifiez le Wi-Fi, l'adresse, et que le PC PEGSCO est allumé.")
            }
        }, 10)
        ajouter(texte("Version " + versionApp(), 12f, couleur = GRIS), 24)
    }

    private fun demarrerApresConnexion() {
        deverrouille = true
        pile.clear()
        if (!moteur.aUnPin) ouvrir { ecranNouveauPin() } else ouvrir { ecranAccueil() }
    }

    private fun ecranNouveauPin() = page("Code PIN", "Protège l'application sur ce téléphone", retour = false) {
        ajouter(texte("Choisissez un code PIN de 4 à 8 chiffres. Il sera demandé à l'ouverture de l'application.", 14f, couleur = GRIS), 14)
        val p1 = champ("Nouveau PIN", "", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD)
        val p2 = champ("Confirmer le PIN", "", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD)
        ajouter(p1, 14)
        ajouter(p2, 8)
        ajouter(bouton("Enregistrer le PIN") {
            faire {
                if (p1.text.toString() != p2.text.toString()) throw ErreurApp("Les deux PIN sont différents.")
                moteur.definirPin(p1.text.toString())
                deverrouille = true
                pile.clear()
                ouvrir { ecranAccueil() }
            }
        }, 16)
    }

    private var essaisPin = 0
    private fun ecranPin() = page("IST/B-SCO", moteur.nomUtilisateur, retour = false) {
        ajouter(texte("Saisissez votre code PIN", 18f, true), 24)
        val p = champ("PIN", "", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD)
        ajouter(p, 12)
        ajouter(bouton("Ouvrir") {
            if (moteur.verifierPin(p.text.toString())) {
                essaisPin = 0
                deverrouille = true
                pile.clear()
                ouvrir { ecranAccueil() }
            } else {
                essaisPin++
                p.setText("")
                erreur(ErreurApp(if (essaisPin >= 5) "PIN incorrect. PIN oublié : demandez un nouveau code au secrétariat et reconnectez-vous (menu « PIN oublié »)." else "PIN incorrect."))
            }
        }, 14)
        ajouter(bouton("PIN oublié", false) {
            confirmer("PIN oublié", "Vous allez vous reconnecter avec un code du secrétariat. Les appels non envoyés restent sur le téléphone.", "Continuer") {
                moteur.deconnecter(forcer = true)
                pile.clear()
                ouvrir { ecranConnexion() }
            }
        }, 10)
    }

    // ---------- Accueil ----------

    private fun ecranAccueil() {
        val d = moteur.donnees
        val p = moteur.profil
        val sous = if (moteur.estCP) "CP ${p.texte("classe")} ${p.texte("filiere")} · ${p.texte("annee")}" else p.texte("fonction").lowercase().replaceFirstChar { it.uppercase() }
        page(moteur.nomUtilisateur, sous, retour = false) {
            val etat = carte()
            val nb = moteur.nbAEnvoyer()
            etat.addView(texte(
                when (serveurDispo) { true -> "● Serveur disponible"; false -> "● Serveur injoignable"; null -> "● Serveur : appuyez sur Actualiser" },
                14f, true, when (serveurDispo) { true -> VERT; false -> ROUGE; null -> GRIS }))
            etat.addView(texte("Dernière actualisation : " + heureLocale(moteur.derniereActualisation), 13f, couleur = GRIS))
            etat.addView(texte(if (nb == 0) "Rien à envoyer." else "$nb élément(s) à envoyer.", 15f, true, if (nb == 0) VERT else NOIR))
            val br = rangee()
            br.ajouter(bouton("Actualiser", false) { actualiser() }, 0, 1f)
            br.ajouter(bouton("Envoyer ($nb)") { envoyer() }, 8, 1f)
            etat.ajouter(br, 10)
            ajouter(etat, 12)
            if (moteur.heureDouteuse) ajouter(alerte("L'heure du téléphone diffère de celle du serveur (${moteur.ecartHorlogeSecondes / 60} min). Réglez la date et l'heure du téléphone : sinon vos appels seront marqués « heure douteuse »."), 10)
            if (moteur.sessionRefusee) ajouter(alerte("Code régénéré ou désactivé : reconnectez-vous (Paramètres › Changer de code)."), 10)

            ajouter(texte("Faire l'appel", 17f, true), 18)
            if (d == null || d.cours.isEmpty()) {
                ajouter(texte(if (d == null) "Appuyez sur Actualiser pour recevoir vos cours." else "Aucun cours attribué pour le moment (voir le secrétariat).", 14f, couleur = GRIS), 6)
            } else {
                for (c in d.cours) {
                    val k = carte()
                    k.addView(texte(c.intitule, 16f, true))
                    k.addView(texte("${c.classe} · ${c.filieres} · ${c.etudiants.size} étudiant(s) · ${c.annee}", 13f, couleur = GRIS))
                    k.setOnClickListener { nouvelAppel(c) }
                    ajouter(k, 8)
                }
            }

            val ap = moteur.appels()
            ajouter(texte("Mes appels", 17f, true), 20)
            if (ap.isEmpty()) ajouter(texte("Aucun appel pour le moment.", 14f, couleur = GRIS), 6)
            for (a in ap.take(60)) ajouter(ligneAppel(a), 8)

            ajouter(texte("Autres", 17f, true), 20)
            if (!moteur.estCP) ajouter(bouton("Signalements et demandes de blocage", false) { ouvrir { ecranSignalements() } }, 8)
            ajouter(bouton("Fichier de sauvegarde (serveur injoignable)", false) { ouvrir { ecranSauvegarde() } }, 8)
            ajouter(bouton("Paramètres", false) { ouvrir { ecranParametres() } }, 8)
        }
    }

    private fun ligneAppel(a: Appel): View {
        val c = moteur.donnees?.cours(a.coursId)
        val k = carte()
        k.addView(texte((c?.titre ?: "Cours ${a.coursId}") + " — " + a.date, 15f, true))
        k.addView(texte(nomTypes(a) + " · ${a.nb("P")} présent(s), ${a.nb("A")} absent(s)", 13f, couleur = GRIS))
        val (lib, coul) = when {
            a.refus.isNotEmpty() -> "Refusé par le serveur : ${a.refus}" to ROUGE
            moteur.etat(a) == EtatAppel.EN_COURS -> "En cours — pas encore validé" to Color.parseColor("#8A6D00")
            a.aEnvoyer -> (if (moteur.etat(a) == EtatAppel.VERROUILLE) "Verrouillé · à envoyer" else "Validé · à envoyer · modifiable encore ${moteur.minutesRestantes(a)} min") to NOIR
            moteur.etat(a) == EtatAppel.VALIDE_MODIFIABLE -> "Envoyé (v${a.envoyeVersion}) · modifiable encore ${moteur.minutesRestantes(a)} min" to VERT
            else -> "Envoyé (v${a.envoyeVersion}) · verrouillé" to VERT
        }
        k.addView(texte(lib, 13f, true, coul))
        k.setOnClickListener { ouvrir { ecranAppel(a.uid) } }
        return k
    }

    private fun actualiser() {
        val connecte = moteur.connecte && !moteur.sessionRefusee
        enTache("Actualisation…", {
            val dispo = moteur.serveurDisponible()
            if (dispo && connecte) moteur.actualiser()
            dispo
        }) { dispo ->
            serveurDispo = dispo
            rafraichir()
            if (!dispo) info("Serveur injoignable", "Vérifiez le Wi-Fi de l'Institut et que le PC PEGSCO est allumé. Vos appels restent sur le téléphone : envoyez-les plus tard, ou faites un fichier de sauvegarde.")
        }
    }

    private fun envoyer() {
        if (moteur.nbAEnvoyer() == 0) {
            info("Envoyer", "Rien à envoyer : seuls les appels VALIDÉS partent au serveur.")
            return
        }
        enTache("Envoi au serveur…", { moteur.envoyer() }) { b ->
            serveurDispo = true
            rafraichir()
            info("Envoi terminé", b.resume + if (b.raisons.isEmpty()) "" else "\n\n" + b.raisons.joinToString("\n• ", "• "))
        }
    }

    // ---------- Appel ----------

    private fun choisirTypes(titre: String, initiaux: List<String>, autreInitial: String, ok: (List<String>, String) -> Unit) {
        val z = colonne().apply { setPadding(dp(20), dp(8), dp(20), 0) }
        val cases = TYPES_SEANCE.map { (code, lib) -> code to CheckBox(this).apply { text = lib; textSize = 16f; isChecked = code in initiaux } }
        cases.forEach { z.addView(it.second) }
        val autre = champ("Préciser (si « Autre »)", autreInitial)
        z.ajouter(autre, 6)
        AlertDialog.Builder(this).setTitle(titre).setView(ScrollView(this).apply { addView(z) })
            .setPositiveButton("OK") { _, _ -> faire { ok(cases.filter { it.second.isChecked }.map { it.first }, autre.text.toString()) } }
            .setNegativeButton("Annuler", null).show()
    }

    private fun nouvelAppel(c: Cours) {
        choisirTypes("Séance de « ${c.intitule} » — cochez tout ce qui s'applique", listOf("COURS"), "") { types, autre ->
            val a = moteur.nouvelAppel(c.id, types, autre)
            ouvrir { ecranAppel(a.uid) }
        }
    }

    private fun ecranAppel(uid: String) {
        val a = try { moteur.appel(uid) } catch (e: Exception) { @Suppress("DEPRECATION") onBackPressed(); return }
        val c = moteur.donnees?.cours(a.coursId)
        val etat = moteur.etat(a)
        val modifiable = etat != EtatAppel.VERROUILLE
        page(c?.intitule ?: "Appel", "${c?.classe ?: ""} · ${a.date} · ${heureLocale(a.heureAppel)}") {
            val entete = carte()
            entete.addView(texte(nomTypes(a), 16f, true))
            entete.addView(texte("${a.nb("P")} présent(s) · ${a.nb("A")} absent(s) · ${moteur.nonMarques(a).size} non marqué(s)", 14f, couleur = GRIS))
            entete.addView(texte(when (etat) {
                EtatAppel.EN_COURS -> "Pas encore validé : marquez chaque étudiant puis validez."
                EtatAppel.VALIDE_MODIFIABLE -> "Validé le ${heureLocale(a.valideLe)} · modifiable encore ${moteur.minutesRestantes(a)} min" + if (a.aEnvoyer) " · à envoyer" else " · envoyé"
                EtatAppel.VERROUILLE -> "Verrouillé (plus d'1 h après la validation). Correction : secrétariat seulement."
            }, 13f, true, if (etat == EtatAppel.VERROUILLE) ROUGE else NOIR))
            if (a.refus.isNotEmpty()) entete.addView(texte("Refusé par le serveur : ${a.refus}", 13f, true, ROUGE))
            if (modifiable) {
                val br = rangee()
                br.ajouter(bouton("Type de séance", false) { choisirTypes("Type de séance", a.types, a.typeAutre) { t, x -> moteur.changerTypes(uid, t, x); rafraichir() } }, 0, 1f)
                if (moteur.nonMarques(a).isNotEmpty()) br.ajouter(bouton("Autres présents", false) { faire { moteur.tousPresents(uid); rafraichir() } }, 8, 1f)
                entete.ajouter(br, 10)
            }
            ajouter(entete, 12)

            val etudiants = c?.etudiants ?: emptyList()
            var n = 0
            for (e in etudiants) {
                n++
                ajouter(ligneEtudiant(n, e, a, modifiable), 6)
            }
            if (c == null) ajouter(alerte("Ce cours n'est plus dans vos données (actualisez)."), 10)

            if (etat == EtatAppel.EN_COURS) {
                ajouter(bouton("Valider l'appel") {
                    faire {
                        val manque = moteur.nonMarques(a)
                        if (manque.isNotEmpty()) throw ErreurApp("${manque.size} étudiant(s) non marqué(s) : ${manque.take(3).joinToString { it.nomComplet }}${if (manque.size > 3) "…" else ""}")
                        confirmer("Valider l'appel", "${a.nb("P")} présent(s), ${a.nb("A")} absent(s).\n\nAprès validation, vous pourrez encore corriger pendant 1 heure ; ensuite l'appel sera verrouillé.", "Valider") {
                            faire { moteur.valider(uid); rafraichir() }
                        }
                    }
                }, 16)
                ajouter(bouton("Supprimer ce brouillon", false) {
                    confirmer("Supprimer", "Supprimer cet appel non validé ?", "Supprimer") { faire { moteur.supprimerBrouillon(uid); @Suppress("DEPRECATION") onBackPressed() } }
                }, 8)
            } else {
                if (a.aEnvoyer) ajouter(bouton("Envoyer maintenant") { envoyer() }, 16)
                if (!moteur.estCP) ajouter(bouton("Comparer avec l'appel du CP", false) { ouvrir { ecranComparaison(a.coursId, a.date, uid) } }, 8)
            }
        }
    }

    private fun ligneEtudiant(n: Int, e: Etudiant, a: Appel, modifiable: Boolean): View {
        val st = a.presences[e.id]
        val r = rangee().apply {
            background = fond(Color.WHITE, BORD, 6)
            setPadding(dp(10), dp(6), dp(8), dp(6))
        }
        val noms = colonne()
        noms.addView(texte("$n. ${e.nomComplet}", 15f, true).apply { maxLines = 2 })
        noms.addView(texte(listOf(e.matricule, e.filiere).filter { it.isNotEmpty() }.joinToString(" · "), 12f, couleur = GRIS))
        r.addView(noms, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        fun choix(lib: String, code: String, couleur: Int, clair: Int): Button = Button(this).apply {
            text = lib
            isAllCaps = false
            textSize = 14f
            minWidth = dp(64)
            minimumWidth = dp(64)
            val actif = st == code
            setTextColor(if (actif) Color.WHITE else couleur)
            background = fond(if (actif) couleur else clair, couleur, 18)
            isEnabled = modifiable
            setOnClickListener { faire { moteur.marquer(a.uid, e.id, code); rafraichir() } }
        }
        r.ajouter(choix("Présent", "P", VERT, VERT_CLAIR), 6)
        r.ajouter(choix("Absent", "A", ROUGE, ROUGE_CLAIR), 6)
        if (!moteur.estCP && a.valide) {
            r.setOnLongClickListener { signalerEtudiant(a.coursId, e, a.uid); true }
        }
        return r
    }

    // ---------- Comparaison CP / professeur ----------

    private fun ecranComparaison(coursId: Long, date: String, uid: String) {
        val c = moteur.donnees?.cours(coursId)
        page("Comparer avec le CP", "${c?.titre ?: ""} · $date") {
            val cp = moteur.appelsCP(coursId, date)
            ajouter(texte("Appels des CP reçus par le serveur (appuyez sur Actualiser pour les recevoir).", 13f, couleur = GRIS), 10)
            if (cp.isEmpty()) {
                ajouter(alerte("Aucun appel de CP reçu pour ce cours ce jour-là."), 10)
                return@page
            }
            for (x in cp) {
                val k = carte()
                k.addView(texte(x.auteur, 15f, true))
                k.addView(texte("${x.types.joinToString(" + ") { TYPES_SEANCE[it] ?: it }} · version ${x.version} · ${x.presences.values.count { it == "P" }} présent(s), ${x.presences.values.count { it == "A" }} absent(s)" +
                    if (x.heureDouteuse) " · heure douteuse" else "", 13f, couleur = GRIS))
                if (x.historique.size > 1) k.addView(texte("Modifié ${x.historique.size - 1} fois après le premier envoi.", 13f, couleur = Color.parseColor("#8A6D00")))
                ajouter(k, 8)
            }
            val ecarts = moteur.comparer(coursId, date)
            ajouter(texte(if (ecarts.isEmpty()) "Aucune différence avec votre appel." else "${ecarts.size} différence(s) :", 16f, true, if (ecarts.isEmpty()) VERT else ROUGE), 16)
            for (e in ecarts) {
                val k = carte()
                k.addView(texte(e.etudiant.nomComplet, 15f, true))
                k.addView(texte("Selon le CP : ${if (e.selonCP == "P") "Présent" else "Absent"} · Selon vous : ${if (e.selonMoi == "P") "Présent" else "Absent"}", 14f))
                k.ajouter(bouton("Signaler cet écart au secrétariat", false) {
                    faire {
                        moteur.signaler(coursId, e.etudiant.id, uid, "ECART", "CP (${e.auteurCP}) : ${if (e.selonCP == "P") "présent" else "absent"} ; intervenant : ${if (e.selonMoi == "P") "présent" else "absent"}")
                        info("Signalement", "Écart enregistré : il partira au prochain envoi.")
                    }
                }, 8)
                ajouter(k, 8)
            }
        }
    }

    // ---------- Signalements ----------

    private fun signalerEtudiant(coursId: Long, e: Etudiant, appelUid: String) {
        val z = colonne().apply { setPadding(dp(20), dp(8), dp(20), 0) }
        val groupe = RadioGroup(this)
        MOTIFS.entries.forEachIndexed { i, (code, lib) -> groupe.addView(RadioButton(this).apply { id = 1000 + i; text = lib; tag = code; textSize = 15f }) }
        z.addView(groupe)
        val detail = champ("Détail (facultatif)", "", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
        z.ajouter(detail, 6)
        val blocage = CheckBox(this).apply { text = "Demander aussi le blocage de l'examen (l'Institut décide)"; textSize = 14f }
        z.ajouter(blocage, 6)
        AlertDialog.Builder(this).setTitle("Signaler : ${e.nomComplet}").setView(ScrollView(this).apply { addView(z) })
            .setPositiveButton("Enregistrer") { _, _ ->
                faire {
                    val motif = groupe.findViewById<RadioButton>(groupe.checkedRadioButtonId)?.tag as? String ?: throw ErreurApp("Choisissez un motif.")
                    moteur.signaler(coursId, e.id, appelUid, motif, detail.text.toString())
                    if (blocage.isChecked) moteur.demanderBlocage(coursId, e.id, (MOTIFS[motif] ?: motif) + if (detail.text.isNotBlank()) " : " + detail.text else "")
                    info("Signalement", "Enregistré : il partira au prochain envoi.")
                    rafraichir()
                }
            }.setNegativeButton("Annuler", null).show()
    }

    private fun ecranSignalements() = page("Signalements", "Preuves pour l'Institut") {
        val d = moteur.donnees
        ajouter(texte("Pour signaler un étudiant pendant un appel validé, appuyez longuement sur son nom. Vous pouvez aussi signaler ici.", 13f, couleur = GRIS), 10)
        if (d != null && d.cours.isNotEmpty()) {
            val k = carte()
            val sCours = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, d.cours.map { it.titre }) }
            val sEtu = Spinner(this)
            fun majEtudiants() {
                val c = d.cours[sCours.selectedItemPosition.coerceAtLeast(0)]
                sEtu.adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, c.etudiants.map { it.nomComplet })
            }
            majEtudiants()
            sCours.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: android.widget.AdapterView<*>?, v: View?, pos: Int, id: Long) = majEtudiants()
                override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
            }
            k.addView(texte("Nouveau signalement", 16f, true))
            k.ajouter(texte("Cours", 13f, couleur = GRIS), 8); k.addView(sCours)
            k.ajouter(texte("Étudiant", 13f, couleur = GRIS), 8); k.addView(sEtu)
            k.ajouter(bouton("Continuer", false) {
                val c = d.cours[sCours.selectedItemPosition.coerceAtLeast(0)]
                val e = c.etudiants.getOrNull(sEtu.selectedItemPosition) ?: return@bouton
                val dernier = moteur.appels().firstOrNull { it.coursId == c.id && it.valide && it.date == moteur.aujourdhui() }
                signalerEtudiant(c.id, e, dernier?.uid ?: "")
            }, 10)
            ajouter(k, 10)
        }
        val sg = moteur.signalements()
        val dm = moteur.demandes()
        ajouter(texte("Mes signalements", 16f, true), 18)
        if (sg.isEmpty()) ajouter(texte("Aucun.", 14f, couleur = GRIS), 4)
        for (s in sg.take(80)) {
            val e = d?.cours(s.coursId)?.etudiants?.firstOrNull { it.id == s.etudiantId }
            val k = carte()
            k.addView(texte("${MOTIFS[s.motif] ?: s.motif} — ${e?.nomComplet ?: "étudiant ${s.etudiantId}"}", 15f, true))
            if (s.texte.isNotEmpty()) k.addView(texte(s.texte, 13f))
            k.addView(texte("${heureLocale(s.creeLe)} · " + when { s.refus.isNotEmpty() -> "refusé : ${s.refus}"; s.envoye -> "envoyé"; else -> "à envoyer" }, 12f, couleur = if (s.refus.isNotEmpty()) ROUGE else GRIS))
            ajouter(k, 6)
        }
        ajouter(texte("Mes demandes de blocage", 16f, true), 18)
        if (dm.isEmpty()) ajouter(texte("Aucune.", 14f, couleur = GRIS), 4)
        for (x in dm.take(80)) {
            val e = d?.cours(x.coursId)?.etudiants?.firstOrNull { it.id == x.etudiantId }
            val k = carte()
            k.addView(texte(e?.nomComplet ?: "étudiant ${x.etudiantId}", 15f, true))
            k.addView(texte(x.motif, 13f))
            k.addView(texte("${heureLocale(x.creeLe)} · " + when { x.refus.isNotEmpty() -> "refusée : ${x.refus}"; x.envoye -> "envoyée (décision de l'Institut)"; else -> "à envoyer" }, 12f, couleur = GRIS))
            ajouter(k, 6)
        }
    }

    // ---------- Sauvegarde ----------

    private fun ecranSauvegarde() = page("Fichier de sauvegarde", "Quand le serveur est injoignable") {
        ajouter(texte("Le fichier contient vos appels VALIDÉS, signés avec la clé de ce téléphone. Remettez-le au secrétariat (câble, Bluetooth, WhatsApp…) : il l'importe dans PEGSCO. Un fichier modifié est refusé ; un appel déjà reçu n'est pas compté deux fois.", 14f, couleur = GRIS), 10)
        ajouter(bouton("Tout ce qui n'est pas encore envoyé") { preparerSauvegarde(null) }, 16)
        ajouter(bouton("Tous les appels d'aujourd'hui", false) { preparerSauvegarde(moteur.aujourdhui()) }, 8)
        val dates = moteur.appels().filter { it.valide }.map { it.date }.distinct().filter { it != moteur.aujourdhui() }.take(10)
        for (dt in dates) ajouter(bouton("Appels du $dt", false) { preparerSauvegarde(dt) }, 8)
        dernierFichier?.let { u ->
            ajouter(message("Dernier fichier enregistré.", false), 16)
            ajouter(bouton("Partager ce fichier (WhatsApp, Bluetooth…)", false) { partager(u) }, 8)
        }
    }

    private fun preparerSauvegarde(date: String?) = faire {
        val s = moteur.sauvegarde(date)
        sauvegardeEnAttente = s
        val i = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_TITLE, s.first)
        }
        @Suppress("DEPRECATION")
        startActivityForResult(i, DEMANDE_SAUVEGARDE)
    }

    @Deprecated("Résultat du choix de l'emplacement du fichier")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != DEMANDE_SAUVEGARDE) return
        val s = sauvegardeEnAttente ?: return
        val u = data?.data
        if (resultCode != RESULT_OK || u == null) return
        faire {
            contentResolver.openOutputStream(u, "w")?.use { it.write(s.second.toByteArray(Charsets.UTF_8)) } ?: throw ErreurApp("Écriture du fichier impossible.")
            sauvegardeEnAttente = null
            dernierFichier = u
            rafraichir()
            confirmer("Sauvegarde", "Fichier « ${s.first} » enregistré. Le partager maintenant ?", "Partager") { partager(u) }
        }
    }

    private fun partager(u: Uri) = faire {
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, u)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(i, "Envoyer la sauvegarde au secrétariat"))
    }

    // ---------- Paramètres ----------

    private fun ecranParametres() = page("Paramètres") {
        val k = carte()
        k.addView(texte(moteur.nomUtilisateur, 16f, true))
        k.addView(texte("Serveur : ${moteur.adresseServeur}", 13f, couleur = GRIS))
        k.addView(texte("Téléphone : ${nomAppareil()}", 13f, couleur = GRIS))
        k.addView(texte("Version de l'application : ${versionApp()}", 13f, couleur = GRIS))
        ajouter(k, 12)
        ajouter(bouton("Changer le code PIN", false) { ouvrir { ecranNouveauPin() } }, 12)
        ajouter(bouton("Changer de code / de serveur", false) {
            confirmer("Changer de code", "Saisissez l'adresse et le code donnés par le secrétariat. Vos appels restent sur le téléphone.", "Continuer") {
                ouvrir { ecranConnexion() }
            }
        }, 8)
        ajouter(bouton("Se déconnecter de ce téléphone", false) {
            confirmer("Déconnexion", "Le téléphone oubliera votre code (les appels déjà envoyés restent au serveur).", "Se déconnecter") {
                faire {
                    moteur.deconnecter()
                    deverrouille = false
                    pile.clear()
                    ouvrir { ecranConnexion() }
                }
            }
        }, 8)
        ajouter(texte("Règles : un appel validé reste modifiable 1 heure, puis il est verrouillé (correction par le secrétariat seulement). Rien n'est envoyé automatiquement : appuyez sur Actualiser puis Envoyer.", 13f, couleur = GRIS), 18)
    }

    private fun versionApp(): String = try {
        packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
    } catch (e: Exception) { "?" }

    companion object {
        private const val DEMANDE_SAUVEGARDE = 41
    }
}
