# Known Limitations — 0.1.1-alpha

- KingdomsAndCurrency 0.1.4-alpha does not expose a public extension API. RoadsAndTransport therefore integrates through reflection against that version's internal model and service methods. A future KingdomsAndCurrency source refactor may require a RoadsAndTransport compatibility update.
- Free home chunks are created as synthetic personal claims. They are non-expandable through RoadsAndTransport's command guard, but KingdomsAndCurrency may still include them in displays or accounting intended for ordinary personal claims.
- Normal mail uses an elapsed-time equivalent of 24,000 ticks (20 minutes). Time spent with the server offline currently counts toward delivery.
- All political ranks currently default to four public waypoints because only the Peasantry limit was finalized. Each rank has a separate value in `config.yml` for later balancing.
- Horse speed inheritance defaults to 50% per upgraded parent because an exact probability was not finalized during design.
- Abrupt process termination during an active waypoint fare reservation may prevent the graceful-shutdown refund path. Regular server stops refund active reservations.
- This source package was structure-, YAML-, and syntax-checked in the creation environment, but the authoritative Paper API compilation is the included Java 25 GitHub Actions build.

- `/createmailboxfor` can only target a player already known to the server, because it uses the server's existing offline-player records.
- Mailbox names are split across the lower two sign lines when longer than sixteen characters.
