# Distribution et mises à jour Android

Le bouton Réglages › Mises à jour consulte uniquement la dernière **GitHub Release stable** de `Por-Tra/mobile-manager`. Il ne consulte pas les commits et n'envoie aucune donnée d'usage. Un dépôt privé sans accès anonyme ne fonctionne pas avec ce client ; aucun jeton personnel ne doit être embarqué dans l'APK.

Au 11 novembre 2026, la Release existante utilise le tag `Beta` et fournit `app-debug.apk`. Elle est signalée comme incompatible ; il faut publier une nouvelle Release au format ci-dessous pour proposer une mise à jour.

Le parcours est : rechercher → télécharger → installer → confirmation Android. Si nécessaire, Android demande d'autoriser Mobile Manager à installer des applications ; revenir ensuite et toucher Installer. Aucune désinstallation n'est nécessaire : profils, préférences et historique restent en place. Une installation totalement silencieuse n'est pas garantie par Android pour une application personnelle ordinaire.

Les mises à jour sont indisponibles pendant une session stricte ou lorsque le code du proche est verrouillé : le service protège aussi les écrans de l'installateur dans ces situations.

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

La version doit augmenter. Le code Android est calculé ainsi : `major × 1 000 000 + minor × 1 000 + patch`, avec minor et patch entre 0 et 999. La version de développement actuelle est `0.2.0`, code `2000`. Un tag `v0.2.0` ne constitue donc pas une mise à jour de cette version ; utiliser `v0.2.1` ou supérieur.

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
./gradlew.bat :core:domain:test :app:lintRelease :app:assembleRelease -PreleaseVersionName=0.2.1 -PreleaseVersionCode=2001
```

Sans configuration de signature, le build release local produit un APK non signé, qui ne doit pas être publié comme mise à jour. Aucune clé ni Release n'a été créée automatiquement dans le dépôt distant.

Références : [langues par application](https://developer.android.com/guide/topics/resources/app-languages), [mises à jour Android](https://developer.android.com/google/play/app-updates), [API GitHub Releases](https://docs.github.com/en/rest/releases/releases).
