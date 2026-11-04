# Arabia TV + Radio 1.1.0

Integrates the original Radio Maroc Android Auto v2.2 sources (22 stations, background playback, favorites/history, sleep timer, alarms, car media browsing) with Arabia TV. Adds Android media search entry, Samsung-compatible LAN UPnP AVTransport/DLNA discovery/controls and Android cast settings/Smart View guidance.

New applicationId: it.mourad.arabiatvradio. Installs beside the old apps; their private data is not migrated automatically. Radio timers control the local player, not the TV. Physical Samsung model and codec compatibility must be checked on the user's television; mock protocol tests are not hardware tests.

Source SHA-256: f63e1b449c88de1b260911e85bddcc7e01e00868ce26a3ba5c34b82b41b0f991. See INTEGRAZIONE-RADIO-TV.md in the source archive. The dedicated workflow builds, signs, lints and runs parser/catalog/protocol tests plus radio/cast UI and live playback tests on emulator. Refer to actual run results.
