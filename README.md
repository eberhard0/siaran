# Siaran

A small Android TV player for the free, publicly available channels collected by
[iptv-org/iptv](https://github.com/iptv-org/iptv). Built for the Galaxy Z Fold: video on
top on the cover screen, channels and video side by side when unfolded.

- **Home**: your country's channels (Indonesia by default), filtered by category.
- **Browse**: every country and category from iptv-org.
- **Search**: all ~11,000 streams by name.
- **Favourites** and recently watched, stored on the phone.
- Full screen, picture-in-picture when you leave the app, next / previous channel.

The playlists are downloaded from `iptv-org.github.io` and cached for a day. Siaran hosts no
streams; availability depends on each broadcaster, and many channels are offline at any time.

Builds run on GitHub Actions (`.github/workflows/android.yml`). Tag `vX.Y.Z` to publish a
signed release.
