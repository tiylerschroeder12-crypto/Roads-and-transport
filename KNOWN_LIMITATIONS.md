# Known Limitations — 0.1.6-alpha

- KingdomsAndCurrency 0.1.5-alpha does not expose a public extension API. RoadsAndTransport therefore integrates through reflection against that version's internal model and service methods. A future KingdomsAndCurrency source refactor may require a RoadsAndTransport compatibility update.
- Normal mail uses an elapsed-time equivalent of 24,000 ticks (20 minutes). Time spent with the server offline currently counts toward delivery.
- All political ranks currently default to four public waypoints because only the Peasantry limit was finalized. Each rank has a separate value in `config.yml` for later balancing.
- Horse speed inheritance defaults to 50% per upgraded parent because an exact probability was not finalized during design.
- Abrupt process termination during an active waypoint fare reservation may prevent the graceful-shutdown refund path. Regular server stops refund active reservations.
- Homes upgraded from 0.1.5-alpha or older remove their legacy synthetic claim on startup. If KingdomsAndCurrency rejects a removal because of external data corruption, RoadsAndTransport keeps the legacy claim id and retries on the next startup.
- This source package was structure-, YAML-, and syntax-checked in the creation environment, but the authoritative Paper API compilation is the included Java 25 GitHub Actions build.

- `/createmailboxfor` can only target a player already known to the server, because it uses the server's existing offline-player records.
- Mailbox names are split across the lower two sign lines when longer than sixteen characters.
