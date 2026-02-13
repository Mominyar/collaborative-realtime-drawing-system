package opsmap.shared;

import java.io.Serializable;
import java.time.Instant;

public class ChatMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String username;
    private final String message;
    private final Instant timestamp;

    public ChatMessage(String username, String message, Instant timestamp) {
        this.username = username;
        this.message = message;
        this.timestamp = timestamp;
    }

    public String getUsername() {
        return username;
    }

    public String getMessage() {
        return message;
    }

    public Instant getTimestamp() {
        return timestamp;
    }
}
