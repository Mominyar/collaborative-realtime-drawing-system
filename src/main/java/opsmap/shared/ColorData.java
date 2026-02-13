package opsmap.shared;

import java.io.Serializable;

public class ColorData implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int red;
    private final int green;
    private final int blue;

    public ColorData(int red, int green, int blue) {
        this.red = clamp(red);
        this.green = clamp(green);
        this.blue = clamp(blue);
    }

    public int getRed() {
        return red;
    }

    public int getGreen() {
        return green;
    }

    public int getBlue() {
        return blue;
    }

    private int clamp(int value) {
        if (value < 0) {
            return 0;
        }
        if (value > 255) {
            return 255;
        }
        return value;
    }
}
