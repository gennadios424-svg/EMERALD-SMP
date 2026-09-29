# Emerald SMP — Connection Foundation

EmeraldSMP does not implement Minecraft protocol translation or authentication.

## Intended server stack

- Paper 1.21.11 — Minecraft Java server
- Geyser-Spigot — Bedrock -> Java protocol bridge
- Floodgate-Spigot — Bedrock identity/authentication for Geyser
- Optional established authentication infrastructure for cracked Java players, kept separate from EmeraldSMP

Geyser and Floodgate remain external server infrastructure. EmeraldSMP only consumes normal Bukkit/Paper player identities and keeps its own persistent data keyed by UUID.

## Geyser/Floodgate

For a Paper/Spigot server, install Geyser-Spigot and Floodgate-Spigot in the server's plugins directory. Configure Geyser to use Floodgate authentication.

Geyser's Bedrock listener uses UDP. The default Bedrock port is 19132; the actual port must match the hosting/network setup.

Keep Floodgate's key.pem private. Never commit it to this repository or distribute it.

## Cracked Java players

Allowing cracked Java players requires an offline-mode authentication architecture. Do not implement authentication inside EmeraldSMP and never store Mojang/Microsoft passwords.

Use an established, separately maintained authentication solution appropriate for the server host if cracked Java accounts are enabled. The security responsibility for authenticating offline Java identities stays outside EmeraldSMP.

Important: a plain offline-mode server cannot safely treat a username as proof of identity. EmeraldSMP therefore never uses usernames as player-data keys. UUID is always the permanent key.

## EmeraldSMP compatibility

EmeraldSMP intentionally does not depend on Geyser/Floodgate for its core player-data storage. A player entering through Java or Bedrock is still represented by the Paper Player object and persisted by UUID.

If a future EmeraldSMP feature needs to know whether a player is Bedrock, that feature may use the Floodgate API through an optional integration layer. It must not become a requirement for the core plugin.

## Testing checklist

The actual connection test must be performed on the running server/network:

1. Premium Java player joins.
2. Cracked Java player joins through the chosen authentication architecture.
3. Bedrock player joins through Geyser/Floodgate.
4. Java and Bedrock players can see and interact with each other.
5. EmeraldSMP creates/loads the correct UUID data file for each player.
6. Disconnect/reconnect preserves data.
7. Server restart preserves data.
8. No player-data files are overwritten because of username changes.
9. Console contains no EmeraldSMP errors or stack traces.
10. Geyser console connectivity can be checked with its geyser connectiontest command.

This repository does not include Geyser, Floodgate, authentication plugins, their generated configuration, or Floodgate keys. Those belong to the server infrastructure layer, not EmeraldSMP.
