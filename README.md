# SpawnBots

SpawnBots is a lightweight Paper plugin for creating controllable fake players. Bots use real player profiles and name-based skins, can collect and use items, keep their inventories between sessions, and manage their own force-loaded chunk area.

No client mods, external databases, or additional plugins are required.

## Features

- Real fake-player entities with signed, name-based Mojang skins
- Full outer skin layers for the complete 3D skin appearance
- Exact spawn position, yaw, and pitch
- Pushable bots with gravity and item pickup
- Command-driven sword attacks, tool-based mining, and eating
- Clearly labeled equipment GUI for hands, armor, hotbar, and storage
- Persistent inventories across deaths, removals, and server restarts
- Optional invulnerability per bot
- Teleportation to a player or explicit coordinates
- A 5×5 force-loaded chunk area per bot with automatic cleanup
- Permission-filtered tab completion
- Built-in performance and error diagnostics
- Death removal with a leave message

## Requirements

- **Paper 26.3**
- **Java 25**
- Access to Mojang's profile services for name-based skin loading

SpawnBots uses Paper internals (NMS) and is intended specifically for the Paper version listed above. Other server implementations and Minecraft versions are not guaranteed to work.

## Installation

1. Download `SpawnBots-26.3-2.2.0.jar`.
2. Place it in your server's `plugins` folder.
3. Restart the server.
4. Run `/bot player <Name>` while standing at the desired spawn location.

Using the same spawn command again removes that bot, saves its inventory, and releases its chunk tickets.

## Commands and permissions

All permissions default to server operators. The `spawnbots.*` permission grants every command.

| Command | Permission | Description |
| --- | --- | --- |
| `/bot player <Name>` | `spawnbots.spawn` | Spawns a bot at your position, or removes the named bot if it is already active. |
| `/bot equip` | `spawnbots.equip` | Opens the active-bot selector. |
| `/bot equip <Name>` | `spawnbots.equip` | Opens the bot's equipment inventory directly. |
| `/bot <Name> attack` | `spawnbots.attack` | Starts attacking the targeted entity; run again to stop. |
| `/bot <Name> mine` | `spawnbots.mine` | Starts mining the targeted block; run again to stop. |
| `/bot <Name> eat` | `spawnbots.eat` | Eats food from the main hand or offhand when hungry. |
| `/bot <Name> stop` | `spawnbots.stop` | Stops the bot's current action. |
| `/bot <Name> undying` | `spawnbots.undying` | Makes the bot invulnerable. |
| `/bot <Name> dying` | `spawnbots.dying` | Allows the bot to take damage and die. |
| `/bot <Name> tp` | `spawnbots.tp` | Teleports the bot to your current position. |
| `/bot <Name> tp <x> <y> <z>` | `spawnbots.tp` | Teleports the bot to coordinates in your current world. |
| `/bot log` | `spawnbots.log` | Shows active bots, actions, chunk tickets, task timings, saved inventories, and recent errors. |

Available commands, bot names, actions, and teleport coordinates are suggested through tab completion according to the sender's permissions.

## Inventory storage

Inventory snapshots are stored in:

```text
plugins/SpawnBots/inventories.yml
```

The plugin saves the main inventory, hotbar, armor, and offhand. Items are restored when the bot is created again. Death drops are cleared for bots because their inventory is retained in this storage.

Back up this file before manually editing or deleting it. Manual edits should only be made while the server is stopped.

## Chunk management

Each active bot keeps a 5×5 chunk area loaded with Paper's plugin chunk-ticket API. Teleporting a bot moves these tickets to the destination. Removing a bot, its death, and plugin shutdown release the associated tickets.

## Building from source

A Java 25 JDK is required. The Gradle wrapper downloads the remaining build dependencies automatically.

### Linux and macOS

```bash
bash ./gradlew build
```

### Windows

```bat
gradlew.bat build
```

The plugin is written to:

```text
build/libs/SpawnBots-26.3-2.2.0.jar
```

The project uses the official `paperweight-userdev` build tooling to compile against Mojang-mapped Paper internals. The Paper dev-bundle version is pinned in `build.gradle.kts` for reproducible builds.

## Contributing

Bug reports, focused improvements, and pull requests are welcome. See [CONTRIBUTING.md](CONTRIBUTING.md) for development and testing guidelines.

## License

SpawnBots is available under the [MIT License](LICENSE).
