# Arabia TV + Radio 1.1.0

Integrates original Radio Maroc Android Auto v2.2 (22 stations, background playback, favorites, history, timer, alarms and media browsing). Adds Samsung-compatible LAN UPnP/DLNA playback controls and Android screen cast/Smart View guidance. Physical TV compatibility depends on model, codecs and network; mock protocol tests are not hardware validation.

ApplicationId: it.mourad.arabiatvradio. Installs alongside the old apps; private data is not migrated automatically. Radio timers control the local player, not the television.

Source SHA-256: 5adaca5928f156399837a1133acb439700994ea9a6729aec477dbc04bc52280e. Setup and limitations: INTEGRAZIONE-RADIO-TV.md inside the source archive. The workflow builds, signs, lints and tests protocol, radio/cast UI and native playback on emulator. Refer to actual run results.
