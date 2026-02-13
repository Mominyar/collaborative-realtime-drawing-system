package opsmap.shared;

import java.io.Serializable;
import java.util.List;

public class DrawingOperation implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String id;
    private final String owner;
    private final ToolType toolType;
    private final List<PointData> points;
    private final double x;
    private final double y;
    private final double width;
    private final double height;
    private final double radius;
    private final String text;
    private final ColorData color;
    private final int textSize;
    private final int thickness;

    public DrawingOperation(
            String id,
            String owner,
            ToolType toolType,
            List<PointData> points,
            double x,
            double y,
            double width,
            double height,
            double radius,
            String text,
            ColorData color,
            int textSize,
            int thickness
    ) {
        this.id = id;
        this.owner = owner;
        this.toolType = toolType;
        this.points = points;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.radius = radius;
        this.text = text;
        this.color = color;
        this.textSize = textSize;
        this.thickness = thickness;
    }

    public String getId() {
        return id;
    }

    public String getOwner() {
        return owner;
    }

    public ToolType getToolType() {
        return toolType;
    }

    public List<PointData> getPoints() {
        return points;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getWidth() {
        return width;
    }

    public double getHeight() {
        return height;
    }

    public double getRadius() {
        return radius;
    }

    public String getText() {
        return text;
    }

    public ColorData getColor() {
        return color;
    }

    public int getTextSize() {
        return textSize;
    }

    public int getThickness() {
        return thickness;
    }
}