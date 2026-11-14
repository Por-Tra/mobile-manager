# Distribution et mises à jour Android

Le bouton Réglages › Mises à jour consulte uniquement la dernière **GitHub Release stable** de `Por-Tra/mobile-manager`. Il ne consulte pas les commits et n'envoie aucune donnée d'usage. Un dépôt privé sans accès anonyme ne fonctionne pas avec ce client ; aucun jeton personnel ne doit être embarqué dans l'APK.

La distribution stable utilise des tags sémantiques et un asset signé `app-release.apk` depuis `v0.2.1`. Le tag historique `Beta`, qui fournit `app-debug.apk`, est incompatible avec le client de mise à jour. La version préparée le 12 novembre 2026 est `v0.3.5` (code `3005`) : budget quotidien partagé facultatif, pause d'ouverture facultative, bilan hebdomadaire local, préavis de limite, diagnostic de protection, explications des blocages et bouton de désinstallation protégé.

Le parcours est : rechercher → télécharger → installer → confirmation Android. Si nécessaire, Android demande d'autoriser Mobile Manager à installer des applications ; revenir ensuite et toucher Installer. Aucune désinstallation n'est nécessaire : profils, préférences et historique restent en place. Une installation totalement silencieuse n'est pas garantie par Android pour une application personnelle ordinaire.

Après une mise à jour depuis une version sans modes, choisir explicitement Libre, Semi-libre ou Restreint sur l'accueil ou dans Réglages. Aucun mode avec blocages n'est imposé : tant que ce choix n'est pas confirmé, les blocages sont désactivés. Les règles et profils antérieurs sont conservés ; choisir Restreint réapplique leur comportement habituel. En Semi-libre sans code du proche, les mises à jour comme les réglages protégés exigent d'abord une demande d'attente puis un déverrouillage ; avec un code, celui-ci reste nécessaire.

Les mises à jour sont indisponibles pendant une session stricte ou lorsque le code du proche est verrouillé : le service protège aussi les écrans de l'installateur dans ces situations.

La version locale remplace le passage automatique des Reels par un écran qui couvre tout le fil recommandé Instagram, photos comprises, lorsque la règle Reels bloque ou que son quota est atteint. La navigation et les messages restent accessibles. Le bouton « Voir les comptes suivis » ouvre le fil natif « Suivi(e) »/« Following », qui reste disponible, vidéos intégrées comprises ; le lecteur Reels plein écran conserve le blocage habituel. Aucun schéma ni autorisation supplémentaire, aucune collecte d’abonnements. Les détecteurs personnalisés restent prioritaires pour la détection des zones ; leurs anciens champs de défilement sont encore pris en charge. La couverture du fil recommandé s’applique tant qu’une zone `reels` existe dans ces détecteurs. Le masque ne garantit pas l’arrêt du son.

## Version 0.4.1 (14 novembre 2026)

Code Android `4001`. Cette version regroupe les changements testés localement en 0.4.0 / code 4000 et permet aussi leur mise à jour. Refonte claire lavande/violet/menthe, logo vectoriel assorti, navigation Menu / Statistiques / Profils / Anti-scroll / Réglages. Profils et règles globales ont un accès direct ; Anti-scroll regroupe les zones et les habitudes ; Réglages propose Protection / Application / Avancé. Aucune migration de données ni nouvelle permission. Les entrées des fonctions principales prennent un à trois appuis depuis les onglets ; formulaires, confirmations et déverrouillages gardent leurs étapes.

Retouches graphiques locales : scènes natives avec relief, animations d’entrée courtes, anneau de répartition réelle de l’usage, frises horaires des profils et graphique hebdomadaire enrichi. Aucune nouvelle donnée persistée ni réseau ajouté.

La publication GitHub est déclenchée explicitement à la demande de l’utilisateur, puis le workflow teste et compile l’APK de distribution. La signature historique est conservée : son certificat Android Debug correspond à celui de la Release 0.3.5 et des installations locales validées. Le type de build release ne change pas le nom du certificat. Une mise à jour en place conserve les profils, préférences et statistiques.

## Désinstallation volontaire

Réglages › Avancé › Désinstaller l'application ouvre la confirmation de désinstallation Android. Ce parcours est distinct des mises à jour et exige l'autorisation de modifier les réglages ; il est indisponible pendant une session stricte. Le bouton retire d'abord notre administration de l'appareil si elle est active. Annuler la confirmation conserve l'application et ses données, mais ne réactive pas cette administration automatiquement. Confirmer supprime l'application et ses données locales. Le message de départ rappelle que le temps de l'utilisateur est précieux. Aucun écran de désinstallation d'une autre application n'est ouvert par ce bouton.

## Première configuration GitHub

Configurer ces quatre **Actions secrets** dans le dépôt :

| Secret | Valeur |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | Keystore de distribution encodé en base64 |
| `ANDROID_KEYSTORE_PASSWORD` | Mot de passe du keystore |
| `ANDROID_KEY_ALIAS` | Alias de la clé |
| `ANDROID_KEY_PASSWORD` | Mot de passe de la clé |

Utiliser la clé qui a signé l'application actuellement installée. Un APK debug ne peut pas recevoir un APK signé avec une autre clé de distribution. Sans cette clé, aucune mise à jour en place ne peut préserver l'installation existante. Ne pas générer une nouvelle clé à chaque publication et ne jamais committer le keystore ou ses mots de passe.

Publier une Release avec un tag stable `v0.2.1`, par exemple. Le workflow `android-release.yml` exécute les tests, lint et la compilation signée, puis joint `app-release.apk` à cette Release. Le bouton peut afficher une erreur tant que la compilation n'a pas encore joint l'APK ; réessayer après la fin du workflow.

Le workflow installe `platform-tools` explicitement : le paquet historique `tools` n'est plus disponible. Après correction d'un échec CI, utiliser Actions › Signed Android release › Run workflow depuis `main`, avec `release_tag` égal au tag existant. Le code compilé provient de ce tag ; le workflow de relance provient de `main`. Il n'est pas nécessaire de déplacer le tag ou de recréer la Release.

La version doit augmenter. Le code Android est calculé ainsi : `major × 1 000 000 + minor × 1 000 + patch`, avec minor et patch entre 0 et 999. La version de distribution est `v0.4.1`, code `4001`, après `v0.3.5` (code `3005`) et les tests locaux `0.4.0` (code `4000`). Une future mise à jour devra avoir un code supérieur à `4001`.

## Contrôles avant installation

- Un seul asset `app-release.apk`, de 1 à 200 Mio, avec le digest SHA-256 fourni par GitHub.
- Téléchargement HTTPS, source dans le dépôt attendu, redirections vers les hôtes GitHub autorisés uniquement.
- Taille et SHA-256 vérifiés ; fichier incomplet effacé en cas d'échec.
- Identifiant Android identique, `versionCode` strictement supérieur, même ensemble de certificats signataires. La rotation de clé n'est pas prise en charge par ce client.
- Android effectue ses propres vérifications et demande la confirmation d'installation.

Le téléchargement reste lié à l'écran Réglages : il survit à une rotation mais doit être relancé si le processus est tué ou si l'écran est quitté. La langue du téléphone, le français et l'anglais sont disponibles sans réseau.

## Compiler localement une version signée

Définir `ANDROID_KEYSTORE_PATH` (chemin absolu), `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` et `ANDROID_KEY_PASSWORD`, puis exécuter :

```powershell
$env:JAVA_HOME = 'C:/Program Files/Android/Android Studio/jbr'
./gradlew.bat :core:domain:test :app:lintRelease :app:assembleRelease -PreleaseVersionName=0.4.1 -PreleaseVersionCode=4001
```

Sans configuration de signature, le build release local produit un APK non signé, qui ne doit pas être publié comme mise à jour. Aucune clé ni Release n'a été créée automatiquement dans le dépôt distant.

## Diagnostic Google Play Protect

Un APK signé n'est pas automatiquement approuvé par Play Protect. Google documente notamment un blocage possible des applications téléchargées depuis un navigateur, une messagerie ou un gestionnaire de fichiers lorsqu'elles utilisent l'accessibilité. Cette capacité est nécessaire au moteur actuel ; la retirer désactiverait la surveillance et les zones. Le texte exact de l'alerte, le moment où elle apparaît et l'APK concerné sont nécessaires pour identifier le cas.

- Une demande d'analyse d'une application inconnue n'est pas une classification comme application dangereuse : laisser Play Protect analyser l'APK.
- Pour une application signalée comme dangereuse ou un blocage persistant, examiner les capacités et le consentement, puis demander une réévaluation officielle si la classification paraît erronée. Ne pas désactiver Play Protect.
- L'app utilise aussi la superposition et l'administration de l'appareil, et referme certains écrans des Paramètres et de désinstallation pendant une session ou un verrouillage. Ces comportements doivent être expliqués explicitement ; leur présence ne prouve pas la cause d'une classification.
- Distribuer un APK release signé avec une clé de distribution stable. Cela assure l'identité et les mises à jour, sans garantir l'absence d'alerte. Ne pas changer de clé pour tenter de faire disparaître une classification.

Contrôle local du 12 novembre 2026 : les APK présents dans `app/build/outputs/apk` au moment de l'audit étaient tous deux signés par le même certificat **Android Debug**, y compris `release/app-release.apk` (ancienne version 0.1.0 / code 1). Le debug était en 0.2.0 / code 2000. Le nom `app-release.apk` ne suffit donc pas à identifier un APK de distribution à jour ; ce constat ne permet pas de conclure sur l'APK effectivement bloqué sur le téléphone.

Pour contrôler l'APK exact avant distribution (adapter la version des build-tools installés) :

```powershell
$env:JAVA_HOME = 'C:/Program Files/Android/Android Studio/jbr'
& "$env:LOCALAPPDATA/Android/Sdk/build-tools/36.1.0/apksigner.bat" verify --print-certs app/build/outputs/apk/release/app-release.apk
Get-FileHash app/build/outputs/apk/release/app-release.apk -Algorithm SHA256
```

Conserver pour le diagnostic : texte de l'alerte, version Android, source d'installation, version de l'app, SHA-256 de l'APK et empreinte SHA-256 du certificat. Aucun secret de signature n'est nécessaire.

Références : [messages et recommandations Play Protect](https://developers.google.com/android/play-protect/warning-dev-guidance), [réévaluation d'une classification](https://support.google.com/googleplay/android-developer/answer/2992033), [principes sur les logiciels indésirables](https://developers.google.com/android/play-protect/mobile-unwanted-software).

Références : [langues par application](https://developer.android.com/guide/topics/resources/app-languages), [mises à jour Android](https://developer.android.com/google/play/app-updates), [API GitHub Releases](https://docs.github.com/en/rest/releases/releases).
