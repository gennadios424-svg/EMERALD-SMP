# Emerald SMP — Java Client Version Compatibility

## Server version

The backend remains **Paper 1.21.11**. This change does not upgrade Paper and does not change EmeraldSMP's UUID/player-data system.

## Compatibility layer

The server plugin bundle now includes **ViaVersion 5.12.0**.

ViaVersion is installed as a separate Paper plugin in:

`server/plugins/ViaVersion-5.12.0.jar`

ViaVersion 5.12.0 supports newer Java clients through **26.3** while supporting Paper servers from 1.8 through 26.3. For this EmeraldSMP setup, the backend remains Paper 1.21.11.

No custom protocol translation code is added to EmeraldSMP.

## Connection architecture

Java:

`Java client -> ViaVersion -> Paper 1.21.11 -> EmeraldSMP`

Bedrock:

`Bedrock client -> Geyser/Floodgate -> Paper 1.21.11 -> EmeraldSMP`

Geyser, Floodgate, premium Java authentication, cracked Java authentication, and UUID player data are not modified by this compatibility change.

## Installation

1. Keep the existing Paper 1.21.11 server JAR.
2. Put `ViaVersion-5.12.0.jar` in the server's `plugins/` directory.
3. Keep Geyser/Floodgate in the same plugins directory.
4. Start the server normally.
5. Confirm ViaVersion loads successfully in the console.
6. Test a native 1.21.11 Java client.
7. Test a newer Java client.

### Important

This repository change prepares and packages the ViaVersion JAR through GitHub Actions. It does **not** claim that a real client connection has been tested here.

Do not add ViaBackwards or ViaRewind unless the server later needs older Java client versions. The requested change is newer-client compatibility, which ViaVersion provides directly.

## Current target

- Native Java: 1.21.11
- Newer Java: supported where ViaVersion 5.12.0 has protocol mappings, currently through 26.3
- Bedrock: unchanged through Geyser/Floodgate
- Paper backend: **1.21.11 — unchanged**
