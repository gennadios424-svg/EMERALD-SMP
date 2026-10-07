# Emerald SMP Final Restoration Changelog

## RESTORED
- Supplied working JAR remains the base.
- Existing economy, shards, shop, sell, AH, worth, pay/eco, RTP, homes, TPA, AFK, teams, tags, roles, Tab/scoreboard, crates/keys, spawners, Emerald tools, drill/gainer, invest, live, combat, Suslist, anti-cheat, authentication, player data and spawn protection are preserved.

## ADDED
- Clean side scoreboard layer.
- Main player list with role, team and ping.
- Secure /role hierarchy command.
- Staff /banlist GUI with no IP/UUID/network fields.
- AFK +3 Emerald Shards every 5 minutes while genuinely AFK.
- Ground-spawn correction after authenticated joins.
- Correct crate controls: LEFT preview, RIGHT open.

## FIXED
- Side scoreboard ping/online-count display removed by the replacement UI.
- Crate interaction direction corrected.
- Stale airborne reconnect handling corrected.

## CHANGED
- New restoration layer wraps the supplied plugin instead of replacing its systems.
- Final artifact target: EmeraldSMP-1.0.jar.

## VALIDATION
- The final build workflow compiles on Java 21 and attempts a Paper 1.21.11 startup test.
- A successful build must not be treated as runtime-validated unless the Paper startup check passes.
