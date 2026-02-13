package opsmap.shared;

import java.io.Serial;
import java.io.Serializable;

public class PointData implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final double x;
    private final double y;

    public PointData(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }
}
