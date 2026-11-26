# EmoteCreator — Coconut Network themed Paper plugin

A starter implementation of a Coconut-inspired emote GUI and admin studio.

## Requirements
- Paper 1.21.4
- Java 21
- Maven 3.9+

## Build
Run `mvn clean package`. The plugin JAR will be in `target/EmoteCreator-1.0.0.jar`.
Copy it into the server's `plugins/` directory and restart.

## Commands
- `/emotes` — open the emote selector
- `/emotes <id>` — play an emote
- `/emotes create` — start creating an emote (admin permission)
- `/emotes stop` — stop is currently a lightweight command placeholder; particle effects naturally expire
- `/emoteadmin` — open the admin panel

## Permissions
- `emotecreator.use` — use emotes (default true)
- `emotecreator.admin` — admin GUI (default op)
- `emotecreator.create` — create emote IDs (default op)
- `emotecreator.bypasscooldown` — bypass cooldown (default op)
- `emotecreator.emote.<id>` — per-emote permission, configured in emotes.yml

## Customisation
Edit `config.yml`, `emotes.yml` and `messages.yml`. GUI colours use MiniMessage formatting. The default theme uses warm coconut gold, cream and dark inventory panes as a provisional theme.

## Coconut Network brand matching
No Coconut Network resource pack or screenshots were supplied, so this project does **not** claim to reproduce the server's exact branding. Replace the provisional palette, icons and menu title after comparing against your live server pack. Keep custom model identifiers namespaced to avoid collisions.

## Important animation limitation
Vanilla Paper server plugins cannot create arbitrary skeletal player-model animations on unmodified clients. This implementation provides timed particle and sound effects around the player. True custom dances, waving limbs and custom pixel-art GUI textures require compatible client resource-pack/model support and, for some approaches, client-side mod support.

## Notes
The create flow currently creates a basic emote entry using a coconut icon; edit its name, description, category, icon, rarity, permission and effect in `plugins/EmoteCreator/emotes.yml`. The plugin is a functional starting point, not a finished resource-pack-backed animation suite.
