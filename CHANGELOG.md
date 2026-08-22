# Changelog

## 0.1.6.5-alpha

- Moved `/home` and `/delhome` onto Paper's Brigadier-backed `BasicCommand` registration so saved home names are advertised as real client command suggestions instead of depending on the legacy Bukkit tab-completion bridge.
- The main `/home` and `/delhome` labels intentionally override earlier command registrations, which also prevents another plugin from silently owning the bare command names on Paper.
- Multi-word home names remain supported by suggesting the remaining words as the player types.

## 0.1.6.4-alpha

- Reworked `/home` and `/delhome` tab completion so saved home names are returned as live command suggestions, including names containing spaces.
- Bare `/home` and `/delhome` now send the full saved-home list as one multi-line chat component, avoiding line-by-line chat filtering/collapse.
- `/delhome` uses a deletion-specific hint while `/home` uses the travel hint.


## 0.1.6.2-alpha

- Running `/home` with no name now lists the player's saved home names instead of showing a usage error.
- Running `/delhome` with no name now shows the same home-name list before the player chooses one to delete.
- `/home` and `/delhome` tab completion now suggests the player's saved home names for the first argument.

## 0.1.6.1-alpha

- Added an inert compatibility shim for stale `HomeClaimListener.java` files left behind when a repository is updated by overlaying the 0.1.6 source tree instead of deleting removed files.
- Fixes the GitHub Actions compile error for `HomeService.isManagedClaimName(String)` without restoring any Home claim behavior.
- Homes remain pure teleport anchors; the obsolete listener always receives `false` from the compatibility method.

## 0.1.6-alpha

- Redesigned Homes as named teleport points rather than synthetic one-chunk claims.
- Increased the default and migrated Home limit from two to four per player.
- `/createhome <name>` no longer creates or requires land ownership and now reports simply `Home Created!`.
- `/delhome <name>` removes only the saved teleport point.
- Home travel no longer becomes invalid when the destination's claim ownership changes.
- Removed the generated-home-claim command guard and therefore the home-specific claim enter/leave message spam.
- Added one-time migration of legacy generated Home claims from 0.1.5-alpha and older; saved Home destinations are retained.
- Existing custom `homes.maximum` values are preserved except the old default value of 2, which migrates to 4.

## 0.1.5-alpha

- Homes can now be used at night; only public waypoint travel remains closed until dawn.
- Added horse-to-horse lead chains for cargo caravans.
- Caravans support a maximum of four linked horses total.
- Attempting to add a fifth horse now displays `The caravan cannot lead any more horses`.
- The lead cargo horse keeps its 54-slot storage and each linked follower adds one chest-sized 27-slot cargo page.
- Added a paginated caravan cargo interface with two base pages plus one page per follower.
- Added a configurable 10% speed penalty per additional linked horse by default.
- Caravan gold, speed suppression, and unique-chunk rewards now use the linked caravan as a whole.

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
