package jpotify.model;

import helper.FileHelper;
import helper.StringHelper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class NetworkPlaylist extends Playlist {
    private static final long serialVersionUID = 5262883635031145552L;
    private String host;
    private int port;
    private List<Song> shuffledOriginal;

    public NetworkPlaylist(List<Song> songs, String host, int port) {
        this(songs, host, port, songs);
    }

    public NetworkPlaylist(List<Song> songs, String host, int port, List<Song> shuffledOriginal) {
        super(songs);
        this.host = host;
        this.port = port;
        this.shuffledOriginal = shuffledOriginal;
        for (Song s :
                songs) {
            s.useDefaultArtwork();
        }
    }

    @Override
    public Song next(RepeatRule rule) {
        Song ret = super.next(rule);
        return getSong(ret);
    }

    @Override
    public Song previous(RepeatRule rule) {
        Song ret = super.previous(rule);
        return getSong(ret);
    }

    private Song getSong(Song ret) {
        if (ret == null) return null;
        try {
            ret = new Song(new File(FileHelper.downloadSong("data/tmp/", StringHelper.randomString(10), host, port, shuffledOriginal.indexOf(ret))));
            return ret;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public Playlist getShuffled() {
        Playlist ret = new NetworkPlaylist(new ArrayList<>(songs), host, port, songs);
        Collections.shuffle(ret.songs);
        ret.current = this.current;
        return ret;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NetworkPlaylist that = (NetworkPlaylist) o;
        return port == that.port &&
                host.equals(that.host);
    }

    @Override
    public int hashCode() {
        return Objects.hash(host, port);
    }
}
