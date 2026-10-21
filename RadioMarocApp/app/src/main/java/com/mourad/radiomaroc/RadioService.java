package com.mourad.radiomaroc;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.LibraryResult;
import androidx.media3.session.MediaLibraryService;
import androidx.media3.session.MediaSession;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.List;

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

    private static final String ROOT_ID="root", ALL_ID="all", FAVORITES_ID="favorites", RECENT_ID="recent", CAT_PREFIX="cat:";
    private ExoPlayer player;
    private MediaLibrarySession session;
    private Prefs prefs;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private String currentStationId;
    private List<String> candidates=new ArrayList<>();
    private int candidateIndex;
    private long sleepDeadline;
    private Runnable sleepRunnable;
    private String dynamicMetadata="";

    @Override public void onCreate(){
        super.onCreate(); prefs=new Prefs(this);
        player=new ExoPlayer.Builder(this).build();
        player.setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),true);
        player.setHandleAudioBecomingNoisy(true);
        player.addListener(new Player.Listener(){
            @Override public void onPlayerError(PlaybackException e){if(!tryNextCandidate())broadcastState("Errore stream");}
            @Override public void onIsPlayingChanged(boolean playing){broadcastState(playing?"LIVE":"In pausa");}
            @Override public void onPlaybackStateChanged(int state){if(state==Player.STATE_BUFFERING)broadcastState("Connessione…");else if(state==Player.STATE_READY)broadcastState(player.isPlaying()?"LIVE":"Pronta");else if(state==Player.STATE_ENDED)broadcastState("Stream terminato");}
            @Override public void onMediaItemTransition(MediaItem item,int reason){if(item==null)return;Station s=StationRepository.find(item.mediaId);if(s!=null){currentStationId=s.id;prefs.setLastStationId(s.id);prefs.addHistory(s.id);dynamicMetadata="";broadcastState(player.isPlaying()?"LIVE":"Connessione…");}}
            @Override public void onMediaMetadataChanged(MediaMetadata m){if(m.title!=null){String t=m.title.toString();Station s=StationRepository.find(currentStationId);if(s==null||!s.name.equalsIgnoreCase(t))dynamicMetadata=t;}broadcastState(player.isPlaying()?"LIVE":"Pronta");}
        });
        PendingIntent open=PendingIntent.getActivity(this,31,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        session=new MediaLibrarySession.Builder(this,player,new LibraryCallback()).setSessionActivity(open).build();
        addSession(session);
    }

    @Override public MediaLibrarySession onGetSession(MediaSession.ControllerInfo controllerInfo){return session;}

    @Override public int onStartCommand(Intent i,int flags,int startId){
        if(i!=null){String a=i.getAction();
            if(ACTION_PLAY_STATION.equals(a)){Station s=StationRepository.find(i.getStringExtra(EXTRA_STATION_ID));if(s!=null)playStation(s);}
            else if(ACTION_TOGGLE.equals(a)){if(player.isPlaying())player.pause();else if(player.getCurrentMediaItem()!=null)player.play();else{Station s=StationRepository.find(prefs.lastStationId());if(s!=null)playStation(s);}}
            else if(ACTION_STOP.equals(a)){player.stop();player.clearMediaItems();broadcastState("Fermata");}
            else if(ACTION_NEXT.equals(a))playRelative(1);
            else if(ACTION_PREV.equals(a))playRelative(-1);
            else if(ACTION_SLEEP_TIMER.equals(a))setSleepTimer(i.getIntExtra(EXTRA_MINUTES,0));
            else if(ACTION_RELOAD.equals(a)){Station s=StationRepository.find(currentStationId);if(s!=null)playStation(s);}
        }
        return super.onStartCommand(i,flags,startId);
    }

    private void playStation(Station s){currentStationId=s.id;candidates=buildCandidates(s);candidateIndex=0;dynamicMetadata="";playCurrent(s);}
    private void playCurrent(Station s){if(candidates.isEmpty())return;String url=candidates.get(Math.max(0,Math.min(candidateIndex,candidates.size()-1)));player.setMediaItem(playable(s,url));player.prepare();player.play();prefs.setLastStationId(s.id);prefs.addHistory(s.id);broadcastState("Connessione…");}
    private boolean tryNextCandidate(){Station s=StationRepository.find(currentStationId);if(s==null)return false;if(candidates.isEmpty())candidates=buildCandidates(s);if(candidateIndex+1>=candidates.size())return false;candidateIndex++;playCurrent(s);return true;}
    private List<String> buildCandidates(Station s){List<String> u=new ArrayList<>();if(prefs.isDataSaver())addUnique(u,s.lowDataUrl);addUnique(u,s.primaryUrl);addUnique(u,s.backupUrl);return u;}
    private static void addUnique(List<String> l,String v){if(v!=null&&!v.isEmpty()&&!l.contains(v))l.add(v);}
    private void playRelative(int d){List<Station>a=StationRepository.all();if(a.isEmpty())return;int x=StationRepository.indexOf(currentStationId);if(x<0)x=StationRepository.indexOf(prefs.lastStationId());if(x<0)x=0;playStation(a.get((x+d+a.size())%a.size()));}
    private void setSleepTimer(int m){if(sleepRunnable!=null)handler.removeCallbacks(sleepRunnable);sleepRunnable=null;sleepDeadline=0;if(m>0){sleepDeadline=System.currentTimeMillis()+m*60000L;sleepRunnable=()->{player.pause();sleepDeadline=0;broadcastState("Sleep timer terminato");};handler.postDelayed(sleepRunnable,m*60000L);}broadcastState(player.isPlaying()?"LIVE":"In pausa");}
    private void broadcastState(String state){Intent i=new Intent(ACTION_STATE);i.setPackage(getPackageName());i.putExtra(EXTRA_STATION_ID,currentStationId==null?prefs.lastStationId():currentStationId);i.putExtra(EXTRA_STATE,state);i.putExtra(EXTRA_METADATA,dynamicMetadata==null?"":dynamicMetadata);i.putExtra(EXTRA_SLEEP_DEADLINE,sleepDeadline);sendBroadcast(i);}

    private MediaItem playable(Station s,String url){MediaMetadata m=new MediaMetadata.Builder().setTitle(s.name).setArtist(s.subtitle()).setAlbumTitle(s.category).setIsBrowsable(false).setIsPlayable(true).build();return new MediaItem.Builder().setMediaId(s.id).setUri(url).setMediaMetadata(m).build();}
    private MediaItem catalog(Station s){List<String>u=buildCandidates(s);return playable(s,u.isEmpty()?s.primaryUrl:u.get(0));}
    private MediaItem node(String id,String title){MediaMetadata m=new MediaMetadata.Builder().setTitle(title).setIsBrowsable(true).setIsPlayable(false).build();return new MediaItem.Builder().setMediaId(id).setMediaMetadata(m).build();}
    private List<MediaItem> roots(){List<MediaItem>i=new ArrayList<>();i.add(node(FAVORITES_ID,"Preferiti"));i.add(node(RECENT_ID,"Ascoltate di recente"));i.add(node(ALL_ID,"Tutte le radio"));i.add(node(CAT_PREFIX+StationRepository.CAT_MUSIC,"Musica"));i.add(node(CAT_PREFIX+StationRepository.CAT_NEWS,"News"));i.add(node(CAT_PREFIX+StationRepository.CAT_SPORT,"Sport"));i.add(node(CAT_PREFIX+StationRepository.CAT_RELIGIOUS,"Religiosa"));i.add(node(CAT_PREFIX+StationRepository.CAT_AMAZIGH,"Amazigh"));i.add(node(CAT_PREFIX+StationRepository.CAT_REGIONAL,"Regionali"));i.add(node(CAT_PREFIX+StationRepository.CAT_GENERAL,"Generaliste"));return i;}
    private List<Station> stationsFor(String p){if(ALL_ID.equals(p))return StationRepository.all();List<Station>r=new ArrayList<>();if(FAVORITES_ID.equals(p)){for(Station s:StationRepository.all())if(prefs.isFavorite(s.id))r.add(s);return r;}if(RECENT_ID.equals(p)){for(String id:prefs.historyIds()){Station s=StationRepository.find(id);if(s!=null)r.add(s);}return r;}if(p!=null&&p.startsWith(CAT_PREFIX))return StationRepository.byCategory(p.substring(CAT_PREFIX.length()));return r;}
    private List<MediaItem> items(List<Station>s){List<MediaItem>r=new ArrayList<>();for(Station x:s)r.add(catalog(x));return r;}
    private static List<MediaItem> page(List<MediaItem>s,int p,int size){int from=Math.min(s.size(),Math.max(0,p*size));int to=Math.min(s.size(),from+size);return new ArrayList<>(s.subList(from,to));}

    private final class LibraryCallback implements MediaLibrarySession.Callback{
        @Override public ListenableFuture<LibraryResult<MediaItem>> onGetLibraryRoot(MediaLibrarySession s,MediaSession.ControllerInfo b,LibraryParams p){return Futures.immediateFuture(LibraryResult.ofItem(node(ROOT_ID,"Radio Maroc"),p));}
        @Override public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> onGetChildren(MediaLibrarySession s,MediaSession.ControllerInfo b,String parent,int pg,int size,LibraryParams p){List<MediaItem>it=ROOT_ID.equals(parent)?roots():items(stationsFor(parent));return Futures.immediateFuture(LibraryResult.ofItemList(page(it,pg,size),p));}
        @Override public ListenableFuture<LibraryResult<MediaItem>> onGetItem(MediaLibrarySession s,MediaSession.ControllerInfo b,String id){if(ROOT_ID.equals(id))return Futures.immediateFuture(LibraryResult.ofItem(node(ROOT_ID,"Radio Maroc"),null));if(ALL_ID.equals(id)||FAVORITES_ID.equals(id)||RECENT_ID.equals(id)||id.startsWith(CAT_PREFIX))return Futures.immediateFuture(LibraryResult.ofItem(node(id,id.startsWith(CAT_PREFIX)?id.substring(CAT_PREFIX.length()):id),null));Station st=StationRepository.find(id);return st!=null?Futures.immediateFuture(LibraryResult.ofItem(catalog(st),null)):Futures.immediateFuture(LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE));}
        @Override public ListenableFuture<List<MediaItem>> onAddMediaItems(MediaSession ms,MediaSession.ControllerInfo c,List<MediaItem> requested){List<MediaItem>r=new ArrayList<>();for(MediaItem x:requested){Station st=StationRepository.find(x.mediaId);r.add(st!=null?catalog(st):x);}return Futures.immediateFuture(r);}
        @Override public ListenableFuture<LibraryResult<Void>> onSearch(MediaLibrarySession s,MediaSession.ControllerInfo b,String q,LibraryParams p){s.notifySearchResultChanged(b,q,StationRepository.search(q).size(),p);return Futures.immediateFuture(LibraryResult.ofVoid(p));}
        @Override public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> onGetSearchResult(MediaLibrarySession s,MediaSession.ControllerInfo b,String q,int pg,int size,LibraryParams p){return Futures.immediateFuture(LibraryResult.ofItemList(page(items(StationRepository.search(q)),pg,size),p));}
    }

    @Override public void onDestroy(){if(sleepRunnable!=null)handler.removeCallbacks(sleepRunnable);if(session!=null){session.release();session=null;}if(player!=null){player.release();player=null;}super.onDestroy();}
}
