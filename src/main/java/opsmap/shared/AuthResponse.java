package opsmap.shared;

import java.io.Serializable;

public class AuthResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    private final boolean success;
    private final String message;
    private final String username;
    private final String position;

    public AuthResponse(boolean success, String message, String username, String position) {
        this.success = success;
        this.message = message;
        this.username = username;
        this.position = position;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public String getUsername() {
        return username;
    }

    public String getPosition() {
        return position;
    }
}
