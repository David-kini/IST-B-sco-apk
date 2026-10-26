# IST/B-SCO — Application Android de prise de présence

Cahier des charges (version 1 — à valider avant tout code).
Application pour les **professeurs, assistants et CP** de l'IST/Boma, reliée à
**PEGSCO** (logiciel de scolarité sur le PC serveur).

---

## 1. Principe

- Les étudiants continuent de **signer eux-mêmes le cahier de présence**, une liste
  papier imprimée par PEGSCO. Rien ne change.
- Le **professeur, l'assistant ou le CP** fait **l'appel sur son téléphone**. C'est le
  contrôle **anti-fraude** : un étudiant ne peut pas signer pour un absent.
- L'application marche **sans réseau**. Les appels restent dans le téléphone jusqu'à
  l'envoi.
- **Pas d'envoi automatique** :
  - sur le Wi-Fi de l'IST, l'utilisateur appuie sur **Actualiser** pour savoir si le
    PC serveur est disponible, puis sur **Envoyer** ;
  - si le serveur n'est pas disponible, on réessaie **un autre jour** : rien n'est perdu.
- PEGSCO enregistre les **présents et les absents** et produit les rapports.

## 2. Qui fait quoi

| Qui | Où | Rôle |
|---|---|---|
| Secrétariat / Institut | PEGSCO (PC) | Crée les cours, les attribue, donne les codes de connexion, consulte les rapports, tranche les écarts et les demandes de blocage |
| Professeur / assistant / autre intervenant | Application | Fait l'appel de **ses** cours, indique le type de séance (interrogation…), signale des cas |
| CP (chef de promotion, un étudiant) | Application | **Un CP par filière** (ex. L1 SG, L1 LABO) : fait l'appel des cours de **sa filière** seulement |

- L'appel d'une séance peut être fait **par le CP, par le professeur, ou par les deux**.
  Rien ne l'interdit. Quand les deux appellent, PEGSCO **compare** et **signale les
  écarts**.

## 3. Côté PEGSCO (à ajouter, sans rien enlever)

### 3.1 Intervenants
Fiche avec :
- nom, post-nom, prénom, téléphone ;
- **fonction** : Professeur, Assistant, Chef de travaux, Autre ;
- liste des cours attribués.

### 3.2 Cours
- Intitulé, **classe** et **filière** (ex. « Anatomie — L1 SG »), année académique.
- **Attribution** à un ou plusieurs intervenants (titulaire, assistant…).

### 3.3 CP
- **Un CP par filière** : L1 SG a son CP, L1 LABO a le sien, et ainsi de suite.
- Dans la fiche d'un étudiant : case **« Chef de promotion (CP) »**, pour l'année.
  Elle vaut pour sa filière, dans sa classe.
- Le CP ne fait l'appel que des cours de **sa filière**, et seulement pour les
  étudiants de **sa filière**.
- Pour un cours commun à plusieurs filières (tronc commun), chaque CP appelle
  uniquement les étudiants de sa filière. Le professeur, lui, peut appeler toutes les
  filières de son cours.

### 3.4 Code de connexion à l'application
- Dès qu'une personne a un cours attribué (intervenant) ou qu'elle est désignée CP
  (étudiant), un **code de connexion** apparaît **sur sa fiche** :
  - par exemple `K7Q2-M9XA`, accompagné d'un **QR code** à scanner ;
  - boutons **Régénérer** (code perdu) et **Désactiver** (téléphone perdu, départ).
- Le code ne donne accès qu'aux cours de cette personne.

### 3.5 Réception des appels
Chaque appel reçu est gardé avec :
- le cours, la date, le **type de séance**, la liste présent ou absent ;
- **qui** l'a fait, **l'heure de l'appel** (sur le téléphone), **l'heure d'envoi** et le
  téléphone utilisé.

Un appel reçu **n'est plus modifiable depuis le téléphone**.

### 3.6 Écrans et rapports
- **Séances d'un cours** : date, type (cours, interrogation, TP, examen…), qui a fait
  l'appel, nombre de présents et d'absents.
- **Assiduité d'un étudiant par cours** :
  - nombre de présences sur nombre de séances (ex. 9 / 12) ;
  - présence à chaque interrogation, TP et examen ;
  - signalements.
- **Écarts CP / professeur** : même séance, étudiant présent pour l'un et absent pour
  l'autre. Le secrétariat tranche, et la décision est gardée.
- **Demandes de blocage** : celles des professeurs. L'Institut **valide ou refuse**,
  avec la raison.
- **Rapport par intervenant** : séances données, interrogations et TP faits.
- **Absents du jour**, par promotion.

## 4. L'application Android

### 4.1 Première utilisation
1. Sur le Wi-Fi de l'IST, saisir l'**adresse du PC serveur** (ou scanner le QR code) et
   le **code de connexion**.
2. L'application télécharge les **cours** de la personne et la **liste des étudiants**
   (pour un CP : seulement ceux de sa filière).
3. L'utilisateur choisit un **code PIN** pour ouvrir l'application.

### 4.2 Faire l'appel (hors ligne)
1. Choisir le **cours**. La date est celle du jour.
2. Choisir le **type de séance** : **Cours**, **Interrogation**, **TP**, **Examen** ou
   **Autre**. C'est ici qu'on **signale une interrogation**.
3. Les noms défilent dans l'ordre de la liste, avec deux gros boutons **Présent / Absent**.
4. Un récapitulatif s'affiche (ex. « 52 présents, 7 absents »), puis on enregistre.
   L'heure de l'appel est notée automatiquement.
5. Tant que l'appel n'est pas envoyé, il peut être corrigé sur le téléphone.

### 4.3 Signaler un cas (professeur)
- Sur un étudiant : parti avant la fin, fraude ou triche, copie non rendue,
  comportement, ou autre (texte libre).
- **Demande de blocage** d'un étudiant pour l'examen du cours, avec le motif.
  La décision revient à l'Institut.

### 4.4 Envoyer
- Écran **« À envoyer »** : appels groupés par date, non encore envoyés.
- Bouton **Actualiser** :
  - s'il voit le PC serveur, il affiche « Serveur disponible », met à jour les cours et
    les listes d'étudiants, et propose **Envoyer** (tout, ou une date précise) ;
  - sinon, il affiche « Serveur indisponible : réessayez plus tard ». Les appels restent
    dans le téléphone.
- On peut envoyer **le jour même ou un autre jour**.
- Après l'envoi, l'appel passe en « Envoyé » et n'est plus modifiable.

### 4.5 Sécurité
- Code de connexion personnel et révocable, plus un PIN local.
- Un appel envoyé ne se modifie plus. Toute correction est faite dans PEGSCO par le
  secrétariat, avec la raison notée.
- Le serveur refuse :
  - un appel pour un cours non attribué à la personne ;
  - une date future ;
  - un doublon (même téléphone, même cours, même date et même type de séance).

## 5. Décisions prises par défaut (modifiables)
- **Statuts** : **Présent / Absent** seulement.
- **Notes** : l'application ne note **pas** les notes obtenues, seulement la
  **présence** aux interrogations, TP et examens.
- **Correction après envoi** : uniquement par le secrétariat dans PEGSCO, avec la raison.
- **Seuil de présence pour l'examen** : aucun au départ. L'Institut pourra en fixer un
  (ex. 75 %) par cours ou pour tous ; PEGSCO listera alors les étudiants en dessous.

## 6. Technique
- **Application** :
  - Android natif, **Kotlin**, avec une base locale SQLite (Room) ;
  - Android 8 minimum, légère pour les petits téléphones (ex. Tecno Pop 7) ;
  - installée par **APK**, sans le Play Store.
- **Réseau** :
  - Wi-Fi local de l'IST, vers PEGSCO réglé en « Réseau local » (port 8100) ;
  - pas d'Internet nécessaire.
- **PEGSCO** :
  - nouvelles tables : intervenants, cours, attributions, CP, codes de connexion,
    séances, présences, signalements, demandes de blocage ;
  - nouvelles adresses d'API réservées à l'application (`/mobile/...`) ;
  - le reste de PEGSCO n'est pas modifié.

## 7. Étapes
1. **PEGSCO** :
   - intervenants, cours et attributions, CP ;
   - codes de connexion et QR code ;
   - réception des appels ;
   - rapports, écarts, blocages.
2. **Application** :
   - connexion, téléchargement des listes ;
   - appel hors ligne, type de séance, signalements ;
   - Actualiser et Envoyer.
3. **Essai réel** sur 1 ou 2 téléphones, avec une promotion.
4. **Installation** sur tous les téléphones.

## 8. Questions encore ouvertes
- Un **horaire fixe** par cours (jour et heure) est-il nécessaire, ou l'appel se fait-il
  au moment du cours, sans horaire enregistré ?
- Faut-il aussi un **fichier de sauvegarde** (copie de secours d'une date, à remettre
  au secrétariat), ou l'envoi par Wi-Fi suffit-il ?
