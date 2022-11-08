package me.netrum.jartodll;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import me.netrum.jartodll.base.Jar2DLL;

import java.io.IOException;

public class Main extends Application {
    public static void main(String[] args) throws Exception {
        if(args.length != 0){
            Jar2DLL.doThing(args);
        }else launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        primaryStage.setScene(new Scene(FXMLLoader.load(Main.class.getResource("/gui.fxml"))));
        primaryStage.setTitle("Jar2DLL");
        primaryStage.getIcons().add(new Image(Main.class.getResourceAsStream("/logo.jpg")));
        primaryStage.setResizable(false);


        primaryStage.show();
    }
}
