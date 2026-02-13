package opsmap.shared;

import java.io.Serializable;
import java.util.List;

public class MapState implements Serializable {
    private static final long serialVersionUID = 1L;

    // All drawings currently on the map.
    private final List<DrawingOperation> drawings;
    public MapState(List<DrawingOperation> drawings) {
        this.drawings = drawings;
    }

    public List<DrawingOperation> getDrawings() {
        return drawings;
    }

}