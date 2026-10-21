package com.mourad.radiomaroc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class StationRepository {
    private StationRepository() {}

    public static final String CAT_MUSIC = "Musica";
    public static final String CAT_NEWS = "News";
    public static final String CAT_SPORT = "Sport";
    public static final String CAT_RELIGIOUS = "Religiosa";
    public static final String CAT_AMAZIGH = "Amazigh";
    public static final String CAT_REGIONAL = "Regionale";
    public static final String CAT_GENERAL = "Generalista";

    private static final List<Station> STATIONS = Collections.unmodifiableList(Arrays.asList(
        new Station("radio_mars", "Radio Mars", "Casablanca", CAT_SPORT, "Sport marocchino in diretta",
            "https://radiomars.ice.infomaniak.ch/radiomars-128.mp3", null, null, "RM"),
        new Station("hit_radio", "HIT Radio", "Rabat", CAT_MUSIC, "Hits, rap e musica internazionale",
            "https://hitradio-maroc.ice.infomaniak.ch/hitradio-maroc-128.mp3", null, null, "HIT"),
        new Station("medi1", "Medi 1 Radio", "Tanger", CAT_GENERAL, "Notizie, cultura e musica",
            "https://cdn.live.easybroadcast.io/live/83_medi1radio-maghreb_8s9i4bn/playlist.m3u8", null, null, "M1"),
        new Station("mfm", "MFM Radio", "Casablanca", CAT_MUSIC, "Music For Morocco",
            "https://a5.asurahosting.com:7980/radio.mp3", null, null, "MFM"),
        new Station("aswat", "Radio Aswat", "Rabat", CAT_NEWS, "Attualità, talk e cultura",
            "https://broadcast.ice.infomaniak.ch/aswat-high.mp3", "http://broadcast.ice.infomaniak.ch/aswat-high.mp3?.mp3", null, "AS"),
        new Station("medradio", "Med Radio", "Casablanca", CAT_NEWS, "Talk, società e attualità",
            "https://medradio.ice.infomaniak.ch/medradio-128.mp3", "http://medradio.ice.infomaniak.ch/medradio-128.mp3", null, "MED"),
        new Station("chada", "Chada FM", "Casablanca", CAT_MUSIC, "Musica marocchina e chaabi",
            "https://edge16.vedge.infomaniak.com/livecast/ik:chadatv/manifest.m3u8", null, null, "CH"),
        new Station("uradio", "U Radio", "Marocco", CAT_MUSIC, "Hits anni 80, 90 e 2000",
            "https://uradio-aac.ice.infomaniak.ch/uradio.aac", null, null, "U"),
        new Station("atlantic", "Atlantic Radio", "Casablanca", CAT_GENERAL, "Informazione, economia e musica",
            "https://atlantic-sonic.nindohost.net:9300/stream", null, "https://atlantic-sonic.nindohost.net:9300/stream", "AT"),
        new Station("radio2m", "Radio 2M", "Casablanca", CAT_GENERAL, "La radio della rete 2M",
            "https://cdnamd-hls-globecast.akamaized.net/live/ramdisk/radio_2m/radio_hls_ts/index.m3u8", null, null, "2M"),
        new Station("capradio", "Cap Radio", "Tanger", CAT_GENERAL, "Radio di prossimità del nord del Marocco",
            "https://listen.radioking.com/radio/710810/stream/776366", null, null, "CAP"),
        new Station("plus_agadir", "Radio Plus Agadir", "Agadir", CAT_REGIONAL, "Musica e programmi regionali",
            "https://stream-158.zeno.fm/bqdbb6hd0neuv", null, null, "R+"),
        new Station("marrakech_plus", "Marrakech Plus", "Marrakech", CAT_REGIONAL, "Musica e vita di Marrakech",
            "https://cast5.my-control-panel.com/proxy/marrakec/stream", null, null, "M+"),
        new Station("medina", "Medina FM", "Meknès", CAT_GENERAL, "Musica, cultura e varietà",
            "https://medinafm.ice.infomaniak.ch/medinafm-64.mp3", null, "https://medinafm.ice.infomaniak.ch/medinafm-64.mp3", "MF"),
        new Station("yabiladi", "Radio Yabiladi", "Rabat", CAT_MUSIC, "Musica marocchina e diaspora",
            "https://radio.yabiladi.com:8002/;stream.mp3", null, null, "YB"),
        new Station("azawan", "Radio Azawan", "Marocco", CAT_AMAZIGH, "Musica e cultura amazigh",
            "https://az-maroc.ice.infomaniak.ch/az-maroc-128.mp3", null, null, "AZ"),
        new Station("yabiladi_amazigh", "Yabiladi Azawan Amazigh", "Marocco", CAT_AMAZIGH, "Musica amazigh",
            "https://radio.yabiladi.com:9002/;stream.mp3", null, null, "YA"),
        new Station("tanger_med", "Radio Tanger Med", "Tanger", CAT_REGIONAL, "Informazione e vita del nord",
            "https://radiotangermed-22.ice.infomaniak.ch/radiotangermed-22-128.mp3", null, null, "TM"),
        new Station("star_maroc", "Radio Star Maroc FM", "Marocco", CAT_MUSIC, "Pop e musica marocchina",
            "https://a2.asurahosting.com:6100/radio.mp3", null, null, "★"),
        new Station("quran_abdulbasit", "Quran • Abdul Basit", "Marocco", CAT_RELIGIOUS, "Recitazione del Corano",
            "https://radio.mp3islam.com/listen/abdulbasit/radio.mp3", null, null, "Q"),
        new Station("quran_maher", "Quran • Maher Al Muaiqly", "Marocco", CAT_RELIGIOUS, "Recitazione del Corano",
            "https://backup.qurango.net/radio/maher", null, null, "Q"),
        new Station("quran_ayyub", "Quran • Mohammed Ayyub", "Marocco", CAT_RELIGIOUS, "Recitazione del Corano",
            "https://qurango.net/radio/mohammed_ayyub", null, null, "Q")
    ));

    public static List<Station> all() { return STATIONS; }

    public static Station find(String id) {
        if (id == null) return null;
        for (Station station : STATIONS) if (station.id.equals(id)) return station;
        return null;
    }

    public static int indexOf(String id) {
        for (int i = 0; i < STATIONS.size(); i++) if (STATIONS.get(i).id.equals(id)) return i;
        return -1;
    }

    public static List<Station> byCategory(String category) {
        if (category == null || category.isEmpty()) return STATIONS;
        List<Station> result = new ArrayList<>();
        for (Station station : STATIONS) if (category.equals(station.category)) result.add(station);
        return result;
    }

    public static List<Station> search(String query) {
        if (query == null || query.trim().isEmpty()) return STATIONS;
        String q = query.trim().toLowerCase(Locale.ROOT);
        List<Station> result = new ArrayList<>();
        for (Station station : STATIONS) {
            String hay = (station.name + " " + station.city + " " + station.category + " " + station.description).toLowerCase(Locale.ROOT);
            if (hay.contains(q)) result.add(station);
        }
        return result;
    }
}
