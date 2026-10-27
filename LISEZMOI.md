# IST/B-SCO — application Android de prise de présence

Application des professeurs, assistants et CP de l'IST Boma. Elle fait l'appel
sur le téléphone (hors ligne) et l'envoie au serveur **PEGSCO** du secrétariat.
Les étudiants continuent à signer le cahier papier.

- Android 8 à 15, téléphones 32 et 64 bits (Kotlin pur, aucune bibliothèque native).
- Réseau local de l'Institut en HTTP ; chaque envoi est signé (HMAC-SHA256) avec la clé du téléphone.
- Spécification complète : [CAHIER-DES-CHARGES.md](CAHIER-DES-CHARGES.md).

## Obtenir l'APK

L'APK est compilé par **GitHub Actions** à chaque envoi sur `main` :

1. Onglet **Releases** du dépôt → dernière version → fichier `IST-B-SCO-1.0.N.apk`.
2. Copier le fichier sur le téléphone, l'ouvrir, autoriser l'installation depuis cette source.

On peut aussi relancer la compilation : onglet **Actions** → **APK** → **Run workflow**.

## Clé de signature (à faire une fois, avant la distribution aux professeurs)

Sans clé, l'APK est signé avec une clé de **débogage** qui change à chaque
compilation : chaque mise à jour obligerait à désinstaller l'application
(et perdre les appels non envoyés). Il faut donc la clé de l'Institut :

GitHub → dépôt → **Settings** → **Secrets and variables** → **Actions** →
**New repository secret**, et créer les 4 secrets :

| Secret | Valeur |
|---|---|
| `ANDROID_KEYSTORE_B64` | contenu du fichier `cle-IST-B-SCO.jks` en base64 |
| `ANDROID_KEYSTORE_MDP` | mot de passe du fichier |
| `ANDROID_KEY_ALIAS` | `ist-b-sco` |
| `ANDROID_KEY_MDP` | mot de passe de la clé (le même) |

Gardez une copie du fichier `.jks` et du mot de passe en lieu sûr : sans eux,
impossible de publier une mise à jour installable par-dessus l'application.

## Côté PEGSCO (secrétariat)

1. Outil **PEGSCO Configuration** : écoute sur le **réseau local** (sinon les téléphones ne joignent pas le serveur).
2. Menu **Présences (téléphones)** → *Intervenants et codes*, *Cours et attributions*
   (après attribution, le code apparaît sur la fiche), *CP par filière*.
3. Sur le téléphone : adresse affichée par PEGSCO (ex. `192.168.1.10:8100`) + code.

## Règles

- Présent / Absent seulement ; plusieurs types par séance (Cours, Interrogation, TP, Examen, Autre).
- Rien n'est envoyé automatiquement : **Actualiser** (le serveur répond ?) puis **Envoyer**.
- Appel validé : modifiable **1 heure**, puis verrouillé (correction par le secrétariat, avec raison).
- Le délai est mesuré avec l'horloge interne du téléphone : reculer l'heure ne le prolonge pas.
- Le CP ne voit que sa filière ; le professeur voit l'appel du CP et les écarts.
- Serveur injoignable : **Fichier de sauvegarde** signé, importé par le secrétariat.

## Développement

- `app/src/main/java/cd/istboma/sco/coeur/` : cœur en Kotlin pur (testé sur PC).
- `app/src/test/` : tests du cœur (lancés par la compilation GitHub).
- `outils/integration/` : test du cœur contre un vrai serveur PEGSCO.
