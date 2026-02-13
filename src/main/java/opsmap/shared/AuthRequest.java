package opsmap.shared;

import java.io.Serializable;

public class AuthRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String username;
    private final String password;
    private final String position;
    private final boolean registration;

    public AuthRequest(String username, String password, String position, boolean registration) {
        this.username = username;
        this.password = password;
        this.position = position;
        this.registration = registration;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getPosition() {
        return position;
    }

    public boolean isRegistration() {
        return registration;
    }
}
