# Meridian Camera Tweaks

Freecam, perspective and entity-camera controls for the
[Meridian Proxy](../meridian-proxy) — a pure Layer-2 module built on top of
`meridian-core`.

It talks only to neutral APIs (`meridian-api` + `meridian-core-api`) and never
touches raw Hytale packets, so a Hytale protocol update cannot break it. The
actual camera packets (`SetServerCamera` / `SetFlyCameraMode`) are built by
`meridian-core`'s `CameraControl` service.

## Requirements

This module **requires `meridian-core` ≥ 0.2.0** — it drives the camera through
`meridian-core`'s `CameraControl` service and picks follow/POV targets through
its `EntityTracker` service. Put **both** jars in the proxy's modules folder:

```
modules/
├── meridian-core-impl-*.jar
└── meridian-camera-tweaks-*.jar
```

`meridian-core` loads first (camera-tweaks' `module.json` declares
`dependsOn: meridian-core >=0.2.0`). Without it, camera-tweaks is skipped with a
warning.

## Features

Pick a **Camera Mode** from the module's settings panel in the proxy window:

- **First Person** — native first-person view.
- **Third Person** — third-person view with a tunable trailing **distance** and
  **X / Y / Z offset**. A **Front view** toggle flips the camera 180° to face
  the player from the front, looking back (Minecraft's F5 mode).
- **Freecam** — free-fly spectator camera. The **Always allow in-game freecam
  key** toggle lets the proxy answer the client's own freecam keybind directly,
  bypassing the server's `FLY_CAM` permission.
- **Follow Entity** — spectator follow-cam trailing a chosen entity, with a
  tunable distance and X / Y / Z offset.
- **Entity POV** — first-person view from inside a chosen entity, with the same
  offset controls.

Follow / POV targets are selected by **Target Selection**:

- **Nearest** — the entity closest to the player.
- **Crosshair** — the entity the player is looking at.

All settings persist except **Camera Mode**, which is session-only and always
starts at `Default` after a restart.

## Build

```sh
mvn clean package
```

Needs `meridian-api` and `meridian-core-api` in the local Maven repo — build the
[`meridian-proxy`](../meridian-proxy) and [`meridian-core`](../meridian-core)
repos first (`mvn install`). Produces the loadable module:

```
target/meridian-camera-tweaks-<version>.jar
```

Or build every Meridian module at once with the repo-root `build-releases.ps1`,
which collects all jars into `_releases/`.

## How it works

Camera control is inherently packet-level, which a pure Layer-2 module cannot do
on its own. The split:

- **`meridian-core`** (Layer-1) observes `EntityUpdates` / `ClientMovement` /
  `SetClientId` to track entity positions and the player's pose, and exposes the
  `CameraControl` + `EntityTracker` services. It owns all `meridian-protocol`
  contact.
- **`meridian-camera-tweaks`** (Layer-2) is just a declarative `SettingsSpec`
  plus the logic that maps each mode onto a `CameraControl` call. No raw packets,
  no protocol imports.

See [meridian-xray](../meridian-xray) for another worked Layer-2 example.
