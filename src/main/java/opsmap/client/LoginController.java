package opsmap.client;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import opsmap.shared.AuthRequest;
import opsmap.shared.AuthResponse;
import opsmap.shared.Message;
import opsmap.shared.MessageType;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.net.URL;

public class LoginController {
    @FXML
    private TextField usernameField;

    @FXML
    private TextField positionField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private Label statusLabel;

    private final ClientConnection connection = new ClientConnection();
    private boolean connected;
    private final List<Message> pendingMessages = new ArrayList<>();
    private OperationsController operationsController;

    @FXML
    // Wire the message handler once the UI is ready.
    private void initialize() {
        connection.setHandler(this::handleMessage);
    }

    @FXML
    // Log in with the provided credentials.
    private void handleLogin() {
        handleAuth(false);
    }

    @FXML
    // Register a new account with the provided details.
    private void handleRegister() {
        handleAuth(true);
    }

    // Send an auth request to the server (login or registration).
    private void handleAuth(boolean registration) {
        String username = usernameField.getText();
        String password = passwordField.getText();
        String position = positionField.getText();

        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            statusLabel.setText("Username and password are required.");
            return;
        }

        if (registration && (position == null || position.isBlank())) {
            statusLabel.setText("Position is required for registration.");
            return;
        }

        try {
            if (!connected) {
                connection.connect("localhost", 5050);
                connected = true;
            }
            AuthRequest request = new AuthRequest(username, password, position, registration);
            connection.send(new Message(MessageType.AUTH_REQUEST, request, username));
        } catch (IOException exception) {
            statusLabel.setText("Unable to connect to server.");
        }
    }

    // Handle server responses and queue messages until the room opens.
    private void handleMessage(Message message) {
        Platform.runLater(() -> {
            if (message.getType() == MessageType.AUTH_RESPONSE) {
                AuthResponse response = (AuthResponse) message.getPayload();
                if (response.isSuccess()) {
                    openOperationsRoom(response.getUsername(), response.getPosition());
                    if (operationsController != null && !pendingMessages.isEmpty()) {
                        operationsController.replayMessages(new ArrayList<>(pendingMessages));
                        pendingMessages.clear();
                    }
                } else {
                    statusLabel.setText(response.getMessage());
                }
            } else if (message.getType() == MessageType.ERROR) {
                statusLabel.setText(String.valueOf(message.getPayload()));
                connected = false;
                connection.close();
            } else {
                pendingMessages.add(message);
            }
        });
    }

    // Swap from the login screen to the operations room.
    private void openOperationsRoom(String username, String position) {
        try {
            URL location = LoginController.class.getResource("/fxml/operations.fxml");
            if (location == null) {
                statusLabel.setText("Unable to load operations.fxml (missing resource).");
                return;
            }
            // Load the operations room UI and pass the connection to it.
            FXMLLoader loader = new FXMLLoader(location);
            Parent root = loader.load();
            OperationsController controller = loader.getController();
            operationsController = controller;
            controller.bindConnection(connection, username, position);
            Stage stage = (Stage) usernameField.getScene().getWindow();
            stage.setTitle("OpsMap - Operations Room");
            stage.setScene(new Scene(root));
            stage.setWidth(1200);
            stage.setHeight(768);
            stage.centerOnScreen();
        } catch (IOException exception) {
            statusLabel.setText("Unable to load operations room.");
        }
    }
}
