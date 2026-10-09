# Mobile Manager — CLAUDE.md

App Android **personnelle** (APK installé hors Play Store) de gestion du temps d'écran « par la manière forte ». UI en **français**, ton **jamais culpabilisant**. Tout reste en local : pas de compte, pas de réseau, pas de télémétrie, aucune lecture du contenu des messages/posts.

Spécification produit complète : `readme.md` + prompt initial. Téléphone cible : **Samsung (One UI)** ; tests de fin de phase sur émulateur Android Studio.

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

## Conventions
- **Conventional Commits** (`feat(onboarding): …`, `fix: …`, `chore: …`), un commit propre par fin de phase au minimum.
- Logique métier = fonctions/classes pures dans `:core:domain`, testées. L'heure passe toujours par `TimeProvider` (jamais `Instant.now()` direct dans le domaine) pour tester minuit, changements d'heure et jours de semaine.
- ViewModels exposent des `StateFlow` ; écrans Compose séparés en `XxxScreen` (branche le ViewModel) / contenu sans état.
- Textes UI uniquement dans `res/values/strings.xml`, en français, formulation neutre ou encourageante. Jamais de rouge agressif pour une limite atteinte : utiliser `LocalExtendedColors.current.limitReachedContainer`.
- Enums persistées en texte (nom), jamais en ordinal.
- Schémas Room exportés dans `core/data/schemas/` (à committer) ; toute évolution = migration explicite.

## Décisions d'architecture
- **Détection du premier plan** : `MonitorAccessibilityService` est la source de vérité. Seuls les `TYPE_WINDOW_STATE_CHANGED` d'une **activité** changent l'app courante (`ForegroundTracker`, domaine) : les panneaux du bureau (flux Google), widgets, popups, volet et claviers sont ignorés. Le flux Google du Pixel Launcher est une vraie activité de l'app Google → liste explicite `HomePanels` (à compléter si un autre panneau pose problème : lire la ligne `window pkg/classe` dans `adb logcat -s MobileManager`). `QUERY_ALL_PACKAGES` est déclaré (APK perso) pour que la vérification d'activité marche pour toutes les apps.
- `typeWindowContentChanged` est reçu mais ignoré immédiatement, sauf pour les Paramètres / l'installateur pendant une session stricte (écrans protégés). La phase 7 l'utilisera pour les zones.
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
- **Anti-doublon** : un re-blocage de la même app dans les 2 s est ignoré (une app qui s'ouvre émet plusieurs événements de fenêtre) → une seule entrée `event_log` par blocage.
- Écran éteint → réévaluations suspendues ; rallumé → réévaluation de l'app courante.
- **Liste blanche système** (`SystemWhitelistProvider`, cache 5 min) résolue via `PackageManager` : launcher(s), composeur par défaut + `ACTION_DIAL`, `SET_ALARM`/`SHOW_ALARMS`, `ACTION_SETTINGS`, claviers, Mobile Manager, plus une liste fixe de composants système (systemui, telecom, urgences, contrôleur de permissions, horloges/téléphones Samsung et Google).
- Logs de diagnostic : `adb logcat -s MobileManager` (build debug uniquement) affiche chaque évaluation (`package usage=… -> Decision`).
- **Outils de test (debug)** : Réglages › « Créer le profil Test » (24 h/24, Chrome limité à 1 min, YouTube bloqué).

### Moteur de règles (`:core:domain`, package `rules`)
- **Modèle** : `Rule(Target, RuleAction)`. `Target` = `AllApps` < `App(pkg)` < `Zone(pkg, zone)` (précision croissante). `RuleAction` = `Allow` (exception explicite) / `Limit(dailyLimit)` / `Block`. Les plages horaires interdites = un profil avec `Rule(AllApps, Block)` + exceptions `Allow`.
- **`Schedule`** : jours + début/fin locales ; `end <= start` → passe minuit (jours = jour de début) ; `start == end` → 24 h ; début inclus, fin exclue. `ScheduleEvaluator` gère le changement d'heure (heure inexistante → décalée vers l'avant ; heure doublée → les deux occurrences couvertes) et enchaîne les plages contiguës pour calculer la fin. **Activité ≠ fin** : `isAnyActive()` dit si les plages s'appliquent ; `activeUntil()` renvoie `null` si la période ne se termine pas dans les 8 jours (ex. 24 h/24 tous les jours → « pas d'heure de fin prévue »).
- **`ProfileResolver`** → couches ordonnées : 1. session stricte en cours, 2. profil activé manuellement, 3. profils planifiés actifs (`priority` décroissante puis `id` croissant), 4. règles globales. Un profil manuel n'est pas répété en planifié ; profils `enabled = false` ignorés ; `autoActivation = false` = activable seulement à la main.
- **`RuleEngine`** :
  1. liste blanche système (`SystemWhitelist`) → autorisé, avant tout ;
  2. dans chaque couche, seule la règle **la plus précise** compte (à précision égale : la plus restrictive) ;
  3. parcours des couches de haut en bas : `Block`/`Limit` retenus, **`Allow` arrête le parcours** (les couches inférieures sont ignorées) ;
  4. **la restriction la plus forte gagne** : `Block` > `Limit` ; entre limites, la plus courte ;
  5. limite atteinte (`usage >= limite`) → bloqué jusqu'au minuit local suivant.
  - Motif affiché = couche bloquante la plus prioritaire ; fin du blocage = fin la plus tardive des couches bloquantes (`null` si l'une est sans fin connue).
  - Les règles `Zone` ne s'appliquent que si la zone est détectée (`AppRequest.zone != null`).
- **Profils par défaut** (`DefaultProfiles`) créés une seule fois au premier lancement (`SeedDefaultProfiles`, indicateur DataStore), **tous désactivés** : Sommeil (00–06, tout bloqué, prio 100), Études (lun–ven 9–18, apps distrayantes bloquées, prio 50), Temps libre (18–00, 30 min/app, prio 10).

### Sessions de concentration (mode strict)
- **Domaine** (`domain/session`) : `StrictSessionPolicy` (durée ≤ **12 h**, délai de réflexion **2–10 min** choisi avant la session, au moins une app), `StrictSessionManager` (démarrage, dérogation, clôture, journal), `ProtectedScreenDetector`.
- **Dérogation** : « Demander » lance le délai de réflexion (état stocké, on peut quitter l'écran) → à la fin du délai, « Mettre fin » est possible pendant **2 min** seulement, sinon la demande expire et il faut recommencer (empêche de lancer le délai au début pour sortir quand on veut). La dérogation **termine la session** (`OVERRIDDEN`). Aucun autre moyen d'arrêter depuis l'app.
- **Heures absolues** en base (`strict_session`, qui garde aussi l'historique) → survit au redémarrage.
- **`StrictSessionService`** (foreground service `specialUse`) : notification « Fin à HH:MM · n apps en pause » avec compte à rebours, clôture automatique à l'heure de fin (`COMPLETED`), contrôle toutes les 30 s que le service d'accessibilité est actif (sinon `PROTECTION_DISABLED` + notification prioritaire + écran `ProtectionAlertActivity` si la superposition est accordée). Il réutilise sa dernière notification quand il est relancé (sinon le texte provisoire l'écrasait).
- **Relance** : `BootReceiver` (`BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`) ; l'accueil relance aussi le service par sécurité à chaque reprise. Les receivers système obtiennent leurs dépendances via `SessionEntryPoint` (pas d'injection Hilt directe).
- **Écrans protégés** pendant une session : tout écran des Paramètres (AOSP/Samsung) ou de l'installateur de paquets dont le texte visible contient « Mobile Manager » est refermé (`GLOBAL_ACTION_BACK` + toast « Indisponible pendant la session »). Effet de bord assumé : la liste des applis / d'accessibilité se referme aussi si l'entrée est visible.
- **Admin de l'appareil** : message dans la confirmation de désactivation pendant une session ; désactivation journalisée.
- Pendant une session, les interrupteurs de profils et les outils de test sont désactivés (« Modifiable après la session »).

### Base de données
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
- **Détection de zones (Reels, Shorts)** à venir en phase 7 : fragile par nature, les sélecteurs UI changent à chaque mise à jour d'Instagram/YouTube → détecteurs externalisés en JSON + mode diagnostic.
- `AppOpsManager.unsafeCheckOpNoThrow` est déprécié en API 37 mais reste nécessaire pour minSdk 29 (`@Suppress` documenté).
- **Émulateur restauré depuis un snapshot** : le service d'usage d'Android se désynchronise après le saut d'horloge (« Unexpected activity event reported! » dans logcat) et `queryEvents()` ne renvoie plus rien → les limites quotidiennes ne se déclenchent pas. Toujours tester les limites après un démarrage à froid (`emulator -avd … -no-snapshot-load`). Problème propre à l'émulateur.
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
Une réinstallation (`adb install -r`) peut désactiver le service d'accessibilité : relancer la première commande.

## Avancement
- [x] **Phase 1 — Fondations** : modules Gradle, Hilt, Room (`event_log`), DataStore, navigation, thème, onboarding des permissions en direct, stubs accessibilité + admin.
- [x] **Phase 2 — Moteur de règles** : modèle, `ScheduleEvaluator`, `ProfileResolver`, `RuleEngine` (70 tests domaine), persistance Room v2 des profils/règles, profils par défaut désactivés.
- [x] **Phase 3 — Surveillance + blocage basique** : service d'accessibilité, usage via `queryEvents`, écran de blocage, liste blanche système, interrupteurs de profils, profil Test (debug). Vérifié sur émulateur.
- [x] **Phase 4 — Sessions strictes** : démarrage avec sélecteur d'apps, délai de réflexion + fenêtre de 2 min, foreground service, relance après redémarrage/mise à jour, écrans protégés, alerte si surveillance coupée, admin. Vérifié sur émulateur (dont redémarrage et fin automatique).
- [ ] Phase 5 — UI des profils
- [ ] Phase 6 — Doomscrolling + écran de transition + TikTok
- [ ] Phase 7 — Restrictions par zones (JSON, Reels, Shorts, diagnostic)
- [ ] Phase 8 — Dashboard
- [ ] Phase 9 — Finitions
