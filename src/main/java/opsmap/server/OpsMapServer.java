package opsmap.server;

import opsmap.shared.AuthRequest;
import opsmap.shared.AuthResponse;
import opsmap.shared.DrawingOperation;
import opsmap.shared.MapState;
import opsmap.shared.Message;
import opsmap.shared.MessageType;
import opsmap.shared.UserList;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class OpsMapServer {
    private static final int DEFAULT_PORT = 5050;
    private final Map<String, String> credentials = new ConcurrentHashMap<>();
    private final Map<String, String> positions = new ConcurrentHashMap<>();
    private final Map<String, ClientHandler> clients = new ConcurrentHashMap<>();
    private final List<DrawingOperation> drawings = new CopyOnWriteArrayList<>();
    // Boot the server with the chosen port.
    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        if (args.length > 0) {
            port = Integer.parseInt(args[0]);
        }
        OpsMapServer server = new OpsMapServer();
        server.start(port);
    }

    // Start the server socket and accept clients.
    public void start(int port) {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("OpsMap server running on port " + port);
            while (true) {
                // Accept each client and handle it in a new thread.
                Socket socket = serverSocket.accept();
                ClientHandler handler = new ClientHandler(socket, this);
                handler.start();
            }
        } catch (IOException exception) {
            System.err.println("Server error: " + exception.getMessage());
        }
    }

    // Validate credentials and send the initial state.
    public AuthResponse handleAuth(AuthRequest request, ClientHandler handler) {
        if (request.getUsername() == null || request.getUsername().isBlank()) {
            return new AuthResponse(false, "Username is required.", null, null);
        }
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            return new AuthResponse(false, "Password is required.", null, null);
        }

        String username = request.getUsername();

        if (clients.containsKey(username)) {
            return new AuthResponse(false, "User is already logged in.", null, null);
        }
        if (request.isRegistration()) {
            if (credentials.containsKey(username)) {
                return new AuthResponse(false, "Username already exists.", null, null);
            }
            if (request.getPosition() == null || request.getPosition().isBlank()) {
                return new AuthResponse(false, "Position is required.", null, null);
            }
            credentials.put(username, request.getPassword());
            positions.put(username, request.getPosition());
        } else if (!request.getPassword().equals(credentials.get(username))) {
            return new AuthResponse(false, "Invalid credentials.", null, null);
        }

        clients.put(username, handler);
        sendUserList(handler);
        broadcastUserListExcept(username);
        // Send the current drawings in one state sync.
        handler.sendMessage(new Message(MessageType.STATE_SYNC, new MapState(new ArrayList<>(drawings)), "server"));
        return new AuthResponse(true, "Authenticated.", username, positions.get(username));
    }

    // Store a drawing and broadcast it to all clients.
    public void registerDrawing(DrawingOperation drawing) {
        drawings.add(drawing);
        broadcast(new Message(MessageType.DRAWING_ADD, drawing, drawing.getOwner()));
    }

    // Remove a drawing owned by the given user.
    public void removeDrawing(String drawingId, String username) {
        drawings.removeIf(drawing -> drawing.getId().equals(drawingId) && drawing.getOwner().equals(username));
        broadcast(new Message(MessageType.DRAWING_REMOVE, drawingId, username));
    }

    // Broadcast a message to all connected clients.
    public void broadcast(Message message) {
        for (ClientHandler handler : clients.values()) {
            handler.sendMessage(message);
        }
    }

    // Remove a client and update the user list.
    public void removeClient(String username) {
        if (username != null) {
            clients.remove(username);
            broadcastUserList();
        }
    }

    // Send the full user list to everyone.
    private void broadcastUserList() {
        broadcast(buildUserListMessage());
    }

    // Send the user list to everyone except one user.
    private void broadcastUserListExcept(String username) {
        Message message = buildUserListMessage();
        for (Map.Entry<String, ClientHandler> entry : clients.entrySet()) {
            if (!entry.getKey().equals(username)) {
                entry.getValue().sendMessage(message);
            }
        }
    }

    // Send the user list to a single client.
    private void sendUserList(ClientHandler handler) {
        handler.sendMessage(buildUserListMessage());
    }

    private Message buildUserListMessage() {
        List<String> usernames = new ArrayList<>(clients.keySet());
        Collections.sort(usernames);
        return new Message(MessageType.USER_LIST, new UserList(usernames), "server");
    }

    // Generate a new drawing ID.
    public String newDrawingId() {
        return UUID.randomUUID().toString();
    }
}
