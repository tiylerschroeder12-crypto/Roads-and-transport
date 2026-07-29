# Installation and Build Guide

## Build with GitHub Actions

This source project includes `.github/workflows/build.yml`.

1. Create a GitHub repository or replace the contents of the existing RoadsAndTransport repository.
2. Upload the contents of the `RoadsAndTransport` source folder to the repository root.
3. Commit and push to `main`.
4. Open the repository's **Actions** tab.
5. Open **Build RoadsAndTransport**.
6. Wait for the build to complete.
7. Download the `RoadsAndTransport-JAR` artifact.
8. Extract the artifact to obtain `RoadsAndTransport-0.1.0-alpha.jar`.

The workflow installs Temurin Java 25 and Gradle 9.6.1, runs `gradle clean build`, and uploads the built JAR.

## Install on the server

1. Stop the Paper server completely.
2. Confirm that `KingdomsAndCurrency-0.1.4-alpha.jar` is installed and working.
3. Place `RoadsAndTransport-0.1.0-alpha.jar` in the server's `plugins` folder.
4. Remove older RoadsAndTransport JARs so only one version is present.
5. Start the server.
6. Confirm that both plugins enable without errors.
7. Run `/rat help` in game.

The plugin creates `plugins/RoadsAndTransport/` and its data files on first start.

## Updating

1. Stop the server.
2. Back up `plugins/RoadsAndTransport/` and `plugins/KingdomsAndCurrency/`.
3. Replace only the RoadsAndTransport JAR.
4. Keep the existing RoadsAndTransport data folder.
5. Start the server and inspect the console.

## Manual local build

A local build requires Java 25 and Gradle:

```text
gradle clean build
```

The JAR will be placed in `build/libs/`.
