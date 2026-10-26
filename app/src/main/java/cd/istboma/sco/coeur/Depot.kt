package cd.istboma.sco.coeur

import java.io.File

// Stockage sur le téléphone (dossier privé de l'application) : fichiers JSON
// écrits de façon sûre (fichier temporaire puis remplacement), pour ne jamais
// perdre un appel si la batterie lâche pendant l'écriture.

class Depot(private val dossier: File) {
    init {
        dossier.mkdirs()
    }

    fun lire(nom: String): Map<String, Any?>? {
        val f = File(dossier, nom)
        val sec = File(dossier, "$nom.precedent")
        for (x in listOf(f, sec)) {
            if (!x.exists()) continue
            try {
                return Json.objet(x.readText(Charsets.UTF_8))
            } catch (e: Exception) {
                // fichier abîmé : on essaie la copie précédente
            }
        }
        return null
    }

    fun ecrire(nom: String, contenu: Map<String, Any?>) {
        val f = File(dossier, nom)
        val tmp = File(dossier, "$nom.tmp")
        tmp.writeText(Json.ecrire(contenu), Charsets.UTF_8)
        if (f.exists()) {
            val sec = File(dossier, "$nom.precedent")
            sec.delete()
            f.renameTo(sec)
        }
        if (!tmp.renameTo(f)) {
            f.writeText(Json.ecrire(contenu), Charsets.UTF_8)
            tmp.delete()
        }
    }

    fun effacer(nom: String) {
        File(dossier, nom).delete()
        File(dossier, "$nom.precedent").delete()
    }
}
