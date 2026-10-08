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
- **Détection du premier plan** : `MonitorAccessibilityService` est la source de vérité (temps réel). `UsageStatsManager` sert aux statistiques et au recoupement (il a du retard).
- **Flux prévu** : événement d'accessibilité → `ForegroundTracker` → `ZoneDetector` (JSON) → `ProfileResolver.resolve()` + `RuleEngine.evaluate()` (purs) → `Decision` → `Enforcer`.

### Moteur de règles (`:core:domain`, package `rules`)
- **Modèle** : `Rule(Target, RuleAction)`. `Target` = `AllApps` < `App(pkg)` < `Zone(pkg, zone)` (précision croissante). `RuleAction` = `Allow` (exception explicite) / `Limit(dailyLimit)` / `Block`. Les plages horaires interdites = un profil avec `Rule(AllApps, Block)` + exceptions `Allow`.
- **`Schedule`** : jours + début/fin locales ; `end <= start` → passe minuit (jours = jour de début) ; `start == end` → 24 h ; début inclus, fin exclue. `ScheduleEvaluator` gère le changement d'heure (heure inexistante → décalée vers l'avant ; heure doublée → les deux occurrences couvertes) et enchaîne les plages contiguës pour calculer la fin.
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

### Base de données
- v2 : `event_log`, `profile`, `profile_schedule` (jours en bitmask, bit 0 = lundi ; minutes depuis minuit), `rule` (`profile_id` NULL = règle globale). Migration 1 → 2 = `AutoMigration` (tables ajoutées). En pratique aucune base v1 n'existe sur un appareil (rien ne l'ouvrait en phase 1).
- **Liste blanche système permanente** : horloge/réveil, téléphone, urgences, paramètres système, Mobile Manager lui-même.
- **Mode strict** : dérogation possible uniquement après le délai de réflexion configuré avant la session ; la dérogation **termine la session** (et est enregistrée dans `event_log`). Session stockée par **heure de fin absolue** pour survivre au redémarrage.
- **Paramètres pendant une session stricte** : accessibles, sauf les pages ciblées (Accessibilité › Mobile Manager, Infos de l'app Mobile Manager, Admin de l'appareil) → renvoi immédiat en arrière.
- **Onboarding** : statut des permissions relu à chaque `ON_RESUME` (`PermissionChecker`, aucun cache). Requises : accessibilité, statistiques d'usage, exclusion batterie. Recommandées : superposition, notifications. Mode strict : admin de l'appareil. Logique d'état pure : `OnboardingState` (domaine).
- **Pas de sauvegarde cloud** (`allowBackup=false`, `data_extraction_rules.xml` exclut tout) ; export/import JSON manuel prévu en phase 9.

## Limites connues (honnêteté)
- **Paramètres restreints (Android 13+, One UI)** : pour un APK installé hors store, l'activation du service d'accessibilité est grisée tant que l'utilisateur n'a pas fait *Infos de l'app › ⋮ › Autoriser les paramètres restreints*. L'onboarding l'explique. Une installation via `adb install` n'est généralement pas concernée.
- **Device admin n'empêche pas la désinstallation** : il ajoute une étape. La vraie friction vient du blocage des pages Paramètres ciblées. Un utilisateur déterminé (mode sans échec, ADB, réinitialisation) passera toujours — c'est assumé.
- **Samsung** coupe agressivement les services en arrière-plan : exclusion d'optimisation batterie + « Applis jamais en veille » (carte dédiée dans l'onboarding, affichée seulement si `Build.MANUFACTURER == samsung`, donc invisible sur l'émulateur).
- **Détection de zones (Reels, Shorts)** à venir en phase 7 : fragile par nature, les sélecteurs UI changent à chaque mise à jour d'Instagram/YouTube → détecteurs externalisés en JSON + mode diagnostic.
- `AppOpsManager.unsafeCheckOpNoThrow` est déprécié en API 37 mais reste nécessaire pour minSdk 29 (`@Suppress` documenté).

## Avancement
- [x] **Phase 1 — Fondations** : modules Gradle, Hilt, Room (`event_log`), DataStore, navigation, thème, onboarding des permissions en direct, stubs accessibilité + admin.
- [x] **Phase 2 — Moteur de règles** : modèle, `ScheduleEvaluator`, `ProfileResolver`, `RuleEngine` (70 tests domaine), persistance Room v2 des profils/règles, profils par défaut désactivés.
- [ ] Phase 3 — Surveillance + blocage basique
- [ ] Phase 4 — Sessions strictes + délai de réflexion + anti-désactivation
- [ ] Phase 5 — UI des profils
- [ ] Phase 6 — Doomscrolling + écran de transition + TikTok
- [ ] Phase 7 — Restrictions par zones (JSON, Reels, Shorts, diagnostic)
- [ ] Phase 8 — Dashboard
- [ ] Phase 9 — Finitions
