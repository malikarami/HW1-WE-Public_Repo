package jpotify.model.Network;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import jpotify.model.NetworkPlaylist;
import jpotify.model.Playlist;
import jpotify.model.RemoteClient;
import jpotify.model.User;

import java.io.IOException;
import java.io.ObjectInputStream;

public class updatePlaylistHandler implements HttpHandler, ChangeableUser {

    private User user;

    public updatePlaylistHandler(User user) {
        this.user = user;
    }

    @Override
    public void changeUser(User user) {
        this.user = user;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String host = exchange.getRemoteAddress().getHostString();
        if (user.getAllowedIPs().contains(exchange.getRemoteAddress().getHostString())) {
            ObjectInputStream in = new ObjectInputStream(exchange.getRequestBody());
            int port = in.readInt();
            String userName = null;
            NetworkPlaylist playlist = null;
            try {
                userName = (String) in.readObject();
                playlist = new NetworkPlaylist(((Playlist) in.readObject()).getSongs(), host, port);
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
            user.addSharedPlaylist(new RemoteClient(host, port, userName), playlist);
            exchange.sendResponseHeaders(200, 0);
            exchange.close();
        }
        exchange.sendResponseHeaders(403, 0);
        exchange.close();
    }
}
