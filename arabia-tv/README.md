# Arabia TV + Radio 1.1.0

Integrates the original Radio Maroc Android Auto v2.2 sources (22 stations, background MediaLibraryService, favorites, recent stations, sleep timer, alarms and car browsing) with Arabia TV. Adds a LAN-only UPnP AVTransport/DLNA controller and Android screen cast/Smart View guidance. Actual Samsung hardware compatibility depends on the model, codecs and network; mock protocol tests do not establish physical TV compatibility.

New applicationId: it.mourad.arabiatvradio, installable beside previous apps; private data is not migrated automatically.

Source archive SHA-256: 731700efa6ca39ddd52c647f49159b306621b0ab6c755a88ded06e07e277fb80. GitHub workflow builds, signs, lints, tests SOAP against a mock renderer and exercises radio/cast entry points plus live TV playback on Android emulator.
