package opsmap.server;

import opsmap.shared.AuthRequest;
import opsmap.shared.AuthResponse;
import opsmap.shared.DrawingOperation;
import opsmap.shared.Message;
import opsmap.shared.MessageType;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class ClientHandler extends Thread {
    private final Socket socket;
    private final OpsMapServer server;
    private ObjectOutputStream outputStream;
    private String username;

    // Create a handler for a new client socket.
    public ClientHandler(Socket socket, OpsMapServer server) {
        this.socket = socket;
        this.server = server;
    }

    @Override
    // Read messages from a single client connection.
    public void run() {
        try (ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream inputStream = new ObjectInputStream(socket.getInputStream())) {
            outputStream = output;
            while (!socket.isClosed()) {
                // Read and dispatch messages until the socket closes.
                Object incoming = inputStream.readObject();
                if (incoming instanceof Message message) {
                    handleMessage(message);
                }
            }
        } catch (IOException | ClassNotFoundException exception) {
            System.err.println("Client disconnected: " + exception.getMessage());
        } finally {
            server.removeClient(username);
            try {
                socket.close();
            } catch (IOException ignored) {
                // Ignore close failures.
            }
        }
    }

    // Dispatch a message based on its type.
    private void handleMessage(Message message) {
        try {
            switch (message.getType()) {
                case AUTH_REQUEST -> handleAuth((AuthRequest) message.getPayload());
                case DRAWING_ADD -> handleDrawingAdd((DrawingOperation) message.getPayload());
                case DRAWING_REMOVE -> handleDrawingRemove((String) message.getPayload());
                // Forward simple real-time updates as-is.
                case POINTER_UPDATE, CHAT_MESSAGE -> handleForward(message);
                default -> sendMessage(new Message(MessageType.ERROR, "Unsupported message type.", "server"));
            }
        } catch (ClassCastException exception) {
            sendMessage(new Message(MessageType.ERROR, "Invalid payload type.", "server"));
        }
    }

    // Authenticate a user and store the username.
    private void handleAuth(AuthRequest request) {
        AuthResponse response = server.handleAuth(request, this);
        if (response.isSuccess()) {
            this.username = response.getUsername();
        }
        sendMessage(new Message(MessageType.AUTH_RESPONSE, response, "server"));
    }

    // Send a message to this client safely.
    public synchronized void sendMessage(Message message) {
        if (outputStream == null) {
            return;
        }
        try {
            outputStream.writeObject(message);
            outputStream.flush();
        } catch (IOException exception) {
            System.err.println("Failed to send message: " + exception.getMessage());
        }
    }

    private void handleDrawingAdd(DrawingOperation drawing) {
        if (ensureAuthenticated()) {
            return;
        }
        DrawingOperation sanitized = new DrawingOperation(
                drawing.getId(),
                username,
                drawing.getToolType(),
                drawing.getPoints(),
                drawing.getX(),
                drawing.getY(),
                drawing.getWidth(),
                drawing.getHeight(),
                drawing.getRadius(),
                drawing.getText(),
                drawing.getColor(),
                drawing.getTextSize(),
                drawing.getThickness()
        );
        server.registerDrawing(sanitized);
    }

    private void handleDrawingRemove(String drawingId) {
        if (ensureAuthenticated()) {
            return;
        }
        server.removeDrawing(drawingId, username);
    }

    private boolean ensureAuthenticated() {
        if (username == null) {
            sendMessage(new Message(MessageType.ERROR, "Authenticate before sending updates.", "server"));
            return true;
        }
        return false;
    }

    private void handleForward(Message message) {
        if (ensureAuthenticated()) {
            return;
        }
        Message sanitized = new Message(message.getType(), message.getPayload(), username);
        server.broadcast(sanitized);
    }
}
