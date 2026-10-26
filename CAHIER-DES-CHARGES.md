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
| Professeur / assistant / autre intervenant | Application | Fait l'appel de **ses** cours, indique le ou les types de séance (cours, interrogation…), voit l'appel du CP et les écarts, signale des cas |
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
- le cours, la date, le ou les **types de séance**, la liste présent ou absent ;
- **qui** l'a fait, **l'heure de l'appel**, **l'heure de validation** (sur le téléphone),
  **l'heure d'envoi** et le téléphone utilisé.

Un appel reçu peut encore être **corrigé depuis le téléphone** tant qu'il est dans
son **heure de correction** (1 h après sa validation, voir 4.2). Passé ce délai, il
est **verrouillé** : le serveur refuse toute nouvelle version, et seul le secrétariat
peut corriger dans PEGSCO.

### 3.5 bis Traçabilité : rien n'est effacé
- **Chaque envoi est une version**. Si un appel est envoyé puis renvoyé corrigé,
  PEGSCO **garde toutes les versions** (v1 reçue à 10 h 12, v2 reçue à 10 h 40…).
  La dernière fait foi, mais les anciennes restent consultables.
- Pour chaque version : qui, depuis quel téléphone, l'heure de validation, l'heure
  d'envoi, et **la liste de ce qui a changé** (ex. « MBUMBA NZAU : Absent → Présent »).
- Les corrections du secrétariat sont aussi des versions, avec la raison et le nom de
  la personne.
- Un même envoi reçu deux fois, ou un même fichier de sauvegarde importé deux fois,
  n'est pas compté deux fois : chaque appel porte un identifiant unique.
- **Écran « Historique d'un appel »** dans PEGSCO : toutes les versions, avec les
  changements surlignés.

### 3.6 Écrans et rapports
- **Séances d'un cours** : date, type(s) (cours, interrogation, TP, examen…), qui a fait
  l'appel, nombre de présents et d'absents.
- **Assiduité d'un étudiant par cours** :
  - nombre de présences sur nombre de séances (ex. 9 / 12) ;
  - présence à chaque interrogation, TP et examen ;
  - signalements.
- **Écarts CP / professeur** : même cours, même jour, étudiant présent pour l'un et
  absent pour l'autre. On y voit aussi les **corrections successives** de chacun
  (versions). Le secrétariat tranche, et la décision est gardée.
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
2. Cocher le ou les **types de séance** : **Cours**, **Interrogation**, **TP**,
   **Examen**, **Autre**. **Plusieurs types sont possibles** (ex. Cours + Interrogation).
   C'est ici qu'on **signale une interrogation**.
   Pas d'horaire fixe : l'appel se fait au moment du cours, et la date et l'heure de
   l'appel sont notées automatiquement.
3. Les noms défilent dans l'ordre de la liste, avec deux gros boutons **Présent / Absent**.
4. Un récapitulatif s'affiche (ex. « 52 présents, 7 absents »), puis on enregistre.
   L'heure de l'appel est notée automatiquement.
5. **Tant que l'appel n'est pas validé**, il reste un brouillon : on peut le corriger
   autant qu'on veut.
6. **Valider l'appel du jour** : bouton « Valider ». L'heure de validation est notée.
7. **Heure de correction** : pendant **1 heure** après la validation, l'appel peut
   encore être corrigé, par exemple pour un retardataire ou une erreur de bouton.
   Un compte à rebours s'affiche (« modifiable encore 42 min »).
8. **Après 1 heure**, l'appel est **verrouillé** sur le téléphone. Il n'est plus
   modifiable, qu'il soit envoyé ou non.

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
- On ne peut envoyer que des appels **validés**.
- Après l'envoi, l'appel passe en « Envoyé ».
- S'il est corrigé pendant son heure de correction, il repasse en « À renvoyer ». À
  l'envoi suivant, PEGSCO ajoute la nouvelle version **sans effacer l'ancienne** (voir
  3.5 bis).

### 4.4 bis Fichier de sauvegarde
- Bouton **« Sauvegarde d'une date »** : crée un fichier qui contient tous les appels
  **validés** de cette date, avec leurs versions et leurs signalements.
- Usage :
  - copie de secours dans le téléphone ;
  - à remettre au secrétariat (câble, clé USB, partage) si le Wi-Fi ou le serveur reste
    indisponible.
- PEGSCO : menu **« Importer une sauvegarde »**. Le fichier est **signé** : un fichier
  modifié à la main est refusé. Ce qui a déjà été reçu n'est pas compté deux fois, et
  l'import est tracé (qui, quand, quel fichier).

### 4.4 ter Professeur : voir l'appel du CP et les écarts
- Après **Actualiser**, le professeur reçoit, pour **ses** cours, les appels **envoyés
  par les CP** (dernière version, et l'historique des corrections).
- Écran **« Comparer »** pour un cours et une date :
  - son appel et celui du CP côte à côte ;
  - les **étudiants en désaccord** mis en avant (ex. « présent selon le CP, absent
    selon vous ») ;
  - les **corrections du CP** après sa validation (ex. « le CP a passé MBUMBA NZAU de
    Absent à Présent à 10 h 40 »).
- Le professeur peut **signaler l'écart** au secrétariat (« vérifier : présence
  contestée »), avec un commentaire.
- Le CP, lui, **ne voit pas** l'appel du professeur : il ne peut pas recopier sa liste
  pour faire disparaître un écart.

### 4.5 Sécurité
- Code de connexion personnel et révocable, plus un PIN local.
- Un appel validé depuis plus d'**1 heure** ne se modifie plus, ni sur le téléphone ni
  par un renvoi. Toute correction est alors faite dans PEGSCO par le secrétariat, avec
  la raison notée.
- Le **serveur contrôle aussi** ce délai : il refuse une nouvelle version reçue pour un
  appel dont l'heure de correction est passée.
- **Contrôle de l'heure** : il compare l'heure du téléphone et la sienne à chaque envoi.
  Si le téléphone a été mis à une fausse date ou heure, l'appel est marqué « heure
  douteuse » dans PEGSCO, pour vérification.
- Le serveur refuse :
  - un appel pour un cours non attribué à la personne ;
  - une date future ;
  - une version d'un appel déjà verrouillé.

## 5. Décisions prises par défaut (modifiables)
- **Statuts** : **Présent / Absent** seulement.
- **Notes** : l'application ne note **pas** les notes obtenues, seulement la
  **présence** aux interrogations, TP et examens.
- **Correction** : libre avant la validation, puis **1 heure** après la validation.
  Au-delà, uniquement par le secrétariat dans PEGSCO, avec la raison.
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
   - appel hors ligne, types de séance, signalements ;
   - validation et heure de correction, envoi, fichier de sauvegarde ;
   - comparaison CP / professeur, historique des versions ;
   - Actualiser et Envoyer.
3. **Essai réel** sur 1 ou 2 téléphones, avec une promotion.
4. **Installation** sur tous les téléphones.

## 8. Décisions prises par défaut (à confirmer)
- **Le CP ne voit pas l'appel du professeur.** Seul le professeur et le secrétariat
  voient les écarts.
- Plusieurs appels du même cours **le même jour** sont possibles (deux séances). Chacun
  est une séance distincte, repérée par son heure d'appel.
