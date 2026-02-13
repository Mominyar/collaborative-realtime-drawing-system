package opsmap.shared;

import java.io.Serializable;

public class PointerUpdate implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String username;
    private final double x;
    private final double y;

    public PointerUpdate(String username, double x, double y) {
        this.username = username;
        this.x = x;
        this.y = y;
    }

    public String getUsername() {
        return username;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }
}
