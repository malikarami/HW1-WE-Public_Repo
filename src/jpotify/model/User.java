package jpotify.model;

import helper.FileHelper;
import helper.StringHelper;
import jpotify.controller.MainController;
import jpotify.model.Network.Server;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.util.*;

public class User implements Serializable {

    private static final long serialVersionUID = 4879049040173323873L;
    private String name;

    private SongList library;
    private ArrayList<Playlist> playlists;
    private HashMap<String, Album> albums;
    private transient SongList currentList;
    private transient SongList currentSelectedListInGUI;
    private transient Playlist shuffledOriginal;
    private transient RecentlyPlayedPlaylist recentlyPlayed = new RecentlyPlayedPlaylist();
    private RepeatRule repeatRule = RepeatRule.OFF;
    private transient boolean shuffled = false;

    private static transient Server server;
    private static transient int myPort;
    private transient ArrayList<String> allowedIPs = new ArrayList<>();
    private transient HashSet<RemoteClient> remoteClients;
    private transient HashMap<RemoteClient, NetworkPlaylist> othersSharedPlaylists = new HashMap<>();
    private transient HashMap<RemoteClient, RecentlyPlayedPlaylist> othersRecentlyPlayed = new HashMap<>();

    {
        try {
            allowedIPs = FileHelper.ipFileToArrayList("ip_list.txt");
        } catch (FileNotFoundException e) {
            allowedIPs = new ArrayList<>();
//            e.printStackTrace();
        }

        library = new SongList();

        playlists = new ArrayList<>();
        playlists.add(new Playlist("Favorites", false));
        playlists.add(new Playlist("Shared", false));

        albums = new HashMap<>();
    }

    public User(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public Playlist getSharedPlaylist() {
        return playlists.get(1);
    }

    public HashSet<RemoteClient> getRemoteClients() {
        if (remoteClients == null)
            remoteClients = new HashSet<>();
        return remoteClients;
    }

    public ArrayList<String> getAllowedIPs() {
        if (allowedIPs == null)
            try {
                allowedIPs = FileHelper.ipFileToArrayList("ip_list.txt");
            } catch (FileNotFoundException e) {
                allowedIPs = new ArrayList<>();
//            e.printStackTrace();
            }
        return allowedIPs;
    }

    public RecentlyPlayedPlaylist getRecentlyPlayed() {
        if (recentlyPlayed == null)
            recentlyPlayed = new RecentlyPlayedPlaylist();
        return recentlyPlayed;
    }

    public HashMap<RemoteClient, RecentlyPlayedPlaylist> getOthersRecentlyPlayed() {
        if (othersRecentlyPlayed == null)
            othersRecentlyPlayed = new HashMap<>();
        return othersRecentlyPlayed;
    }

    /**
     * Adds or replaces a friend recently played song to list
     *
     * @param remoteClient friends info (host, ip, name)
     * @param otherList    friends new list
     */
    public void addOthersRecentlyPlayed(RemoteClient remoteClient, RecentlyPlayedPlaylist otherList) {
        if (otherList == null)
            return;
        if (othersRecentlyPlayed == null)
            othersRecentlyPlayed = new HashMap<>();
        othersRecentlyPlayed.put(remoteClient, otherList);
    }

    /**
     * Plays a song from current list
     *
     * @param index song index in the current list
     * @return Song object that should be played by Jlayer
     */
    public Song playSong(int index) {
        if (currentList instanceof NetworkPlaylist) throw new Error("Don't use this method over network!");
        Song ret = currentList.songs.get(index);
        ret.updateLastPlayed();
        if (getSharedPlaylist().getSongs().contains(ret)) {
            getRecentlyPlayed().setCurrentSong(ret);
            tellOthersAboutMyRecent();
        }
        currentList.current = ret;
        return ret;
    }

    /**
     * Fetches song from network and plays it
     *
     * @param index song index in the current list
     * @param host  which song should be fetched from
     * @param port  which song should be fetched from
     * @return Song object that should be played by Jlayer
     * @throws IOException if cannot fetch song
     */
    public Song playSongFromNetwork(int index, String host, int port) throws IOException {
        currentList.current = currentList.songs.get(index);
        return new Song(new File(FileHelper.downloadSong("data/tmp/", StringHelper.randomString(10), host, port, index)));
    }

    /**
     * Plays song object
     *
     * @param song song to be played
     */
    public void playSong(Song song) {
        song.updateLastPlayed();
        if (getSharedPlaylist().getSongs().contains(song)) {
            getRecentlyPlayed().setCurrentSong(song);
            tellOthersAboutMyRecent();
        }
    }

    /**
     * Stops current playing song
     */
    public void stopSong() {
        getRecentlyPlayed().removeCurrentSong();
        tellOthersAboutMyRecent();
    }

    /**
     * Adds new Song to library
     *
     * @param song Song to be added
     */
    public void addSong(Song song) {
        if (library.songs.contains(song)) return;
        library.songs.add(song);
        String album = song.getAlbum();
        if (album == null) return;
        Album albumReference;
        if (albums.containsKey(album)) {
            albumReference = albums.get(album);
            albumReference.addSong(song);
        } else {
            albumReference = new Album(song);
            albums.put(album, albumReference);
        }
        song.setAlbumReference(albumReference);
    }

    /**
     * Removes Song from library
     *
     * @param songIndex song index in library
     */
    public boolean removeSongFromLibrary(int songIndex) {
        if (getCurrentList().current == getLibrarySongs().get(songIndex)) return false;
        Song song = getLibrarySongs().get(songIndex);
        for (int i = playlists.size() - 1; i >= 0; i--) {
            playlists.get(i).removeSong(song);
        }
        Album songAlbum = song.getAlbumReference();
        if (songAlbum != null) {
            if (songAlbum.songs.size() == 1)
                albums.remove(songAlbum.getName());
            else songAlbum.removeSong(song);
        }
        getLibrarySongs().remove(songIndex);
        return true;
    }

    /**
     * Removes song from selected playlist
     *
     * @param playlistIndex playlist index which song should be removed from it
     * @param songIndex     song index in playlist
     * @return true if was able to delete song. false if song is currently playing and cannot be deleted
     */
    public boolean removeSongFromPlaylist(int playlistIndex, int songIndex) {
        if (getCurrentList().current == playlists.get(playlistIndex).getSongs().get(songIndex)) return false;
        playlists.get(playlistIndex).removeSong(songIndex);
        return true;
    }

    /**
     * Changes selected playlist name
     *
     * @param playlistIndex playlist index which name should be changed
     * @param newName       new name for selected playlist
     */
    public void changePlaylistName(int playlistIndex, String newName) {
        playlists.get(playlistIndex).setName(newName);
    }

    public List<Song> getLibrarySongs() {
        Collections.sort(library.songs);
        return library.songs;
    }

    public SongList getLibrary() {
        return library;
    }

    public List<Album> getAlbums() {
        ArrayList<Album> ret = new ArrayList<>(albums.values());
        Collections.sort(ret);
        return ret;
    }

    /**
     * Search for an album name returns it's index
     *
     * @param name album name
     * @return album index
     */
    public int searchAlbums(String name) {
        List<Album> albums = getAlbums();
        for (Album a : albums) {
            if (a.getName().equals(name))
                return albums.indexOf(a);
        }
        return -1;
    }

    public SongList getCurrentList() {
        if (currentList == null)
            currentList = new SongList();
        return currentList;
    }

    public SongList getCurrentSelectedListInGUI() {
        return currentSelectedListInGUI;
    }

    public Playlist newPlaylist(String name) {
        Playlist ret = new Playlist(name, true);
        playlists.add(ret);
        return ret;
    }

    /**
     * Creates new playlist
     *
     * @param name  playlist name
     * @param songs playlist songs
     * @return playlist index in list
     */
    public int newPlaylist(String name, ArrayList<Song> songs) {
        Playlist newList = new Playlist(name, songs);
        playlists.add(newList);
        return playlists.size() - 1;
    }

    /**
     * Adds or replaces a friend shared playlist
     *
     * @param remoteClient Friends info (ip, port, name)
     * @param playlist     Friends shared playlist
     */
    public void addSharedPlaylist(RemoteClient remoteClient, NetworkPlaylist playlist) {
        if (playlist == null)
            return;
        getOthersSharedPlaylists().put(remoteClient, playlist);
    }

//    public void removeSharedPlaylist(int index) {
//        if (othersSharedPlaylists == null)
//            othersSharedPlaylists = new ArrayList<>();
//        othersSharedPlaylists.remove(index);
//    }

//    public void removeSharedPlaylist(NetworkPlaylist playlist) {
//        if (othersSharedPlaylists == null)
//            othersSharedPlaylists = new HashMap<>();
//        othersSharedPlaylists.remove(playlist);
//    }

    public HashMap<RemoteClient, NetworkPlaylist> getOthersSharedPlaylists() {
        if (othersSharedPlaylists == null)
            othersSharedPlaylists = new HashMap<>();
        return othersSharedPlaylists;
    }

    /**
     * Deletes a playlist
     */
    public void removePlaylist() {
        playlists.remove(getCurrentSelectedListInGUI());
    }

    public ArrayList<Playlist> getPlaylists() {
        return playlists;
    }

//    Shuffle, Repeat, Next and Previous, Set current list

    /**
     * Change selected list in GUI
     *
     * @param currentSelectedListInGUI
     */
    public void setCurrentSelectedListInGUI(SongList currentSelectedListInGUI) {
        this.currentSelectedListInGUI = currentSelectedListInGUI;
    }

    /**
     * Sets current selected list in GUI for playing songs
     */
    public void setCurrentList() {
        currentList = currentSelectedListInGUI;
    }

    /**
     * Shuffles playing playlist songs
     */
    public void shuffleCurrentSelected() {
        if (currentList instanceof Playlist) {
            shuffledOriginal = (Playlist) currentList;
            currentList = ((Playlist) currentList).getShuffled();
        }
    }

    /**
     * Unshuffle recently shuffled list
     */
    public void unshuffleCurrentSelected() {
        if (currentList instanceof Playlist && shuffledOriginal != null) {
            currentList = shuffledOriginal;
            shuffledOriginal = null;
        }
    }

    /**
     * turns shuffling on
     *
     * @return true if current selected list is playlist and can be shuffled
     */
    public boolean turnShuffleOn() {
        if (currentList instanceof Playlist) {
            shuffledOriginal = (Playlist) currentList;
            currentList = ((Playlist) currentList).getShuffled();
            return shuffled = true;
        }
        return false;
    }

    /**
     * turns shuffling off
     */
    public void turnShuffleOff() {
        shuffled = false;
    }

    public boolean isShuffled() {
        return shuffled;
    }

    public void setRepeatRule(RepeatRule repeatRule) {
        this.repeatRule = repeatRule;
    }

    /**
     * Next song in list. return current playing again if repeat is on
     *
     * @return next Song
     */
    public Song next() {
        return currentList.next(repeatRule);
    }

    /**
     * Next song in list
     *
     * @return next Song
     */
    public Song forceNext() {
        return currentList.next(repeatRule == RepeatRule.REPEAT_ONE ? RepeatRule.REPEAT : repeatRule);
    }

    /**
     * Previous song in list
     *
     * @return previous Song
     */
    public Song previous() {
        return currentList.previous(repeatRule);
    }

//    Network methods

    /**
     * Starts HTTP Server for networking
     *
     * @param controller Controller object to be called later
     * @param port       Server running port
     */
    public void startHttpServer(MainController controller, int port) {
        myPort = port;
        if (server == null) {
            try {
                server = new Server(controller, this, port);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            server.changeUser(this);
        }
//        startConnectionToOthers(port);
    }

    /**
     * Stops HTTP Server
     */
    public void stopHttpServer() {
        server.stopServer();
    }

    /**
     * Updates other user recently played list after it changed
     */
    private void tellOthersAboutMyRecent() {
        for (RemoteClient r :
                getRemoteClients()) {
            try {
                URL url = new URL("http://" + r.getHost() + ":" + r.getPort() + "/updateRecent");
                URLConnection connection = url.openConnection();
                connection.setDoOutput(true);
                ObjectOutputStream out = new ObjectOutputStream(connection.getOutputStream());
                out.writeInt(myPort);
                out.writeObject(name);
                out.writeObject(getRecentlyPlayed());
                out.flush();
                out.close();
                connection.getInputStream().close();
            } catch (IOException e) {
//                e.printStackTrace();
            }
        }
    }

    /**
     * Updates other user shared list after it changed
     */
    public void tellOthersAboutMyShared() {
        for (RemoteClient r :
                getRemoteClients()) {
            try {
                URL url = new URL("http://" + r.getHost() + ":" + r.getPort() + "/updatePlaylist");
                URLConnection connection = url.openConnection();
                connection.setDoOutput(true);
                ObjectOutputStream out = new ObjectOutputStream(connection.getOutputStream());
                out.writeInt(myPort);
                out.writeObject(name);
                out.writeObject(getSharedPlaylist());
                out.flush();
                out.close();
                connection.getInputStream().close();
            } catch (IOException e) {
//                e.printStackTrace();
            }
        }
    }

    private void startConnectionToOthers(int port) {
        for (RemoteClient r :
                getRemoteClients()) {
            connectToClient(port, r);
        }
    }

    public void addRemoteClient(String host, int port) {
        RemoteClient client = new RemoteClient(host, port);
        remoteClients.add(client);
    }

    /**
     * Adds friend to list
     *
     * @param host friend's host
     * @param port friend's port
     * @param name friend's name
     */
    public void addRemoteClient(String host, int port, String name) {
        RemoteClient client = new RemoteClient(host, port, name);
        remoteClients.add(client);
    }

    /**
     * Adds friend to list and tries to connect to him/her
     *
     * @param host friend's host
     * @param port friend's port
     * @param name friend's name
     */
    public void addAndConnectRemoteClient(String host, int port) {
        RemoteClient client = new RemoteClient(host, port);
        remoteClients.add(client);
        client.setName(connectToClient(myPort, client));
    }

    /**
     * Starts connection to friend
     *
     * @param myPort current user running port
     * @param client friends info (host, port)
     * @return friends name
     */
    private String connectToClient(int myPort, RemoteClient client) {
        try {
            URL url = new URL("http://" + client.getHost() + ":" + client.getPort() + "/connect");
            URLConnection connection = url.openConnection();
            connection.setDoOutput(true);
            ObjectOutputStream out = new ObjectOutputStream(connection.getOutputStream());
            out.writeInt(myPort);
            out.writeObject(name);
            out.writeObject(getSharedPlaylist());
            out.writeObject(getRecentlyPlayed());
            out.flush();
            out.close();
            ObjectInputStream in = new ObjectInputStream(connection.getInputStream());
            String userName = (String) in.readObject();
            RemoteClient namedClient = new RemoteClient(client.getHost(), client.getPort(), userName);
            this.addSharedPlaylist(namedClient, new NetworkPlaylist(((Playlist) in.readObject()).getSongs(), client.getHost(), client.getPort()));
            this.addOthersRecentlyPlayed(namedClient, (RecentlyPlayedPlaylist) in.readObject());
            in.close();
            return userName;
        } catch (IOException | ClassNotFoundException e) {
//                e.printStackTrace();
        }
        return null;
    }
}
