# Changelog

## 0.1.4-alpha

- Replaced `/createmailbox` with `/mailbox create <name>`.
- Standardized player-facing deletion commands as `/delmailbox`, `/delwaypoint`, and `/delhome <name>`.
- Updated protected-block guidance and command help to use the new deletion commands.
- Corrected the free-home message to `Home Created! One chunk claimed for safety`.
- Changed caravan speed suppression so it begins only when cargo contains at least 9g.
- Prevented horse teleports and portal travel from marking destination chunks as visited caravan territory.

## 0.1.3-alpha

- Fixed physical mail crates arriving without the items stored in the sent barrel.
- Added automatic restoration for unopened delivered crates affected by the empty-crate bug when their shipment data still contains the original items.

## 0.1.2-alpha

- Reworded home and waypoint travel countdowns for clearer cancellation instructions.
- Reworded home deletion for generated one-chunk home claims.
- Replaced technical horse-upgrade and cargo-attachment messages with natural wording.
- Simplified `/horseinfo` to speed tier, cargo usage, cargo gold, and caravan progress.

## 0.1.1-alpha

- Named mailbox areas with automatically placed, protected edge signs
- Added `/createmailboxfor <player> <mailbox name>` for administrator setup and solo testing
- Mail-area data migration from the 0.1.0-alpha center-only format
- Enchanted golden apples now have a 25% horse-speed success chance
- Each horse may receive only one successful enchanted-apple speed increase
- Updated successful horse message to `Your horse's eyes seem to be invigorated`
- Updated free home-claim creation message to `Home Created! One chunk claimed for saftey`

## 0.1.0-alpha

Initial alpha implementation:

- Speed I dirt paths and Speed II redstone-supported roads
- Public and personal copper waypoints
- Night closures, labels, safe arrival, fares, adoption, and removal refunds
- Physical barrel mail, rush delivery, recursive pricing, status, returns, and protected crates
- Two-player-home limit with free one-chunk home claims in unclaimed land
- Horse speed upgrades and inheritance
- Armored-horse 54-slot cargo inventories and trust permissions
- Gold-speed suppression, unique-chunk caravan rewards, and nighttime hostile targeting
- Persistent YAML storage and GitHub Actions Java 25 build workflow
