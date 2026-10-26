# Contributing to SpawnBots

Thank you for considering a contribution!

## Development setup

1. Install a Java 25 JDK.
2. Fork and clone the repository.
3. Run `bash ./gradlew build` (or `gradlew.bat build` on Windows).
4. Test changes on a local Paper 26.3 server before opening a pull request.

## Pull requests

- Keep changes focused and avoid unrelated reformatting.
- Preserve the lightweight scope of the plugin.
- Test bot removal, chunk-ticket cleanup, inventory persistence, and affected commands.
- Update the README when commands, permissions, or behavior change.
- Describe the tests you performed in the pull request.

SpawnBots uses Paper internals (NMS), so compatibility with the target Paper version must be verified after dependency updates.

By contributing, you agree that your contributions are licensed under the MIT License.
