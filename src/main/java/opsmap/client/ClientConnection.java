package opsmap.client;

import opsmap.shared.Message;
import opsmap.shared.MessageType;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.function.Consumer;

public class ClientConnection {
    private Socket socket;
    private ObjectOutputStream outputStream;
    private ObjectInputStream inputStream;
    private Consumer<Message> handler;

    // Connect to the server and start the listener thread.
    public void connect(String host, int port) throws IOException {
        socket = new Socket(host, port);
        outputStream = new ObjectOutputStream(socket.getOutputStream());
        inputStream = new ObjectInputStream(socket.getInputStream());
        Thread listener = new Thread(this::listen, "OpsMap-Listener");
        listener.setDaemon(true);
        listener.start();
    }

    // Set the callback that handles incoming messages.
    public void setHandler(Consumer<Message> handler) {
        this.handler = handler;
    }

    // Send a message to the server if connected.
    public synchronized void send(Message message) throws IOException {
        if (outputStream != null) {
            outputStream.writeObject(message);
            outputStream.flush();
        }
    }

    // Close the connection and ignore close errors.
    public void close() {
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException ignored) {
            // Ignore close failures.
        }
    }

    // Read messages from the server and forward them to the handler.
    private void listen() {
        try {
            while (socket != null && !socket.isClosed()) {
                Object incoming = inputStream.readObject();
                if (incoming instanceof Message message && handler != null) {
                    handler.accept(message);
                }
            }
        } catch (IOException | ClassNotFoundException exception) {
            if (handler != null) {
                handler.accept(new Message(MessageType.ERROR, "Connection lost.", "server"));
            }
        } finally {
            close();
        }
    }
}
