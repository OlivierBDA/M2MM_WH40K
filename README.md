# M2MM (Gamified Habit Tracker)

M2MM est une application Android de suivi d'activités entièrement gamifiée. Elle vous aide à suivre vos habitudes (positives ou négatives) et vous fait progresser à travers différents niveaux visuels en fonction de votre assiduité et de votre score global.

## 🌟 Fonctionnalités Principales

*   **Gamification & Niveaux** : Gagnez des points en accomplissant des actions positives (ex: sport, conception, apprentissage) et perdez-en lors de comportements négatifs (ex: malbouffe). En accumulant des points, vous franchissez des paliers pour monter de niveau.
*   **Synchronisation du Fond d'Écran (Wallpaper Sync)** : Fonctionnalité immersive unique : le fond d'écran de votre smartphone (Home / Lock screen) change automatiquement pour refléter votre niveau actuel dans l'application. Cette logique intègre un redimensionnement intelligent (recadrage/crop) pour s'adapter parfaitement à l'écran sans distorsion ni "zoom" par défaut du système.
*   **Widget Interactif (Jetpack Glance)** :
    *   Format ultra-compact : optimisé pour ne prendre qu'une seule ligne (1x4 cellules) sur l'écran d'accueil.
    *   Tableau de bord : affichage de votre score actuel et d'un "différentiel" calculant votre progression par rapport aux 7 derniers jours.
    *   Actions directes : de larges boutons interactifs (60dp) permettent d'enregistrer des activités en un clin d'œil, sans devoir ouvrir l'application.
    *   Mise à jour fiable : utilisation de callbacks personnalisés et de délais temporels pour assurer une synchronisation transparente entre la base de données et l'affichage de l'écran d'accueil.
*   **Historique et Statistiques** : Suivez la tendance de votre score sur les 30 derniers jours à l'aide d'un graphique interactif élaboré sur mesure.
*   **Configuration et Thèmes Dynamiques (JSON)** : L'application est agnostique de son thème. Toutes les données (activités, points, icônes, images de fond, seuils des niveaux) sont pilotées par un fichier `config.json`. Il est aisé de basculer d'un thème à l'autre (ex: passage d'un thème *Dragon Ball* à *Warhammer 40k*).
*   **Malus Journalier (Daily Decay)** : Un système de "dégradation" soustrait automatiquement des points au score chaque jour, afin d'encourager la régularité.

## 🛠️ Architecture et Pipeline Technique

L'application est construite selon les standards avancés du développement Android natif :
*   **Langage Principal** : Kotlin
*   **Interface Graphique** : Jetpack Compose & Material Design 3.
*   **Architecture** : MVVM (Model-View-ViewModel) et principes de flux de données unidirectionnels (StateFlow).
*   **Persistance Locale** : Base de données SQLite gérée par la bibliothèque *Room Persistence Library*.
*   **Asynchronisme & Concurrence** : Kotlin Coroutines pour les appels réseaux, écritures disque et traitements graphiques en arrière-plan.
*   **Widget** : API Jetpack Glance (dédiée à l'architecture App Widgets avec Compose).
*   **Sérialisation** : `kotlinx.serialization` pour le traitement efficace du fichier JSON.
*   **OS APIs** : Intégration avancée du `WallpaperManager` d'Android.

## 📂 Structure Générale du Projet

*   `MainActivity.kt` : L'épine dorsale de l'UI. Gère la navigation entre l'écran d'accueil interactif, le gestionnaire de configuration (`SettingsScreen`) et l'écran des graphiques (`StatsScreen`).
*   **Couche de Données (`data/`)** :
    *   `M2MMDatabase.kt`, `M2MMDao.kt` : Contrôle de l'historique et de l'état système via Room.
    *   `GameRepository.kt` : Gère le moteur de jeu, s'occupant des calculs de progression (seuils), du decay quotidien, et gère l'état global du *Single Source of Truth*.
    *   `ConfigModels.kt` : Parsing et typage strict des règles du fichier de configuration JSON.
*   **Espace Widget (`widget/`)** :
    *   `M2MMWidget.kt` : Vue asynchrone du dashboard de l'écran d'accueil.
    *   `RecordActivityAction.kt`, `RefreshSyncAction.kt` : Logique de mise à jour des instances Glance et d'intégration en BDD hors-Contexte.
*   `WallpaperHelper.kt` : Classe technique gérant l'extraction des DisplayMetrics du terminal pour appliquer des transformations Bitmap spécifiques afin de bloquer les rendus de parallaxe non voulus d'Android.
*   `WidgetRefreshHelper.kt` : Gestionnaire de processus de rafraichissement forcé.

## 🎨 Personnalisation & Thèmes

Pour modifier l'environnement de votre instance M2MM :
1.  **Dossier `res/drawable-nodpi/`** : Placez-y vos fonds d'écrans de niveaux haute-résolution. Privilégier ce répertoire empèche les processus internes Android de dégrader la qualité visuelle pour adapter la densité DPI (le `WallpaperHelper` le gère bien mieux).
2.  **Dossier `res/drawable/`** : Intégrez les petites icônes dédiées aux boutons d'actions (+ et -).
3.  **Fichier `assets/config.json`** :
    *   Paramétrez le tableau `levels` en indexant le nom d'affichage, le seuil de bascule vers ce niveau et le nom physique du fichier image (ex: `wh_level1.png`).
    *   Éditez le tableau `activities` en choisissant : `id`, `name`, le montant accordé (`points`), la localisation des icônes (`align: left|right`) et le comportement du widget (`show_in_widget: boolean`).

*Note : Afin de garantir le déploiement pur de vos refontes de thèmes via de la Side-load Installation, l'attribut Manifest `android:allowBackup="false"` a été volontairement fixé pour empêcher l'algorithme "Google Auto Backup for Apps" de ré-écraser votre nouveau `config.json` avec un ancien cache Cloud de l'utilisateur.*
