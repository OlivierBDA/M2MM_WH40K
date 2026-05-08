# Contexte pour l'Agent de Codage (Antigravity / Gemini)

Ce fichier `Agent.md` sert de point de départ et de mémoire pour toute nouvelle session de développement sur le projet **M2MM**. Il rassemble les éléments architecturaux clés et les solutions techniques acquises lors des sessions précédentes pour éviter de répéter les mêmes erreurs ou recherches.

## 1. Contexte du Projet M2MM

*   **Application** : M2MM (Le Meilleur de Moi-Même)
*   **Plateforme** : Android Native (Kotlin)
*   **UI** : Jetpack Compose, Material Design 3
*   **Données** : Room (SQLite), Datastore/JSON pour la configuration (`activities.json`, `levels.json`, `parameters.json`)
*   **Architecture** : MVVM (Model-View-ViewModel), Flux Unidirectionnel (StateFlow)
*   **Fonctionnalité Phare** : "Coach Primarque", une IA de motivation générant des messages push quotidiens personnalisés en fonction des actions de l'utilisateur.

---

## 2. Architecture IA Hybride (Le "Coach Primarque")

L'application utilise un pattern **Strategy** pour gérer l'inférence de l'intelligence artificielle. Toute nouvelle modification sur l'IA doit respecter cette interface.

*   **Interface Commune** : `me.data_architect.m2mm.data.LlmService`
    *   Expose la méthode principale : `suspend fun generateEncouragementDynamic(...)`
*   **Implémentation Cloud** : `CloudLlmService.kt`
    *   Utilise l'API Web HTTP de Google AI Studio (Gemini 4). 
    *   Nécessite une API Key.
*   **Implémentation Locale (On-Device)** : `LocalLlmService.kt`
    *   Utilise le NPU/TPU du smartphone (Pixel 8 Pro, Pixel 10 Pro, etc.).
    *   S'appuie sur le SDK **Google AI Edge (AICore)** avec le modèle Gemma Nano / Gemini Nano natif.

---

## 3. Documentation Critique : Manipulation de l'API Google Edge AICore

> **⚠️ À l'attention de l'Agent de Codage : Lisez ceci AVANT de modifier l'IA Locale**

La librairie `com.google.ai.edge.aicore:aicore` (Google AI Edge AICore) est souvent en version **Early Access / Expérimentale** (ex: `0.0.1-exp02`). Sa documentation sur le web est souvent **obsolète**, fausse ou confondue avec la librairie Cloud (`generativeai`).

### Règle d'or en cas d'erreur de compilation sur un SDK Beta
**Ne devinez jamais les signatures des méthodes en cas d'erreur (ex: `Unresolved reference`).**
Si l'API a changé silencieusement, vous DEVEZ :
1. Télécharger l'archive `.aar` de la version exacte utilisée dans le `build.gradle.kts` via un script bash/powershell (ex: avec `curl`).
2. Décompresser l'archive et utiliser `javap` sur le fichier `classes.jar` pour lire la signature exacte des constructeurs et des méthodes (ex: `javap -public -cp classes.jar com.google.ai.edge.aicore.GenerativeModel`).

### Le Piège du Constructeur `GenerativeModel` (AICore Edge)
Contrairement à la version Cloud qui prend `(modelName, apiKey)`, la version Edge On-Device (`com.google.ai.edge.aicore.GenerativeModel`) **ne prend pas de paramètre `modelName` ni de `context` direct** dans son constructeur. Le système Android gère lui-même le choix du modèle Nano téléchargé.

**La syntaxe exacte et fonctionnelle (vérifiée par rétro-ingénierie) est :**

```kotlin
// 1. Instancier la configuration via le Builder (qui lui, demande le Context Android)
val config = com.google.ai.edge.aicore.GenerationConfig.Builder().apply {
    context = this@LocalLlmService.context // Le contexte système Android
}.build()

// 2. Instancier le modèle avec UNIQUEMENT la configuration
val model = com.google.ai.edge.aicore.GenerativeModel(config)

// 3. Lancer l'inférence (Fonction Suspend)
val result = model.generateContent(combinedPrompt)
val realResponse = result.text ?: ""
```

*(Note: N'essayez pas d'utiliser des classes comme `AiCoreClient` ou `ModelOptions` qui existaient dans les tous premiers drafts théoriques de Google mais n'existent pas dans les releases `.aar`).*

---

## 4. WorkManager et Tâches de Fond

La notification est générée par le `CoachWorker.kt` (Android WorkManager) chaque jour.
Ce Worker instancie dynamiquement le bon service (Cloud ou Local) en lisant la valeur du paramètre booléen `useLocalLlm` depuis `GameRepository.loadConfig()`.

Puisque l'inférence locale via AICore est asynchrone et lourde, elle doit s'exécuter dans le dispatcher de la coroutine du Worker (`Dispatchers.IO`). Assurez-vous de ne jamais bloquer le thread principal (UI Thread).
