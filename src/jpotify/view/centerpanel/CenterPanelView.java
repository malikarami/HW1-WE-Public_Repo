package jpotify.view.centerpanel;

import jpotify.controller.MainController;
import jpotify.view.MainView;
import jpotify.view.rightpanel.PlaylistList;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;

public class CenterPanelView extends JPanel {

    private MainController controller;
    public static final int WIDTH = 800, ELEMENTS = 210;
    private SongsPanel songsPanel;
    private AlbumsPanel albumsPanel;
    private PlaylistPanel playlistPanel;
    private JScrollPane scrollPane;
    private PlaylistList networkList;
    private SongsPanel albumSongPanel;

    public CenterPanelView(MainController mainController) {
        controller = mainController;
        setPreferredSize(new Dimension(WIDTH, MainView.HEIGHT));
        setLayout(new BorderLayout());
        this.setBackground(new Color(14,14, 14));

        Border border1 = BorderFactory.createMatteBorder(5,15,10,15, new Color(14,14,14));
        Border border2 = BorderFactory.createMatteBorder(1,0,0,0, Color.lightGray);
        this.setBorder(BorderFactory.createCompoundBorder(border1, border2));

        setVisible(true);
    }

    public void displayPanel(int mode, int index)  {
        controller.setCurrentMode(mode, index);
        if (mode == MainController.MYSONG){
            this.removeAll();
            System.out.println("song");
            try {
                songsPanel = new SongsPanel(controller, controller.getJSongs(mode, 0));
            } catch (IOException e) {
                e.printStackTrace();
            }
            songsPanel.setVisible(true);
            scrollPane = new JScrollPane(songsPanel);
            this.add(scrollPane, BorderLayout.CENTER);
            this.revalidate();
        }
        else if (mode == MainController.ALBUMS){
            System.out.println("album");
            this.removeAll();
            try {
                albumsPanel = new AlbumsPanel(controller, controller.getJAlbum());
            } catch (IOException e) {
                e.printStackTrace();
            }
            albumsPanel.setVisible(true);
            scrollPane = new JScrollPane(albumsPanel);
            this.add(scrollPane, BorderLayout.CENTER);
//            this.setBackground(Color.MAGENTA);
            this.revalidate();
        }
        else if(mode == MainController.JALBUM){
            System.out.println("selcted album pressed");
            this.removeAll();
            try {
                albumSongPanel = new SongsPanel(controller, controller.getJSongs(mode, index));
            } catch (IOException e) {
                e.printStackTrace();
            }
            albumsPanel.setVisible(true);
            scrollPane = new JScrollPane(albumSongPanel);
            this.add(scrollPane, BorderLayout.CENTER);
            this.revalidate();
        }
        else if(mode == MainController.PLAYLIST){
            System.out.println("playlist");
            this.removeAll();
            ArrayList<JPlaylistSong> playlistSongs = controller.getJPlaylistSong(index);
            playlistPanel = new PlaylistPanel(controller, controller.getPlayListName(index), playlistSongs, controller.isPlaylistChangeable(index));
            playlistPanel.setVisible(true);
//            scrollPane = new JScrollPane(playlistPanel);
            this.add(playlistPanel);
            this.revalidate();
        }
        else if(mode == MainController.BLANPAGE){
            System.out.println("blank page");
            this.removeAll();
            this.add(new CenterPanelView(controller));
            this.revalidate();
        }
        else if(mode == MainController.NETWORK){
            System.out.println("network");
            this.removeAll();
            networkList = controller.getNetworkPlaylist();
            this.add(networkList);
            this.revalidate();
        }
    }

}
