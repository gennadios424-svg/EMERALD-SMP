# 💚 EMERALD SMP — FINAL RESTORATION SPEC

This repository uses the uploaded working Emerald SMP JAR as the baseline. Do not replace it with a simplified plugin or remove working systems.

## Non-negotiable preservation
Preserve all existing working systems: Money, Emerald Shards, sell/shop/AH/worth/pay/eco/cf/rtp/home/tpa/tpahere/spawn/setspawn/AFK/teams/tags/roles/tab/side tab/scoreboard/crates/keys/spawners/Emerald tools/Emerald Gainer/Original Drill/invest/live/combat/Suslist/anti-cheat/spawn protection/SimpleLogin/Geyser-Floodgate compatibility/player data/economy data.

## Tab
Side tab only: Emerald SMP, rank, money, Emerald Shards, server address. Remove ping, online count, filler, duplicate/debug values.
Main tab: online players, names, rank, team, ping.
Role colors: professional emerald/dark green/light green/gold/aqua/purple mixture; ranks must be visibly distinguishable without rainbow styling.
Hierarchy: OWNER > DEV > MOD > EMERALD > MVP > VIP > MEDIA > MEMBER.

## Staff and roles
Higher roles inherit appropriate lower-role features, but permissions remain controlled.
MOD+ uses /suslist and staff investigation tools. Suslist must not auto-ban solely from suspicion.
Staff need vanish/watch/spectate and anti-cheat information.
Add real useful role kits. CPvP kit includes crystals, obsidian, totems, golden apples, suitable armor/weapons/tools.
/media is available to appropriate higher roles such as MOD+.
Add secure /role <player> <role> or Emerald GUI. Never allow self-promotion or promotion above authority; no hardcoded usernames.

## Banlist/privacy
/banlist is a GUI with player, reason, expiry and banned-by. Pagination and player detail view.
Never expose IP, IP history, connection address, hostname, UUID, database ID, network information or debug data to players. Audit every player-facing GUI/lore/chat/tab/hover/command/Suslist/Banlist/Role Manager output.

## Spawners
Skeleton spawn amount 1–6 every 25 seconds, server-side and without duplicate schedulers after reload/restart.
Diamond and Netherite pickaxes can break spawners; lower tiers cannot. Spawner item drops normally.
No owner lock.
Keep Drop All; release contents/entities forward based on the player's current facing direction.

## Join/authentication
After login/authentication, immediately place players at the configured ground-floor spawn at the correct login/spawn stage. Prevent stale airborne coordinates from triggering anti-cheat. Do not introduce a delayed teleport that permits a false-flying kick. Preserve SimpleLogin password storage/authentication.

## Crates and keys
Emerald keys are consumable: exactly one key per successful crate opening. KeyAll keys are also consumable.
Crate hologram identifies required key and controls:
LEFT CLICK = Preview, no key consumed.
RIGHT CLICK = Open, checks key, consumes exactly one, grants reward.
Restore crate-only Emerald Sell Axe, Emerald Tree Chopper and Emerald 2x Sell Helmet. Authenticate with persistent internal item data, not only name/lore/material/enchantments.
Axe: 6 days. Tree Chopper: 6 days. Helmet: 24 hours.
Timer starts only when won/received. Persist through movement, drop/pickup, chest, death, relog, restart, AH listing and purchase. Expired items show EXPIRED and lose their ability.
Helmet provides 2x sell value.

## Economy
Use one K/M/B formatter everywhere without changing underlying values:
1000=$1K, 1500=$1.5K, 25000=$25K, 1,000,000=$1M, 1,250,000=$1.25M, 25,000,000=$25M, 1,000,000,000=$1B.
Apply to pay/bal/eco/shop/AH/worth/sell/invest/Tab/transactions/reset-related displays.
Shop: CPvP contains End Crystal; Spawners, Nether and CPvP category clicks must open their own correct shops.
AFK: /afk and /setafk; genuinely AFK players receive +3 Emerald Shards every 5 minutes; movement cancels AFK. One configured AFK hologram, no duplicates.
Combat restrictions remain; /shop stays allowed.
Offline /eco give/take/set must update persistent balances for offline players.
AH hover stays clean: item, price, seller, listing expiry; Emerald items also show their own expiry. AH expiry and item expiry are separate.
LIVE accepts only YouTube, Twitch and TikTok URLs.
Spawn protection remains active for the configured safe spawn.

## Validation
Test startup and runtime on Paper 1.21.11 / Java 21, not merely compilation. Validate spawn protection, join/auth/ground teleport/no false fly, spawner timing/breaking/drop-all, consumable keys and crate controls, authenticated Emerald items and persistence, staff permissions/hierarchy, Tab, Banlist, zero IP exposure, economy formatting/offline eco, AH and live URL validation.

## Delivery
Final artifact name: EmeraldSMP-1.0.jar.
Provide a complete changelog grouped into RESTORED / ADDED / FIXED / CHANGED.
Never claim runtime success without actually verifying plugin enablement on the target Paper/Java runtime.
