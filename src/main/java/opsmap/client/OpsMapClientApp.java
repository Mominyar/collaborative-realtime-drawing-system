package opsmap.client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.net.URL;

public class OpsMapClientApp extends Application {
    @Override
    // Create the primary stage and show the login UI.
    public void start(Stage stage) throws Exception {
        URL location = OpsMapClientApp.class.getResource("/fxml/login.fxml");
        if (location == null) {
            throw new IllegalStateException("Unable to load /fxml/login.fxml. Ensure resources are on the classpath.");
        }
        // The app starts on the login screen.
        FXMLLoader loader = new FXMLLoader(location);
        Scene scene = new Scene(loader.load());
        stage.setTitle("OpsMap - Login");
        // Show the login screen first.
        stage.setScene(scene);
        stage.show();
    }

    // Launch the JavaFX application.
    public static void main(String[] args) {
        launch(args);
    }
}
