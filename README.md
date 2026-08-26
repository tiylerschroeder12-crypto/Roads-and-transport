# RoadsAndTransport

**RoadsAndTransport** is a Paper 26.2 infrastructure plugin built for a civilization server. It connects roads, waypoint travel, physical mail, homes, upgraded cargo horses, and caravan trade to the physical gold economy and claims supplied by **KingdomsAndCurrency 0.1.5-alpha**.

Current version: **0.1.6.8-alpha**

## Requirements

- Paper `26.2`
- Java `25`
- KingdomsAndCurrency `0.1.5-alpha`

RoadsAndTransport has a hard dependency on KingdomsAndCurrency and will disable itself if that plugin is missing or unavailable.

## Features

### Roads

- Dirt-path blocks grant Speed I to grounded players walking on foot.
- A road surface with a redstone block directly beneath it grants Speed II.
- Bonuses expire almost immediately after leaving the road.
- Mounted, swimming, flying, and gliding players are excluded by default.

### Waypoints

Public waypoints are copper blocks created with:

```text
/createwaypoint public <waypoint name>
```

They cost 300g, must be inside a political land, and may be created by the owner or a Knight. Each rank currently defaults to four public waypoints, with separate configurable limits. Public trips cost 25g, paid into the destination land Treasury. If the original land no longer exists, the fare is stored in the waypoint.

Personal waypoints are created inside the player's own personal claim with:

```text
/createwaypoint personal
```

Each personal claim may contain one. Trips to a personal waypoint cost 5g and the fare is stored inside it. The owner can authorize another player for 100g with `/waypoint access add <player>`; that fee is also stored in the waypoint.

Travel is waypoint-to-waypoint only. It has a ten-second cancellable warm-up, is unavailable at night, and searches an eight-block radius for a safe arrival position. Public waypoint text appears two blocks above the copper block as:

```text
[Public Waypoint] owned by <Claim>, created by <Player>
```

At night, the display changes to:

```text
[Closed Until Dawn]
```

Public waypoints remain when their original land dissolves. A qualifying new land may adopt one with `/waypoint adopt`. Legitimate removal refunds the 300g construction cost and releases stored funds.

### Physical mail

`/mailbox create <mailbox name>` registers a named five-block delivery area centered on the player. The plugin plants a protected sign at the edge of the area reading `<Player>'s Mailbox- <Mailbox Name>`. Administrators can create a mailbox area for an offline or absent player at the administrator's current location with `/createmailboxfor <player> <mailbox name>`, which is useful for setup and solo testing. To send a package, fill a barrel, look at it, and use:

```text
/mail send <player>
```

The barrel and all of its contents enter plugin-managed transit. Normal delivery costs 5g and arrives after one Minecraft day. A delivered barrel appears physically in the recipient's mail area and is labeled:

```text
[Crate] from <Player> [<Claim>]
```

Rush mail uses:

```text
/mail rush send <player>
```

It arrives immediately and costs 10g plus 1g for every occupied slot. Occupied slots inside shulker boxes, bundles, and other container items are counted recursively. If a delivery area has no safe open space, the crate remains queued. If the recipient removes their mail area before delivery, the crate returns to the sender's area.

Delivered crates are protected from unauthorized opening, breaking, hoppers, pistons, and explosions until the recipient opens or breaks them. `/mail status` shows recent shipment states.

### Homes

Players may have up to four named home teleport points:

```text
/createhome <name>
/home <name>
/homelist
/delhome <name>
```

Homes are saved destinations only. Creating a home records the player's current block as a return point; it does not create, expand, reserve, or protect a KingdomsAndCurrency claim. `/home` and `/delhome` use explicit Paper Brigadier command trees intended to suggest the player's saved home names while typing. `/homelist` is the dedicated list command and shows every saved home as `/home <name> [visited X times]`; successful home teleports increment that persistent counter. Existing homes begin at 0 recorded visits when upgraded. Because listing is now a separate command, `list` is an ordinary valid home name again. Home travel is free, uses the configurable warm-up, and remains available during both day and night. A home remains usable if the surrounding land later changes ownership because the home is not a land claim. `/delhome <name>` removes only the teleport point. Public waypoints still close at night.

Starting a Home warm-up prints `Teleporting to <home>` followed by `Sneak, move, or open your inventory to cancel`; the action bar then carries only the short remaining-seconds countdown.

When upgrading from 0.1.5-alpha or older, RoadsAndTransport automatically removes the synthetic one-chunk claims it previously created for homes in unclaimed land while preserving the home teleport points themselves. Ordinary KingdomsAndCurrency claims are not touched.

### Horses and caravans

- Feeding a tamed horse an enchanted golden apple has a 25% chance to raise its custom speed tier by one.
- Each horse can gain at most one successful enchanted-apple speed increase, preventing one horse from being raised directly from tier 0 to tier 3. Further tiers must come through breeding.
- On success, the owner sees: `Your horse's eyes seem to be invigorated`.
- Upgraded speed can be inherited by offspring. The per-parent inheritance chance is configurable and defaults to 50%.
- Right-clicking an armored, owned horse with a chest attaches a persistent 54-slot cargo inventory.
- Sneak-right-clicking the horse with an empty hand opens cargo.
- Cargo access is limited to the owner, explicitly trusted players, and administrators.
- Cargo horses may be chained together with ordinary leads. Leash the first cargo horse to yourself, then leash another owned cargo horse while standing near the first; the new horse is attached to the tail of the caravan.
- A caravan may contain at most four horses total. Attempting to add a fifth reports `The caravan cannot lead any more horses`.
- The lead horse supplies the original 54 cargo slots. Each follower adds one chest-sized 27-slot page to the lead horse's paginated caravan cargo menu, for a maximum accessible capacity of 135 slots.
- Every additional linked horse applies a configurable speed penalty. The default is 10% per follower, so a four-horse caravan travels 30% slower before other speed rules are applied.
- `/horseinfo`, `/horsetrust <player>`, and `/horseuntrust <player>` manage and inspect cargo horses.

A caravan carrying at least 9g temporarily loses its custom speed bonus and earns 1g for every four newly visited chunks during that active shipment. Gold carried by any linked horse counts toward the caravan. Each chunk counts once until all gold is unloaded, at which point the route history resets. Only legitimate adjacent movement by the lead horse while ridden or led counts; teleports and unexplained chunk jumps do not.

At night, hostile mobs actively target cargo-equipped horses, even when their cargo is empty. Cargo and the attached chest drop if the horse dies.

## Commands

| Command | Purpose |
|---|---|
| `/createwaypoint public <name>` | Create a named public waypoint |
| `/createwaypoint personal` | Create a personal waypoint |
| `/delwaypoint` | Remove the waypoint being viewed |
| `/waypoint adopt` | Adopt an orphaned public waypoint |
| `/waypoint info` | Inspect the waypoint being viewed |
| `/waypoint access add <player>` | Authorize personal-waypoint access for 100g |
| `/waypoint access remove <player>` | Revoke personal-waypoint access |
| `/mailbox create <name>` | Create and name your mail delivery area |
| `/createmailboxfor <player> <name>` | Create a named area for another player at your location (admin) |
| `/mailbox info` | Show mail-area information |
| `/delmailbox` | Remove the player's mail area |
| `/mail send <player>` | Send a barrel after one Minecraft day |
| `/mail rush send <player>` | Send a barrel immediately at rush pricing |
| `/mail status` | Show recent shipments |
| `/createhome <name>` | Create a named home |
| `/home <name>` | Return to a named home |
| `/homelist` | List every saved home with its visit count |
| `/delhome [name]` | List deletable homes with visit counts, or delete a saved home teleport point |
| `/horseinfo` | Inspect the targeted horse |
| `/horsetrust <player>` | Grant cargo access |
| `/horseuntrust <player>` | Revoke cargo access |
| `/rat help` | Show command help |
| `/rat save` | Save plugin data immediately (admin) |
| `/rat reload` | Reload configuration (admin; restart recommended) |

## Data files

The plugin saves persistent state in:

```text
plugins/RoadsAndTransport/waypoints.yml
plugins/RoadsAndTransport/homes.yml
plugins/RoadsAndTransport/mail.yml
plugins/RoadsAndTransport/horses.yml
```

Back up this folder before replacing builds or changing compatibility-sensitive settings.
