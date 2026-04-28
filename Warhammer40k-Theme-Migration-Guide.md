# Guide de Migration de Thème : Warhammer 40,000

Ce guide détaille les étapes nécessaires pour remplacer le thème actuel (Dragon Ball) par votre nouveau thème **Warhammer 40,000**.

## 1. Préparation des Images

Toutes les images doivent être placées dans le dossier des ressources de l'application :
`c:\Workplace\Dev\M2MM\app\src\main\res\drawable-nodpi\`

### Images de Fond (Wallpapers)
Copiez vos 7 images de progression. Assurez-vous qu'elles sont au format `.png` ou `.jpg`.
*Exemple de noms recommandés :* `wh_level_1.png`, `wh_level_2.png`, etc.

### Icônes d'Activités
Copiez les nouvelles icônes pour vos boutons d'actions.
*Exemple :* `bolter.png`, `chainsword.png`, etc.

> [!IMPORTANT]
> **Règle de nommage Android** : Les noms de fichiers ne doivent contenir que des lettres minuscules (a-z), des chiffres (0-9) et des underscores (_). Pas d'espaces ni de majuscules.

---

## 2. Modification de la Configuration

Le fichier central de configuration se trouve ici :
`c:\Workplace\Dev\M2MM\app\src\main\assets\config.json`

Ouvrez ce fichier et mettez à jour les sections suivantes :

### Niveaux de Progression (`levels`)
Remplacez la liste actuelle par vos 7 nouveaux niveaux.
```json
"levels": [
  {
    "name": "Néophyte",
    "threshold": 0,
    "image": "wh_level_1.png"
  },
  {
    "name": "Initié",
    "threshold": 500,
    "image": "wh_level_2.png"
  },
  ... (ajoutez les suivants)
]
```

### Activités (`activities`)
Mettez à jour les identifiants, noms et icônes :
```json
{
  "id": "entrainement_bolter",
  "name": "Entraînement au Bolter",
  "points": 50,
  "icon": "bolter.png",
  "type": "good",
  ...
}
```

---

## 3. Application des Changements

Une fois les images copiées et le fichier `config.json` modifié :

1.  **Réinitialisation de la configuration utilisateur** : L'application copie le fichier `assets/config.json` vers un stockage interne lors du premier lancement. Pour forcer la mise à jour :
    *   Soit vous désinstallez et réinstallez l'application.
    *   Soit vous effacez les données de l'application dans les paramètres Android de votre appareil/émulateur.
2.  **Compilation** : Relancez une compilation via Gradle pour inclure les nouvelles images.

---

## 4. Vérification du Widget
Si vous avez changé les noms des icônes ou des activités, n'oubliez pas de retourner dans les **Paramètres** de l'application pour cocher à nouveau les activités que vous souhaitez voir sur le widget.
