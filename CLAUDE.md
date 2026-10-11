# Mobile Manager — CLAUDE.md

App Android **personnelle** (APK installé hors Play Store) de gestion du temps d'écran « par la manière forte ». UI en **français**, ton **jamais culpabilisant**. Tout reste en local : pas de compte, pas de réseau, pas de télémétrie, aucune lecture du contenu des messages/posts.

Spécification produit complète : `readme.md` + prompt initial. Téléphone cible : **Samsung Galaxy A04s (SM-A047F, Android 14, One UI 6.1)**, branché en ADB (`R58W70316NW`) : depuis la phase 9, tests et débogage directement sur le téléphone (Instagram, YouTube réels) ; l'émulateur reste utile pour les tests destructifs.

## Stack
- Kotlin 2.4, Jetpack Compose (Material 3, BOM 2026.09), MVVM, couches `data / domain / ui`
- AGP 9.4 (**Kotlin intégré à AGP** : ne pas appliquer `org.jetbrains.kotlin.android`), Gradle 9.8, KSP
- `minSdk 29`, `compileSdk`/`targetSdk 37` (les dernières libs AndroidX exigent 37)
- Hilt, Room, DataStore, Navigation Compose (routes `@Serializable`), Coroutines/Flow
- Tests domaine : JUnit 6 (Jupiter) + Turbine + kotlinx-coroutines-test
- Versions centralisées dans `gradle/libs.versions.toml`

## Modules
| Module | Rôle |
|---|---|
| `:core:domain` | **Kotlin/JVM pur, aucune dépendance Android.** Modèles, logique métier (résolution de profils, moteur de règles, détection de sessions), interfaces de repositories, `TimeProvider`. Toute la logique pure va ici et est testée unitairement. |
| `:core:data` | Room (`MobileManagerDatabase`), DataStore, implémentations des repositories, modules Hilt (`DataModule.kt`). |
| `:app` | Application Hilt, UI Compose, services Android (accessibilité, admin, à venir : foreground service, boot), lecture des permissions. |

Package racine : `com.lucas.mobilemanager`.

## Commandes
Le JDK système (25) est trop récent : utiliser le **JBR 21 d'Android Studio**.
```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew :core:domain:test        # tests unitaires du domaine
./gradlew assembleDebug            # APK : app/build/outputs/apk/debug/app-debug.apk
./gradlew lint                     # rapport : app/build/reports/lint-results-debug.html
./gradlew installDebug             # sur l'émulateur/téléphone connecté
```

**Documentation** (`doc/`, anglais, style Microsoft Learn, générée depuis le code) : `python doc/tools/gen_docs.py` après chaque modification (aucune dépendance). Articles écrits à la main dans `doc/tools/pages/*.html` (`{{Type}}` / `{{Type.membre}}` → lien vers la référence) ; traductions anglaises des KDoc (en français dans le code) dans `doc/tools/kdoc_en.txt` — `--missing out.txt` liste celles qui manquent (affichées en français sinon). Ouvrir `doc/index.html` directement dans un navigateur.

## Conventions
- **Conventional Commits** (`feat(onboarding): …`, `fix: …`, `chore: …`), un commit propre par fin de phase au minimum.
- Logique métier = fonctions/classes pures dans `:core:domain`, testées. L'heure passe toujours par `TimeProvider` (jamais `Instant.now()` direct dans le domaine) pour tester minuit, changements d'heure et jours de semaine.
- ViewModels exposent des `StateFlow` ; écrans Compose séparés en `XxxScreen` (branche le ViewModel) / contenu sans état.
- Textes UI uniquement dans `res/values/strings.xml`, en français, formulation neutre ou encourageante. Jamais de rouge agressif pour une limite atteinte : utiliser `LocalExtendedColors.current.limitReachedContainer`.
- Enums persistées en texte (nom), jamais en ordinal.
- Schémas Room exportés dans `core/data/schemas/` (à committer) ; toute évolution = migration explicite.

## Décisions d'architecture
- **Détection du premier plan** : `MonitorAccessibilityService` est la source de vérité. Seuls les `TYPE_WINDOW_STATE_CHANGED` d'une **activité** changent l'app courante (`ForegroundTracker`, domaine) : les panneaux du bureau (flux Google), widgets, popups, volet et claviers sont ignorés. Le flux Google du Pixel Launcher est une vraie activité de l'app Google → liste explicite `HomePanels` (à compléter si un autre panneau pose problème : lire la ligne `window pkg/classe` dans `adb logcat -s MobileManager`). `QUERY_ALL_PACKAGES` est déclaré (APK perso) pour que la vérification d'activité marche pour toutes les apps.
- `typeWindowContentChanged` est reçu mais ignoré immédiatement, sauf pour les apps à détecteurs de zones et pour les Paramètres / l'installateur quand ils sont protégés (session ou code du proche). `notificationTimeout` = 250 ms (regroupe les rafales pendant un défilement).
- **Temps d'usage du jour** : calculé à partir de `UsageStatsManager.queryEvents()` (événements `ACTIVITY_RESUMED`/`PAUSED`, quasi temps réel), lus depuis minuit − 6 h puis coupés à minuit par `UsageCalculator` (domaine, pur). Rien n'est stocké : le calcul survit à un arrêt du service et reste cohérent avec le Bien-être numérique.

### Chaîne de surveillance (`app/enforcement`, `app/service`, `app/block`)
```
MonitorAccessibilityService (fenêtre au premier plan)
  → ignore : volet système, claviers, doublons ; nos propres écrans arrêtent la surveillance
  → EnforcementCoordinator.evaluate(pkg)  (profils gardés en mémoire via stateIn)
       SystemWhitelistProvider + UsageStatsReader + ProfileResolver + RuleEngine
  → Blocked : BlockActivity (tâche séparée, exclue des récents) + event_log APP_BLOCKED
  → Allowed : réévaluation à min(temps restant, 30 s) tant que l'app reste ouverte
```
- **Pas de `GLOBAL_ACTION_HOME` avant l'écran de blocage** : l'action est asynchrone et l'accueil recouvrait l'écran. C'est `BlockActivity` qui renvoie à l'accueil (bouton ou retour arrière).
- **Anti-doublon du journal uniquement** : l'écran de blocage est **toujours** réaffiché (une app qui enchaîne deux écrans au démarrage, comme Messages et sa page de connexion, repassait sinon par-dessus), mais un re-blocage de la même app dans les 2 s ne crée pas de nouvelle entrée `event_log`.
- Écran éteint → réévaluations suspendues ; rallumé → réévaluation de l'app courante.
- **Liste blanche système** (`SystemWhitelistProvider`, cache 5 min) résolue via `PackageManager` : launcher(s), composeur par défaut + `ACTION_DIAL`, `SET_ALARM`/`SHOW_ALARMS`, `ACTION_SETTINGS`, claviers, Mobile Manager, plus une liste fixe de composants système (systemui, telecom, urgences, contrôleur de permissions, horloges/téléphones Samsung et Google).
- Logs de diagnostic : `adb logcat -s MobileManager` (build debug uniquement) affiche chaque évaluation (`package usage=… -> Decision`).
- **Outils de test (debug)** : Réglages › « Créer le profil Test » (24 h/24, Chrome limité à 1 min, YouTube bloqué) ; quotas de 1 et 2 min dans « Zones et quotas ».

### Moteur de règles (`:core:domain`, package `rules`)
- **Modèle** : `Rule(Target, RuleAction)`. `Target` = `AllApps` < `App(pkg)` < `Zone(pkg, zone)` (précision croissante). `RuleAction` = `Allow` (exception explicite) / `Limit(dailyLimit)` / `Block`. Les plages horaires interdites = un profil avec `Rule(AllApps, Block)` + exceptions `Allow`.
- **`Schedule`** : jours + début/fin locales ; `end <= start` → passe minuit (jours = jour de début) ; `start == end` → 24 h ; début inclus, fin exclue. `ScheduleEvaluator` gère le changement d'heure (heure inexistante → décalée vers l'avant ; heure doublée → les deux occurrences couvertes) et enchaîne les plages contiguës pour calculer la fin. **Activité ≠ fin** : `isAnyActive()` dit si les plages s'appliquent ; `activeUntil()` renvoie `null` si la période ne se termine pas dans les 8 jours (ex. 24 h/24 tous les jours → « pas d'heure de fin prévue »).
- **`ProfileResolver`** → couches ordonnées : 1. session stricte en cours, 2. profil activé manuellement, 3. profils planifiés actifs (`priority` décroissante puis `id` croissant), 4. règles globales. Un profil manuel n'est pas répété en planifié ; profils `enabled = false` ignorés ; `autoActivation = false` = activable seulement à la main.
- **`RuleEngine`** :
  1. liste blanche système (`SystemWhitelist`) → autorisé, avant tout ;
  2. dans chaque couche, seule la règle **la plus précise** compte (à précision égale : la plus restrictive) ;
  3. parcours des couches de haut en bas : `Block`/`Limit` retenus, **`Allow` arrête le parcours** (les couches inférieures sont ignorées) ;
  4. **la restriction la plus forte gagne** : `Block` > `Limit` ; entre limites, la plus proche d'être atteinte ;
  5. limite atteinte (`usage >= limite`) → bloqué jusqu'au minuit local suivant. **Une limite posée sur une zone (quota) se mesure au temps passé dans la zone** (`AppRequest.zoneUsageToday`), une limite d'app au temps passé dans l'app.
  - Motif affiché = couche bloquante la plus prioritaire ; fin du blocage = fin la plus tardive des couches bloquantes (`null` si l'une est sans fin connue).
  - Les règles `Zone` ne s'appliquent que si la zone est détectée (`AppRequest.zone != null`).
- **Profils par défaut** (`DefaultProfiles`) créés une seule fois au premier lancement (`SeedDefaultProfiles`, indicateur DataStore), **tous désactivés** : Sommeil (00–06, tout bloqué, prio 100), Études (lun–ven 9–18, apps distrayantes bloquées, prio 50), Temps libre (18–00, 30 min/app, prio 10).
- **Règles par défaut initiales** (« tout bloqué par défaut », chacune ajoutée une seule fois, sans écraser un réglage existant) : Reels et Shorts bloqués (`SeedDefaultZoneRules`), TikTok bloqué (`SeedDefaultAppBlocks`, les deux packages).

### Sessions de concentration (mode strict)
- **Domaine** (`domain/session`) : `StrictSessionPolicy` (durée ≤ **12 h**, délai de réflexion **2–10 min** choisi avant la session, au moins une app), `StrictSessionManager` (démarrage, dérogation, clôture, journal), `ProtectedScreenDetector`.
- **Dérogation** : « Demander » lance le délai de réflexion (état stocké, on peut quitter l'écran) → à la fin du délai, « Mettre fin » est possible pendant **2 min** seulement, sinon la demande expire et il faut recommencer (empêche de lancer le délai au début pour sortir quand on veut). La dérogation **termine la session** (`OVERRIDDEN`). Aucun autre moyen d'arrêter depuis l'app.
- **Heures absolues** en base (`strict_session`, qui garde aussi l'historique) → survit au redémarrage.
- **`StrictSessionService`** (foreground service `specialUse`) : notification « Fin à HH:MM · n apps en pause » avec compte à rebours, clôture automatique à l'heure de fin (`COMPLETED`), contrôle toutes les 30 s que le service d'accessibilité est actif (sinon `PROTECTION_DISABLED` + notification prioritaire + écran `ProtectionAlertActivity` si la superposition est accordée). Il réutilise sa dernière notification quand il est relancé (sinon le texte provisoire l'écrasait).
- **Relance** : `BootReceiver` (`BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`) ; l'accueil relance aussi le service par sécurité à chaque reprise. Les receivers système obtiennent leurs dépendances via `SessionEntryPoint` (pas d'injection Hilt directe).
- **Écrans protégés** pendant une session : tout écran des Paramètres (AOSP/Samsung) ou de l'installateur de paquets dont le texte visible contient « Mobile Manager » est refermé (`GLOBAL_ACTION_BACK` + toast « Indisponible pendant la session »). Effet de bord assumé : la liste des applis / d'accessibilité se referme aussi si l'entrée est visible.
- **Admin de l'appareil** : message dans la confirmation de désactivation pendant une session ; désactivation journalisée.
- Pendant une session, les interrupteurs de profils et les outils de test sont désactivés (« Modifiable après la session »).

### Profils (interface, phase 5)
- **Ordre affiché = priorité** (`ProfileOrdering`) : le premier l'emporte en cas de chevauchement (même départage que `ProfileResolver`). « Monter / Descendre » réattribue des priorités espacées (…, 30, 20, 10) ; un nouveau profil va en bas.
- **Éditeur** (`ui/profiles/ProfileEditScreen`) : nom, activation automatique, plages (jours, heures, raccourcis, mention « passe minuit » / « 24 h/24 »), « Bloquer toutes les apps » (= `Rule(AllApps, Block)`, les règles par app deviennent des exceptions), règles par app Bloquée / Limitée (5 min → 3 h) / Autorisée. Les règles non affichées (zones, phase 7) sont conservées telles quelles. Validation : `ProfileValidator` (domaine).
- **Règles par défaut** : même éditeur sans nom ni plages (`ProfileEditRoute(defaultRules = true)`) → `ProfileRepository.replaceGlobalRules`. Niveau 4 du moteur.
- **Activation manuelle** : DataStore (`manual_profile_id`, `manual_until_ms`), une seule à la fois, options 1 h / 2 h / jusqu'à HH:MM / jusqu'à désactivation (`ManualActivationPolicy`). **Désactivable à tout moment** (choix produit : l'engagement ferme, c'est la session). Activer un profil désactivé le réactive. Une activation expirée est ignorée par le moteur et nettoyée au retour sur l'accueil. `DeleteProfile` efface aussi l'activation du profil supprimé.
- Pendant une session de concentration : éditeur, menu, ordre, activation et règles par défaut sont inaccessibles (et le ViewModel refuse toute modification).
- Avec un code du proche verrouillé : profils et règles se consultent en lecture seule (bandeau « Déverrouiller ») ; activer / désactiver à la main reste libre.

### Quotas (phase 9, remplace l'anti-doomscrolling)
- **Un quota = une règle `Limit` du moteur** : sur une zone (`Zone(instagram, reels) Limit 50 min`) ou sur une app (`App(tiktok) Limit 30 min`). Une fois atteint, c'est fini jusqu'à minuit (sortie de la zone, bouton masqué, toast « Quota de Reels atteint pour aujourd'hui (50 min) ») ; la messagerie n'est jamais concernée.
- **Temps en zone** : `ZoneUsageAccumulator` (domaine) ajoute le temps entre deux constats « dans la zone », plafonné à 15 s par écart et coupé à minuit. `ZoneUsageRecorder` (app) bufferise et écrit dans `zone_usage` toutes les 15 s de temps compté et à la sortie de l'app / écran éteint. Le temps est compté **avant** la décision (quota jugé sur l'usage à jour) ; vérification au moins toutes les 5 s (ou au moment exact où le quota tombe) tant qu'une zone permise est à l'écran.
- **Interface « Zones et quotas »** (Réglages) : par zone Bloquée / Quota (5 min → 3 h) / Autorisée, avec « 32 min sur 50 aujourd'hui » ; lien vers les règles par défaut pour les apps entières.
- **Supprimé** : `ScrollGuard*`, `ContinuousSessions`, `PauseActivity`, `Decision.Pause`, `BlockReason.SessionLimitReached`, `ZoneVisitTracker`, table `scroll_guard`, clés DataStore `scroll_guard_*` (effacées une fois). Les types d'événements historiques (`INTERSTITIAL_SHOWN`, `PAUSE_CLOSED_APP`, `ZONE_VISIT`) restent dans l'enum pour relire le journal ; les anciens blocages `session_limit` comptent comme « règle ».

### Restrictions par zone (phases 7 et 9)
- **Principe** : on ne modifie pas Instagram/YouTube. Le service détecte qu'on est dans une zone (Reels, Shorts) et **en sort** (actions `exit`, une par essai) + toast « Shorts masqués ». Une app entièrement bloquée garde l'écran de blocage ; les zones ne servent que si l'app est autorisée.
- **Seulement les nœuds visibles** (`UiNode.visible` = `isVisibleToUser`) : Instagram garde les onglets visités dans l'arbre, hors écran ; c'est ce qui bloquait la messagerie (lecteur Reels invisible détecté). Le mode diagnostic affiche aussi les nœuds invisibles, grisés « hors écran (ignoré) ».
- **Masquage des points d'entrée** (`hide` + `hideColor` dans le JSON) : tant que la zone est inaccessible (bloquée ou quota atteint), une fenêtre `TYPE_ACCESSIBILITY_OVERLAY` (`EntryPointMask`, aucune permission) recouvre exactement le bouton (couleur de la barre selon le thème clair/sombre, 2 dp de marge en haut pour garder le liseré de YouTube) et absorbe les appuis. Retirée quand l'app quitte le premier plan, quand une autre fenêtre (volet) devient active, quand le bouton n'est plus visible ou que la zone redevient accessible. Décision par zone mise en cache 5 s pour le masque.
- **Détecteurs JSON** : `app/src/main/assets/zone_detectors.json` (version livrée) ou `filesDir/zone_detectors.json` (version personnalisée, prioritaire si valide). Format (`ZoneDetectorFile`, domaine) : par app, des zones avec `anyOf` (au moins un signal), `noneOf` (aucun ne doit correspondre), `exit` (actions : la 1re applicable au 1er essai, la suivante si la zone est toujours là, la dernière répétée), `hide` (points d'entrée à masquer), `hideColor` (`light`/`dark`, `#RRGGBB`). Un **signal** = conditions sur un même nœud visible : `viewId` (court ou complet), `viewIdContains`, `text`/`textContains`, `desc`/`descContains` (listes FR/EN, sans casse), `selected`, `clickable`.
- **YouTube Shorts** : signaux réels capturés sur l'émulateur (`reel_watch_fragment_root`, `reel_player_page_container`, `reel_recycler`, onglet « Shorts » sélectionné). ⚠️ `reel_time_bar` existe aussi sur les vidéos classiques : ne jamais utiliser `viewIdContains: "reel"`. Captures de référence dans `core/domain/src/test/resources/zones/` ; `ZoneMatcherTest` vérifie le fichier livré contre elles.
- **Instagram Reels** (vérifié sur le téléphone, Instagram 451) : `anyOf` `clips_viewer_view_pager`, `root_clips_layout`, `clips_tab` sélectionné ; `noneOf` `reel_viewer_root` (Stories, « reel » en interne) ; `exit` retour arrière puis `feed_tab` (un Reel ouvert depuis une conversation ou Explorer ramène là d'où l'on vient) ; `hide` `clips_tab` ; masque sombre `#0C1014`. Le sélectionné est porté par `clips_tab` sur l'écran Reels (et par l'icône enfant `tab_icon` ailleurs). Captures anonymisées (identifiants et libellés d'onglets seulement, aucun texte) : `instagram_home/reels/direct/story.json`.
- **YouTube** : `hide` = nœud `desc « Shorts »` cliquable (onglet du bas), masque sombre `#0F0F0F`.
- **Application** (`zones/ZoneEnforcer`) : événements de fenêtre/contenu des apps à détecteurs ; vérification après 400 ms, espacée à 800 ms puis 1,2 s tant que l'écran ne change pas (même zone, mêmes nœuds), remise à 400 ms à chaque changement de fenêtre. **Chemin rapide** : si tous les signaux et clics d'une app portent sur un `viewId` exact (`ZoneMatcher.exactViewIds`, cas d'Instagram), seuls ces identifiants sont demandés (`findAccessibilityNodeInfosByViewId`) au lieu de parcourir l'arbre (chaque nœud lu = un aller-retour vers l'app, ~500 ms par parcours pendant un défilement d'Instagram) ; sinon (YouTube, libellé « Shorts ») parcours borné à 800 nœuds visibles. Capture hors du thread principal. `ZoneMatcher` → `EnforcementCoordinator.evaluateZone` (moteur + temps en zone) → `ZoneExitPlanner`. Anti-boucle `ZoneExitThrottle` : 1 action / 700 ms, écran de blocage après 3 sorties sans succès.
- **Règles** : zones bloquées par défaut ; Réglages › Zones et quotas ; un profil peut autoriser une zone (`Rule(Zone, Allow)`, conservée par l'éditeur de profil). Journal : `ZONE_REDIRECTED` ; temps passé dans `zone_usage`. Log debug : `… zone reels signal N [ids]` à chaque détection.

### Mode diagnostic (Réglages › Avancé)
- **Bandeau en direct** : fenêtre `TYPE_ACCESSIBILITY_OVERLAY` du service (vues Android classiques, déplaçable), « App · zone : X (signal n°N) » + « Capturer ». État en mémoire (`DiagnosticStore`), désactivé au redémarrage.
- **Capture** : arbre de la fenêtre au premier plan (2 000 nœuds max), nœuds déclencheurs en surbrillance ; **appui long = copie d'un signal JSON** prêt à coller. Jamais écrite sur disque.
- **Éditeur** : JSON en vigueur, Vérifier / Enregistrer (refusé si invalide, message lisible) / Réinitialiser ; clavier sans correction (type URI : Gboard ignore `autoCorrectEnabled` avec Ascii et transformait `anyOf` en `any of`). Enregistrement verrouillé pendant une session et sans le code du proche. Le bandeau de diagnostic force le parcours complet (pour voir tout ce qui est détecté).
- **Réparer un détecteur** : activer le bandeau → ouvrir l'app → « Capturer » sur l'écran fautif (et sur un écran qui ne doit pas déclencher) → appui long sur l'élément distinctif → coller le signal dans l'éditeur (`anyOf` ou `noneOf`) → Vérifier → Enregistrer → contrôler avec le bandeau.

### Code du proche (phase 9)
- **Domaine** (`domain/lock`) : `PinHasher` (PBKDF2-HMAC-SHA256, sel aléatoire de 16 octets, **30 000 itérations** = 0,67 s sur l'A04s — 120 000 prenaient 2,7 s ; le nombre d'itérations est stocké avec l'empreinte, les anciennes empreintes sans ce champ valent 120 000), comparaison en temps constant ; `LockPolicy` (4 à 8 chiffres, déverrouillage 5 min, 5 essais libres puis attente de 30 s qui double à chaque échec, plafond 1 h ; pendant l'attente même le bon code est refusé).
- **Stockage** : DataStore `lock_pin_hash`, `lock_pin_salt`, `lock_pin_iterations`, `lock_failures`, `lock_locked_out_until`. Jamais le code en clair. Le déverrouillage est **en mémoire** (`AppLock`, singleton) : un redémarrage du processus reverrouille.
- **Demande le code** : modifier / supprimer / activer-désactiver (interrupteur) / réordonner un profil, en créer un, règles par défaut, zones et quotas, éditeur de détecteurs, objectif du tableau de bord, outils de test, changer ou retirer le code. **Libre** : session de concentration, activation / désactivation manuelle d'un profil, consultation (éditeurs en lecture seule avec bandeau « Déverrouiller »). Double garde : l'UI désactive les contrôles (`rememberCanModify`, `LockBanner`, `UnlockDialog`) et les ViewModels refusent (`AppLock.canModifyNow()`).
- **Protection de l'app** : tant qu'un code existe et que l'app n'est pas déverrouillée, les écrans protégés des Paramètres / de l'installateur (infos de l'app, accessibilité, désinstallation) sont refermés comme pendant une session (toast « Protégé par le code du proche »). Sinon « Forcer l'arrêt » suffirait : sur le téléphone, un arrêt forcé **délie le service d'accessibilité sans qu'Android le relie** jusqu'à sa réactivation.
- Réglages › Code du proche : définir (deux saisies), modifier, retirer ; « En cas d'oubli, seule la désinstallation de l'app permet de repartir de zéro ».

### Tableau de bord (phase 8)
- **Ton** : mesurer sans juger. Le domaine qualifie (`Trend` LESS / MORE / SAME à ±5 min / NO_HISTORY), l'UI formule (« 40 min de moins qu'hier », « Objectif dépassé aujourd'hui : demain est un autre jour »). Jamais de rouge : les jours au-delà de l'objectif sont en `tertiary` (neutre).
- **Calcul** (`DashboardCalculator`, domaine, pur) : total + comparaison avec la période précédente (hier / 7 jours d'avant), apps triées, série des 7 derniers jours, zones (temps lu dans `zone_usage`, « Reels (Instagram) : 32 min / 50 min aujourd'hui » quand un quota existe, sorties `ZONE_REDIRECTED`), interruptions (`APP_BLOCKED` par motif via le détail), objectifs (jours sous l'objectif, jours avec limite quotidienne atteinte, sessions de concentration menées à terme / arrêtées plus tôt).
- **Temps d'écran** : `UsageCalculator.usageByPackage` suit la **chronologie globale** (une seule app au premier plan ; ouvrir une app clôt la précédente ; `SCREEN_NON_INTERACTIVE` / `DEVICE_SHUTDOWN` / `DEVICE_STARTUP` = `INTERRUPTION`, clôt tout). Sans cela, une app ouverte lors d'une coupure brutale comptait jusqu'à maintenant (10 h 59 observées sur l'émulateur). Exclus du total : Mobile Manager, launcher(s), interface système, claviers (`SystemWhitelistProvider.nonScreenTimePackages`).
- **Instantanés** (`daily_usage`, Room v5) : Android ne garde les événements détaillés qu'environ une semaine. `DailyUsageSnapshotter` fige chaque jour manquant des 14 derniers (hier inclus) ; ligne `__none__` = jour enregistré sans usage. Appelé au démarrage de l'app, à l'ouverture du tableau de bord et par `DailyUsageWorker` (WorkManager périodique 24 h, batterie non faible ; Hilt via `Configuration.Provider`, initialiseur par défaut retiré du manifest). Le jour en cours est toujours calculé en direct.
- Objectif quotidien optionnel : DataStore `screen_time_goal_min`. Carte « Aujourd'hui » sur l'accueil + icône dans la barre du haut.
- Durées affichées arrondies à la minute (`Formats.duration`), « < 1 min » pour les très courtes.

### Base de données
- v6 : `zone_usage` (date, package, zone, secondes) en plus, `scroll_guard` supprimée (`AutoMigration 5 → 6` + `@DeleteTable`). Vérifiée sur le téléphone avec les données réelles.
- v5 : `daily_usage` en plus (`AutoMigration 4 → 5`).
- v4 : `scroll_guard` en plus (`AutoMigration 3 → 4`).
- v3 : `event_log`, `profile`, `profile_schedule` (jours en bitmask, bit 0 = lundi ; minutes depuis minuit), `rule` (`profile_id` NULL = règle globale), `strict_session` (packages séparés par un saut de ligne). Migrations 1 → 2 → 3 = `AutoMigration` (tables ajoutées).

### Principes transverses
- **Liste blanche système permanente** : horloge/réveil, téléphone, urgences, paramètres système, Mobile Manager lui-même.
- **Onboarding** : statut des permissions relu à chaque `ON_RESUME` (`PermissionChecker`, aucun cache). Requises : accessibilité, statistiques d'usage, exclusion batterie. Recommandées : superposition, notifications. Mode strict : admin de l'appareil. Logique d'état pure : `OnboardingState` (domaine).
- **Pas de sauvegarde cloud** (`allowBackup=false`, `data_extraction_rules.xml` exclut tout) ; export/import JSON manuel prévu en phase 9.

## Limites connues (honnêteté)
- **Paramètres restreints (Android 13+, One UI)** : pour un APK installé hors store, l'activation du service d'accessibilité est grisée tant que l'utilisateur n'a pas fait *Infos de l'app › ⋮ › Autoriser les paramètres restreints*. L'onboarding l'explique. Une installation via `adb install` n'est généralement pas concernée.
- **Device admin n'empêche pas la désinstallation** : il ajoute une étape. La vraie friction vient de la fermeture des écrans protégés. Un utilisateur déterminé (**mode sans échec**, ADB, réinitialisation) passera toujours — c'est assumé : la session ajoute de la friction, elle ne verrouille pas le téléphone.
- Les écrans protégés sont repérés par leur **texte** : si One UI change ses écrans ou ses packages (`ProtectedScreenDetector.PROTECTED_PACKAGES`), il faudra ajuster.
- Pendant que le service d'accessibilité est coupé, rien n'est bloqué (ni apps ni écrans protégés) : seule l'alerte le signale.
- **Samsung** coupe agressivement les services en arrière-plan : exclusion d'optimisation batterie + « Applis jamais en veille » (carte dédiée dans l'onboarding, affichée seulement si `Build.MANUFACTURER == samsung`, donc invisible sur l'émulateur).
- `AppOpsManager.unsafeCheckOpNoThrow` est déprécié en API 37 mais reste nécessaire pour minSdk 29 (`@Suppress` documenté).
- **Émulateur restauré depuis un snapshot** : le service d'usage d'Android se désynchronise après le saut d'horloge (« Unexpected activity event reported! » dans logcat) et `queryEvents()` ne renvoie plus rien → les limites quotidiennes ne se déclenchent pas. Toujours tester les limites après un démarrage à froid (`emulator -avd … -no-snapshot-load`). Problème propre à l'émulateur.
- **Zones fragiles par nature** : chaque mise à jour d'Instagram/YouTube peut renommer ses identifiants → la détection cesse (la zone et son bouton redeviennent visibles) jusqu'à réparation via le mode diagnostic. Si seul `clips_tab` est renommé, le bouton n'est plus masqué mais la sortie automatique continue tant que le lecteur est reconnu.
- **Masque** : couleur fixe par app et par thème du téléphone ; si l'app n'utilise pas le thème du système (réglage propre à Instagram/YouTube), le rectangle se voit. Il peut aussi apparaître une fraction de seconde au mauvais endroit pendant une transition d'écran.
- **Code du proche** : friction sociale, pas un verrou. Mode sans échec, ADB ou réinitialisation passent outre. Le hash d'un code à 4–8 chiffres se devine hors ligne si quelqu'un lit les données de l'app (root) : la vraie protection est la limite d'essais.
- **Coût de la surveillance** (mesuré sur l'A04s, 2 min de défilement du fil Instagram, CPU de Mobile Manager) : 6,3 % d'un cœur avant optimisation, 2,8 % après (build debug, interprété et vérifications JNI), **1,6 % en build release**. Hors apps à détecteurs : ~0,6 %. Une lecture `queryEvents` coûte ~40 ms toutes les 30 s.
- **Coupure brutale** (batterie vide, plantage, `emu kill`) : la pause de l'app au premier plan n'est jamais enregistrée ; elle est close au prochain événement (autre app, démarrage). Le temps entre les deux peut être surestimé (ex. 25 min pour YouTube sur l'émulateur). Rare sur un vrai téléphone, où l'écran éteint enregistre bien la pause.
- WorkManager refuse une exécution forcée (`cmd jobscheduler run`) avant la fin de la période : comportement normal, le travail tourne au plus une fois par 24 h.
- La liste blanche n'est pas exhaustive pour tous les constructeurs : à vérifier sur le Samsung (horloge, téléphone, appel en cours, urgences) avec `adb logcat -s MobileManager`.

## Tester sur l'émulateur (sans toucher l'UI)
```bash
export MSYS_NO_PATHCONV=1   # Git Bash : sinon /sdcard/... est converti en chemin Windows
P=com.lucas.mobilemanager
adb shell settings put secure enabled_accessibility_services $P/$P.service.MonitorAccessibilityService
adb shell appops set $P GET_USAGE_STATS allow
adb shell dumpsys deviceidle whitelist +$P
adb shell cmd alarm set-timezone Europe/Paris   # pour tester une plage horaire (ex. Sommeil la nuit)
adb shell appops set $P SYSTEM_ALERT_WINDOW allow
adb shell pm grant $P android.permission.POST_NOTIFICATIONS
adb shell dpm set-active-admin $P/.admin.MobileManagerAdminReceiver
# Fin de session sans attendre : avancer l'horloge (auto_time à 0, puis le remettre à 1)
adb shell settings put global auto_time 0 && adb shell cmd alarm set-time <epoch_ms>
```
Si `adb install` échoue avec `INSUFFICIENT_STORAGE` : `adb shell pm trim-caches 999G`.

### Sur le téléphone (Galaxy A04s)
- Après `adb install -r` (et après `am force-stop`), le service peut rester **lié mais muet** : aucun log `window …` à l'ouverture d'une app. Signe : `adb shell dumpsys accessibility | grep "Bound services"` affiche `label=com.lucas.mobilemanager.service.MonitorAccessib…` au lieu de `label=Surveillance Mobile Manager`. Remède : `adb shell settings delete secure enabled_accessibility_services`, attendre 3 s, `settings put …` à nouveau (parfois deux fois). Ne jamais écrire la valeur `'""'` (état incohérent).
- `uiautomator dump` échoue tant qu'une vidéo joue (« could not get idle state ») : mettre en pause, ou se fier au log debug `zone … signal …`. Couper le service pendant une capture, sinon il quitte la zone avant.
- Données : pas de `sqlite3` sur le téléphone → `adb exec-out run-as com.lucas.mobilemanager cat databases/mobile_manager.db` (et `-wal`), lecture avec Python.
- Mesure CPU : `/proc/<pid>/stat` (champs 14 + 15) avant/après un défilement scripté ; profil : `simpleperf record --app com.lucas.mobilemanager` (build debug).
- Dumps d'Instagram : ne garder que les identifiants (aucun texte de message ni nom) ; captures d'écran supprimées après usage.

## Avancement
- [x] **Phase 1 — Fondations** : modules Gradle, Hilt, Room (`event_log`), DataStore, navigation, thème, onboarding des permissions en direct, stubs accessibilité + admin.
- [x] **Phase 2 — Moteur de règles** : modèle, `ScheduleEvaluator`, `ProfileResolver`, `RuleEngine` (70 tests domaine), persistance Room v2 des profils/règles, profils par défaut désactivés.
- [x] **Phase 3 — Surveillance + blocage basique** : service d'accessibilité, usage via `queryEvents`, écran de blocage, liste blanche système, interrupteurs de profils, profil Test (debug). Vérifié sur émulateur.
- [x] **Phase 4 — Sessions strictes** : démarrage avec sélecteur d'apps, délai de réflexion + fenêtre de 2 min, foreground service, relance après redémarrage/mise à jour, écrans protégés, alerte si surveillance coupée, admin. Vérifié sur émulateur (dont redémarrage et fin automatique).
- [x] **Phase 5 — Profils** : éditeur complet (plages, règles par app, « tout bloquer » + exceptions), ordre de priorité, activation manuelle, règles par défaut, suppression. Vérifié sur émulateur. Correctif : l'écran de blocage est toujours réaffiché (apps à deux écrans au démarrage).
- [x] **Phase 6 — Anti-doomscrolling** : sessions continues, écran de pause, blocage + repos, régime strict TikTok, réglages par app. Vérifié sur émulateur (pause à 1 min, blocage à 2 min, repos, remise à zéro, interrupteur). Remplacé en phase 9a par les quotas.
- [x] **Phase 7 — Restrictions par zone** : détecteurs JSON, Shorts YouTube (vérifié sur émulateur avec captures réelles), Reels Instagram (à valider sur le téléphone), réglages par zone, mode diagnostic (bandeau, capture, éditeur).
- [x] **Phase 8 — Tableau de bord** : temps par app (jour / 7 jours), comparaison neutre, graphique 7 jours + objectif, zones, interruptions, objectifs, instantanés quotidiens (Room v5 + WorkManager). Correctif : chronologie globale de l'usage (apps « bloquées » ouvertes après une coupure).
- [x] **Phase 9a — Retours terrain** (sur le téléphone) : détection des zones limitée aux éléments visibles (la messagerie Instagram n'est plus bloquée), détecteur Instagram réel, bouton Reels / onglet Shorts masqués, quotas quotidiens par zone ou par app à la place de l'anti-doomscrolling (Room v6), TikTok bloqué par défaut, code du proche, surveillance allégée (6,3 % → 1,6 % d'un cœur en release).
- [ ] Phase 9b — Export/import JSON, build release signé, icône, finitions UI, robustesse (service lié mais muet après une mise à jour).
