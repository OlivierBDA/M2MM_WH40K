# Projet M2MM : "Le Meilleur de Moi-Même" - Spécifications Techniques

Ce document décrit l'architecture et le fonctionnement de l'application de motivation personnelle "Le Meilleur de Moi-Même" (M2MM). Initialement développée en Python (via BeeWare/Toga), l'application est désormais une application **Android Native**.

## 1. Vision et Spécifications de l'Application

### 1.1. Objectif Principal

M2MM est une application de gamification personnelle conçue pour un usage unique. Son but est d'encourager et de motiver l'utilisateur à maintenir des habitudes saines (alimentation, exercice physique) à travers un système de points et de progression visuelle.

### 1.2. Mécaniques de Jeu (Gamification)

*   **Système de Points :** L'utilisateur possède un score global qui évolue en fonction de ses actions.
    *   **Gain de points :** Réaliser des activités bénéfiques (ex: faire du sport) augmente le score. Les valeurs de points sont éditables par l'utilisateur.
    *   **Perte de points :** Réaliser des activités néfastes (ex: mauvaise alimentation) diminue le score.
    *   **Décroissance Journalière :** Un malus de points fixe est appliqué quotidiennement pour simuler la nécessité d'un effort constant.
    *   **Feedback Visuel :** Un overlay animé affiche instantanément les points gagnés ou perdus (+25, -10) lors de l'enregistrement d'une activité.

*   **Niveaux et Progression :**
    *   Le score est divisé en paliers. Chaque palier correspond à un "niveau".
    *   Chaque niveau est représenté par une image de personnage thématique (Dragon Ball). L'image change à chaque nouveau palier.

*   **Suivi de Régularité :**
    *   L'application affiche le nombre de jours écoulés depuis la dernière réalisation de chaque activité.
    *   Un code couleur et un emoji indiquent le statut de régularité selon des seuils personnalisables.

### 1.3. Interface Utilisateur (UI)

L'interface est réalisée avec **Jetpack Compose** (Material 3), offrant un design moderne et fluide.

*   **Vue Principale (MainScreen) :**
    *   **En-tête** : Affiche le niveau actuel, le score et la progression vers le prochain palier.
    *   **Navigation** : Icône histogramme (gauche) pour les statistiques, icône engrenage (droite) pour les paramètres.
    *   **Centre** : Image du personnage en arrière-plan semi-transparent.
    *   **Activités** : Boutons-icônes disposés en colonnes avec indicateurs de statut (jours et emoji).

*   **Écran de Statistiques (StatsScreen) :**
    *   Affiche un graphique d'évolution du score sur les 30 derniers jours.
    *   Le graphique est réalisé sur mesure via `Canvas`, affichant le dernier score connu pour chaque journée.

*   **Écran de Configuration (SettingsScreen) :**
    *   Design moderne à base de cartes extensibles ("Accordion").
    *   Permet d'éditer le malus journalier, les points attribués par activité, et les seuils de régularité.

### 1.4. Configurabilité

Toute la logique de jeu est définie dans trois fichiers JSON distincts (`activities.json`, `levels.json`, `parameters.json`). L'utilisateur peut modifier les points et les seuils directement via l'interface, les changements étant persistés en toute sécurité dans un fichier `activities.json` local situé dans l'espace utilisateur. L'application possède une logique de migration si un ancien `user_config.json` est présent.

## 2. Architecture et Choix de Développement

### 2.1. Stack Technologique

*   **Langage :** [Kotlin](https://kotlinlang.org/).
*   **Framework UI :** [Jetpack Compose](https://developer.android.com/jetpack/compose).
*   **Base de Données :** [Room](https://developer.android.com/training/data-storage/room) (SQLite).
*   **Architecture :** MVVM (Model-View-ViewModel).
*   **Sérialisation :** Kotlinx Serialization.

### 2.2. Structure du Code (Source)

Le code est structuré dans le package `me.data_architect.m2mm` :

1.  **UI & ViewModels :**
    *   `MainActivity.kt` : Point d'entrée, gère la navigation et les écrans (`MainScreen`, `SettingsScreen`, `StatsScreen`).
    *   `MainViewModel.kt` : Gère l'état global, le score et le feedback.
    *   `SettingsViewModel.kt` : Gère l'édition de la configuration.
    *   `StatsViewModel.kt` : Gère le chargement de l'historique des scores.

2.  **Data Layer (`data/`) :**
    *   `GameRepository.kt` : Orchestre l'accès aux données et la logique métier (calculs, decay, history).
    *   `M2MMDatabase.kt` & `M2MMDao.kt` : Persistance Room.
    *   `GameState.kt`, `ActivityLog.kt`, `ScoreHistory.kt` : Entités de la base de données.

### 2.3. Gestion de la Persistance des Données

*   **Base de données Room** :
    *   `game_state` : Score actuel et date de mise à jour.
    *   `activity_log` : Historique des activités validées.
    *   `score_history` : Évolution temporelle du score pour les graphiques.
*   **Stockage Interne** :
    *   `user_config.json` : Configuration personnalisée de l'utilisateur.

## 3. Processus de Développement et Déploiement

### 3.1. Build & Workflow

*   **Gradle** gère les dépendances et la compilation.
*   **Workflow** : Développement itératif via Android Studio, installation via `./gradlew installDebug`.

### 3.2. Ressources

*   Les images (personnages) et icônes (activités) sont chargées dynamiquement à partir de leurs noms définis dans le JSON via `getIdentifier()`.
