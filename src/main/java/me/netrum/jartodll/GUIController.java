package me.netrum.jartodll;

import javafx.application.Platform;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.KeyEvent;
import me.netrum.jartodll.base.Jar2DLL;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class GUIController {
    @FXML
    private Button process;

    @FXML
    private TextField input, output, cmakePath, mainClass;

    @FXML
    private CheckBox saveSource;

    @FXML
    private ProgressBar progressBar;

    @FXML
    private void onClick() {
        process.setDisable(true);
        process.setText("Processing...");

        new Thread(() -> {
            try {
                Jar2DLL.doThing(new String[]{
                        "--input", input.getText(), "--output", output.getText(),
                        "--cmakePath", cmakePath.getText(),
                        "--entryPoint", mainClass.getText(),
                        "--saveSource", String.valueOf(saveSource.isSelected())
                });

                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
                    alert.setTitle("Done");
                    alert.setHeaderText("The DLL was saved successfully.");
                    alert.showAndWait();
                });

            } catch (Exception e) {
                e.printStackTrace();

                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Error");
                    alert.setHeaderText("Failed to create the DLL.");
                    alert.showAndWait();
                });
            }

            Platform.runLater(() -> {
                progressBar.setProgress(0);
                process.setText("Start");
                process.setDisable(false);
            });
        }).start();
    }

    @FXML
    private void onKeyPressed(KeyEvent event) throws IOException {
        if(!event.getCode().isLetterKey() && !event.getCode().isDigitKey()) return;

        String text = input.getText() + event.getText();
        Path path = Paths.get(text);

        if(Files.exists(path) && text.endsWith(".jar")){
            String entryPoint = Jar2DLL.getEntryPoint(path);
            mainClass.setText(entryPoint == null ? "" : entryPoint);
        }
    }

    public void initialize(){
        Jar2DLL.progressBar = progressBar;
    }
}
