# SpawnBots auf GitHub veröffentlichen

## Empfohlene Repository-Daten

- **Name:** `SpawnBots`
- **Beschreibung:** `Lightweight controllable fake players for Paper with skins, equipment, actions, persistent inventories, and chunk management.`
- **Topics:** `minecraft`, `paper-plugin`, `bukkit`, `fake-players`, `bots`, `java`
- **Lizenz:** MIT (bereits enthalten)

## Hochladen mit Git

Entpacke das Projekt, öffne ein Terminal im Projektordner und ersetze `DEIN-BENUTZERNAME`:

```bash
git init
git add .
git commit -m "Initial SpawnBots 2.2.0 release"
git branch -M main
git remote add origin https://github.com/DEIN-BENUTZERNAME/SpawnBots.git
git push -u origin main
```

Erstelle dafür vorher auf GitHub ein leeres Repository ohne README, Lizenz oder Gitignore. Alternativ kannst du alle entpackten Dateien über **Add file → Upload files** hochladen.

## Release erstellen

1. Öffne im Repository **Releases → Create a new release**.
2. Erstelle den Tag `v2.2.0`.
3. Verwende den Titel `SpawnBots 2.2.0 for Paper 26.3`.
4. Lade `SpawnBots-26.3-2.2.0.jar` als Release-Datei hoch.
5. Veröffentliche das Release.

Der enthaltene GitHub-Workflow baut bei jedem Push und Pull Request automatisch eine JAR und stellt sie als Build-Artifact bereit.
