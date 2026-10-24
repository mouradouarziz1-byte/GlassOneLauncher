package com.mourad.radiomaroc;

import android.app.PendingIntent;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.LibraryResult;
import androidx.media3.session.MediaConstants;
import androidx.media3.session.MediaLibraryService;
import androidx.media3.session.MediaSession;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.List;

@UnstableApi
public class RadioService extends MediaLibraryService {
    public static final String ACTION_PLAY_STATION = "com.mourad.radiomaroc.PLAY_STATION";
    public static final String ACTION_TOGGLE = "com.mourad.radiomaroc.TOGGLE";
    public static final String ACTION_STOP = "com.mourad.radiomaroc.STOP";
    public static final String ACTION_NEXT = "com.mourad.radiomaroc.NEXT";
    public static final String ACTION_PREV = "com.mourad.radiomaroc.PREV";
    public static final String ACTION_SLEEP_TIMER = "com.mourad.radiomaroc.SLEEP_TIMER";
    public static final String ACTION_RELOAD = "com.mourad.radiomaroc.RELOAD";
    public static final String ACTION_STATE = "com.mourad.radiomaroc.STATE";
    public static final String EXTRA_STATION_ID = "station_id";
    public static final String EXTRA_MINUTES = "minutes";
    public static final String EXTRA_STATE = "state";
    public static final String EXTRA_METADATA = "metadata";
    public static final String EXTRA_SLEEP_DEADLINE = "sleep_deadline";

    private static final String ROOT_ID = "root";
    private static final String FAVORITES_ID = "favorites";
    private static final String RECENT_ID = "recent";
    private static final String POPULAR_ID = "popular";
    private static final String CATEGORIES_ID = "categories";
    private static final String ALL_ID = "all";
    private static final String CAT_PREFIX = "cat:";

    private ExoPlayer player;
    private MediaLibrarySession session;
    private Prefs prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private String currentStationId;
    private List<String> candidates = new ArrayList<>();
    private int candidateIndex;
    private long sleepDeadline;
    private Runnable sleepRunnable;
    private String dynamicMetadata = "";

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = new Prefs(this);
        player = new ExoPlayer.Builder(this).build();
        player.setAudioAttributes(
                new AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(), true);
        player.setHandleAudioBecomingNoisy(true);
        player.setRepeatMode(Player.REPEAT_MODE_ALL);

        player.addListener(new Player.Listener() {
            @Override
            public void onPlayerError(PlaybackException error) {
                if (!tryNextCandidate()) broadcastState("Errore stream");
            }

            @Override
            public void onIsPlayingChanged(boolean playing) {
                broadcastState(playing ? "LIVE" : "In pausa");
            }

            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_BUFFERING) broadcastState("Connessione…");
                else if (state == Player.STATE_READY) broadcastState(player.isPlaying() ? "LIVE" : "Pronta");
                else if (state == Player.STATE_ENDED) broadcastState("Stream terminato");
            }

            @Override
            public void onMediaItemTransition(MediaItem item, int reason) {
                if (item == null) return;
                Station station = StationRepository.find(item.mediaId);
                if (station == null) return;

                currentStationId = station.id;
                prefs.setLastStationId(station.id);
                prefs.addHistory(station.id);
                dynamicMetadata = "";
                candidates = buildCandidates(station);
                candidateIndex = 0;

                if (item.localConfiguration != null && item.localConfiguration.uri != null) {
                    int index = candidates.indexOf(item.localConfiguration.uri.toString());
                    if (index >= 0) candidateIndex = index;
                }

                broadcastState(player.isPlaying() ? "LIVE" : "Connessione…");
                if (session != null) {
                    session.notifyChildrenChanged(RECENT_ID, prefs.historyIds().size(), null);
                }
            }

            @Override
            public void onMediaMetadataChanged(MediaMetadata metadata) {
                if (metadata.title != null) {
                    String title = metadata.title.toString();
                    Station station = StationRepository.find(currentStationId);
                    if (station == null || !station.name.equalsIgnoreCase(title)) dynamicMetadata = title;
                }
                broadcastState(player.isPlaying() ? "LIVE" : "Pronta");
            }
        });

        PendingIntent open = PendingIntent.getActivity(
                this, 31, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        session = new MediaLibrarySession.Builder(this, player, new LibraryCallback())
                .setSessionActivity(open)
                .build();
        addSession(session);
    }

    @Override
    public MediaLibrarySession onGetSession(MediaSession.ControllerInfo controllerInfo) {
        return session;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_PLAY_STATION.equals(action)) {
                Station station = StationRepository.find(intent.getStringExtra(EXTRA_STATION_ID));
                if (station != null) playStation(station);
            } else if (ACTION_TOGGLE.equals(action)) {
                if (player.isPlaying()) player.pause();
                else if (player.getCurrentMediaItem() != null) player.play();
                else {
                    Station station = StationRepository.find(prefs.lastStationId());
                    if (station != null) playStation(station);
                }
            } else if (ACTION_STOP.equals(action)) {
                player.stop();
                player.clearMediaItems();
                broadcastState("Fermata");
            } else if (ACTION_NEXT.equals(action)) {
                skipBy(1);
            } else if (ACTION_PREV.equals(action)) {
                skipBy(-1);
            } else if (ACTION_SLEEP_TIMER.equals(action)) {
                setSleepTimer(intent.getIntExtra(EXTRA_MINUTES, 0));
            } else if (ACTION_RELOAD.equals(action)) {
                Station station = StationRepository.find(currentStationId);
                if (station != null) playStation(station);
            }
        }
        return super.onStartCommand(intent, flags, startId);
    }

    private void playStation(Station station) {
        List<MediaItem> queue = rotatedQueue(station.id);
        if (queue.isEmpty()) return;
        dynamicMetadata = "";
        player.setMediaItems(queue, 0, 0L);
        player.prepare();
        player.play();
        broadcastState("Connessione…");
    }

    private List<MediaItem> rotatedQueue(String selectedId) {
        List<Station> all = StationRepository.all();
        List<MediaItem> queue = new ArrayList<>();
        if (all.isEmpty()) return queue;
        int start = StationRepository.indexOf(selectedId);
        if (start < 0) start = 0;
        for (int offset = 0; offset < all.size(); offset++) {
            Station station = all.get((start + offset) % all.size());
            queue.add(catalog(station));
        }
        return queue;
    }

    private void skipBy(int direction) {
        int count = player.getMediaItemCount();
        if (count <= 1) {
            Station station = StationRepository.find(currentStationId);
            if (station == null) station = StationRepository.find(prefs.lastStationId());
            if (station != null) playStation(station);
            return;
        }
        int current = Math.max(0, player.getCurrentMediaItemIndex());
        int target = (current + direction + count) % count;
        player.seekTo(target, 0L);
        player.play();
    }

    private boolean tryNextCandidate() {
        Station station = StationRepository.find(currentStationId);
        if (station == null) return false;
        if (candidates.isEmpty()) candidates = buildCandidates(station);
        if (candidateIndex + 1 >= candidates.size()) return false;

        candidateIndex++;
        int currentIndex = player.getCurrentMediaItemIndex();
        if (currentIndex < 0 || currentIndex >= player.getMediaItemCount()) return false;

        MediaItem fallback = playable(station, candidates.get(candidateIndex));
        player.replaceMediaItem(currentIndex, fallback);
        player.seekTo(currentIndex, 0L);
        player.prepare();
        player.play();
        return true;
    }

    private List<String> buildCandidates(Station station) {
        List<String> urls = new ArrayList<>();
        if (prefs.isDataSaver()) addUnique(urls, station.lowDataUrl);
        addUnique(urls, station.primaryUrl);
        addUnique(urls, station.backupUrl);
        return urls;
    }

    private static void addUnique(List<String> list, String value) {
        if (value != null && !value.isEmpty() && !list.contains(value)) list.add(value);
    }

    private void setSleepTimer(int minutes) {
        if (sleepRunnable != null) handler.removeCallbacks(sleepRunnable);
        sleepRunnable = null;
        sleepDeadline = 0;
        if (minutes > 0) {
            sleepDeadline = System.currentTimeMillis() + minutes * 60000L;
            sleepRunnable = () -> {
                player.pause();
                sleepDeadline = 0;
                broadcastState("Sleep timer terminato");
            };
            handler.postDelayed(sleepRunnable, minutes * 60000L);
        }
        broadcastState(player.isPlaying() ? "LIVE" : "In pausa");
    }

    private void broadcastState(String state) {
        Intent intent = new Intent(ACTION_STATE);
        intent.setPackage(getPackageName());
        intent.putExtra(EXTRA_STATION_ID, currentStationId == null ? prefs.lastStationId() : currentStationId);
        intent.putExtra(EXTRA_STATE, state);
        intent.putExtra(EXTRA_METADATA, dynamicMetadata == null ? "" : dynamicMetadata);
        intent.putExtra(EXTRA_SLEEP_DEADLINE, sleepDeadline);
        sendBroadcast(intent);
    }

    private MediaItem playable(Station station, String url) {
        MediaMetadata.Builder metadata = new MediaMetadata.Builder()
                .setTitle(station.name)
                .setArtist(station.subtitle())
                .setAlbumTitle(station.category)
                .setIsBrowsable(false)
                .setIsPlayable(true);
        if (station.artworkUrl != null && !station.artworkUrl.isEmpty()) {
            metadata.setArtworkUri(Uri.parse(station.artworkUrl));
        }
        return new MediaItem.Builder()
                .setMediaId(station.id)
                .setUri(url)
                .setMediaMetadata(metadata.build())
                .build();
    }

    private MediaItem catalog(Station station) {
        List<String> urls = buildCandidates(station);
        return playable(station, urls.isEmpty() ? station.primaryUrl : urls.get(0));
    }

    private MediaItem node(String id, String title) {
        MediaMetadata metadata = new MediaMetadata.Builder()
                .setTitle(title)
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .build();
        return new MediaItem.Builder().setMediaId(id).setMediaMetadata(metadata).build();
    }

    private List<MediaItem> carRootItems() {
        List<MediaItem> items = new ArrayList<>();
        items.add(node(FAVORITES_ID, "Preferiti"));
        items.add(node(RECENT_ID, "Recenti"));
        items.add(node(POPULAR_ID, "Popolari"));
        items.add(node(CATEGORIES_ID, "Categorie"));
        return items;
    }

    private List<MediaItem> categoryItems() {
        List<MediaItem> items = new ArrayList<>();
        items.add(node(ALL_ID, "Tutte le radio"));
        items.add(node(CAT_PREFIX + StationRepository.CAT_MUSIC, "Musica"));
        items.add(node(CAT_PREFIX + StationRepository.CAT_NEWS, "News"));
        items.add(node(CAT_PREFIX + StationRepository.CAT_SPORT, "Sport"));
        items.add(node(CAT_PREFIX + StationRepository.CAT_RELIGIOUS, "Religiosa"));
        items.add(node(CAT_PREFIX + StationRepository.CAT_AMAZIGH, "Amazigh"));
        items.add(node(CAT_PREFIX + StationRepository.CAT_REGIONAL, "Regionali"));
        items.add(node(CAT_PREFIX + StationRepository.CAT_GENERAL, "Generaliste"));
        return items;
    }

    private List<Station> popularStations() {
        List<Station> all = StationRepository.all();
        return new ArrayList<>(all.subList(0, Math.min(8, all.size())));
    }

    private List<Station> stationsFor(String parentId) {
        if (ALL_ID.equals(parentId)) return StationRepository.all();
        if (POPULAR_ID.equals(parentId)) return popularStations();

        List<Station> result = new ArrayList<>();
        if (FAVORITES_ID.equals(parentId)) {
            for (Station station : StationRepository.all()) if (prefs.isFavorite(station.id)) result.add(station);
            return result;
        }
        if (RECENT_ID.equals(parentId)) {
            for (String id : prefs.historyIds()) {
                Station station = StationRepository.find(id);
                if (station != null) result.add(station);
            }
            return result;
        }
        if (parentId != null && parentId.startsWith(CAT_PREFIX)) {
            return StationRepository.byCategory(parentId.substring(CAT_PREFIX.length()));
        }
        return result;
    }

    private List<MediaItem> items(List<Station> stations) {
        List<MediaItem> result = new ArrayList<>();
        for (Station station : stations) result.add(catalog(station));
        return result;
    }

    private static List<MediaItem> page(List<MediaItem> source, int page, int size) {
        int from = Math.min(source.size(), Math.max(0, page * size));
        int to = Math.min(source.size(), from + size);
        return new ArrayList<>(source.subList(from, to));
    }

    private LibraryParams resultParams(LibraryParams requested) {
        Bundle extras = new Bundle();
        extras.putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE,
                MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_CATEGORY_LIST_ITEM);
        extras.putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE,
                MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM);
        LibraryParams.Builder builder = new LibraryParams.Builder().setExtras(extras);
        if (requested != null) {
            builder.setRecent(requested.isRecent);
            builder.setSuggested(requested.isSuggested);
            builder.setOffline(requested.isOffline);
        }
        return builder.build();
    }

    private final class LibraryCallback implements MediaLibrarySession.Callback {
        @Override
        public ListenableFuture<LibraryResult<MediaItem>> onGetLibraryRoot(
                MediaLibrarySession mediaSession,
                MediaSession.ControllerInfo browser,
                LibraryParams params) {
            String id = ROOT_ID;
            String title = "Radio Maroc";
            if (params != null && params.isRecent) {
                id = RECENT_ID;
                title = "Recenti";
            } else if (params != null && params.isSuggested) {
                id = POPULAR_ID;
                title = "Popolari";
            }
            return Futures.immediateFuture(LibraryResult.ofItem(node(id, title), resultParams(params)));
        }

        @Override
        public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> onGetChildren(
                MediaLibrarySession mediaSession,
                MediaSession.ControllerInfo browser,
                String parentId,
                int page,
                int pageSize,
                LibraryParams params) {
            List<MediaItem> result;
            if (ROOT_ID.equals(parentId)) result = carRootItems();
            else if (CATEGORIES_ID.equals(parentId)) result = categoryItems();
            else result = items(stationsFor(parentId));
            return Futures.immediateFuture(
                    LibraryResult.ofItemList(page(result, page, pageSize), resultParams(params)));
        }

        @Override
        public ListenableFuture<LibraryResult<MediaItem>> onGetItem(
                MediaLibrarySession mediaSession,
                MediaSession.ControllerInfo browser,
                String id) {
            if (ROOT_ID.equals(id)) {
                return Futures.immediateFuture(
                        LibraryResult.ofItem(node(ROOT_ID, "Radio Maroc"), resultParams(null)));
            }
            if (FAVORITES_ID.equals(id) || RECENT_ID.equals(id) || POPULAR_ID.equals(id)
                    || CATEGORIES_ID.equals(id) || ALL_ID.equals(id) || id.startsWith(CAT_PREFIX)) {
                String title;
                if (FAVORITES_ID.equals(id)) title = "Preferiti";
                else if (RECENT_ID.equals(id)) title = "Recenti";
                else if (POPULAR_ID.equals(id)) title = "Popolari";
                else if (CATEGORIES_ID.equals(id)) title = "Categorie";
                else if (ALL_ID.equals(id)) title = "Tutte le radio";
                else title = id.substring(CAT_PREFIX.length());
                return Futures.immediateFuture(LibraryResult.ofItem(node(id, title), resultParams(null)));
            }
            Station station = StationRepository.find(id);
            return station != null
                    ? Futures.immediateFuture(LibraryResult.ofItem(catalog(station), resultParams(null)))
                    : Futures.immediateFuture(LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE));
        }

        @Override
        public ListenableFuture<List<MediaItem>> onAddMediaItems(
                MediaSession mediaSession,
                MediaSession.ControllerInfo controller,
                List<MediaItem> requested) {
            if (requested != null && !requested.isEmpty()) {
                Station first = StationRepository.find(requested.get(0).mediaId);
                if (first != null) {
                    return Futures.immediateFuture(rotatedQueue(first.id));
                }
            }
            List<MediaItem> result = new ArrayList<>();
            if (requested != null) {
                for (MediaItem item : requested) {
                    Station station = StationRepository.find(item.mediaId);
                    result.add(station != null ? catalog(station) : item);
                }
            }
            return Futures.immediateFuture(result);
        }

        @Override
        public ListenableFuture<LibraryResult<Void>> onSearch(
                MediaLibrarySession mediaSession,
                MediaSession.ControllerInfo browser,
                String query,
                LibraryParams params) {
            mediaSession.notifySearchResultChanged(
                    browser, query, StationRepository.search(query).size(), resultParams(params));
            return Futures.immediateFuture(LibraryResult.ofVoid(resultParams(params)));
        }

        @Override
        public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> onGetSearchResult(
                MediaLibrarySession mediaSession,
                MediaSession.ControllerInfo browser,
                String query,
                int page,
                int pageSize,
                LibraryParams params) {
            return Futures.immediateFuture(
                    LibraryResult.ofItemList(
                            page(items(StationRepository.search(query)), page, pageSize),
                            resultParams(params)));
        }
    }

    @Override
    public void onDestroy() {
        if (sleepRunnable != null) handler.removeCallbacks(sleepRunnable);
        if (session != null) {
            session.release();
            session = null;
        }
        if (player != null) {
            player.release();
            player = null;
        }
        super.onDestroy();
    }
}
