# Arabia TV

Native Android app: editorial directory of 64 channels across 22 Arab League countries and international broadcasters; Arabic/Italian interface, native HLS player, favorites, history and local M3U import.

Source: `ArabiaTV-Android-sorgenti.zip`. SHA-256: 5dfc4f77fdf98a65b36adaa2a2d588b910ce13894592788093062ba590fca60f.

The dedicated workflow builds a signed debug APK, checks public live streams, runs unit tests and Android Lint, then tests the UI on an emulator. Refer to the actual workflow result. Channels without a reachable public stream open their official broadcaster. This selected directory does not contain every existing channel and does not bypass broadcaster access or territory restrictions.
