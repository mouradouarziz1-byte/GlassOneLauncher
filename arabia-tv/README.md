# Arabia TV + Radio 1.1.0

Integrates the original Radio Maroc Android Auto v2.2 sources: 22 stations, background playback, radio favorites/history, categories, sleep timer, alarms and Android Auto browsing. The Radio entry is on the TV home screen.

Samsung options: LAN-only UPnP AVTransport/DLNA discovery and playback controls, plus Android screen-cast settings and Galaxy Smart View guidance. Compatibility depends on the TV model, codecs and network; physical Samsung TV testing remains required. Radio timers control the local player, not playback on the television.

The applicationId is it.mourad.arabiatvradio. It installs alongside previous apps and does not migrate their private favorites/preferences automatically. The original radio UI remains Italian; the TV UI supports Italian/Arabic.

Source archive SHA-256: 2eebc439fffa8aac84063351e300f1471229a90854cb11fac5e49d5d364f9917. See INTEGRAZIONE-RADIO-TV.md inside the archive for setup, permissions, limitations and provenance. GitHub Actions builds the APK, runs lint, parser/protocol unit tests, mock TV SOAP tests, radio/cast UI tests and live TV playback on emulator. Consult the actual run for results. No physical-TV compatibility claim is made by those tests.
