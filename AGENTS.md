# Contexte Mobile Manager

Lire ce fichier en premier. `CLAUDE.md` contient les décisions détaillées du moteur et les retours terrain ; `UPDATES.md` décrit la distribution. Mettre ces fichiers à jour lorsque l'architecture, les commandes ou les limitations changent.

## Produit et architecture

Application Android personnelle hors Play Store pour gérer le temps d'écran. Ton neutre et encourageant ; ne pas utiliser de rouge alarmant pour une limite atteinte. Données d'usage exclusivement locales. Le seul réseau ajouté est une vérification/téléchargement GitHub déclenché explicitement depuis Réglages.

- `core/domain` : Kotlin/JVM pur, modèles et repositories, moteur de règles, profils/plages, sessions strictes, quotas, usage, verrouillage, statistiques. Aucune API Android. Temps métier via `TimeProvider`.
- `core/data` : Room v6 (migrations 1→6), DataStore, repositories, injection Hilt. Ne pas changer le schéma sans migration.
- `app` : Compose Material 3, ViewModels et navigation sérialisée, services Android, permissions, surveillance et blocage.
- `doc/tools` : générateur de documentation Python sans dépendances ; sources éditoriales dans `pages`, traductions des KDoc dans `kdoc_en.txt`.

Flux principal : `MonitorAccessibilityService` → `EnforcementCoordinator` → `ProfileResolver`/`RuleEngine` → `BlockActivity` ou `ZoneEnforcer`. Liste blanche système avant toute restriction. Priorité : session stricte, profil manuel, profils planifiés, règles globales. Règle la plus précise dans chaque couche ; Allow arrête les couches inférieures, sinon la restriction la plus forte gagne. Ne pas modifier ce comportement à l'occasion de retouches UI.

Les zones Reels/Shorts s'appuient sur `assets/zone_detectors.json`, éventuellement remplacé par un fichier local validé. Ne lire que les nœuds visibles ; préserver la messagerie Instagram. Quotas = règles Limit sur des zones ou apps ; temps des zones dans Room, temps des apps via UsageStats.

Instagram : lorsque la zone `reels` est bloquée ou son quota atteint, `InstagramFeedShield` couvre tout le fil recommandé (photos comprises), absorbe les gestes et conserve les barres de navigation. Aucun passage automatique dans les détecteurs livrés. `InstagramFeedPolicy` reconnaît les surfaces visibles par identifiants exacts ; le fil dédié « Suivi(e) »/« Following » reste ouvert, avec un bouton pour le rejoindre. Ne jamais déduire un abonnement de l’absence du bouton Suivre. L’exception concerne le fil Suivi(e), pas le lecteur Reels plein écran. Pas de liste d’abonnements collectée ; le masque ne garantit pas l’arrêt du son. Retirer les masques au changement de fenêtre, d’app, de mode et à l’extinction de l’écran.

Robustesse : réévaluer les apps et relancer les zones à chaque ouverture d'activité reconnue (même package), au déverrouillage (`USER_PRESENT`), au réveil sans verrouillage et à la connexion du service. Reprendre depuis la fenêtre active avec des tentatives bornées si elle n'est pas prête ; suspendre les contrôles écran éteint/verrouillé. Invalider les décisions de zones à la reprise et annuler les contrôles de l'ancien premier plan.

Modes : `domain/mode/AppMode` (`FREE`, `SEMI_FREE`, `RESTRICTED`), choix utilisateur exclusivement, persisté par `ModeRepository` dans DataStore. Sans choix (y compris après mise à jour), aucun blocage ; profils/règles conservés. Libre désactive apps/zones/masques/protection des écrans système. Semi-libre applique le moteur habituel mais les modifications exigent le code du proche s'il existe ; sinon demande d'attente configurable (1–1 440 min, départ au clic, persistée), puis déverrouillage explicite des réglages pendant 5 min. Aucune levée automatique. Restreint conserve le moteur existant. Session stricte : aucun changement de mode, aucune dérogation par l'attente semi-libre. Pas de démarrage de session en libre. Les commandes manuelles de profils respectent aussi `AppLock`.

## Interface et langues (novembre 2026)

- Navigation principale persistante : Accueil / Statistiques / Réglages. Éditeurs, onboarding et sessions sortent de cette navigation. `MobileManagerNavHost` conserve les piles des onglets.
- Accueil : introduction, permissions manquantes si nécessaire, action de concentration, usage du jour, état de protection, profils et règles. Les boutons de création/règles sont empilés pour les petits écrans.
- Réglages défilants : protection, langue/mises à jour, avancé. Thème clair/sombre, cartes arrondies et couleurs de surface centralisés dans `ui/theme`.
- Réglages › Avancé : bouton de désinstallation avec message de départ, protégé par AppLock et refusé pendant une session stricte. Retire notre administration active avant ACTION_DELETE sur notre package, puis confirmation Android. En cas d'annulation, les données restent mais l'administration retirée n'est pas réactivée automatiquement.
- Français par défaut dans `res/values/strings.xml`, traduction complète dans `values-en/strings.xml`. Tout texte UI doit exister dans les deux, avec les mêmes arguments printf. Vérification : `tools/check_resources.py`.
- `locale/AppLanguage` : LocaleManager Android 13+, préférence privée et contextes localisés Android 10–12. `LocalizedActivity` est la base des trois activités. Les services lisent les ressources de l'application. Les jours Compose utilisent `LocalConfiguration`, les formats hors Compose la locale courante. Ne pas revenir à `Locale.FRENCH` fixe.
- Les noms de profils sont des données utilisateur persistées ; ils ne sont pas renommés lors d'un changement de langue.

## Habitudes et diagnostic

- Budget quotidien partagé facultatif : une sélection d'apps, un budget commun depuis minuit local, sans addition du temps des zones. Préférences DataStore, désactivé par défaut. Restriction de dernier recours après les couches existantes : une règle Allow arrête aussi le budget partagé ; les restrictions supérieures restent retenues. La liste blanche et le mode Libre restent prioritaires.
- Pause d'ouverture facultative : apps sélectionnées, 3–30 s, désactivée par défaut. Superposition d'accessibilité tactile, puis clic explicite sur Continuer ; Retour à l'accueil disponible. Une validation ne vaut que pour le passage courant dans l'app ; une nouvelle activité du même package ne relance pas la pause. Retirer au changement d'app/mode/préférences, à l'écran éteint et à la destruction du service. Le son peut continuer et Android peut compter ce temps comme usage de l'app.
- Préavis activé par défaut : Toast neutre à ≤ 5 min restantes, une fois par app/zone et jour local pendant le processus courant. Aucun besoin de permission de notifications ; pas d'alerte en mode Libre. Les options sont protégées par AppLock et non modifiables pendant une session stricte.
- Statistiques : bilan des 7 journées terminées hier, couverture des données affichée, moyenne sur les jours renseignés ; comparaison seulement si les deux périodes ont 7 jours de données. Aucun envoi ou notification automatique.
- Réglages : diagnostic des permissions et connexion effective du service via MonitorHealth, actualisé à la reprise ; un état prêt ne garantit pas la survie du service Android. Les limites atteintes indiquent leur origine (budget partagé, profil/global, zone).

## Mises à jour

- Domaine : `domain/update/GitHubRelease` valide les métadonnées et `ReleaseVersion` compare des tags stables sémantiques. Tests JVM couvrant versions, URL, taille, digest et assets ambigus.
- Android : `update/GitHubUpdater` consulte `Por-Tra/mobile-manager/releases/latest`, télécharge l'unique `app-release.apk`, contrôle SHA-256, taille, package, versionCode supérieur et certificat identique. HTTPS borné, redirections GitHub autorisées uniquement ; aucun token embarqué.
- `UpdateViewModel` gère recherche, disponibilité, progression, erreur et APK prêt. Les opérations et l'installation sont refusées pendant une session et si le code du proche est verrouillé.
- `ui/settings/AppPreferences` propose langue et mise à jour, ouvre l'autorisation des sources inconnues si nécessaire, puis l'installateur Android via FileProvider privé (cache/updates uniquement).
- La confirmation système peut être requise. La mise à jour ne désinstalle pas l'app ; aucune promesse d'installation silencieuse. Une clé différente ne permet pas une mise à jour en place.
- `.github/workflows/android-release.yml` compile/teste/signe lors d'une Release publiée puis joint l'APK. Secrets et première configuration dans `UPDATES.md`. Aucun secret ni keystore dans Git.
- Version actuelle 0.3.5 / code 3005 ; propriétés Gradle `releaseVersionName`, `releaseVersionCode`. Code de distribution : major×1 000 000 + minor×1 000 + patch.

## Commandes Windows

```powershell
$env:JAVA_HOME = 'C:/Program Files/Android/Android Studio/jbr'
./gradlew.bat :core:domain:test :app:assembleDebug :app:lintDebug --offline
python tools/check_resources.py
python doc/tools/gen_docs.py
```

Le Python système peut être un alias Windows inutilisable ; le runtime fourni par l'application possède un Python fonctionnel (`load_workspace_dependencies`). Gradle utilise son cache dans le profil utilisateur : l'environnement sandbox peut nécessiter une autorisation d'accès. Ne pas confondre un refus d'accès au cache avec une erreur du projet.

SDK : `C:/Users/lucas/AppData/Local/Android/Sdk`. JBR 21, Kotlin 2.4, AGP 9.4 (Kotlin intégré, ne pas ajouter kotlin.android), Gradle 9.8, compile/target 37, min 29. Versions dans `gradle/libs.versions.toml`.

Pour tester l'interface, privilégier l'émulateur existant `Medium_Phone_API_36.1` ; éviter de modifier les autorisations ou données d'un téléphone réel. Ne pas publier de Release ni pousser les commits sans demande. Conventional Commits pour les changements locaux.
