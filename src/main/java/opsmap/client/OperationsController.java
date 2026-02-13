package opsmap.client;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.canvas.Canvas;
import javafx.scene.Node;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Polyline;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.geometry.VPos;
import opsmap.shared.ChatMessage;
import opsmap.shared.ColorData;
import opsmap.shared.DrawingOperation;
import opsmap.shared.MapState;
import opsmap.shared.Message;
import opsmap.shared.MessageType;
import opsmap.shared.PointData;
import opsmap.shared.PointerUpdate;
import opsmap.shared.ToolType;
import opsmap.shared.UserList;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class OperationsController {
    private static final int[] THICKNESS_OPTIONS = {2, 4, 6, 8};
    private static final double GRID_SIZE = 25.0;

    @FXML
    private Pane mapPane;

    @FXML
    private ListView<String> userListView;

    @FXML
    private TextArea chatLog;

    @FXML
    private TextArea chatInput;

    @FXML
    private Label statusLabel;

    @FXML
    private Label userNameLabel;

    @FXML
    private Label userPositionLabel;

    @FXML
    private Button penButton;

    @FXML
    private Button arrowButton;

    @FXML
    private Button circleButton;

    @FXML
    private Button rectangleButton;

    @FXML
    private Button pinButton;

    @FXML
    private Button textButton;

    @FXML
    private Button eraserButton;

    @FXML
    private ComboBox<Integer> thicknessCombo;

    @FXML
    private ComboBox<Integer> textSizeCombo;

    @FXML
    private Button colorWhiteButton;

    @FXML
    private Button colorBlackButton;

    @FXML
    private Button colorRedButton;

    @FXML
    private Button colorYellowButton;

    @FXML
    private Button colorGreenButton;

    @FXML
    private Button colorBlueButton;

    @FXML
    private Button colorBrownButton;

    @FXML
    private Button colorGrayButton;

    private final Map<String, javafx.scene.Node> drawingNodes = new HashMap<>();
    private final Map<String, String> drawingOwners = new HashMap<>();
    private final Map<String, javafx.scene.Node> pointerNodes = new HashMap<>();

    private ClientConnection connection;
    private String username;
    private ToolType currentTool = ToolType.PEN;
    private javafx.scene.Node activeNode;
    private double startX;
    private double startY;
    private Canvas gridCanvas;
    private Rectangle clipRect;
    private Color currentColor = Color.BLACK;

    // Bind the client connection and set up user labels.
    public void bindConnection(ClientConnection connection, String username, String position) {
        this.connection = connection;
        this.username = username;
        connection.setHandler(this::handleMessage);
        statusLabel.setText("Connected as " + username);
        String safePosition = position == null ? "" : position;
        userNameLabel.setText(username);
        userPositionLabel.setText(safePosition);
    }

    // Replay queued messages that arrived before the UI was ready.
    public void replayMessages(List<Message> messages) {
        for (Message message : messages) {
            handleMessage(message);
        }
    }

    @FXML
    // Initialize UI controls, tools, and drawing layers.
    private void initialize() {
        thicknessCombo.setItems(FXCollections.observableArrayList(2, 4, 6, 8));
        thicknessCombo.getSelectionModel().select(Integer.valueOf(4));
        textSizeCombo.setItems(FXCollections.observableArrayList(12, 16, 20, 24));
        textSizeCombo.getSelectionModel().select(Integer.valueOf(16));

        penButton.setOnAction(event -> setTool(ToolType.PEN));
        arrowButton.setOnAction(event -> setTool(ToolType.ARROW));
        circleButton.setOnAction(event -> setTool(ToolType.CIRCLE));
        rectangleButton.setOnAction(event -> setTool(ToolType.RECTANGLE));
        pinButton.setOnAction(event -> setTool(ToolType.PIN));
        textButton.setOnAction(event -> setTool(ToolType.TEXT));
        eraserButton.setOnAction(event -> setTool(null));
        mapPane.addEventHandler(MouseEvent.MOUSE_PRESSED, this::handleMousePressed);
        mapPane.addEventHandler(MouseEvent.MOUSE_DRAGGED, this::handleMouseDragged);
        mapPane.addEventHandler(MouseEvent.MOUSE_RELEASED, this::handleMouseReleased);
        mapPane.addEventHandler(MouseEvent.MOUSE_MOVED, this::handlePointerMoved);

        // Order matters: grid below drawings.
        setupGrid();
        setupClip();
        setupColorPalette();
    }

    @FXML
    // Send the chat text to the server.
    private void sendChatMessage() {
        if (chatInput.getText() == null || chatInput.getText().isBlank()) {
            return;
        }
        ChatMessage message = new ChatMessage(username, chatInput.getText(), Instant.now());
        sendMessage(new Message(MessageType.CHAT_MESSAGE, message, username));
        chatInput.clear();
    }

    // Switch the active drawing tool.
    private void setTool(ToolType tool) {
        this.currentTool = tool;
        // Null tool means we are in eraser mode.
        if (tool == null) {
            statusLabel.setText("Eraser selected.");
        } else {
            statusLabel.setText(tool + " tool selected.");
        }
    }

    // Prepare the grid canvas and keep it resized.
    private void setupGrid() {
        gridCanvas = new Canvas(mapPane.getPrefWidth(), mapPane.getPrefHeight());
        // Keep the grid below drawings.
        mapPane.getChildren().add(0, gridCanvas);
        mapPane.widthProperty().addListener((obs, oldVal, newVal) -> resizeGrid());
        mapPane.heightProperty().addListener((obs, oldVal, newVal) -> resizeGrid());
        drawGrid();
    }

    // Clip drawings to the map pane bounds.
    private void setupClip() {
        clipRect = new Rectangle();
        mapPane.setClip(clipRect);
        updateClip();
        mapPane.widthProperty().addListener((obs, oldVal, newVal) -> updateClip());
        mapPane.heightProperty().addListener((obs, oldVal, newVal) -> updateClip());
    }

    // Update the clip rectangle to match the pane.
    private void updateClip() {
        clipRect.setWidth(mapPane.getWidth());
        clipRect.setHeight(mapPane.getHeight());
    }

    // Wire color buttons to the current brush color.
    private void setupColorPalette() {
        colorWhiteButton.setOnAction(event -> setColor(255, 255, 255));
        colorBlackButton.setOnAction(event -> setColor(0, 0, 0));
        colorRedButton.setOnAction(event -> setColor(255, 0, 0));
        colorYellowButton.setOnAction(event -> setColor(255, 255, 0));
        colorGreenButton.setOnAction(event -> setColor(0, 128, 0));
        colorBlueButton.setOnAction(event -> setColor(0, 0, 255));
        colorBrownButton.setOnAction(event -> setColor(139, 69, 19));
        colorGrayButton.setOnAction(event -> setColor(128, 128, 128));
    }

    // Update the currently selected drawing color.
    private void setColor(int red, int green, int blue) {
        currentColor = Color.rgb(red, green, blue);
    }

    // Resize the grid when the pane changes size.
    private void resizeGrid() {
        // Keep the grid aligned with the visible map pane.
        gridCanvas.setWidth(mapPane.getWidth());
        gridCanvas.setHeight(mapPane.getHeight());
        drawGrid();
    }

    // Draw the grid overlay and optional tint.
    private void drawGrid() {
        GraphicsContext gc = gridCanvas.getGraphicsContext2D();
        gc.clearRect(0, 0, gridCanvas.getWidth(), gridCanvas.getHeight());
        gc.setStroke(Color.LIGHTGRAY);
        gc.setLineWidth(0.5);
        for (double x = 0; x < gridCanvas.getWidth(); x += GRID_SIZE) {
            gc.strokeLine(x, 0, x, gridCanvas.getHeight());
        }
        for (double y = 0; y < gridCanvas.getHeight(); y += GRID_SIZE) {
            gc.strokeLine(0, y, gridCanvas.getWidth(), y);
        }
        gc.setFill(Color.rgb(245, 245, 245, 0.4));
        gc.fillRect(0, 0, gridCanvas.getWidth(), gridCanvas.getHeight());
    }

    // Start a drawing operation or erase on mouse press.
    private void handleMousePressed(MouseEvent event) {
        startX = event.getX();
        startY = event.getY();

        // Ignore clicks outside the drawing area.
        if (!isInsideDrawingArea(startX, startY)) {
            return;
        }

        // When no tool is active, treat this as an erase action.
        if (currentTool == null) {
            attemptErase(event.getX(), event.getY());
            return;
        }

        switch (currentTool) {
            case PEN -> {
                Polyline polyline = new Polyline();
                polyline.setStroke(getCurrentColor());
                polyline.setStrokeWidth(getCurrentThickness());
                polyline.getPoints().addAll(startX, startY);
                activeNode = polyline;
                mapPane.getChildren().add(polyline);
            }
            case RECTANGLE -> {
                Rectangle rectangle = new Rectangle(startX, startY, 0, 0);
                rectangle.setStroke(getCurrentColor());
                rectangle.setFill(Color.TRANSPARENT);
                rectangle.setStrokeWidth(getCurrentThickness());
                activeNode = rectangle;
                mapPane.getChildren().add(rectangle);
            }
            case CIRCLE -> {
                Circle circle = new Circle(startX, startY, 0);
                circle.setStroke(getCurrentColor());
                circle.setFill(Color.TRANSPARENT);
                circle.setStrokeWidth(getCurrentThickness());
                activeNode = circle;
                mapPane.getChildren().add(circle);
            }
            case ARROW -> {
                Polyline polyline = new Polyline();
                polyline.setStroke(getCurrentColor());
                polyline.setStrokeWidth(getCurrentThickness());
                polyline.getPoints().addAll(startX, startY, startX, startY);
                activeNode = polyline;
                mapPane.getChildren().add(polyline);
            }
            case PIN -> {
                DrawingOperation operation = buildPinOperation(startX, startY);
                javafx.scene.Node pin = createNode(operation);
                finalizeDrawing(operation, pin);
            }
            case TEXT -> createTextBox(startX, startY);
            default -> {
            }
        }
    }

    // Update the active shape as the mouse drags.
    private void handleMouseDragged(MouseEvent event) {
        if (activeNode == null) {
            return;
        }
        // Only draw when the pointer stays inside the drawing area.
        if (!isInsideDrawingArea(event.getX(), event.getY())) {
            return;
        }
        if (activeNode instanceof Polyline polyline) {
            if (currentTool == ToolType.ARROW && polyline.getPoints().size() >= 4) {
                // Rebuild the arrow so the head stays attached while dragging.
                PointData start = new PointData(polyline.getPoints().get(0), polyline.getPoints().get(1));
                PointData end = new PointData(event.getX(), event.getY());
                Polyline arrow = buildArrowPolyline(start, end, getCurrentThickness());
                polyline.getPoints().setAll(arrow.getPoints());
            } else {
                // Pen tool keeps adding points as you drag.
                polyline.getPoints().addAll(event.getX(), event.getY());
            }
        } else if (activeNode instanceof Rectangle rectangle) {
            double width = event.getX() - startX;
            double height = event.getY() - startY;
            rectangle.setWidth(Math.abs(width));
            rectangle.setHeight(Math.abs(height));
            rectangle.setX(Math.min(startX, event.getX()));
            rectangle.setY(Math.min(startY, event.getY()));
        } else if (activeNode instanceof Circle circle) {
            Bounds bounds = mapPane.getLayoutBounds();
            double clampedX = clamp(event.getX(), bounds.getMinX(), bounds.getMaxX());
            double clampedY = clamp(event.getY(), bounds.getMinY(), bounds.getMaxY());
            double deltaX = clampedX - startX;
            double deltaY = clampedY - startY;
            double size = Math.min(Math.abs(deltaX), Math.abs(deltaY));
            double left = deltaX < 0 ? startX - size : startX;
            double top = deltaY < 0 ? startY - size : startY;
            circle.setCenterX(left + size / 2);
            circle.setCenterY(top + size / 2);
            circle.setRadius(size / 2);
        }
    }

    // Finalize the active shape on mouse release.
    private void handleMouseReleased(MouseEvent event) {
        if (activeNode == null) {
            return;
        }
        // If you release outside, cancel the current shape.
        if (!isInsideDrawingArea(event.getX(), event.getY())) {
            activeNode = null;
            return;
        }
        if (activeNode instanceof Polyline polyline) {
            if (currentTool == ToolType.ARROW && polyline.getPoints().size() >= 2) {
                PointData start = new PointData(polyline.getPoints().get(0), polyline.getPoints().get(1));
                PointData end = new PointData(event.getX(), event.getY());
                Polyline arrow = buildArrowPolyline(start, end, getCurrentThickness());
                polyline.getPoints().setAll(arrow.getPoints());
            }
            DrawingOperation operation = currentTool == ToolType.ARROW
                    ? buildArrowOperation(polyline)
                    : buildPenOperation(polyline);
            if (currentTool == ToolType.ARROW) {
                applyArrowHead(polyline, operation);
            }
            finalizeDrawing(operation, polyline);
        } else if (activeNode instanceof Rectangle rectangle) {
            DrawingOperation operation = buildRectangleOperation(rectangle);
            finalizeDrawing(operation, rectangle);
        } else if (activeNode instanceof Circle circle) {
            DrawingOperation operation = buildCircleOperation(circle);
            finalizeDrawing(operation, circle);
        }
        activeNode = null;
    }

    // Create a floating text box and convert it to a drawing.
    private void createTextBox(double x, double y) {
        Pane container = new Pane();
        container.setLayoutX(x - 14);
        container.setLayoutY(y - 14);

        TextArea textArea = new TextArea();
        textArea.setPrefRowCount(2);
        textArea.setPrefWidth(200);
        textArea.setPrefHeight(60);
        textArea.setLayoutX(14);
        textArea.setLayoutY(14);

        Rectangle handle = new Rectangle(14, 14, Color.DARKGRAY);
        handle.setArcWidth(4);
        handle.setArcHeight(4);

        container.getChildren().addAll(handle, textArea);
        mapPane.getChildren().add(container);

        final double[] dragOffset = new double[2];
        handle.setOnMousePressed(event -> {
            dragOffset[0] = event.getX();
            dragOffset[1] = event.getY();
            event.consume();
        });
        handle.setOnMouseDragged(event -> {
            Point2D point = mapPane.sceneToLocal(event.getSceneX(), event.getSceneY());
            if (!isInsideDrawingArea(point.getX(), point.getY())) {
                return;
            }
            container.setLayoutX(point.getX() - dragOffset[0]);
            container.setLayoutY(point.getY() - dragOffset[1]);
            event.consume();
        });

        textArea.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!isFocused) {
                String content = textArea.getText();
                mapPane.getChildren().remove(container);
                if (content != null && !content.isBlank()) {
                    int textSize = getCurrentTextSize();
                    double textX = container.getLayoutX() + textArea.getLayoutX();
                    double textY = container.getLayoutY() + textArea.getLayoutY();
                    Text textNode = new Text(textX, textY, content.trim());
                    textNode.setFont(Font.font(textSize));
                    textNode.setTextOrigin(VPos.TOP);
                    textNode.setFill(getCurrentColor());
                    DrawingOperation operation = createOperation(
                            ToolType.TEXT,
                            List.of(),
                            textNode.getX(),
                            textNode.getY(),
                            0,
                            0,
                            0,
                            content.trim(),
                            textSize
                    );
                    finalizeDrawing(operation, textNode);
                }
            }
        });

        textArea.requestFocus();
    }

    // Erase the topmost drawing at the given point.
    private void attemptErase(double x, double y) {
        // Walk from top to bottom so the topmost item is erased first.
        for (int i = mapPane.getChildren().size() - 1; i >= 0; i--) {
            javafx.scene.Node node = mapPane.getChildren().get(i);
            String drawingId = findDrawingId(node);
            if (drawingId == null) {
                continue;
            }
            if (node.getBoundsInParent().contains(x, y)) {
                String owner = drawingOwners.get(drawingId);
                if (!username.equals(owner)) {
                    statusLabel.setText("You can only erase your own drawings.");
                    return;
                }
                mapPane.getChildren().remove(node);
                drawingNodes.remove(drawingId);
                drawingOwners.remove(drawingId);
                sendMessage(new Message(MessageType.DRAWING_REMOVE, drawingId, username));
                return;
            }
        }
    }

    // Find the drawing ID that matches the given node.
    private String findDrawingId(javafx.scene.Node node) {
        for (Map.Entry<String, javafx.scene.Node> entry : drawingNodes.entrySet()) {
            if (entry.getValue() == node) {
                return entry.getKey();
            }
        }
        return null;
    }

    // Build a drawing operation from a pen polyline.
    private DrawingOperation buildPenOperation(Polyline polyline) {
        List<PointData> points = new ArrayList<>();
        for (int i = 0; i < polyline.getPoints().size(); i += 2) {
            points.add(new PointData(polyline.getPoints().get(i), polyline.getPoints().get(i + 1)));
        }
        return createOperation(ToolType.PEN, points, 0, 0, 0, 0, 0, null, 0);
    }

    // Build a drawing operation from an arrow polyline.
    private DrawingOperation buildArrowOperation(Polyline polyline) {
        if (polyline.getPoints().size() < 4) {
            return buildPenOperation(polyline);
        }
        int endIndex = polyline.getPoints().size() >= 4 ? 2 : polyline.getPoints().size() - 2;
        List<PointData> points = List.of(
                new PointData(polyline.getPoints().get(0), polyline.getPoints().get(1)),
                new PointData(polyline.getPoints().get(endIndex),
                        polyline.getPoints().get(endIndex + 1))
        );
        return createOperation(ToolType.ARROW, points, 0, 0, 0, 0, 0, null, 0);
    }

    // Build a drawing operation for a pin at the click point.
    private DrawingOperation buildPinOperation(double x, double y) {
        return createOperation(ToolType.PIN, List.of(), x, y, 0, 0, 0, null, 0);
    }

    // Build a drawing operation from a rectangle shape.
    private DrawingOperation buildRectangleOperation(Rectangle rectangle) {
        return createOperation(
                ToolType.RECTANGLE,
                List.of(),
                rectangle.getX(),
                rectangle.getY(),
                rectangle.getWidth(),
                rectangle.getHeight(),
                0,
                null,
                0
        );
    }

    // Build a drawing operation from a circle shape.
    private DrawingOperation buildCircleOperation(Circle circle) {
        return createOperation(
                ToolType.CIRCLE,
                List.of(),
                circle.getCenterX(),
                circle.getCenterY(),
                0,
                0,
                circle.getRadius(),
                null,
                0
        );
    }

    private DrawingOperation createOperation(
            ToolType toolType,
            List<PointData> points,
            double x,
            double y,
            double width,
            double height,
            double radius,
            String text,
            int textSize
    ) {
        return new DrawingOperation(
                UUID.randomUUID().toString(),
                username,
                toolType,
                points,
                x,
                y,
                width,
                height,
                radius,
                text,
                toColorData(getCurrentColor()),
                textSize,
                getCurrentThickness()
        );
    }

    // Store a drawing locally and broadcast it to the server.
    private void finalizeDrawing(DrawingOperation operation, javafx.scene.Node node) {
        drawingNodes.put(operation.getId(), node);
        drawingOwners.put(operation.getId(), operation.getOwner());
        if (!mapPane.getChildren().contains(node)) {
            mapPane.getChildren().add(node);
        }
        sendMessage(new Message(MessageType.DRAWING_ADD, operation, username));
    }

    // Handle incoming server messages on the UI thread.
    private void handleMessage(Message message) {
        Platform.runLater(() -> {
            switch (message.getType()) {
                case USER_LIST -> updateUsers((UserList) message.getPayload());
                case STATE_SYNC -> loadState((MapState) message.getPayload());
                case CHAT_MESSAGE -> appendChat((ChatMessage) message.getPayload());
                case DRAWING_ADD -> renderDrawing((DrawingOperation) message.getPayload());
                case DRAWING_REMOVE -> removeDrawing((String) message.getPayload());
                case POINTER_UPDATE -> updatePointer((PointerUpdate) message.getPayload());
                case ERROR -> statusLabel.setText(String.valueOf(message.getPayload()));
                default -> {
                }
            }
        });
    }

    // Update the user list and clean up missing pointers.
    private void updateUsers(UserList list) {
        userListView.getItems().setAll(list.getUsernames());
        List<String> activeUsers = list.getUsernames();
        List<String> removedUsers = new ArrayList<>();
        for (String user : pointerNodes.keySet()) {
            if (!activeUsers.contains(user)) {
                removedUsers.add(user);
            }
        }
        for (String user : removedUsers) {
            Node pointer = pointerNodes.remove(user);
            if (pointer != null) {
                mapPane.getChildren().remove(pointer);
            }
        }
    }

    // Load drawings from a state sync.
    private void loadState(MapState state) {
        clearDrawings();
        for (DrawingOperation drawing : state.getDrawings()) {
            renderDrawing(drawing);
        }
        statusLabel.setText("Loaded " + state.getDrawings().size() + " drawings.");
    }

    // Append a chat line to the chat log.
    private void appendChat(ChatMessage message) {
        chatLog.appendText("[" + message.getTimestamp() + "] " + message.getUsername() + ": " + message.getMessage() + "\n");
    }

    // Render a drawing operation if it is not already shown.
    private void renderDrawing(DrawingOperation operation) {
        if (drawingNodes.containsKey(operation.getId())) {
            return;
        }
        // Create the JavaFX node from the stored drawing data.
        javafx.scene.Node node = createNode(operation);
        if (node == null) {
            return;
        }
        drawingNodes.put(operation.getId(), node);
        drawingOwners.put(operation.getId(), operation.getOwner());
        mapPane.getChildren().add(node);
    }

    // Convert a drawing operation into a JavaFX node.
    private javafx.scene.Node createNode(DrawingOperation operation) {
        Color color = Color.rgb(operation.getColor().getRed(), operation.getColor().getGreen(), operation.getColor().getBlue());
        switch (operation.getToolType()) {
            case PEN -> {
                Polyline polyline = new Polyline();
                for (PointData point : operation.getPoints()) {
                    polyline.getPoints().addAll(point.getX(), point.getY());
                }
                polyline.setStroke(color);
                polyline.setStrokeWidth(operation.getThickness());
                return polyline;
            }
            case RECTANGLE -> {
                // Rectangles are stored by top-left, width, and height.
                Rectangle rectangle = new Rectangle(operation.getX(), operation.getY(), operation.getWidth(), operation.getHeight());
                rectangle.setStroke(color);
                rectangle.setStrokeWidth(operation.getThickness());
                rectangle.setFill(Color.TRANSPARENT);
                return rectangle;
            }
            case CIRCLE -> {
                // Circles are stored by center and radius.
                Circle circle = new Circle(operation.getX(), operation.getY(), operation.getRadius());
                circle.setStroke(color);
                circle.setStrokeWidth(operation.getThickness());
                circle.setFill(Color.TRANSPARENT);
                return circle;
            }
            case TEXT -> {
                // Text size comes from the operation, or a default.
                int textSize = operation.getTextSize() > 0 ? operation.getTextSize() : 16;
                Text text = new Text(operation.getX(), operation.getY(), operation.getText());
                text.setFill(color);
                text.setFont(Font.font(textSize));
                text.setTextOrigin(VPos.TOP);
                return text;
            }
            case ARROW -> {
                // The arrow stores start/end points and rebuilds its shape here.
                if (operation.getPoints().size() < 2) {
                    return null;
                }
                PointData start = operation.getPoints().get(0);
                PointData end = operation.getPoints().get(1);
                Polyline arrow = buildArrowPolyline(start, end, operation.getThickness());
                arrow.setStroke(color);
                arrow.setStrokeWidth(operation.getThickness());
                return arrow;
            }
            case PIN -> {
                // The click point is the needle tip.
                double radius = 10;
                double needleEndY = operation.getY();
                double needleStartY = needleEndY - (radius + 14);
                Circle head = new Circle(operation.getX(), needleStartY - radius, radius);
                head.setFill(color);
                head.setStroke(Color.BLACK);
                javafx.scene.shape.Line needle = new javafx.scene.shape.Line(
                        operation.getX(),
                        needleStartY,
                        operation.getX(),
                        needleEndY
                );
                needle.setStroke(Color.DARKGRAY);
                needle.setStrokeWidth(2);
                return new javafx.scene.Group(head, needle);
            }
            default -> {
                return null;
            }
        }
    }

    // Remove a drawing node by its ID.
    private void removeDrawing(String drawingId) {
        javafx.scene.Node node = drawingNodes.remove(drawingId);
        drawingOwners.remove(drawingId);
        if (node != null) {
            mapPane.getChildren().remove(node);
        }
    }

    // Show or move another user's pointer.
    private void updatePointer(PointerUpdate update) {
        if (update.getUsername().equals(username)) {
            return;
        }
        javafx.scene.Node pointer = pointerNodes.get(update.getUsername());
        if (pointer == null) {
            Circle dot = new Circle(4, Color.DARKBLUE);
            Text name = new Text(update.getUsername());
            name.setFont(Font.font(13));
            name.setTextOrigin(VPos.BOTTOM);
            name.boundsInLocalProperty().addListener((obs, oldBounds, newBounds) ->
                    name.setTranslateX(-newBounds.getWidth() / 2));
            name.setTranslateY(-12);
            javafx.scene.Group group = new javafx.scene.Group(dot, name);
            pointer = group;
            pointerNodes.put(update.getUsername(), pointer);
            mapPane.getChildren().add(pointer);
        }
        pointer.setLayoutX(update.getX());
        pointer.setLayoutY(update.getY());
    }

    // Clear all rendered drawings from the map pane.
    private void clearDrawings() {
        mapPane.getChildren().removeIf(node -> drawingNodes.containsValue(node));
        drawingNodes.clear();
        drawingOwners.clear();
    }

    // Send pointer position updates while moving.
    private void handlePointerMoved(MouseEvent event) {
        if (connection == null || username == null) {
            return;
        }
        if (!isInsideDrawingArea(event.getX(), event.getY())) {
            return;
        }
        PointerUpdate update = new PointerUpdate(username, event.getX(), event.getY());
        sendMessage(new Message(MessageType.POINTER_UPDATE, update, username));
    }

    // Send a message to the server with error handling.
    private void sendMessage(Message message) {
        try {
            connection.send(message);
        } catch (Exception exception) {
            statusLabel.setText("Failed to send message.");
        }
    }

    // Return the current drawing color.
    private Color getCurrentColor() {
        return currentColor;
    }

    // Return the current brush thickness.
    private int getCurrentThickness() {
        return getComboValueOrDefault(thicknessCombo, THICKNESS_OPTIONS[0]);
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    // Return the current text size.
    private int getCurrentTextSize() {
        return getComboValueOrDefault(textSizeCombo, 16);
    }

    private int getComboValueOrDefault(ComboBox<Integer> comboBox, int fallback) {
        Integer value = comboBox.getValue();
        return value == null ? fallback : value;
    }

    // Replace the polyline points with a full arrow shape.
    private void applyArrowHead(Polyline polyline, DrawingOperation operation) {
        if (operation.getPoints().size() < 2) {
            return;
        }
        PointData start = operation.getPoints().get(0);
        PointData end = operation.getPoints().get(1);
        Polyline arrow = buildArrowPolyline(start, end, operation.getThickness());
        polyline.getPoints().setAll(arrow.getPoints());
    }

    // Build an arrow polyline from start/end points.
    private Polyline buildArrowPolyline(PointData start, PointData end, int thickness) {
        double dx = end.getX() - start.getX();
        double dy = end.getY() - start.getY();
        double angle = Math.atan2(dy, dx);
        double arrowLength = 12 + thickness;
        double arrowAngle = Math.toRadians(25);
        double x1 = end.getX() - arrowLength * Math.cos(angle - arrowAngle);
        double y1 = end.getY() - arrowLength * Math.sin(angle - arrowAngle);
        double x2 = end.getX() - arrowLength * Math.cos(angle + arrowAngle);
        double y2 = end.getY() - arrowLength * Math.sin(angle + arrowAngle);
        Polyline arrow = new Polyline();
        arrow.getPoints().addAll(
                start.getX(), start.getY(),
                end.getX(), end.getY(),
                x1, y1,
                end.getX(), end.getY(),
                x2, y2
        );
        return arrow;
    }

    // Convert a JavaFX color to serializable color data.
    private ColorData toColorData(Color color) {
        return new ColorData(
                (int) (color.getRed() * 255),
                (int) (color.getGreen() * 255),
                (int) (color.getBlue() * 255)
        );
    }

    // Check if a point is within the drawing area.
    private boolean isInsideDrawingArea(double x, double y) {
        return mapPane.getLayoutBounds().contains(x, y);
    }
}
