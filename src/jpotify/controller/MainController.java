package jpotify.controller;

import com.mpatric.mp3agic.InvalidDataException;
import com.mpatric.mp3agic.UnsupportedTagException;
import helper.FileHelper;
import javazoom.jl.decoder.JavaLayerException;
import jpotify.model.*;
import jpotify.view.MainView;
import jpotify.view.centerpanel.JAlbum;
import jpotify.view.centerpanel.JPlaylistSong;
import jpotify.view.centerpanel.JSong;
import jpotify.view.leftpanel.CreateNewPlaylist;
import jpotify.view.rightpanel.Friend;
import jpotify.view.rightpanel.PlaylistList;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class MainController {
    public static final int JALBUM = 3, PLAYLIST = 2, MYSONG = 0, ALBUMS = 1, BLANPAGE = 4, NETWORK = 5;
    //    public static final int PLAY_BUTTON=1;
    private MainView mainView;
    private Users users;
    private User user;
    private CustomPlayer player;
    private int volume = 0;
    private int currentMode;
    private int saveIndex;
    private boolean muted = false;
    private PlaylistList curentFriend;

    public MainController(Users users, int userIndex) {
        this.users = users;
        this.user = users.getUser(userIndex);
        user.setRepeatRule(RepeatRule.OFF);
//        user.turnShuffleOn();
        user.startHttpServer(this, 3245);
//        System.out.println(user.getOthersSharedPlaylists().get(0).getSongs());
        mainView = new MainView(this);
    }

    /**
     * Adds songs to library
     * @param files array of files to be added
     */
    public void addSongToLibrary(File... files) {
        for (File f :
                files) {
            try {
                user.addSong(new Song(f));
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Sets GUI current state
     * @param mode current mode
     * @param index playlist index
     */
    public void setCurrentMode(int mode, int index) {
        setCurrentMode(mode, index, null, 0);
    }

    /**
     * Sets GUI current state
     * @param mode current mode
     * @param index playlist index
     * @param host friend's host
     * @param port friend's port
     */
    public void setCurrentMode(int mode, int index, String host, int port) {
        this.currentMode = mode;
        switch (mode) {
            case JALBUM:
                user.setCurrentSelectedListInGUI(user.getAlbums().get(index));
                break;
            case MYSONG:
                user.setCurrentSelectedListInGUI(user.getLibrary());
                break;
            case PLAYLIST:
                user.setCurrentSelectedListInGUI(user.getPlaylists().get(index));
                break;
            case NETWORK:
                user.setCurrentSelectedListInGUI(user.getOthersSharedPlaylists().get(new RemoteClient(host, port)));
        }
    }

    public int getCurrentMode() {
        return currentMode;
    }

    /**
     * Plays selected song
     * @param index song index
     */
    public void playSelectedSong(int index) {
        user.setCurrentList();
        Song song = user.playSong(index);
        if (user.isShuffled())
            user.shuffleCurrentSelected();
        playSongWithCustomPlayer(song);
        GUIChangeForSongPlay(song);
    }

    /**
     * Plays selected song from friend
     * @param host friend's host
     * @param port friend's port
     * @param index song index
     */
    public void playSelectedSongFromNetwork(String host, int port, int index) {
        user.setCurrentList();
        try {
            Song song = user.playSongFromNetwork(index, host, port);
            playSongWithCustomPlayer(song);
            GUIChangeForSongPlay(song);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Changes GUI bottom panel for Song playing
     * @param song Song to be played
     */
    private void GUIChangeForSongPlay(Song song) {
        mainView.changeArtwork(song.getArtwork());
        mainView.getBottomPanelView().getSongInfoLabel().setSongInfo(song.getTitle(), song.getArtist(), song.getAlbum());
        mainView.getBottomPanelView().getControlPanel().changePlayButton(false);
    }

    /**
     * Changes GUI bottom panel for song stop
     */
    private void GUIChangeForSongStop() {
        try {
            mainView.changeArtwork(FileHelper.loadSampleArtwork());
        } catch (IOException e) {
            e.printStackTrace();
        }
        mainView.getBottomPanelView().getSongInfoLabel().setSongInfo("", "", "");
        mainView.getBottomPanelView().getControlPanel().changePlayButton(true);
        updateJSlider(0, 0, 0);
    }

    /**
     * Updates bottom panel slider state in GUI
     * @param state JSlider value
     * @param timeSpent Time spent from song
     * @param timeLeft Time left from song
     */
    public void updateJSlider(int state, int timeSpent, int timeLeft) {
        mainView.getBottomPanelView().getControlPanel().getControlBar().setValue(state);
        mainView.getBottomPanelView().getControlPanel().getTimeSpent().setText(timeSpent / 60 + ":" + timeSpent % 60);
        mainView.getBottomPanelView().getControlPanel().getTimeLeft().setText("-"+timeLeft/60 + ":" + timeLeft%60);
        mainView.getBottomPanelView().getControlPanel().getSliderPanel().revalidate();
        mainView.getBottomPanelView().getControlPanel().revalidate();
    }

    /**
     * Pauses song
     * @param pause pause or not?
     * @return true if state changed
     */
    public boolean pause(boolean pause) {
        if (player == null) return false;
        if (pause) player.pause();
        else player.play();
        return true;
    }

    /**
     * pauses player for seek
     */
    public void pausePlayerForSeek() {
        if (player != null)
            player.pause();
    }

    /**
     * resumes player after seek
     */
    public void resumePlayerForSeek() {
        if (player != null && mainView.getBottomPanelView().getControlPanel().isPlaying())
            player.resume();
    }

    /**
     * Changes song position
     * @param value song position from 0 to 100
     * @return if was changed
     */
    public boolean songSliderChanged(int value) {
        if (player == null) return false;
        try {
            player.changePositionHundred(value);
        } catch (JavaLayerException e) {
            e.printStackTrace();
        }
        return true;
    }

    public void songReachedEnd() {
//        user.stopSong();
        Song nextSong = user.next();
        if (nextSong == null) {
            songListReachedEnd();
            return;
        }
        user.playSong(nextSong);
        playSongWithCustomPlayer(nextSong);
        GUIChangeForSongPlay(nextSong);
    }

    public void nextPressed() {
        if (player == null) return;
//        user.stopSong();
        Song nextSong = user.forceNext();
        if (nextSong == null) {
            songListReachedEnd();
            return;
        }
        user.playSong(nextSong);
        playSongWithCustomPlayer(nextSong);
        GUIChangeForSongPlay(nextSong);
    }

    public void previousPressed() {
        if (player == null) return;
//        user.stopSong();
        Song previousSong = user.previous();
        if (previousSong == null) {
            songListReachedEnd();
            return;
        }
        user.playSong(previousSong);
        playSongWithCustomPlayer(previousSong);
        GUIChangeForSongPlay(previousSong);
    }

    private void songListReachedEnd() {
        user.stopSong();
        if (player != null) {
            player.stop();
            player = null;
        }
        GUIChangeForSongStop();
    }

    private void playSongWithCustomPlayer(Song song) {
        try {
            if (player != null)
                player.stop();
            player = new CustomPlayer(song.getAddress(), this);
            if (muted) player.setVolume(-80);
            else player.setVolume(volume);
            player.play();
        } catch (JavaLayerException | IOException | InvalidDataException | UnsupportedTagException e) {
            e.printStackTrace();
        }
    }

    public void changeCenterPanel(int mode, int index) {
        mainView.getCenterPanelView().displayPanel(mode, index);
    }


    public void saveState() {
        user.stopSong();
//        Users users = new Users();
//        users.addUser(user);
        try {
            FileHelper.saveUsers(this.users);
        } catch (IOException e) {
            e.printStackTrace();
        }
        user.stopHttpServer();
        FileHelper.deleteTemporaryDirectory();
    }

    public void changeVolume(int value) {
        volume = value;
        muted = false;
        if (player != null)
            player.setVolume(value);
    }

    public void deleteSongFromLibrary(int index) {
        if (user.removeSongFromLibrary(index)) {
            changeCenterPanel(MYSONG, 0);
        }
    }

    public ArrayList<JAlbum> getJAlbum() throws IOException {
        List<Album> albums = user.getAlbums();
        ArrayList<JAlbum> jAlbums = new ArrayList<>();
        for (int i = 0; i < albums.size(); i++) {
            jAlbums.add(new JAlbum(this, albums.get(i).getName(), albums.get(i).getTitles(), albums.get(i).getArtwork(), i));
        }
        return jAlbums;
    }

    public ArrayList<JPlaylistSong> getJPlaylistSong(int index) {
        ArrayList<JPlaylistSong> ret = new ArrayList<>();
        int i = 0;
        for (Song s : user.getPlaylists().get(index).getSongs()) {
            ret.add(new JPlaylistSong(this, index, i++, s.getTitle(), s.getArtist(), s.getAlbum()));
        }
        return ret;
    }

    public ArrayList<JSong> getJSongs(int mode, int index) throws IOException {
        ArrayList<JSong> jSongs = new ArrayList<>();
        List<Song> songs = null;
        switch (mode) {
            case MYSONG:
                songs = user.getLibrarySongs();
                break;
            case JALBUM:
                songs = user.getAlbums().get(index).getSongs();
                break;
//            case PLAYLIST:
//                songs = user.getPlaylists().get(index).getSongs();
//                break;
        }
        for (int i = 0; i < songs.size(); i++) {
            jSongs.add(new JSong(this, songs.get(i).getTitle(), songs.get(i).getArtist(), songs.get(i).getAlbum(), songs.get(i).getArtwork(), i));
        }
        System.out.println(jSongs.get(0).getTitle().getText());
        return jSongs;
    }

    public int searchAlbumIndex(String text) {
        return user.searchAlbums(text);
    }

    public void createNewPlaylistFrame() {
        ArrayList<String> songNames = getLibrarySongsNames();
        CreateNewPlaylist createNewPlaylist = new CreateNewPlaylist(this, songNames);
        createNewPlaylist.setVisible(true);
    }

    public ArrayList<String> getLibrarySongsNames() {
        ArrayList<String> songNames = new ArrayList<>();
        for (Song s : user.getLibrarySongs())
            songNames.add(s.getTitle());
        return songNames;
    }

    public void createNewPlaylist(String text, ArrayList<Integer> indexes) {
        ArrayList<Song> songs = new ArrayList<>();
        for (Integer i : indexes) {
            songs.add(user.getLibrarySongs().get(i));
        }
        int lastIndex = user.newPlaylist(text, songs);
        mainView.getLeftPanelView().getPlaylistBar().refreshList(lastIndex, text);
        changeCenterPanel(PLAYLIST, lastIndex);
////        for (Playlist p :
////                user.getPlaylists()) {
////            System.out.println(p.getName() + ": " + p.getSongs());
////        }
    }

    public String getPlayListName(int index) {
        return user.getPlaylists().get(index).getName();
    }

    public boolean isPlaylistChangeable(int index) {
        return user.getPlaylists().get(index).isChangeable();
    }

    public HashMap<Integer, String> getPlayLists() {
        HashMap<Integer, String> ret = new HashMap<>();
        int i = 0;
        for (Playlist p : user.getPlaylists()) {
            System.out.println(p.getName());
            ret.put(i++, p.getName());
        }
        return ret;
    }

    public void addSongToPlaylist(ArrayList<Integer> indexes) {
//        ArrayList<Song> songs = new ArrayList<>();
        int index = user.getPlaylists().indexOf(user.getCurrentSelectedListInGUI());
        for (Integer i : indexes) {
//            songs.add(user.getLibrarySongs().get(i));
            user.getPlaylists().get(index).addSong(user.getLibrarySongs().get(i));
        }
        if (index == 1) {
            user.tellOthersAboutMyShared();
        }
//        int lastIndex = user.newPlaylist(text, songs);
//        mainView.getLeftPanelView().getPlaylistBar().refreshList();
//        mainView.getLeftPanelView().getPlaylistBar().getPlayLists().setSelectedIndex(2);
        mainView.getLeftPanelView().getPlaylistBar().revalidate();
//        mainView.getCenterPanelView().displayPanel(PLAYLIST, index);
        changeCenterPanel(PLAYLIST, index);
    }

    public void removePlaylist() {
        user.removePlaylist();
        if (user.getCurrentSelectedListInGUI() == user.getCurrentList()) {
            player.stop();
            user.setCurrentSelectedListInGUI(null);
            GUIChangeForSongStop();
        }
        mainView.getCenterPanelView().displayPanel(BLANPAGE, 0);
        mainView.getLeftPanelView().refreshPlaylistBar();
    }

    public void setRepeat(RepeatRule repeatRule) {
        user.setRepeatRule(repeatRule);
    }

    public void turnShuffleOff() {
        user.turnShuffleOff();
        user.unshuffleCurrentSelected();
    }

    public boolean turnShuffleOn() {
        if (user.turnShuffleOn()) {
            user.shuffleCurrentSelected();
        }
        return true;
    }

    public void addNewUser(String host, String port) {
        //TODO create a new User, create a new user(then creates its friend in get connected users), Add Friend to the FriendsBarView, refresh everything
        user.addAndConnectRemoteClient(host, Integer.parseInt(port));
        refreshFriendsBar();
    }

    public void refreshFriendsBar() {
        mainView.getRightPanelView().getFriendsBarView().setFriends(getConnectedUsers());
    }

    public void showFriendPlaylist(String name, String host, int port) {
        List<Song> songs = user.getOthersSharedPlaylists().get(new RemoteClient(host, port)).getSongs();
        ArrayList<String> songsNames = new ArrayList<>();
        for (Song s : songs)
            songsNames.add(s.getTitle());
        PlaylistList playlist = new PlaylistList(this, name, songsNames, host, port);
        playlist.setVisible(true);
        curentFriend = playlist;
        changeCenterPanel(NETWORK, 0);
    }

    public PlaylistList getNetworkPlaylist() {
        return curentFriend;
    }

    public ArrayList<Friend> getConnectedUsers() {
        //Todo gets all users and sends the re newed user list to the friendsbarvie *here we create Friends items*
        ArrayList<Friend> ret = new ArrayList<>();
        for (RemoteClient r : user.getRemoteClients()) {
            Friend f = new Friend(this, r.getName(), r.getHost(), r.getPort());
//            System.out.println(user.getOthersRecentlyPlayed().get(r));
            Song song = user.getOthersRecentlyPlayed().get(r).getCurrentSong();
            if (song != null) {
                f.setCurrentSongTitle(song.getTitle());
                f.setState("now playing");
            } else {
                f.setCurrentSongTitle("");
                f.setState("minutes ago");
            }
//            long s = Instant.now().toEpochMilli() - user.getOthersRecentlyPlayed().get(r).getCurrentSong().getLastPlayed();
//            s = s/1000;
////            f.setState(Integer.toString(s));
            ret.add(f);
        }
        return ret;
    }

    public void deleteSongFromPlaylist(int songIndex) {
        int playlistIndex = user.getPlaylists().indexOf(user.getCurrentSelectedListInGUI());
        user.removeSongFromPlaylist(playlistIndex, songIndex);
        if (playlistIndex == 1) {
            user.tellOthersAboutMyShared();
        }
        mainView.getLeftPanelView().getPlaylistBar().revalidate();
        changeCenterPanel(PLAYLIST, playlistIndex);
    }

    public void changePositionOfTheSongInPlaylist(boolean moveUp, int songIndex) {
        int playlistIndex = user.getPlaylists().indexOf(user.getCurrentSelectedListInGUI());
        if (moveUp) {
            user.getPlaylists().get(playlistIndex).moveUp(songIndex);
        } else {
            user.getPlaylists().get(playlistIndex).moveDown(songIndex);
        }
        if (playlistIndex == 1) {
            user.tellOthersAboutMyShared();
        }
        mainView.getLeftPanelView().getPlaylistBar().revalidate();
        changeCenterPanel(PLAYLIST, playlistIndex);
    }

    public void changePlaylistName(String newName) {
        int playlistIndex = user.getPlaylists().indexOf(user.getCurrentSelectedListInGUI());
        user.getPlaylists().get(playlistIndex).setName(newName);
        mainView.getLeftPanelView().refreshPlaylistBar();
        changeCenterPanel(PLAYLIST, playlistIndex);
    }


    public void muteVolume() {
        muted = !muted;
        if (muted) {
            if (player != null)
                player.setVolume(-80);
            mainView.getBottomPanelView().getVolumeControlPanelView().getVolumeSlider().setValue(-80);
        } else {
            if (player != null)
                player.setVolume(volume);
            mainView.getBottomPanelView().getVolumeControlPanelView().getVolumeSlider().setValue(volume);
        }
    }

    public String downloadSong(String host, int port, int selectedIndex) {
        Song song = user.getOthersSharedPlaylists().get(new RemoteClient(host, port)).getSongs().get(selectedIndex);
        String name = song.getAddress().getName();
        name = name.substring(0, name.lastIndexOf('.'));
        try {
            return "Downloaded to " + FileHelper.downloadSong("./Downloads/", name, host, port, selectedIndex);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "Download Failed";
    }
}
